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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link TtyDetector}.
 *
 * <p>Tests use the package-private overload {@code shouldUseUi(boolean, boolean, boolean, boolean,
 * boolean, int)} to cover all logic branches without requiring environment variable manipulation.
 */
class TtyDetectorTest {

    // --- shouldUseUi (pure-logic overload) ---

    @Test
    void forceUiReturnsTrueRegardlessOfOtherConditions() {
        assertThat(TtyDetector.shouldUseUi(true, false, false, true, true, 0)).isTrue();
        assertThat(TtyDetector.shouldUseUi(true, true, false, true, true, 0)).isTrue();
        assertThat(TtyDetector.shouldUseUi(true, false, false, false, false, 200))
                .isTrue();
    }

    @Test
    void noUiReturnsFalseWhenForceUiIsAbsent() {
        assertThat(TtyDetector.shouldUseUi(false, true, true, false, false, 200))
                .isFalse();
    }

    @Test
    void forceUiTakesPrecedenceOverNoUi() {
        assertThat(TtyDetector.shouldUseUi(true, true, true, false, false, 200)).isTrue();
    }

    @Test
    void noTtyReturnsFalse() {
        assertThat(TtyDetector.shouldUseUi(false, false, false, false, false, 200))
                .isFalse();
    }

    @Test
    void noColorReturnsFalse() {
        assertThat(TtyDetector.shouldUseUi(false, false, true, true, false, 200))
                .isFalse();
    }

    @Test
    void ciEnvironmentReturnsFalse() {
        assertThat(TtyDetector.shouldUseUi(false, false, true, false, true, 200))
                .isFalse();
    }

    @Test
    void narrowTerminalReturnsFalse() {
        assertThat(TtyDetector.shouldUseUi(false, false, true, false, false, TtyDetector.MIN_TERMINAL_WIDTH - 1))
                .isFalse();
    }

    @Test
    void exactMinWidthReturnsTrue() {
        assertThat(TtyDetector.shouldUseUi(false, false, true, false, false, TtyDetector.MIN_TERMINAL_WIDTH))
                .isTrue();
    }

    @Test
    void allConditionsFavourableReturnsTrue() {
        assertThat(TtyDetector.shouldUseUi(false, false, true, false, false, 120))
                .isTrue();
    }

    // --- parseColumns ---

    @Test
    void parseColumnsReturnsEightyForNull() {
        assertThat(TtyDetector.parseColumns(null)).isEqualTo(80);
    }

    @Test
    void parseColumnsReturnsEightyForNonInteger() {
        assertThat(TtyDetector.parseColumns("abc")).isEqualTo(80);
        assertThat(TtyDetector.parseColumns("")).isEqualTo(80);
    }

    @ParameterizedTest
    @CsvSource({"40,40", "80,80", "120,120", "220,220"})
    void parseColumnsParsesValidIntegers(String input, int expected) {
        assertThat(TtyDetector.parseColumns(input)).isEqualTo(expected);
    }

    @Test
    void parseColumnsTrimsWhitespace() {
        assertThat(TtyDetector.parseColumns("  80  ")).isEqualTo(80);
    }

    // --- public shouldUseUi (environment-reading overload) ---

    @Test
    void shouldUseUiWithForceFlagReturnsTrueRegardlessOfEnvironment() {
        // --ui flag: always true, even without a real TTY in the test process
        assertThat(TtyDetector.shouldUseUi(true, false)).isTrue();
    }

    @Test
    void shouldUseUiWithNoUiFlagReturnsFalse() {
        assertThat(TtyDetector.shouldUseUi(false, true)).isFalse();
    }

    @Test
    void shouldUseUiWithoutFlagsReturnsFalseInTestEnvironment() {
        // Test processes have no console attached, so this should return false.
        assertThat(TtyDetector.shouldUseUi(false, false)).isFalse();
    }

    // --- isTty / supportsColor / isCI / getTerminalWidth (smoke tests) ---

    @Test
    void isTtyReturnsBooleanWithoutThrowing() {
        boolean result = TtyDetector.isTty();
        assertThat(result).isIn(true, false);
    }

    @Test
    void supportsColorReturnsBooleanWithoutThrowing() {
        boolean result = TtyDetector.supportsColor();
        assertThat(result).isIn(true, false);
    }

    @Test
    void isCiReturnsBooleanWithoutThrowing() {
        boolean result = TtyDetector.isCI();
        assertThat(result).isIn(true, false);
    }

    @Test
    void getTerminalWidthReturnsPositiveValue() {
        assertThat(TtyDetector.getTerminalWidth()).isGreaterThan(0);
    }
}
