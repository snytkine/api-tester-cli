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

import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import java.io.PrintWriter;
import java.util.List;
import org.springframework.shell.jline.tui.table.AbsoluteWidthSizeConstraints;
import org.springframework.shell.jline.tui.table.ArrayTableModel;
import org.springframework.shell.jline.tui.table.BorderStyle;
import org.springframework.shell.jline.tui.table.CellMatchers;
import org.springframework.shell.jline.tui.table.TableBuilder;
import org.springframework.shell.jline.tui.table.TableModel;

/**
 * Renders a structured failure-detail table for a single failed test case using Spring Shell's
 * {@link TableBuilder} API.
 *
 * <p>Each {@link AssertionFailure} in the list contributes an {@code Assertion} row containing the
 * failure message, plus optional {@code Expected} and {@code Actual} rows — those two rows are
 * omitted when both fields are {@code null}, keeping the table compact for the common case where
 * AssertJ does not extract structured expected/actual values. The test name occupies the first row.
 *
 * <p>All cell boundaries use {@code fancy_light} box-drawing borders; the header row (test name) is
 * separated from the assertion rows by a heavier {@code fancy_heavy} border produced by {@link
 * TableBuilder#addHeaderBorder}.
 *
 * <p>When {@code useColors} is {@code true}, all {@code Assertion}, {@code Expected}, and {@code
 * Actual} label rows are coloured with a single consistent ANSI foreground colour applied
 * post-render (after {@link org.springframework.shell.jline.tui.table.Table#render}), so that
 * column-width calculations are not affected by escape-byte injection into cell content. Border
 * separator lines are left uncoloured, providing natural visual breaks between assertion entries.
 *
 * <p>This class is package-private and stateless; it holds no mutable state and is safe to call
 * from any thread. It is intentionally not a Spring bean so that it can be instantiated directly
 * in unit tests without a Spring context.
 *
 * <p>Thread-safety: all methods are stateless and may be called concurrently from multiple threads.
 */
final class FailureTableRenderer {

    /** Visible character width of the label column (left column containing row labels). */
    static final int LABEL_COL_WIDTH = 14;

    /** ANSI blue foreground applied to all assertion content rows when colours are enabled. */
    static final String ASSERTION_COLOR = "\033[34m";

    private static final String ANSI_RESET = "\033[0m";

    /**
     * Renders a bordered table for one failed test to {@code output}.
     *
     * <p>The table has two columns: a narrow label column (fixed at {@link #LABEL_COL_WIDTH}
     * characters) and a value column that expands to fill the remaining {@code width}. Row layout:
     *
     * <ol>
     *   <li>Row 0 — {@code Test Name} / {@code testName} (always present)
     *   <li>For each failure: an {@code Assertion} row (always), followed by {@code Expected} and
     *       {@code Actual} rows only when at least one of those fields is non-{@code null}
     * </ol>
     *
     * <p>When {@code useColors} is {@code true}, assertion content rows are coloured post-render via
     * {@link #colorizeAssertionRows(String)}.
     *
     * @param testName the name of the failed test case
     * @param failures the list of assertion failures; must not be empty
     * @param useColors {@code true} to apply ANSI colour codes to assertion rows
     * @param width terminal column count used for table rendering
     * @param output the writer to which rendered output is appended
     */
    void render(String testName, List<AssertionFailure> failures, boolean useColors, int width, PrintWriter output) {
        int rowCount = 1;
        for (AssertionFailure f : failures) {
            rowCount++;
            if (f.expected() != null || f.actual() != null) {
                rowCount += 2;
            }
        }

        Object[][] data = new Object[rowCount][2];
        data[0] = new Object[] {"Test Name", testName};
        int row = 1;
        for (AssertionFailure f : failures) {
            data[row++] = new Object[] {"Assertion", f.description()};
            if (f.expected() != null || f.actual() != null) {
                data[row++] = new Object[] {"Expected", nullToDisplay(f.expected())};
                data[row++] = new Object[] {"Actual", nullToDisplay(f.actual())};
            }
        }

        TableModel model = new ArrayTableModel(data);
        TableBuilder builder = new TableBuilder(model);
        builder.addFullBorder(BorderStyle.fancy_light);
        builder.addHeaderBorder(BorderStyle.fancy_heavy);
        builder.on(CellMatchers.column(0)).addSizer(new AbsoluteWidthSizeConstraints(LABEL_COL_WIDTH));
        String rendered = builder.build().render(width);

        if (useColors) {
            rendered = colorizeAssertionRows(rendered);
        }
        output.println(rendered);
    }

    /**
     * Applies a single ANSI colour to all assertion content rows in an already-rendered table string.
     *
     * <p>Only lines whose label column starts with {@code ┃Assertion}, {@code ┃Expected}, or {@code
     * ┃Actual} are coloured. The {@code ┃} (U+2503 BOX DRAWINGS HEAVY VERTICAL) prefix ensures the
     * match targets only the label column boundary, not values that happen to contain these words.
     * Border separator lines ({@code ┠─…─┨}) are intentionally left uncoloured; they serve as
     * natural visual breaks between assertion entries.
     *
     * @param rendered the raw rendered table string from {@link
     *     org.springframework.shell.jline.tui.table.Table#render}
     * @return the same table with ANSI colour escape codes on assertion content lines
     */
    private String colorizeAssertionRows(String rendered) {
        String[] lines = rendered.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("┃Assertion") || line.contains("┃Expected") || line.contains("┃Actual")) {
                lines[i] = ASSERTION_COLOR + line + ANSI_RESET;
            }
        }
        return String.join("\n", lines);
    }

    /**
     * Returns a display string for a nullable value. Returns {@code "-"} when {@code value} is
     * {@code null}, otherwise delegates to {@link String#valueOf(Object)}.
     *
     * @param value the object to convert; may be {@code null}
     * @return a non-null display string
     */
    private String nullToDisplay(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
