/*
 * Copyright 2026 - 2026 Dmitri Snytkine. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.snytkine.apitester.api_tester_cli.ui;

import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.event.TestStatus;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.jline.tui.component.ViewComponent;
import org.springframework.shell.jline.tui.component.ViewComponentBuilder;
import org.springframework.shell.jline.tui.component.message.ShellMessageBuilder;
import org.springframework.shell.jline.tui.component.view.control.BoxView;
import org.springframework.shell.jline.tui.component.view.control.GridView;
import org.springframework.shell.jline.tui.component.view.screen.Screen;

/**
 * Drives the Spring Shell {@link GridView}-based terminal UI for a single suite run, with Braille
 * spinner animation for in-progress tests.
 *
 * <p>Lifecycle for one suite run:
 *
 * <ol>
 *   <li>{@link #start()} — starts a background controller thread that blocks on the event queue.
 *   <li>The thread receives {@link TestProgressEvent.SuiteStarted} and builds a {@link GridView}
 *       with one row per test, pre-populated with "pending" text.
 *   <li>On {@link TestProgressEvent.TestStarted}: the row transitions to "running" state. The
 *       render loop then advances a per-row Braille spinner frame on every tick ({@value
 *       #POLL_TIMEOUT_MS} ms) and dispatches a redraw, producing smooth animation.
 *   <li>On {@link TestProgressEvent.TestCompleted}: the row is updated with the final status glyph
 *       and duration, and spinner animation for that row stops.
 *   <li>{@link TestProgressEvent.SuiteCompleted} signals the loop to exit, the view component is
 *       stopped, and the controller thread terminates. After the view exits, a one-line summary
 *       (Phase 7) is printed to {@link #output}; any test failures are listed below the summary.
 *   <li>{@link #await()} — blocks the calling thread until the controller thread finishes so the
 *       terminal is fully restored before the caller continues.
 * </ol>
 *
 * <p>Spinner animation runs on the controller thread. Every {@link #POLL_TIMEOUT_MS} ms (or sooner
 * if an event arrives), all rows whose state is "running" have their spinner frame advanced and
 * their row text rewritten. A JLine redraw is dispatched only when at least one row is running or a
 * {@link TestProgressEvent.TestCompleted} event has just updated a row.
 *
 * <p>Test name truncation: names longer than {@code terminalWidth - }{@link #NAME_OVERHEAD_COLS}
 * are truncated with "…" (see {@link #truncateName(String, int)}) so that rows never exceed the
 * terminal width.
 *
 * <p>Post-TUI output: after {@link TestProgressEvent.SuiteCompleted} and terminal restoration, a
 * one-line summary ({@code ✓ N passed ✗ M failed (Xms)}) is printed, followed by a failure block
 * listing each failing test name and its first failure message.
 *
 * <p>This class is <em>not</em> a Spring singleton. One instance is created per suite run by {@link
 * io.github.snytkine.apitester.api_tester_cli.commands.RunSuiteCommand}. All mutable view state is
 * confined to the controller thread. The only cross-thread communication uses an {@link
 * AtomicReferenceArray} for row text (written by the controller thread, read by the JLine render
 * thread).
 *
 * <p>Thread-safety: thread-safe by construction. The controller thread is the sole writer of view
 * state; the JLine render thread is a pure reader. The {@link LinkedBlockingQueue} provides safe
 * producer-consumer handoff for incoming events.
 */
public final class TerminalUiController {

    private static final Logger log = LoggerFactory.getLogger(TerminalUiController.class);

    /** Milliseconds to wait for the next event before looping (enables spinner animation). */
    static final long POLL_TIMEOUT_MS = 100L;

    /** Seconds to wait for the initial {@link TestProgressEvent.SuiteStarted} event. */
    static final long SUITE_STARTED_TIMEOUT_SECONDS = 30L;

    /**
     * Number of terminal columns consumed by the fixed parts of a row: the two-space indent, the
     * status glyph, the separating space, the {@code " ("} before the duration, a five-digit
     * duration, and the {@code "ms)"} suffix. Test names longer than {@code terminalWidth -
     * NAME_OVERHEAD_COLS} are truncated with "…" via {@link #truncateName(String, int)}.
     */
    static final int NAME_OVERHEAD_COLS = 14;

    private final LinkedBlockingQueue<TestProgressEvent> queue;
    private final ViewComponentBuilder viewComponentBuilder;

    /**
     * When {@code true}, row glyphs are rendered with ANSI foreground colours via {@link
     * AttributedStyle}: green for PASS, red for FAIL/ERROR, yellow for the running spinner. The
     * post-TUI summary and failure lines also use ANSI codes when this flag is set. When {@code
     * false} (e.g. {@code NO_COLOR} is set), plain text is used without any colour attributes.
     */
    private final boolean useColors;

    /** Terminal column count used to compute the maximum test-name display width. */
    private final int terminalWidth;

    /**
     * Writer used to print the post-TUI summary line and failure details after the terminal is fully
     * restored.
     */
    private final PrintWriter output;

    private Thread controllerThread;

    /**
     * Constructs a controller for one suite run.
     *
     * @param queue the shared event queue populated by a {@link TerminalUiListener}
     * @param viewComponentBuilder Spring-managed factory for creating a {@link ViewComponent}
     * @param useColors {@code true} to render coloured ANSI glyphs and post-TUI output; {@code false}
     *     for plain-text output (e.g. when the caller detected that {@code NO_COLOR} is set)
     * @param terminalWidth terminal column count; obtain from {@link TtyDetector#getTerminalWidth()};
     *     used to truncate test names that would overflow the row
     * @param output writer used to print the post-TUI summary and failure details; normally {@link
     *     org.springframework.shell.core.command.CommandContext#outputWriter()}
     */
    public TerminalUiController(
            LinkedBlockingQueue<TestProgressEvent> queue,
            ViewComponentBuilder viewComponentBuilder,
            boolean useColors,
            int terminalWidth,
            PrintWriter output) {
        this.queue = queue;
        this.viewComponentBuilder = viewComponentBuilder;
        this.useColors = useColors;
        this.terminalWidth = terminalWidth;
        this.output = output;
    }

    /**
     * Starts the background controller thread.
     *
     * <p>Must be called once before any events are offered to the shared queue. The thread is
     * configured as a daemon thread so it does not prevent JVM shutdown if the main thread exits
     * unexpectedly.
     */
    public void start() {
        controllerThread = new Thread(this::runLoop, "tui-controller");
        controllerThread.setDaemon(true);
        controllerThread.start();
    }

    /**
     * Blocks the calling thread until the controller thread has finished and the terminal has been
     * fully restored.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public void await() throws InterruptedException {
        if (controllerThread != null) {
            controllerThread.join();
        }
    }

    /**
     * Main controller thread loop. Waits for {@link TestProgressEvent.SuiteStarted}, builds the view,
     * then runs the event-and-spinner loop until {@link TestProgressEvent.SuiteCompleted}. After the
     * view exits, prints a one-line summary and any failure details to {@link #output}.
     *
     * <p>Per-row animation state ({@code testNames}, {@code isRunning}, {@code spinnerFrames}) and
     * the failure-collection list are entirely local to this thread — no external synchronisation is
     * required.
     */
    private void runLoop() {
        try {
            TestProgressEvent firstEvent = queue.poll(SUITE_STARTED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!(firstEvent instanceof TestProgressEvent.SuiteStarted suiteStarted)) {
                log.warn("TUI controller received unexpected first event or timed out; aborting UI render");
                return;
            }

            int rowCount = suiteStarted.totalTestCount();
            int maxNameWidth = Math.max(10, terminalWidth - NAME_OVERHEAD_COLS);

            AtomicReferenceArray<AttributedString> rows = new AtomicReferenceArray<>(rowCount);
            String[] testNames = new String[rowCount];
            boolean[] isRunning = new boolean[rowCount];
            int[] spinnerFrames = new int[rowCount];

            AttributedString pendingRow = new AttributedStringBuilder()
                    .append("  " + Glyphs.PENDING + " (pending)")
                    .toAttributedString();
            for (int i = 0; i < rowCount; i++) {
                rows.set(i, pendingRow);
            }

            GridView grid = buildGrid(rows, rowCount);
            ViewComponent vc = viewComponentBuilder.build(grid);
            ViewComponent.ViewComponentRun vcRun = vc.runAsync();

            List<TestProgressEvent.TestCompleted> collectedFailures = new ArrayList<>();
            long summaryPassCount = 0;
            long summaryFailCount = 0;
            long summaryTotalMs = 0;

            try {
                boolean done = false;
                while (!done) {
                    TestProgressEvent event = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        // Capture failure and summary data before delegating to the view updater.
                        if (event instanceof TestProgressEvent.TestCompleted tc
                                && tc.status() != TestStatus.PASS
                                && !tc.failureMessages().isEmpty()) {
                            collectedFailures.add(tc);
                        }
                        if (event instanceof TestProgressEvent.SuiteCompleted sc) {
                            summaryPassCount = sc.passCount();
                            summaryFailCount = sc.failCount();
                            summaryTotalMs = sc.totalDurationMs();
                        }
                        done = applyEvent(event, rows, testNames, isRunning, maxNameWidth);
                    }

                    // Advance the Braille spinner for every row that is currently running.
                    boolean anyRunning = false;
                    for (int i = 0; i < rowCount; i++) {
                        if (isRunning[i]) {
                            anyRunning = true;
                            int frame = spinnerFrames[i];
                            rows.set(
                                    i,
                                    buildRowText(
                                            Glyphs.SPINNER_FRAMES[frame],
                                            testNames[i] + " (running...)",
                                            AttributedStyle.YELLOW));
                            spinnerFrames[i] = (frame + 1) % Glyphs.SPINNER_FRAMES.length;
                        }
                    }

                    // Dispatch a redraw only when the display actually changed.
                    if (anyRunning || event instanceof TestProgressEvent.TestCompleted) {
                        vc.getEventLoop().dispatch(ShellMessageBuilder.ofRedraw());
                    }
                }
            } finally {
                vc.exit();
            }
            vcRun.await();

            // Terminal is fully restored — print the post-TUI summary and failure details.
            printSummary(summaryPassCount, summaryFailCount, summaryTotalMs);
            printFailures(collectedFailures);
            output.flush();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("TUI controller thread interrupted");
        } catch (Exception e) {
            log.error("TUI controller failed unexpectedly", e);
        }
    }

    /**
     * Applies a single event, updating the per-row state arrays used by the spinner loop.
     *
     * <p>{@link TestProgressEvent.TestStarted} records the (possibly truncated) test name and marks
     * the row as running; the spinner loop writes the first animated frame on the next tick. {@link
     * TestProgressEvent.TestCompleted} clears the running flag and writes the final status row
     * directly to {@code rows}; the spinner loop will no longer touch that row. Redraw dispatching is
     * handled by the caller after this method returns.
     *
     * @param event the event to apply
     * @param rows shared row-text array; updated on {@link TestProgressEvent.TestCompleted}
     * @param testNames per-row test name (truncated to {@code maxNameWidth}); written on {@link
     *     TestProgressEvent.TestStarted}
     * @param isRunning per-row running flag; set on {@link TestProgressEvent.TestStarted}, cleared on
     *     {@link TestProgressEvent.TestCompleted}
     * @param maxNameWidth maximum display width for test names; names exceeding this are truncated
     * @return {@code true} when the suite is complete and the render loop should exit
     */
    private boolean applyEvent(
            TestProgressEvent event,
            AtomicReferenceArray<AttributedString> rows,
            String[] testNames,
            boolean[] isRunning,
            int maxNameWidth) {
        return switch (event) {
            case TestProgressEvent.SuiteStarted ignored ->
                // Duplicate SuiteStarted; should never happen — ignore.
                false;
            case TestProgressEvent.TestStarted e -> {
                testNames[e.testIndex()] = truncateName(e.testName(), maxNameWidth);
                isRunning[e.testIndex()] = true;
                yield false;
            }
            case TestProgressEvent.TestCompleted e -> {
                isRunning[e.testIndex()] = false;
                String glyph =
                        switch (e.status()) {
                            case PASS -> Glyphs.PASS;
                            case FAIL, ERROR -> Glyphs.FAIL;
                        };
                int color =
                        switch (e.status()) {
                            case PASS -> AttributedStyle.GREEN;
                            case FAIL, ERROR -> AttributedStyle.RED;
                        };
                String name = testNames[e.testIndex()] != null
                        ? testNames[e.testIndex()]
                        : truncateName(e.testName(), maxNameWidth);
                rows.set(e.testIndex(), buildRowText(glyph, name + " (" + e.durationMs() + "ms)", color));
                yield false;
            }
            case TestProgressEvent.SuiteCompleted ignored -> true;
        };
    }

    /**
     * Builds a {@link GridView} with one {@link BoxView} per row. Each box's draw function reads its
     * content from the shared {@code rows} array at render time, which allows the controller thread
     * to update content without synchronising with the render thread. The {@link AttributedString}
     * values stored in {@code rows} carry embedded ANSI colour attributes when {@link #useColors} is
     * {@code true}, which the JLine render pipeline translates to terminal escape codes.
     *
     * @param rows shared row-content array; updated by the controller thread, read by the render
     *     thread
     * @param rowCount number of rows to create
     * @return a fully configured {@link GridView} ready to be passed to {@link
     *     ViewComponentBuilder#build}
     */
    private GridView buildGrid(AtomicReferenceArray<AttributedString> rows, int rowCount) {
        GridView grid = new GridView();
        int[] rowSizes = new int[rowCount];
        for (int i = 0; i < rowCount; i++) {
            rowSizes[i] = 1;
        }
        grid.setRowSize(rowSizes);
        grid.setColumnSize(0);

        for (int i = 0; i < rowCount; i++) {
            int idx = i;
            BoxView box = new BoxView();
            box.setDrawFunction((screen, rect) -> {
                Screen.Writer writer = screen.writerBuilder().build();
                writer.text(rows.get(idx), rect.x(), rect.y());
                return rect;
            });
            grid.addItem(box, i, 0, 1, 1, 0, 0);
        }
        return grid;
    }

    /**
     * Builds a single row's {@link AttributedString} consisting of a leading two-space indent, the
     * status glyph (optionally coloured), and the label text in the default style.
     *
     * <p>When {@link #useColors} is {@code true}, the glyph character is wrapped in {@link
     * AttributedStyle#DEFAULT}{@code .foreground(jlineColor)} so it appears in the requested ANSI
     * colour. The surrounding text is always rendered in the terminal's default foreground colour.
     * When {@link #useColors} is {@code false} (e.g. the caller detected {@code NO_COLOR}), the same
     * Unicode glyphs are used but no colour attributes are applied.
     *
     * @param glyph the status glyph to display (e.g. {@link Glyphs#PASS}, {@link Glyphs#FAIL}, or a
     *     Braille spinner frame from {@link Glyphs#SPINNER_FRAMES})
     * @param label the remaining text to display after the glyph (test name, duration, etc.)
     * @param jlineColor ANSI foreground colour index from {@link AttributedStyle} (e.g. {@link
     *     AttributedStyle#GREEN}); ignored when {@link #useColors} is {@code false}
     * @return an {@link AttributedString} ready to pass to {@link
     *     Screen.Writer#text(AttributedString, int, int)}
     */
    private AttributedString buildRowText(String glyph, String label, int jlineColor) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.append("  ");
        if (useColors) {
            sb.style(AttributedStyle.DEFAULT.foreground(jlineColor));
        }
        sb.append(glyph);
        sb.style(AttributedStyle.DEFAULT);
        sb.append(" ").append(label);
        return sb.toAttributedString();
    }

    /**
     * Truncates {@code name} to at most {@code maxWidth} characters, replacing the last position with
     * "…" when truncation occurs.
     *
     * <p>Examples: {@code truncateName("abcde", 4)} → {@code "abc…"}; {@code truncateName("ab", 5)} →
     * {@code "ab"}.
     *
     * @param name the test name to (possibly) shorten; not modified in place
     * @param maxWidth maximum allowed character count; values ≤ 0 are treated as "no limit" and the
     *     original name is returned unchanged
     * @return the original name if {@code name.length() <= maxWidth}, or a truncated string ending
     *     with "…"
     */
    static String truncateName(String name, int maxWidth) {
        if (maxWidth <= 0 || name.length() <= maxWidth) return name;
        if (maxWidth == 1) return "…";
        return name.substring(0, maxWidth - 1) + "…";
    }

    /**
     * Wraps {@code text} in ANSI foreground-colour escape sequences when {@link #useColors} is {@code
     * true}; returns {@code text} unchanged otherwise.
     *
     * @param text the text to colourize
     * @param ansiCode the ANSI SGR foreground colour code (31 = red, 32 = green, 33 = yellow, etc.)
     * @return the colourized string, or the original string if colours are disabled
     */
    private String colorize(String text, int ansiCode) {
        return useColors ? "\u001B[" + ansiCode + "m" + text + "\u001B[0m" : text;
    }

    /**
     * Prints the one-line post-TUI summary to {@link #output} after the terminal has been fully
     * restored.
     *
     * <p>Format: {@code ✓ N passed ✗ M failed (Xms)}. The pass count is always coloured green when
     * colours are enabled. The fail count is coloured red only when it is greater than zero; an
     * all-pass run leaves the fail text in the default foreground colour to avoid a misleading red
     * highlight.
     *
     * @param passCount number of test cases that passed
     * @param failCount number of test cases that failed or errored
     * @param totalMs total suite wall-clock duration in milliseconds
     */
    private void printSummary(long passCount, long failCount, long totalMs) {
        String passPart = Glyphs.PASS + " " + passCount + " passed";
        String failPart = Glyphs.FAIL + " " + failCount + " failed";
        String formattedFail = failCount > 0 ? colorize(failPart, 31) : failPart;
        output.println(colorize(passPart, 32) + "  " + formattedFail + "  (" + totalMs + "ms)");
    }

    /**
     * Prints the failure-detail block to {@link #output} after the terminal has been fully restored.
     * Each failing test is listed with its name followed by all failure messages, one per line.
     * Nothing is printed when {@code failures} is empty.
     *
     * @param failures list of {@link TestProgressEvent.TestCompleted} events for non-passing tests
     *     that carry at least one failure message; may be empty
     */
    private void printFailures(List<TestProgressEvent.TestCompleted> failures) {
        if (failures.isEmpty()) return;
        output.println();
        output.println("Failures:");
        for (TestProgressEvent.TestCompleted tc : failures) {
            output.println("  " + colorize(Glyphs.FAIL + " " + tc.testName(), 31));
            for (String message : tc.failureMessages()) {
                output.println("    " + message);
            }
        }
    }
}
