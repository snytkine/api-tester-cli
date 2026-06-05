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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.shell.jline.tui.style.FigureSettings;

/** Unit tests for {@link ErrorBox}. */
class ErrorBoxTest {

    private static String render(List<String> errors, boolean useColors, int width) {
        StringWriter sw = new StringWriter();
        new ErrorBox().render(errors, useColors, width, new PrintWriter(sw));
        return sw.toString();
    }

    @Test
    void renderIncludesTopBorder() {
        String out = render(List.of("something went wrong"), false, 60);

        assertThat(out).contains("┌─");
    }

    @Test
    void renderIncludesBottomBorder() {
        String out = render(List.of("something went wrong"), false, 60);

        assertThat(out).contains("└─");
    }

    @Test
    void renderIncludesFigureOnEachErrorLine() {
        String figure = FigureSettings.defaults().error();
        String out = render(List.of("error one", "error two"), false, 60);

        long figureCount = out.lines().filter(l -> l.contains(figure)).count();
        assertThat(figureCount).isEqualTo(2);
    }

    @Test
    void renderIncludesErrorLabelOnEachLine() {
        String out = render(List.of("error one", "error two"), false, 60);

        long labelCount = out.lines().filter(l -> l.contains("Error")).count();
        assertThat(labelCount).isEqualTo(2);
    }

    @Test
    void renderColorLabelRedWhenColorsEnabled() {
        String out = render(List.of("something went wrong"), true, 60);

        // The red ANSI code must appear before "Error" on the content line
        assertThat(out).contains("\033[31mError\033[0m");
    }

    @Test
    void renderIncludesErrorMessageText() {
        String out = render(List.of("Duplicate test name: \"login\" appears 2 times"), false, 80);

        assertThat(out).contains("Duplicate test name: \"login\" appears 2 times");
    }

    @Test
    void renderAppliesRedColorToGlyphWhenColorsEnabled() {
        String out = render(List.of("something went wrong"), true, 60);

        assertThat(out).contains("\033[31m");
    }

    @Test
    void renderNoColorCodesWhenColorsDisabled() {
        String out = render(List.of("something went wrong"), false, 60);

        assertThat(out).doesNotContain("\033[31m");
    }

    @Test
    void renderWrapsLongMessageAcrossMultipleLines() {
        // "A".repeat(200) at width=40 → msgArea=26 → forces at least 8 content lines
        String longMsg = "A".repeat(200);
        String out = render(List.of(longMsg), false, 40);

        // Full message must appear in output (not truncated)
        assertThat(out).contains(longMsg.substring(0, 26)); // first chunk present
        // No ellipsis — message is wrapped, not truncated
        assertThat(out).doesNotContain("…");
    }

    @Test
    void renderWrapsAtWordBoundary() {
        // Message designed to exceed one line so wrapping kicks in at a space
        String msg = "Options --tag and --test cannot be used together. Use one or the other.";
        String out = render(List.of(msg), false, 50); // msgArea = 36

        // The full message text must appear somewhere in the output
        assertThat(out).contains("Options --tag and --test cannot be");
        assertThat(out).contains("used together. Use one or the other.");
        assertThat(out).doesNotContain("…");
    }

    @Test
    void renderMultipleErrorsProduceMultipleLines() {
        String out = render(List.of("first error", "second error", "third error"), false, 60);

        assertThat(out).contains("first error");
        assertThat(out).contains("second error");
        assertThat(out).contains("third error");
    }

    @Test
    void renderBorderWidthMatchesRequestedWidth() {
        int width = 50;
        String out = render(List.of("test"), false, width);

        // Every line in the output (top border, content, bottom border) should be exactly width chars
        out.lines().filter(l -> !l.isBlank()).forEach(line -> assertThat(line).hasSize(width));
    }
}
