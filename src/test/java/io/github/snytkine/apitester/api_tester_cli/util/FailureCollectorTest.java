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
package io.github.snytkine.apitester.api_tester_cli.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

/**
 * Unit tests for {@link FailureCollector}.
 *
 * <p>Exercises all public methods directly: both {@code fail} overloads, {@code getFailures},
 * {@code assertAll}, and the static {@code rewrap} helper including all branches.
 */
class FailureCollectorTest {

    private FailureCollector collector;

    @BeforeEach
    void setUp() {
        collector = new FailureCollector();
    }

    // -------------------------------------------------------------------------
    // fail(AssertionFailedError)
    // -------------------------------------------------------------------------

    @Test
    void fail_withAssertionFailedError_addsToList() {
        AssertionFailedError error = new AssertionFailedError("boom");
        collector.fail(error);
        assertThat(collector.getFailures()).containsExactly(error);
    }

    @Test
    void fail_withAssertionFailedError_preservesExpectedAndActual() {
        AssertionFailedError error = new AssertionFailedError("msg", "expected", "actual");
        collector.fail(error);
        AssertionFailedError stored = collector.getFailures().get(0);
        assertThat(stored.getExpected().getValue()).isEqualTo("expected");
        assertThat(stored.getActual().getValue()).isEqualTo("actual");
    }

    // -------------------------------------------------------------------------
    // fail(String, Throwable)
    // -------------------------------------------------------------------------

    @Test
    void fail_withMessageAndNullCause_addsEntry() {
        collector.fail("something went wrong", null);
        assertThat(collector.getFailures()).hasSize(1);
        assertThat(collector.getFailures().get(0).getMessage()).contains("something went wrong");
    }

    @Test
    void fail_withMessageAndCause_addsEntryWithCause() {
        RuntimeException cause = new RuntimeException("root");
        collector.fail("wrapped", cause);
        assertThat(collector.getFailures()).hasSize(1);
        assertThat(collector.getFailures().get(0).getCause()).isSameAs(cause);
    }

    // -------------------------------------------------------------------------
    // getFailures
    // -------------------------------------------------------------------------

    @Test
    void getFailures_initiallyEmpty() {
        assertThat(collector.getFailures()).isEmpty();
    }

    @Test
    void getFailures_multipleFailures_returnedInInsertionOrder() {
        AssertionFailedError first = new AssertionFailedError("first");
        AssertionFailedError second = new AssertionFailedError("second");
        collector.fail(first);
        collector.fail(second);
        assertThat(collector.getFailures()).containsExactly(first, second);
    }

    // -------------------------------------------------------------------------
    // assertAll
    // -------------------------------------------------------------------------

    @Test
    void assertAll_whenNoFailures_doesNotThrow() {
        assertThatCode(() -> collector.assertAll()).doesNotThrowAnyException();
    }

    @Test
    void assertAll_whenFailuresExist_throwsMultipleFailuresError() {
        collector.fail("one", null);
        collector.fail("two", null);
        assertThatThrownBy(() -> collector.assertAll())
                .isInstanceOf(MultipleFailuresError.class)
                .satisfies(e ->
                        assertThat(((MultipleFailuresError) e).getFailures()).hasSize(2));
    }

    // -------------------------------------------------------------------------
    // rewrap — structured expected/actual present
    // -------------------------------------------------------------------------

    @Test
    void rewrap_whenCaughtHasExpectedAndActual_copiesValues() {
        AssertionFailedError original = new AssertionFailedError("original", "exp", "act");
        AssertionFailedError result = FailureCollector.rewrap("new message", original);
        assertThat(result.getMessage()).isEqualTo("new message");
        assertThat(result.getExpected().getValue()).isEqualTo("exp");
        assertThat(result.getActual().getValue()).isEqualTo("act");
    }

    @Test
    void rewrap_whenCaughtHasExpectedAndActual_isExpectedAndActualDefined() {
        AssertionFailedError original = new AssertionFailedError("original", 200, 404);
        AssertionFailedError result = FailureCollector.rewrap("status mismatch", original);
        assertThat(result.isExpectedDefined()).isTrue();
        assertThat(result.isActualDefined()).isTrue();
    }

    // -------------------------------------------------------------------------
    // rewrap — plain AssertionError (no structured fields)
    // -------------------------------------------------------------------------

    @Test
    void rewrap_whenCaughtIsPlainAssertionError_returnsErrorWithoutStructuredFields() {
        AssertionError plain = new AssertionError("plain error");
        AssertionFailedError result = FailureCollector.rewrap("wrapped", plain);
        assertThat(result.getMessage()).isEqualTo("wrapped");
        assertThat(result.isExpectedDefined()).isFalse();
        assertThat(result.isActualDefined()).isFalse();
    }

    @Test
    void rewrap_whenCaughtIsAssertionFailedErrorWithoutFields_returnsErrorWithoutStructuredFields() {
        AssertionFailedError noFields = new AssertionFailedError("no fields");
        AssertionFailedError result = FailureCollector.rewrap("wrapped", noFields);
        assertThat(result.isExpectedDefined()).isFalse();
        assertThat(result.isActualDefined()).isFalse();
    }
}
