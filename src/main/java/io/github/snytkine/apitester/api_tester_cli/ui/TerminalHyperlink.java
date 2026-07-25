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

import java.nio.file.Path;

/**
 * Renders clickable terminal hyperlinks using the OSC 8 escape sequence, with a graceful fallback to
 * a plain {@code file://} URI.
 *
 * <h3>OSC 8 hyperlinks</h3>
 *
 * <p>An OSC 8 hyperlink explicitly tells the terminal emulator that a run of text is a link, rather
 * than relying on the emulator's built-in URL-detection regular expression (which frequently covers
 * {@code http}/{@code https} but not {@code file}). The sequence is:
 *
 * <pre>
 * ESC ]8;;&lt;uri&gt; ESC \  &lt;visible text&gt;  ESC ]8;; ESC \
 * </pre>
 *
 * <p>The empty field between the two semicolons is the (unused) hyperlink parameter list. {@code ESC
 * \} is the String Terminator (ST).
 *
 * <h3>Compatibility</h3>
 *
 * <p>OSC 8 is supported by iTerm2, WezTerm, kitty, VTE-based terminals (GNOME Terminal, Tilix),
 * Windows Terminal, Konsole, and foot. Terminals that do not understand the sequence — notably
 * macOS Terminal.app — ignore it and render only the visible text. Because
 * {@link #fileLink(Path, boolean)} uses the {@code file://} URI itself as the visible text, such
 * terminals still show a complete, copy-pasteable URI that their own URL detector may pick up.
 *
 * <h3>Escape-sequence safety</h3>
 *
 * <p>Callers must pass {@code hyperlinksSupported = false} whenever output is not going to an
 * interactive terminal (piped, redirected to a file, or running in CI). Emitting escape sequences
 * into a redirected stream corrupts the captured output.
 *
 * <p>Thread-safety: trivially thread-safe — this class holds no state and every method is a pure
 * function of its arguments.
 */
public final class TerminalHyperlink {

    /** Opens an OSC 8 sequence; the target URI and a String Terminator follow this prefix. */
    private static final String OSC8_START = "\033]8;;";

    /** String Terminator (ST) that closes each half of an OSC 8 sequence. */
    private static final String ST = "\033\\";

    /** Closes an OSC 8 hyperlink by opening a new one with an empty URI. */
    private static final String OSC8_END = OSC8_START + ST;

    private TerminalHyperlink() {}

    /**
     * Converts a filesystem path to an absolute {@code file://} URI suitable for opening in a web
     * browser.
     *
     * <p>Delegates to {@link Path#toUri()}, which produces the correct platform-specific form and
     * percent-encodes characters that are not legal in a URI. This matters in practice for two
     * cases that naive string concatenation gets wrong: report directories containing spaces (which
     * must be encoded as {@code %20}) and Windows paths (which must become {@code
     * file:///C:/path/to/report.html}, with a drive letter and forward slashes).
     *
     * @param path the path to convert; converted to an absolute path first so that the resulting URI
     *     is resolvable independently of the process working directory
     * @return an absolute {@code file://} URI string
     */
    public static String toFileUri(Path path) {
        return path.toAbsolutePath().toUri().toString();
    }

    /**
     * Renders a clickable link to a local file, falling back to the bare URI when the terminal
     * cannot render hyperlinks.
     *
     * <p>The visible text is always the {@code file://} URI itself. This is deliberate: in a
     * terminal that supports OSC 8 the user gets a real hyperlink, and in one that does not the user
     * still sees a complete URI that can be copied or auto-detected by the emulator.
     *
     * @param path the file to link to
     * @param hyperlinksSupported {@code true} when output is going to an interactive terminal and
     *     escape sequences may safely be emitted; {@code false} to return the bare URI
     * @return an OSC 8 hyperlink when {@code hyperlinksSupported} is {@code true}, otherwise the
     *     plain {@code file://} URI
     */
    public static String fileLink(Path path, boolean hyperlinksSupported) {
        String uri = toFileUri(path);
        if (!hyperlinksSupported) {
            return uri;
        }
        return OSC8_START + uri + ST + uri + OSC8_END;
    }
}
