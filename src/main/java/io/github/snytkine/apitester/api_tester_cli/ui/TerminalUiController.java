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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives a raw-ANSI terminal UI for a single test-suite run, using a 4-column grid with Braille
 * spinner animation for in-progress tests.
 *
 * <h3>Visual layout</h3>
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────────┐
 * │              Starting Test Suite &lt;name&gt;                      │
 * └──────────────────────────────────────────────────────────────┘
 * ┌─────────────────────┬────────┬─────────────────┬────────────┐
 * │ Test Name           │ Status │ Response Time   │ Result     │
 * ├─────────────────────┼────────┼─────────────────┼────────────┤
 * │ test-one            │ ⠋      │                 │            │
 * ├─────────────────────┼────────┼─────────────────┼────────────┤
 * │ test-two            │ ✓      │ 142ms           │ 3 passed   │
 * └─────────────────────┴────────┴─────────────────┴────────────┘
 * </pre>
 *
 * <h3>In-place cell updates</h3>
 *
 * <p>After the grid is drawn to the terminal (one {@code println} per line), the cursor rests on
 * the line immediately after the bottom border. Cell updates use ANSI cursor-positioning sequences:
 *
 * <ul>
 *   <li>{@code \033[NA} — move cursor up {@code N} lines to the target data row
 *   <li>{@code \033[CG} — move cursor to 1-indexed column {@code C} within that row
 *   <li>Write the padded cell content (optionally with ANSI colour)
 *   <li>{@code \033[NB} — move cursor down {@code N} lines back to the resting position
 *   <li>{@code \033[1G} — return to column 1
 * </ul>
 *
 * <p>The number of lines up for test row {@code i} in a suite with {@code N} total rows is
 * {@code 2 * (N - i)}: one line for each data row and one line for each inter-row separator,
 * counted from the bottom border upwards. No full-screen refresh is performed after the initial
 * draw.
 *
 * <h3>Column layout (characters per line)</h3>
 *
 * <p>{@code │ }{@link #nameColWidth}{@code  │ }{@link #STATUS_COL_WIDTH}{@code  │ }{@link
 * #TIME_COL_WIDTH}{@code  │ }{@link #RESULT_COL_WIDTH}{@code  │} = {@code terminalWidth} total.
 * Fixed overhead is {@link #FIXED_COL_OVERHEAD} = 13 chars (5 border chars + 8 padding spaces).
 *
 * <h3>Test identity tracking</h3>
 *
 * <p>When a {@link TestProgressEvent.TestStarted} event arrives its {@code uniqueId} is mapped to
 * the grid row index via an internal {@code Map&lt;String, Integer&gt;}. Subsequent
 * {@link TestProgressEvent.TestCompleted} events use the same map to locate the correct row. Using
 * a string key rather than relying solely on {@code testIndex} keeps the design compatible with
 * future parallel execution where completion order may differ from start order.
 *
 * <h3>Lifecycle</h3>
 *
 * <ol>
 *   <li>{@link #start()} — starts a background controller thread.
 *   <li>Thread waits for {@link TestProgressEvent.SuiteStarted}, then draws banner + grid.
 *   <li>On {@link TestProgressEvent.TestStarted}: name and spinner appear in the row.
 *   <li>On each poll timeout: spinner frames advance for all running rows via cursor positioning.
 *   <li>On {@link TestProgressEvent.TestCompleted}: status glyph, response time, and result
 *       replace the spinner in-place.
 *   <li>On {@link TestProgressEvent.SuiteCompleted}: loop exits; summary and failure details are
 *       appended below the grid.
 *   <li>{@link #await()} — blocks the caller until the controller thread finishes.
 * </ol>
 *
 * <p>This class is <em>not</em> a Spring singleton. One instance is created per suite run by
 * {@link io.github.snytkine.apitester.api_tester_cli.commands.RunSuiteCommand}. All mutable view
 * state is confined to the controller thread; the {@link LinkedBlockingQueue} provides safe
 * producer-consumer handoff.
 *
 * <p>Thread-safety: thread-safe by construction. The controller thread is the sole writer to
 * {@link #output}; no other thread writes to it during the run loop.
 */
public final class TerminalUiController {

    private static final Logger log = LoggerFactory.getLogger(TerminalUiController.class);

    /** Milliseconds to wait for the next event before looping (enables spinner animation). */
    static final long POLL_TIMEOUT_MS = 100L;

    /** Seconds to wait for the initial {@link TestProgressEvent.SuiteStarted} event. */
    static final long SUITE_STARTED_TIMEOUT_SECONDS = 30L;

    /**
     * Visible character width of the Status column. Must be at least 8 to accommodate the widest
     * status label: one glyph character, a space, and "Passed" (6 chars) = 8 visible chars total.
     */
    static final int STATUS_COL_WIDTH = 10;

    /** Visible character width of the Response Time column (accommodates "Response Time" header). */
    static final int TIME_COL_WIDTH = 15;

    /** Visible character width of the Result column. */
    static final int RESULT_COL_WIDTH = 10;

    /**
     * Total characters consumed per row by the fixed structure: 5 border bars {@code │} (one before
     * each column and one at the right edge) plus 8 space characters (one padding on each side of
     * each cell's content). This overhead is subtracted from {@link #terminalWidth} to compute {@link
     * #nameColWidth}.
     */
    static final int FIXED_COL_OVERHEAD = 13;

    /** ANSI foreground colour code for yellow (used for the banner and spinner). */
    private static final int ANSI_YELLOW = 33;

    /** ANSI foreground colour code for green (used for PASS status and result). */
    private static final int ANSI_GREEN = 32;

    /** ANSI foreground colour code for red (used for FAIL/ERROR status and result). */
    private static final int ANSI_RED = 31;

    private final LinkedBlockingQueue<TestProgressEvent> queue;
    private final boolean useColors;
    private final int terminalWidth;
    private final PrintWriter output;

    /**
     * Computed visible character width for the Test Name column; equals {@code max(10,
     * terminalWidth - FIXED_COL_OVERHEAD - STATUS_COL_WIDTH - TIME_COL_WIDTH - RESULT_COL_WIDTH)}.
     */
    final int nameColWidth;

    /**
     * 1-indexed terminal column where Status cell content begins, computed at construction time.
     * Equals {@code nameColWidth + 6} (skip left border, space, name content, space, right border,
     * and space before status).
     */
    final int statusColStart;

    /**
     * 1-indexed terminal column where Response Time cell content begins, computed at construction
     * time.
     */
    final int timeColStart;

    /** 1-indexed terminal column where Result cell content begins, computed at construction time. */
    final int resultColStart;

    private Thread controllerThread;

    /**
     * Constructs a controller for one suite run.
     *
     * <p>Column widths and 1-indexed column start positions are computed once from {@code
     * terminalWidth} and stored for use throughout the run loop.
     *
     * @param queue the shared event queue populated by a {@link TerminalUiListener}
     * @param useColors {@code true} to render coloured ANSI glyphs; {@code false} for plain text
     * @param terminalWidth terminal column count used to derive the name-column width and banner width
     * @param output writer connected to the terminal; used for all UI output and post-TUI details
     */
    public TerminalUiController(
            LinkedBlockingQueue<TestProgressEvent> queue, boolean useColors, int terminalWidth, PrintWriter output) {
        this.queue = queue;
        this.useColors = useColors;
        this.terminalWidth = terminalWidth;
        this.output = output;
        this.nameColWidth =
                Math.max(10, terminalWidth - FIXED_COL_OVERHEAD - STATUS_COL_WIDTH - TIME_COL_WIDTH - RESULT_COL_WIDTH);
        // 1-indexed column starts:
        //   col 1:│  col 2:space  col 3..2+nameW: name  col 3+nameW:space  col 4+nameW:│
        //   col 5+nameW:space  col 6+nameW..5+nameW+statusW: status  ...
        this.statusColStart = nameColWidth + 6;
        this.timeColStart = statusColStart + STATUS_COL_WIDTH + 3;
        this.resultColStart = timeColStart + TIME_COL_WIDTH + 3;
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
     * Blocks the calling thread until the controller thread has finished and terminal output is
     * complete.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public void await() throws InterruptedException {
        if (controllerThread != null) {
            controllerThread.join();
        }
    }

    /**
     * Main controller thread loop.
     *
     * <p>Waits for {@link TestProgressEvent.SuiteStarted}, draws the banner and grid, then runs
     * the event-and-spinner loop until {@link TestProgressEvent.SuiteCompleted}. After the loop
     * exits, appends the one-line summary and any failure details below the grid.
     */
    private void runLoop() {
        try {
            TestProgressEvent firstEvent = queue.poll(SUITE_STARTED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!(firstEvent instanceof TestProgressEvent.SuiteStarted suiteStarted)) {
                log.warn("TUI controller received unexpected first event or timed out; aborting UI render");
                return;
            }

            int rowCount = suiteStarted.totalTestCount();

            // Draw static elements — banner and the grid frame with blank data rows.
            drawBanner(suiteStarted.suiteName());
            drawGrid(rowCount);
            output.flush();

            // Per-row mutable state (controller thread only — no synchronisation needed).
            String[] testNames = new String[rowCount];
            boolean[] isRunning = new boolean[rowCount];
            int[] spinnerFrames = new int[rowCount];
            Map<String, Integer> uniqueIdToRow = new HashMap<>();

            List<TestProgressEvent.TestCompleted> collectedFailures = new ArrayList<>();
            long summaryPassCount = 0;
            long summaryFailCount = 0;
            long summaryTotalMs = 0;

            boolean done = false;
            while (!done) {
                TestProgressEvent event = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (event != null) {
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
                    done = applyEvent(event, rowCount, testNames, isRunning, spinnerFrames, uniqueIdToRow);
                }

                // Advance the Braille spinner for every currently-running row using cursor positioning.
                for (int i = 0; i < rowCount; i++) {
                    if (isRunning[i]) {
                        int frame = spinnerFrames[i];
                        updateCell(
                                i,
                                rowCount,
                                statusColStart,
                                STATUS_COL_WIDTH,
                                Glyphs.SPINNER_FRAMES[frame],
                                ANSI_YELLOW);
                        spinnerFrames[i] = (frame + 1) % Glyphs.SPINNER_FRAMES.length;
                    }
                }
                output.flush();
            }

            // Grid is complete — append a blank separator, then summary and failure details.
            output.println();
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
     * Applies a single event by updating the per-row state and writing cell content via cursor
     * positioning.
     *
     * <p>On {@link TestProgressEvent.TestStarted}: records the (possibly truncated) test name in
     * {@code testNames}, marks the row running, resets the spinner frame, and writes the name and
     * initial spinner to the terminal. On {@link TestProgressEvent.TestCompleted}: clears the running
     * flag and writes the status glyph, response time, and result to their respective cells.
     *
     * @param event the event to handle
     * @param rowCount total number of test rows in the grid
     * @param testNames per-row test name storage (written on {@link TestProgressEvent.TestStarted})
     * @param isRunning per-row running flag (set on start, cleared on complete)
     * @param spinnerFrames per-row current spinner frame index
     * @param uniqueIdToRow map from {@link TestProgressEvent.TestStarted#uniqueId()} to row index;
     *     populated on start, read on complete
     * @return {@code true} when the suite is complete and the render loop should exit
     */
    private boolean applyEvent(
            TestProgressEvent event,
            int rowCount,
            String[] testNames,
            boolean[] isRunning,
            int[] spinnerFrames,
            Map<String, Integer> uniqueIdToRow) {
        return switch (event) {
            case TestProgressEvent.SuiteStarted ignored ->
                // Duplicate SuiteStarted — should never happen; ignore.
                false;
            case TestProgressEvent.TestStarted e -> {
                int row = e.testIndex();
                uniqueIdToRow.put(e.uniqueId(), row);
                testNames[row] = truncateName(e.testName(), nameColWidth);
                isRunning[row] = true;
                spinnerFrames[row] = 0;
                updateCell(row, rowCount, 3, nameColWidth, testNames[row], 0);
                updateCell(row, rowCount, statusColStart, STATUS_COL_WIDTH, Glyphs.SPINNER_FRAMES[0], ANSI_YELLOW);
                yield false;
            }
            case TestProgressEvent.TestCompleted e -> {
                int row = uniqueIdToRow.getOrDefault(e.uniqueId(), e.testIndex());
                isRunning[row] = false;
                // Build status label: glyph + space + word (e.g. "✓ Passed", "✗ Failed", "✗ Error")
                String statusText =
                        switch (e.status()) {
                            case PASS -> Glyphs.PASS + " Passed";
                            case FAIL -> Glyphs.FAIL + " Failed";
                            case ERROR -> Glyphs.FAIL + " Error";
                        };
                int statusColor = e.status() == TestStatus.PASS ? ANSI_GREEN : ANSI_RED;
                String timeStr = e.durationMs() + "ms";
                String resultStr = e.status() == TestStatus.PASS
                        ? e.assertionCount() + " passed"
                        : e.failureMessages().size() + " failed";
                updateCell(row, rowCount, statusColStart, STATUS_COL_WIDTH, statusText, statusColor);
                updateCell(row, rowCount, timeColStart, TIME_COL_WIDTH, timeStr, 0);
                updateCell(row, rowCount, resultColStart, RESULT_COL_WIDTH, resultStr, statusColor);
                yield false;
            }
            case TestProgressEvent.SuiteCompleted ignored -> true;
        };
    }

    /**
     * Updates a single cell in the grid using ANSI cursor-positioning escape sequences.
     *
     * <p>The cursor is assumed to rest on the line immediately after the grid's bottom border (column
     * 1) before and after this call. The update sequence is:
     *
     * <ol>
     *   <li>{@code \033[NA} — move up {@code N = 2 * (rowCount - rowIndex)} lines to the target row
     *   <li>{@code \033[CG} — move to 1-indexed column {@code colStart}
     *   <li>Write the content padded to {@code colWidth} visible characters (with ANSI colour when
     *       {@link #useColors} is {@code true} and {@code ansiColor > 0})
     *   <li>{@code \033[NB} — move down {@code N} lines back to the resting line
     *   <li>{@code \033[1G} — return to column 1
     * </ol>
     *
     * <p>The padding is computed from visible characters only; ANSI colour escape codes are applied
     * around the already-padded string so they do not affect the cursor column.
     *
     * @param rowIndex zero-based index of the data row to update
     * @param rowCount total number of data rows in the grid
     * @param colStart 1-indexed terminal column where the cell content starts
     * @param colWidth number of visible characters the cell occupies
     * @param content the new cell value; truncated to {@code colWidth} if necessary
     * @param ansiColor ANSI foreground colour code (e.g. {@link #ANSI_GREEN}); pass {@code 0} to
     *     suppress colouring even when {@link #useColors} is {@code true}
     */
    private void updateCell(int rowIndex, int rowCount, int colStart, int colWidth, String content, int ansiColor) {
        int linesUp = 2 * (rowCount - rowIndex);
        String padded = padRight(content, colWidth);
        String toWrite = (useColors && ansiColor > 0) ? "\033[" + ansiColor + "m" + padded + "\033[0m" : padded;
        output.printf("\033[%dA\033[%dG", linesUp, colStart);
        output.print(toWrite);
        output.printf("\033[%dB\033[1G", linesUp);
    }

    /**
     * Draws the three-line suite-start banner to {@link #output}.
     *
     * <p>The banner box is approximately {@code 0.8 × terminalWidth} wide and centred horizontally
     * using leading spaces. The suite name is centred within the inner box width; if it exceeds the
     * available space it is truncated with "…". Text and border characters are rendered in yellow
     * when {@link #useColors} is {@code true}.
     *
     * @param suiteName the suite name to display; from {@link
     *     io.github.snytkine.apitester.api_tester_cli.model.TestSuite#name()}
     */
    private void drawBanner(String suiteName) {
        int bannerWidth = Math.max(10, Math.min((int) (terminalWidth * 0.8), terminalWidth - 2));
        int innerWidth = bannerWidth - 2;
        String text = "Starting Test Suite " + suiteName;
        if (text.length() > innerWidth) {
            text = text.substring(0, Math.max(1, innerWidth - 1)) + "…";
        }
        int leftPad = Math.max(0, (innerWidth - text.length()) / 2);
        int rightPad = Math.max(0, innerWidth - text.length() - leftPad);
        String indent = " ".repeat(Math.max(0, (terminalWidth - bannerWidth) / 2));

        output.println(colorize(indent + "┌" + "─".repeat(innerWidth) + "┐", ANSI_YELLOW));
        output.println(colorize(indent + "│" + " ".repeat(leftPad) + text + " ".repeat(rightPad) + "│", ANSI_YELLOW));
        output.println(colorize(indent + "└" + "─".repeat(innerWidth) + "┘", ANSI_YELLOW));
    }

    /**
     * Draws the 4-column grid frame to {@link #output}: top border, header row, header separator,
     * blank data rows (one per test case with inter-row separators), and bottom border.
     *
     * <p>Data row cells are initially blank; they are populated in-place as events arrive. When
     * {@code rowCount} is zero the grid omits the header separator and data rows, rendering only the
     * top border, header, and bottom border.
     *
     * @param rowCount number of data rows to reserve (one per test case)
     */
    private void drawGrid(int rowCount) {
        int nw = nameColWidth;
        String topBorder = "┌" + "─".repeat(nw + 2) + "┬" + "─".repeat(STATUS_COL_WIDTH + 2) + "┬"
                + "─".repeat(TIME_COL_WIDTH + 2) + "┬" + "─".repeat(RESULT_COL_WIDTH + 2) + "┐";
        String midBorder = "├" + "─".repeat(nw + 2) + "┼" + "─".repeat(STATUS_COL_WIDTH + 2) + "┼"
                + "─".repeat(TIME_COL_WIDTH + 2) + "┼" + "─".repeat(RESULT_COL_WIDTH + 2) + "┤";
        String botBorder = "└" + "─".repeat(nw + 2) + "┴" + "─".repeat(STATUS_COL_WIDTH + 2) + "┴"
                + "─".repeat(TIME_COL_WIDTH + 2) + "┴" + "─".repeat(RESULT_COL_WIDTH + 2) + "┘";
        String blankRow = "│ " + " ".repeat(nw) + " │ " + " ".repeat(STATUS_COL_WIDTH) + " │ "
                + " ".repeat(TIME_COL_WIDTH) + " │ " + " ".repeat(RESULT_COL_WIDTH) + " │";

        output.println(topBorder);
        output.println("│ " + padRight("Test Name", nw) + " │ " + padRight("Status", STATUS_COL_WIDTH)
                + " │ " + padRight("Response Time", TIME_COL_WIDTH) + " │ "
                + padRight("Result", RESULT_COL_WIDTH) + " │");

        if (rowCount > 0) {
            output.println(midBorder);
            for (int i = 0; i < rowCount; i++) {
                output.println(blankRow);
                if (i < rowCount - 1) {
                    output.println(midBorder);
                }
            }
        }
        output.println(botBorder);
    }

    /**
     * Wraps {@code text} with ANSI foreground-colour escape sequences when {@link #useColors} is
     * {@code true}; returns {@code text} unchanged otherwise.
     *
     * @param text the string to optionally colourize
     * @param ansiCode the ANSI SGR foreground colour code (31 = red, 32 = green, 33 = yellow)
     * @return the colourized string, or the original string if colours are disabled
     */
    private String colorize(String text, int ansiCode) {
        return useColors ? "\033[" + ansiCode + "m" + text + "\033[0m" : text;
    }

    /**
     * Truncates {@code name} to at most {@code maxWidth} characters, replacing the last position with
     * "…" when truncation occurs.
     *
     * @param name the test name to shorten; not modified in place
     * @param maxWidth maximum allowed character count; values ≤ 0 are treated as "no limit"
     * @return the original name if {@code name.length() <= maxWidth}, otherwise a truncated string
     *     ending with "…"
     */
    static String truncateName(String name, int maxWidth) {
        if (maxWidth <= 0 || name.length() <= maxWidth) return name;
        if (maxWidth == 1) return "…";
        return name.substring(0, maxWidth - 1) + "…";
    }

    /**
     * Right-pads {@code s} with spaces to exactly {@code width} visible characters, or truncates it
     * if it is already longer than {@code width}.
     *
     * @param s the string to pad or truncate; {@code null} is treated as an empty string
     * @param width the desired output length in visible characters
     * @return a string of exactly {@code width} visible characters
     */
    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    /**
     * Prints the one-line post-TUI summary to {@link #output}.
     *
     * <p>Format: {@code ✓ N passed ✗ M failed (Xms)}. The pass count is always coloured green when
     * colours are enabled. The fail count is coloured red only when it is greater than zero.
     * @param passCount number of test cases that passed
     * @param failCount number of test cases that failed or errored
     * @param totalMs total suite wall-clock duration in milliseconds
     */
    private void printSummary(long passCount, long failCount, long totalMs) {
        String passPart = Glyphs.PASS + " " + passCount + " passed";
        String failPart = Glyphs.FAIL + " " + failCount + " failed";
        String formattedFail = failCount > 0 ? colorize(failPart, ANSI_RED) : failPart;
        output.println(colorize(passPart, ANSI_GREEN) + "  " + formattedFail + "  (" + totalMs + "ms)");
    }

    /**
     * Prints the failure-detail block to {@link #output} after the run loop exits. Each failing test
     * is listed with its name followed by all failure messages, one per line. Nothing is printed when
     * {@code failures} is empty.
     *
     * @param failures {@link TestProgressEvent.TestCompleted} events for non-passing tests that carry
     *     at least one failure message; may be empty
     */
    private void printFailures(List<TestProgressEvent.TestCompleted> failures) {
        if (failures.isEmpty()) return;
        output.println();
        output.println("Failures:");
        for (TestProgressEvent.TestCompleted tc : failures) {
            output.println("  " + colorize(Glyphs.FAIL + " " + tc.testName(), ANSI_RED));
            for (String message : tc.failureMessages()) {
                output.println("    " + message);
            }
        }
    }
}
