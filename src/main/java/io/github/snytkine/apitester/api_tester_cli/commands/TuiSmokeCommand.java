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
package io.github.snytkine.apitester.api_tester_cli.commands;

import java.util.concurrent.atomic.AtomicReferenceArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.jline.tui.component.ViewComponent;
import org.springframework.shell.jline.tui.component.ViewComponentBuilder;
import org.springframework.shell.jline.tui.component.message.ShellMessageBuilder;
import org.springframework.shell.jline.tui.component.view.control.BoxView;
import org.springframework.shell.jline.tui.component.view.control.GridView;
import org.springframework.shell.jline.tui.component.view.screen.Screen;
import org.springframework.stereotype.Component;

/**
 * Developer smoke-test command that exercises Spring Shell's {@link GridView} and {@link BoxView}
 * TUI components.
 *
 * <p>The command renders a 3-row grid simulating a running test suite: each row shows a spinner
 * while "running" and a status glyph once "complete". It exits automatically after all rows are
 * updated; no keyboard input is required.
 *
 * <p>Purpose: verify that {@link GridView}, {@link BoxView}, {@link ViewComponentBuilder}, and
 * {@link ShellMessageBuilder#ofRedraw()} all work correctly under both JVM and GraalVM native-image
 * execution. See {@code native-build-support.md} for GraalVM findings from this command.
 *
 * <p>Thread-safety: the command uses an {@link AtomicReferenceArray} to exchange row text between
 * the caller thread (which updates content) and the render thread (which reads it via the {@code
 * drawFunction}).
 */
@Component
public class TuiSmokeCommand {

    private static final Logger log = LoggerFactory.getLogger(TuiSmokeCommand.class);

    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final int SPINNER_DELAY_MS = 80;
    private static final int COMPLETE_PAUSE_MS = 300;
    private static final String[] TEST_NAMES = {
        "login-with-valid-credentials", "create-item-returns-201", "get-all-items-returns-array"
    };

    private final ViewComponentBuilder viewComponentBuilder;

    /**
     * Constructs the command with its required {@link ViewComponentBuilder}.
     *
     * @param viewComponentBuilder Spring-managed builder used to create a {@link ViewComponent}
     *     around the grid layout
     */
    public TuiSmokeCommand(ViewComponentBuilder viewComponentBuilder) {
        this.viewComponentBuilder = viewComponentBuilder;
    }

    /**
     * Renders a 3-row progress grid, animates spinners per row, marks each row as passed/failed, and
     * exits automatically. No keyboard input is required.
     *
     * <p>This command is intentionally kept simple: all state lives in a single {@link
     * AtomicReferenceArray} that is shared between this thread and the render thread.
     *
     * @param context Spring Shell command context (not used directly; included for command
     *     infrastructure)
     * @throws InterruptedException if the calling thread is interrupted while sleeping between frames
     */
    @Command(
            name = "tui-smoke",
            description = "Smoke test: verifies that GridView + BoxView render and update correctly."
                    + " Exits automatically; no keyboard input needed.")
    public void tuiSmoke(CommandContext context) throws InterruptedException {
        int rowCount = TEST_NAMES.length;
        AtomicReferenceArray<String> rows = new AtomicReferenceArray<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            rows.set(i, "  · " + TEST_NAMES[i]);
        }

        GridView grid = buildGrid(rows, rowCount);
        ViewComponent vc = viewComponentBuilder.build(grid);
        ViewComponent.ViewComponentRun run = vc.runAsync();

        try {
            for (int testIdx = 0; testIdx < rowCount; testIdx++) {
                animateRow(vc, rows, testIdx);
                boolean pass = (testIdx != 1); // simulate one failure for demo
                String glyph = pass ? "✓" : "✗";
                long fakeMs = 100L + testIdx * 150L;
                rows.set(testIdx, glyph + " " + TEST_NAMES[testIdx] + " (" + fakeMs + "ms)");
                vc.getEventLoop().dispatch(ShellMessageBuilder.ofRedraw());
                Thread.sleep(COMPLETE_PAUSE_MS);
            }
        } finally {
            vc.exit();
        }

        run.await();
        log.debug("TUI smoke test completed");
    }

    /**
     * Builds a {@link GridView} pre-populated with one {@link BoxView} per row. Each box's draw
     * function reads its content from the shared {@code rows} array at render time.
     *
     * @param rows shared row-content array; updated by the caller thread, read by the render thread
     * @param rowCount number of rows to create
     * @return fully configured {@link GridView} ready for use in a {@link ViewComponent}
     */
    private GridView buildGrid(AtomicReferenceArray<String> rows, int rowCount) {
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
     * Animates the spinner for the given row for a few frames, dispatching a redraw message after
     * each frame.
     *
     * @param vc the running {@link ViewComponent} whose event loop receives redraw signals
     * @param rows shared row-content array
     * @param testIdx zero-based index of the row to animate
     * @throws InterruptedException if the calling thread is interrupted
     */
    private void animateRow(ViewComponent vc, AtomicReferenceArray<String> rows, int testIdx)
            throws InterruptedException {
        for (int frame = 0; frame < SPINNER_FRAMES.length; frame++) {
            String spinner = SPINNER_FRAMES[frame];
            rows.set(testIdx, spinner + " " + TEST_NAMES[testIdx] + " (running...)");
            vc.getEventLoop().dispatch(ShellMessageBuilder.ofRedraw());
            Thread.sleep(SPINNER_DELAY_MS);
        }
    }
}
