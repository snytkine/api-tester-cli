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
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a structured failure-detail table for a single failed test case using manually
 * constructed box-drawing characters.
 *
 * <p>Each {@link AssertionFailure} in the list contributes an {@code Assertion} row containing the
 * failure message, plus optional {@code Expected}, {@code Actual}, and {@code Error} rows. When
 * both {@code expected} and {@code actual} are {@code null}, those two rows are suppressed and only
 * an {@code Error} row is emitted if {@code error} is non-{@code null}. When there are multiple
 * failures, a light horizontal separator ({@code ┠─┼─┨}) is drawn between each failure group.
 *
 * <p>The label column is fixed at {@link #LABEL_COL_WIDTH} total characters (including one leading
 * and one trailing space); the value column expands to fill the remaining terminal {@code width}.
 * Long values are word-wrapped into continuation lines whose label cell is left blank.
 *
 * <p>Border rows use heavy box-drawing characters for the outer frame:
 *
 * <pre>
 *   ┏━━━━━━━━━━━━━━┯━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
 *   ┃ Test Name    │ POST /users returns 201                                    ┃
 *   ┣━━━━━━━━━━━━━━┿━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫
 *   ┃ Assertion    │ status_code equals 201                                     ┃
 *   ┃ Expected     │ 201                                                        ┃
 *   ┃ Actual       │ 400                                                        ┃
 *   ┃ Error        │ Expected status code 201 but was 400                      ┃
 *   ┗━━━━━━━━━━━━━━┷━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
 * </pre>
 *
 * <p>When {@code useColors} is {@code true}, the {@code Test Name} row is rendered with ANSI
 * reverse-video (inverted foreground/background) applied post-render via {@link
 * #applyRowColors(String)}, so that column-width calculations are not affected by escape-byte
 * injection into cell content. All other rows use the terminal's default colour.
 *
 * <p>This class is package-private and stateless; it holds no mutable state and is safe to call
 * from any thread. It is intentionally not a Spring bean so that it can be instantiated directly
 * in unit tests without a Spring context.
 *
 * <p>Thread-safety: all methods are stateless and may be called concurrently from multiple threads.
 */
final class FailureTableRenderer {

    /** Total character width of the label column, including one leading and one trailing space. */
    static final int LABEL_COL_WIDTH = 14;

    /** ANSI reverse-video escape applied to the {@code Test Name} row when colours are enabled. */
    static final String INVERSE_VIDEO = "\033[7m";

    /** ANSI bright-green foreground applied to {@code Assertion} rows when colours are enabled. */
    static final String ASSERTION_COLOR = "\033[92m";

    /** ANSI bright-red foreground applied to {@code Error} rows when colours are enabled. */
    static final String ERROR_COLOR = "\033[91m";

    /**
     * ANSI bright-yellow (SGR 93) foreground applied to {@code Expected} rows when colours are
     * enabled. This is lighter than the standard yellow (SGR 33) used for the suite-start banner.
     */
    static final String EXPECTED_COLOR = "\033[93m";

    /**
     * Label text for error rows. A ballot-X (U+2717) prefix followed by a space signals an error
     * condition at a glance without relying on colour alone.
     */
    static final String ERROR_LABEL = "✗ Error";

    private static final String ANSI_RESET = "\033[0m";

    /**
     * Renders a bordered table for one failed test to {@code output}.
     *
     * <p>The table has two columns: a narrow label column (fixed at {@link #LABEL_COL_WIDTH}
     * characters) and a value column that expands to fill the remaining {@code width}. Row layout:
     *
     * <ol>
     *   <li>Row 0 — {@code Test Name} / {@code testName} (always present)
     *   <li>For each failure: an {@code Assertion} row (always); if {@code expected} or {@code
     *       actual} is non-{@code null}: {@code Expected}, {@code Actual}, and optionally {@code
     *       Error} rows; otherwise only an {@code Error} row if {@code error} is non-{@code null}
     *   <li>A light separator ({@code ┠─┼─┨}) is inserted between successive failure groups
     * </ol>
     *
     * <p>When {@code useColors} is {@code true}, row colours are applied post-render via {@link
     * #applyRowColors(String)}: the {@code Test Name} row receives reverse-video, {@code Assertion}
     * rows receive bright-green, and {@code Error} rows receive bright-red.
     *
     * @param testName the name of the failed test case
     * @param failures the list of assertion failures; must not be empty
     * @param useColors {@code true} to apply ANSI colours to the {@code Test Name}, {@code
     *     Assertion}, and {@code Error} rows
     * @param width terminal column count used to size the value column
     * @param output the writer to which rendered output is appended
     */
    void render(String testName, List<AssertionFailure> failures, boolean useColors, int width, PrintWriter output) {
        int valueCellWidth = Math.max(4, width - 2 - LABEL_COL_WIDTH - 1);
        StringBuilder sb = new StringBuilder();

        appendBorderRow(sb, '┏', '━', '┯', '┓', valueCellWidth);
        appendContentRow(sb, "Test Name", testName, valueCellWidth);
        appendBorderRow(sb, '┣', '━', '┿', '┫', valueCellWidth);

        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) {
                appendBorderRow(sb, '┠', '─', '┼', '┨', valueCellWidth);
            }
            AssertionFailure f = failures.get(i);
            appendContentRow(sb, "Assertion", f.description(), valueCellWidth);
            if (f.expected() != null || f.actual() != null) {
                appendContentRow(sb, "Expected", nullToDisplay(f.expected()), valueCellWidth);
                appendContentRow(sb, "Actual", nullToDisplay(f.actual()), valueCellWidth);
                if (f.error() != null) {
                    appendContentRow(sb, ERROR_LABEL, f.error(), valueCellWidth);
                }
            } else if (f.error() != null) {
                appendContentRow(sb, ERROR_LABEL, f.error(), valueCellWidth);
            }
        }

        appendBorderRow(sb, '┗', '━', '┷', '┛', valueCellWidth);

        String rendered = sb.toString();
        if (useColors) {
            rendered = applyRowColors(rendered);
        }
        output.println(rendered);
    }

    /**
     * Renders a bordered table for a test that ended in {@link
     * io.github.snytkine.apitester.api_tester_cli.event.TestStatus#ERROR} (e.g. an HTTP I/O failure
     * such as a connection refused because the target service is not running) to {@code output}.
     *
     * <p>An error is <em>not</em> an assertion outcome: no assertion was evaluated. Unlike {@link
     * #render}, which emits an {@code Assertion} row per failure, this method emits a single {@link
     * #ERROR_LABEL} row per entry carrying the entry's {@link AssertionFailure#description()} — the
     * captured error text — so the failure never appears as a (green) assertion row. No {@code
     * Expected} or {@code Actual} rows are drawn.
     *
     * <p>When {@code useColors} is {@code true}, the {@code Test Name} row receives reverse-video and
     * every {@code Error} row bright-red, applied post-render via {@link #applyRowColors(String)}.
     *
     * @param testName the name of the errored test case
     * @param failures the collected error entries; each entry's {@link AssertionFailure#description()}
     *     is rendered as an {@code Error} row. Typically a single entry; when empty only the {@code
     *     Test Name} row is drawn
     * @param useColors {@code true} to apply ANSI colours to the {@code Test Name} and {@code Error} rows
     * @param width terminal column count used to size the value column
     * @param output the writer to which rendered output is appended
     */
    void renderError(
            String testName, List<AssertionFailure> failures, boolean useColors, int width, PrintWriter output) {
        int valueCellWidth = Math.max(4, width - 2 - LABEL_COL_WIDTH - 1);
        StringBuilder sb = new StringBuilder();

        appendBorderRow(sb, '┏', '━', '┯', '┓', valueCellWidth);
        appendContentRow(sb, "Test Name", testName, valueCellWidth);
        appendBorderRow(sb, '┣', '━', '┿', '┫', valueCellWidth);
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) {
                appendBorderRow(sb, '┠', '─', '┼', '┨', valueCellWidth);
            }
            appendContentRow(sb, ERROR_LABEL, nullToDisplay(failures.get(i).description()), valueCellWidth);
        }
        appendBorderRow(sb, '┗', '━', '┷', '┛', valueCellWidth);

        String rendered = sb.toString();
        if (useColors) {
            rendered = applyRowColors(rendered);
        }
        output.println(rendered);
    }

    /**
     * Renders a bordered table for a single {@code depends-on} parent-failure test to {@code output}.
     *
     * <p>Unlike {@link #render}, this test sent no request and evaluated no assertions of its own: it
     * was failed solely because a parent test it depends on failed. The table therefore shows only two
     * rows — a {@code Test Name} row and a single {@link #ERROR_LABEL} row carrying {@code message} —
     * with no {@code Assertion}, {@code Expected}, or {@code Actual} rows.
     *
     * <p>When {@code useColors} is {@code true}, the {@code Test Name} row receives reverse-video and
     * the {@code Error} row bright-red, applied post-render via {@link #applyRowColors(String)}.
     *
     * @param testName the name of the failed test case
     * @param message the parent-failure message to show as the {@code Error} value (see {@link
     *     io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult#parentFailureMessage(String)})
     * @param useColors {@code true} to apply ANSI colours to the {@code Test Name} and {@code Error} rows
     * @param width terminal column count used to size the value column
     * @param output the writer to which rendered output is appended
     */
    void renderParentFailure(String testName, String message, boolean useColors, int width, PrintWriter output) {
        int valueCellWidth = Math.max(4, width - 2 - LABEL_COL_WIDTH - 1);
        StringBuilder sb = new StringBuilder();

        appendBorderRow(sb, '┏', '━', '┯', '┓', valueCellWidth);
        appendContentRow(sb, "Test Name", testName, valueCellWidth);
        appendBorderRow(sb, '┣', '━', '┿', '┫', valueCellWidth);
        appendContentRow(sb, ERROR_LABEL, message, valueCellWidth);
        appendBorderRow(sb, '┗', '━', '┷', '┛', valueCellWidth);

        String rendered = sb.toString();
        if (useColors) {
            rendered = applyRowColors(rendered);
        }
        output.println(rendered);
    }

    /**
     * Applies per-row ANSI colour escapes to an already-rendered table string, including
     * word-wrapped continuation lines.
     *
     * <p>Named label rows determine which colour is "active":
     *
     * <ul>
     *   <li>{@code ┃ Test Name} — reverse-video ({@link #INVERSE_VIDEO}); not carried to
     *       continuation lines
     *   <li>{@code ┃ Assertion} — bright green ({@link #ASSERTION_COLOR}); carried to continuations
     *   <li>{@code ┃ Expected} — bright yellow ({@link #EXPECTED_COLOR}); carried to continuations
     *   <li>{@code ┃ Actual} — uncoloured; clears the active colour
     *   <li>{@code ┃ ✗} (error row) — bright red ({@link #ERROR_COLOR}); carried to continuations
     * </ul>
     *
     * <p>A <em>continuation line</em> is a content row whose label cell is blank (emitted by {@link
     * #appendContentRow} for wrapped values). It inherits the active colour from the most recent
     * named label row. Border rows (containing {@code ━} or {@code ─}) clear the active colour.
     *
     * <p>In every case the colour escape is applied only between the outer {@code ┃} borders via
     * {@link #colorizeInnerContent(String, String)}, leaving those border characters in the
     * terminal's default colour to avoid rendering artefacts.
     *
     * @param rendered the raw rendered table string
     * @return the table with per-row colour escapes applied
     */
    private String applyRowColors(String rendered) {
        String[] lines = rendered.split("\n", -1);
        String currentColor = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("━") || line.contains("─")) {
                currentColor = null;
            } else if (line.contains("┃ Test Name")) {
                currentColor = null;
                lines[i] = colorizeInnerContent(line, INVERSE_VIDEO);
            } else if (line.contains("┃ Assertion")) {
                currentColor = ASSERTION_COLOR;
                lines[i] = colorizeInnerContent(line, currentColor);
            } else if (line.contains("┃ Expected")) {
                currentColor = EXPECTED_COLOR;
                lines[i] = colorizeInnerContent(line, currentColor);
            } else if (line.contains("┃ Actual")) {
                currentColor = null;
            } else if (line.contains("┃ ✗")) {
                currentColor = ERROR_COLOR;
                lines[i] = colorizeInnerContent(line, currentColor);
            } else if (currentColor != null && line.contains("┃")) {
                lines[i] = colorizeInnerContent(line, currentColor);
            }
        }
        return String.join("\n", lines);
    }

    /**
     * Applies {@code colorCode} to the characters between the outer {@code ┃} borders of {@code
     * line}, appending {@code ANSI_RESET} after the inner content and leaving the border characters
     * in the default colour.
     *
     * <p>If fewer than two {@code ┃} characters are found the escape is applied to the whole line
     * as a fallback.
     *
     * @param line a single rendered table row
     * @param colorCode the opening ANSI escape sequence (e.g. {@link #ASSERTION_COLOR})
     * @return the colourised line
     */
    private static String colorizeInnerContent(String line, String colorCode) {
        int leftBorder = line.indexOf('┃');
        int rightBorder = line.lastIndexOf('┃');
        if (leftBorder >= 0 && rightBorder > leftBorder) {
            return line.substring(0, leftBorder + 1)
                    + colorCode
                    + line.substring(leftBorder + 1, rightBorder)
                    + ANSI_RESET
                    + line.substring(rightBorder);
        }
        return colorCode + line + ANSI_RESET;
    }

    /**
     * Returns a display string for a nullable value. Returns {@code "-"} when {@code value} is
     * {@code null}, otherwise delegates to {@link String#valueOf(Object)}.
     *
     * @param value the object to convert; may be {@code null}
     * @return a non-null display string
     */
    private static String nullToDisplay(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    /**
     * Appends one horizontal border row to {@code sb} and a trailing newline.
     *
     * <p>The row is constructed as: {@code left} + {@code fill} × {@link #LABEL_COL_WIDTH} +
     * {@code junction} + {@code fill} × {@code valueCellWidth} + {@code right} + {@code \n}.
     *
     * @param sb the target string builder
     * @param left the leftmost corner/T character (e.g. {@code ┏}, {@code ┣}, {@code ┗})
     * @param fill the horizontal fill character (e.g. {@code ━}, {@code ─})
     * @param junction the column-intersection character (e.g. {@code ┯}, {@code ┿}, {@code ┷})
     * @param right the rightmost corner/T character (e.g. {@code ┓}, {@code ┫}, {@code ┛})
     * @param valueCellWidth total character width of the value column cell
     */
    private static void appendBorderRow(
            StringBuilder sb, char left, char fill, char junction, char right, int valueCellWidth) {
        sb.append(left);
        sb.append(String.valueOf(fill).repeat(LABEL_COL_WIDTH));
        sb.append(junction);
        sb.append(String.valueOf(fill).repeat(valueCellWidth));
        sb.append(right);
        sb.append('\n');
    }

    /**
     * Appends one or more content rows to {@code sb} for the given label/value pair.
     *
     * <p>The value is word-wrapped to fit the value column's text area (see {@link #wrapText}). The
     * first wrapped line is preceded by the label in the label column; continuation lines leave the
     * label column blank.
     *
     * @param sb the target string builder
     * @param label the row label (e.g. {@code "Assertion"}, {@code "Expected"})
     * @param value the cell value; long values are wrapped across multiple lines
     * @param valueCellWidth total character width of the value column cell (including padding spaces)
     */
    private static void appendContentRow(StringBuilder sb, String label, String value, int valueCellWidth) {
        List<String> valueLines = wrapText(value, valueCellWidth - 2);
        for (int i = 0; i < valueLines.size(); i++) {
            sb.append('┃')
                    .append(cellContent(i == 0 ? label : "", LABEL_COL_WIDTH))
                    .append('│')
                    .append(cellContent(valueLines.get(i), valueCellWidth))
                    .append('┃')
                    .append('\n');
        }
    }

    /**
     * Returns a string of exactly {@code cellWidth} characters for use as table cell content.
     *
     * <p>The string is formatted as one leading space, the (possibly truncated) {@code text} padded
     * with trailing spaces to fill the available text area, and one trailing space. If {@code
     * cellWidth} is too small to accommodate any padding, two spaces are returned.
     *
     * @param text the cell text; must not be {@code null}
     * @param cellWidth the total width of the cell in characters, including the two padding spaces
     * @return a {@code cellWidth}-character cell content string
     */
    private static String cellContent(String text, int cellWidth) {
        int textAreaWidth = cellWidth - 2;
        if (textAreaWidth <= 0) return "  ";
        String content = text.length() > textAreaWidth ? text.substring(0, textAreaWidth) : text;
        return " " + String.format("%-" + textAreaWidth + "s", content) + " ";
    }

    /**
     * Splits {@code text} into a list of lines each no longer than {@code maxWidth} characters,
     * breaking at whitespace where possible.
     *
     * <p>If a segment contains no whitespace within {@code maxWidth}, a hard break is applied at
     * {@code maxWidth}. If {@code maxWidth} is non-positive or the text already fits, a single-
     * element list is returned.
     *
     * @param text the text to wrap; must not be {@code null}
     * @param maxWidth the maximum line length in characters
     * @return a non-empty list of line segments
     */
    private static List<String> wrapText(String text, int maxWidth) {
        if (maxWidth <= 0 || text.length() <= maxWidth) return List.of(text);
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            if (remaining.length() <= maxWidth) {
                lines.add(remaining);
                break;
            }
            int breakAt = remaining.lastIndexOf(' ', maxWidth);
            if (breakAt <= 0) breakAt = maxWidth;
            lines.add(remaining.substring(0, breakAt));
            remaining = remaining.substring(breakAt).stripLeading();
        }
        return lines;
    }
}
