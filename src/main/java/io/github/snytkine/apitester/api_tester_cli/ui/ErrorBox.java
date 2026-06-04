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
 *   <li>Messages that exceed the available inner width are truncated with "…".
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
     * <p>The panel is {@code width} characters wide. Each error message is rendered on its own line
     * inside the box with the prefix: {@link FigureSettings#error()} figure followed by the word
     * {@code Error}. When {@code useColors} is {@code true}, both the figure and the word {@code
     * Error} are coloured red (ANSI code 31); the message itself is always rendered in the default
     * terminal colour. Messages that exceed the available inner width are truncated with "…".
     *
     * <p>Visible line layout: {@code │  <figure>  Error  <message padded>│}. The fixed prefix
     * occupies 12 visible characters (2 leading spaces + 1 figure + 2 spaces + 5 for "Error" + 2
     * spaces), leaving {@code width - 14} characters for the message.
     *
     * @param errors non-empty list of error messages to display; each element produces one line
     * @param useColors {@code true} to render the figure and the word "Error" in red (ANSI code 31)
     * @param width terminal column count used to size the box; values below 14 are clamped to 14
     * @param output target writer; the caller is responsible for flushing after this call returns
     */
    public void render(List<String> errors, boolean useColors, int width, PrintWriter output) {
        int w = Math.max(14, width);
        int innerWidth = w - 2; // visible chars between the two │ borders
        // Fixed prefix per content line (visible chars):
        //   "  " (2) + figure (1) + "  " (2) + "Error" (5) + "  " (2) = 12
        int msgArea = innerWidth - 12;

        String figure = FigureSettings.defaults().error();
        String topBorder = "┌" + "─".repeat(innerWidth) + "┐";
        String botBorder = "└" + "─".repeat(innerWidth) + "┘";

        output.println(topBorder);
        for (String error : errors) {
            String coloredFigure = useColors ? ANSI_RED + figure + ANSI_RESET : figure;
            String coloredLabel = useColors ? ANSI_RED + "Error" + ANSI_RESET : "Error";
            String msg = truncate(error, msgArea);
            // Pad message to fill msgArea so the closing │ stays at column w
            String paddedMsg = msg + " ".repeat(Math.max(0, msgArea - msg.length()));
            output.println("│  " + coloredFigure + "  " + coloredLabel + "  " + paddedMsg + "│");
        }
        output.println(botBorder);
    }

    /**
     * Truncates {@code message} to at most {@code maxLen} visible characters. When truncation is
     * needed the last character of the result is replaced with "…".
     *
     * @param message the message to truncate; must not be {@code null}
     * @param maxLen maximum number of visible characters to return; values ≤ 0 return an empty string
     * @return the original message if it fits, otherwise a truncated string ending with "…"
     */
    private static String truncate(String message, int maxLen) {
        if (maxLen <= 0) return "";
        if (message.length() <= maxLen) return message;
        if (maxLen == 1) return "…";
        return message.substring(0, maxLen - 1) + "…";
    }
}
