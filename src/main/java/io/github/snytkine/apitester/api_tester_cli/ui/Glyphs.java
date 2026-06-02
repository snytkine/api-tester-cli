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

/**
 * Unicode glyph constants and Braille spinner frame sequences used by the terminal UI renderer.
 *
 * <p>All fields are compile-time {@code static final} constants. This class is a pure namespace and
 * cannot be instantiated.
 *
 * <p>Thread-safety: trivially thread-safe — every field is a {@code static final} constant.
 */
public final class Glyphs {

    /**
     * Ten-frame Braille spinner sequence for animating running tests.
     *
     * <p>Used in Phase 5+ to display a cycling animation while a test is in progress. Each frame is
     * one Unicode character wide.
     */
    public static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    /** Glyph displayed when a test case passes all assertions. */
    public static final String PASS = "✓";

    /** Glyph displayed when a test case fails one or more assertions. */
    public static final String FAIL = "✗";

    /** Glyph displayed when a test case is skipped. ⊘ = U+2298 (circled division slash). */
    public static final String SKIP = "⊘";

    /** Glyph displayed when a test case throws an unexpected exception. ⚠ = U+26A0 (warning sign). */
    public static final String ERROR = "⚠";

    /** Glyph displayed while a test case is currently running (static placeholder for Phase 4). */
    public static final String RUNNING = "▶";

    /** Glyph displayed for a test case that has not yet started. */
    public static final String PENDING = "·";

    private Glyphs() {}
}
