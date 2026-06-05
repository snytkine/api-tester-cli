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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.shell.jline.tui.style.FigureSettings;

/**
 * Renders a bordered error panel containing one line per error message to a {@link PrintWriter}.
 *
 * <h3>Visual layout</h3>
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  ✖  Error  Duplicate test name: "login flow" appears 2 times    │
 * │  ✖  Error  Duplicate test name: "get users" appears 3 times     │
 * └──────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h3>Rendering rules</h3>
 *
 * <ul>
 *   <li>The outer box uses Unicode box-drawing characters (┌ ─ ┐ │ └ ┘).
 *   <li>Box width is controlled by the {@code width} parameter passed to {@link #render}.
 *   <li>Each error line is prefixed with the figure from {@link FigureSettings#error()} followed by
 *       the word {@code Error}. Both the figure and the word are rendered in red (ANSI code 31) when
 *       {@code useColors} is {@code true}.
 *   <li>The error message text following the label uses the default terminal colour.
 *   <li>Messages that exceed the available inner width are word-wrapped across multiple lines.
 *       Continuation lines are indented to align with the message text on the first line.
 * </ul>
 *
 * <p>This class is stateless and not a Spring bean; instantiate it directly wherever needed. It is
 * intentionally not a Spring singleton so it can be used from both {@link
 * io.github.snytkine.apitester.api_tester_cli.ui.TerminalUiController} (before the suite grid is
 * drawn) and from non-UI code paths in {@link
 * io.github.snytkine.apitester.api_tester_cli.commands.RunSuiteCommand}.
 *
 * <p>Thread-safety: all methods are stateless and may be called concurrently from multiple threads.
 */
public final class ErrorBox {

    private static final String ANSI_RED = "\033[31m";
    private static final String ANSI_RESET = "\033[0m";

    /**
     * Renders the bordered error panel to {@code output}.
     *
     * <p>The panel is {@code width} characters wide. Each error message is rendered inside the box
     * with the prefix {@link FigureSettings#error()} figure followed by the word {@code Error}. When
     * {@code useColors} is {@code true}, both the figure and the word {@code Error} are coloured red
     * (ANSI code 31); the message itself is always rendered in the default terminal colour.
     *
     * <p>Messages that exceed the available inner width are word-wrapped across multiple lines.
     * Continuation lines are indented by 12 spaces so that the message text aligns with the first
     * line. Breaks are made at the last space before the column limit; when no space is available in
     * the range the line is broken at the column limit directly.
     *
     * <p>Visible line layout (first line): {@code │  <figure>  Error  <message chunk>│}. The fixed
     * prefix occupies 12 visible characters (2 leading spaces + 1 figure + 2 spaces + 5 for "Error"
     * + 2 spaces), leaving {@code width - 14} characters per line for the message.
     *
     * @param errors non-empty list of error messages to display; each element may produce multiple
     *     wrapped lines
     * @param useColors {@code true} to render the figure and the word "Error" in red (ANSI code 31)
     * @param width terminal column count used to size the box; values below 14 are clamped to 14
     * @param output target writer; the caller is responsible for flushing after this call returns
     */
    public void render(List<String> errors, boolean useColors, int width, PrintWriter output) {
        int w = Math.max(14, width);
        int innerWidth = w - 2; // visible chars between the two │ borders
        // Fixed prefix per content line (visible chars):
        //   "  " (2) + figure (1) + "  " (2) + "Error" (5) + "  " (2) = 12
        int prefixWidth = 12;
        int msgArea = innerWidth - prefixWidth;
        String continuation = " ".repeat(prefixWidth); // indent for wrapped continuation lines

        String figure = FigureSettings.defaults().error();
        String topBorder = "┌" + "─".repeat(innerWidth) + "┐";
        String botBorder = "└" + "─".repeat(innerWidth) + "┘";

        output.println(topBorder);
        for (String error : errors) {
            String coloredFigure = useColors ? ANSI_RED + figure + ANSI_RESET : figure;
            String coloredLabel = useColors ? ANSI_RED + "Error" + ANSI_RESET : "Error";

            List<String> chunks = wrapMessage(error, msgArea);

            // First chunk: print with the figure + label prefix
            String first = chunks.get(0);
            output.println("│  " + coloredFigure + "  " + coloredLabel + "  " + pad(first, msgArea) + "│");

            // Continuation chunks: indent to align message text under the first line
            for (int i = 1; i < chunks.size(); i++) {
                output.println("│" + continuation + pad(chunks.get(i), msgArea) + "│");
            }
        }
        output.println(botBorder);
    }

    /**
     * Splits {@code message} into lines of at most {@code lineWidth} visible characters, breaking at
     * word boundaries (spaces) where possible. When no space is available within the range the break
     * is made at the column limit directly.
     *
     * @param message the message to wrap; must not be {@code null}
     * @param lineWidth maximum visible characters per line; values ≤ 0 return a single empty string
     * @return a non-empty list of line chunks, each at most {@code lineWidth} characters long
     */
    static List<String> wrapMessage(String message, int lineWidth) {
        if (lineWidth <= 0) return List.of("");
        if (message.length() <= lineWidth) return List.of(message);
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < message.length()) {
            if (message.length() - start <= lineWidth) {
                lines.add(message.substring(start));
                break;
            }
            int end = start + lineWidth;
            int spaceIndex = message.lastIndexOf(' ', end - 1);
            if (spaceIndex > start) {
                lines.add(message.substring(start, spaceIndex));
                start = spaceIndex + 1; // skip the space
            } else {
                lines.add(message.substring(start, end));
                start = end;
            }
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    /**
     * Right-pads {@code text} with spaces to exactly {@code width} visible characters, or returns it
     * unchanged if it is already {@code width} characters or longer.
     *
     * @param text the string to pad
     * @param width the desired visible width
     * @return a string of at least {@code width} characters
     */
    private static String pad(String text, int width) {
        if (text.length() >= width) return text;
        return text + " ".repeat(width - text.length());
    }
}
