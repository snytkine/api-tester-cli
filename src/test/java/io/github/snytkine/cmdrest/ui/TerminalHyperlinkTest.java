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
package io.github.snytkine.cmdrest.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link TerminalHyperlink}.
 *
 * <p>Assertions avoid hard-coding absolute paths so that the tests pass identically on POSIX and
 * Windows: they check URI structure ({@code file:} scheme, percent-encoding, trailing file name) and
 * exact escape-sequence framing rather than a full literal URI.
 */
class TerminalHyperlinkTest {

    /** ESC character that opens every OSC 8 sequence. */
    private static final String ESC = "\033";

    // --- toFileUri ---

    @Test
    void toFileUriProducesAnAbsoluteFileScheme(@TempDir Path tempDir) {
        String uri = TerminalHyperlink.toFileUri(tempDir.resolve("report.html"));

        assertThat(uri).startsWith("file:/").endsWith("/report.html");
    }

    @Test
    void toFileUriMakesRelativePathsAbsolute() {
        String uri = TerminalHyperlink.toFileUri(Path.of("report.html"));

        assertThat(uri).startsWith("file:/").endsWith("/report.html");
        assertThat(uri).doesNotContain("file:report.html");
    }

    @Test
    void toFileUriPercentEncodesSpaces(@TempDir Path tempDir) {
        String uri = TerminalHyperlink.toFileUri(tempDir.resolve("my reports").resolve("test report.html"));

        assertThat(uri).contains("my%20reports").endsWith("test%20report.html");
        assertThat(uri).doesNotContain(" ");
    }

    // --- fileLink: unsupported terminal ---

    @Test
    void fileLinkReturnsBareUriWhenHyperlinksAreNotSupported(@TempDir Path tempDir) {
        Path report = tempDir.resolve("report.html");

        String rendered = TerminalHyperlink.fileLink(report, false);

        assertThat(rendered).isEqualTo(TerminalHyperlink.toFileUri(report));
        assertThat(rendered).doesNotContain(ESC);
    }

    // --- fileLink: OSC 8 framing ---

    @Test
    void fileLinkWrapsUriInOsc8SequenceWhenSupported(@TempDir Path tempDir) {
        Path report = tempDir.resolve("report.html");
        String uri = TerminalHyperlink.toFileUri(report);

        String rendered = TerminalHyperlink.fileLink(report, true);

        assertThat(rendered).isEqualTo(ESC + "]8;;" + uri + ESC + "\\" + uri + ESC + "]8;;" + ESC + "\\");
    }

    @Test
    void fileLinkUsesTheUriAsVisibleTextSoUnsupportedTerminalsStillShowIt(@TempDir Path tempDir) {
        Path report = tempDir.resolve("report.html");
        String uri = TerminalHyperlink.toFileUri(report);

        String rendered = TerminalHyperlink.fileLink(report, true);

        // Stripping every OSC 8 sequence must leave exactly the URI as the visible text.
        String visible = rendered.replaceAll("\033]8;;[^\033]*\033\\\\", "");
        assertThat(visible).isEqualTo(uri);
    }

    @Test
    void fileLinkTargetAndVisibleTextAreTheSameUri(@TempDir Path tempDir) {
        String rendered = TerminalHyperlink.fileLink(tempDir.resolve("report.html"), true);

        // The URI appears twice: once as the link target, once as the visible label.
        assertThat(rendered.split("file:", -1)).hasSize(3);
    }
}
