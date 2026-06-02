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
package io.github.snytkine.apitester.api_tester_cli.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@link TestProgressEvent} sealed hierarchy and {@link TestStatus} enum. */
class TestProgressEventTest {

    @Test
    void suiteStartedCarriesNameCountAndTimestamp() {
        Instant now = Instant.now();
        TestProgressEvent event = new TestProgressEvent.SuiteStarted("my-suite", 5, now);

        assertThat(event).isInstanceOf(TestProgressEvent.SuiteStarted.class);
        TestProgressEvent.SuiteStarted started = (TestProgressEvent.SuiteStarted) event;
        assertThat(started.suiteName()).isEqualTo("my-suite");
        assertThat(started.totalTestCount()).isEqualTo(5);
        assertThat(started.startedAt()).isEqualTo(now);
    }

    @Test
    void testStartedCarriesUniqueIdIndexAndName() {
        TestProgressEvent event = new TestProgressEvent.TestStarted("uid-2", 2, "login test");

        assertThat(event).isInstanceOf(TestProgressEvent.TestStarted.class);
        TestProgressEvent.TestStarted started = (TestProgressEvent.TestStarted) event;
        assertThat(started.uniqueId()).isEqualTo("uid-2");
        assertThat(started.testIndex()).isEqualTo(2);
        assertThat(started.testName()).isEqualTo("login test");
    }

    @Test
    void testCompletedPassCarriesAssertionCountAndEmptyFailureMessages() {
        TestProgressEvent event =
                new TestProgressEvent.TestCompleted("uid-0", 0, "get users", TestStatus.PASS, 123L, 5, List.of());

        TestProgressEvent.TestCompleted completed = (TestProgressEvent.TestCompleted) event;
        assertThat(completed.uniqueId()).isEqualTo("uid-0");
        assertThat(completed.status()).isEqualTo(TestStatus.PASS);
        assertThat(completed.durationMs()).isEqualTo(123L);
        assertThat(completed.assertionCount()).isEqualTo(5);
        assertThat(completed.failureMessages()).isEmpty();
    }

    @Test
    void testCompletedFailCarriesAllFailureMessages() {
        TestProgressEvent event = new TestProgressEvent.TestCompleted(
                "uid-1",
                1,
                "create item",
                TestStatus.FAIL,
                42L,
                3,
                List.of("expected 201 but was 400", "body did not match"));

        TestProgressEvent.TestCompleted completed = (TestProgressEvent.TestCompleted) event;
        assertThat(completed.status()).isEqualTo(TestStatus.FAIL);
        assertThat(completed.assertionCount()).isEqualTo(3);
        assertThat(completed.failureMessages()).containsExactly("expected 201 but was 400", "body did not match");
    }

    @Test
    void testCompletedErrorCarriesErrorMessage() {
        TestProgressEvent event = new TestProgressEvent.TestCompleted(
                "uid-3", 3, "timeout test", TestStatus.ERROR, 5000L, 0, List.of("Connection refused"));

        TestProgressEvent.TestCompleted completed = (TestProgressEvent.TestCompleted) event;
        assertThat(completed.status()).isEqualTo(TestStatus.ERROR);
        assertThat(completed.assertionCount()).isEqualTo(0);
        assertThat(completed.failureMessages()).containsExactly("Connection refused");
    }

    @Test
    void testCompletedSkipCarriesEmptyFailureMessages() {
        TestProgressEvent event =
                new TestProgressEvent.TestCompleted("uid-4", 4, "skip-me", TestStatus.SKIP, 0L, 0, List.of());

        TestProgressEvent.TestCompleted completed = (TestProgressEvent.TestCompleted) event;
        assertThat(completed.status()).isEqualTo(TestStatus.SKIP);
        assertThat(completed.durationMs()).isEqualTo(0L);
        assertThat(completed.assertionCount()).isEqualTo(0);
        assertThat(completed.failureMessages()).isEmpty();
    }

    @Test
    void suiteCompletedCarriesAllCountsAndDuration() {
        TestProgressEvent event = new TestProgressEvent.SuiteCompleted(8L, 2L, 1L, 0L, 3500L);

        TestProgressEvent.SuiteCompleted completed = (TestProgressEvent.SuiteCompleted) event;
        assertThat(completed.passCount()).isEqualTo(8L);
        assertThat(completed.failCount()).isEqualTo(2L);
        assertThat(completed.skipCount()).isEqualTo(1L);
        assertThat(completed.errorCount()).isEqualTo(0L);
        assertThat(completed.totalDurationMs()).isEqualTo(3500L);
    }

    @Test
    void exhaustiveSwitchCoversAllPermits() {
        TestProgressEvent[] events = {
            new TestProgressEvent.SuiteStarted("s", 1, Instant.now()),
            new TestProgressEvent.TestStarted("0", 0, "t"),
            new TestProgressEvent.TestCompleted("0", 0, "t", TestStatus.PASS, 10L, 1, List.of()),
            new TestProgressEvent.SuiteCompleted(1L, 0L, 0L, 0L, 100L)
        };

        for (TestProgressEvent event : events) {
            String label =
                    switch (event) {
                        case TestProgressEvent.SuiteStarted e -> "suite-started";
                        case TestProgressEvent.TestStarted e -> "test-started";
                        case TestProgressEvent.TestCompleted e -> "test-completed";
                        case TestProgressEvent.SuiteCompleted e -> "suite-completed";
                    };
            assertThat(label).isNotBlank();
        }
    }
}
