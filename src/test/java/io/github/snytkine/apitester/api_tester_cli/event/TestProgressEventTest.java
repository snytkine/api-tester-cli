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
    void testStartedCarriesIndexAndName() {
        TestProgressEvent event = new TestProgressEvent.TestStarted(2, "login test");

        assertThat(event).isInstanceOf(TestProgressEvent.TestStarted.class);
        TestProgressEvent.TestStarted started = (TestProgressEvent.TestStarted) event;
        assertThat(started.testIndex()).isEqualTo(2);
        assertThat(started.testName()).isEqualTo("login test");
    }

    @Test
    void testCompletedPassCarriesNullFailureSummary() {
        TestProgressEvent event = new TestProgressEvent.TestCompleted(0, "get users", TestStatus.PASS, 123L, null);

        TestProgressEvent.TestCompleted completed = (TestProgressEvent.TestCompleted) event;
        assertThat(completed.status()).isEqualTo(TestStatus.PASS);
        assertThat(completed.durationMs()).isEqualTo(123L);
        assertThat(completed.failureSummary()).isNull();
    }

    @Test
    void testCompletedFailCarriesFailureSummary() {
        TestProgressEvent event =
                new TestProgressEvent.TestCompleted(1, "create item", TestStatus.FAIL, 42L, "expected 201 but was 400");

        TestProgressEvent.TestCompleted completed = (TestProgressEvent.TestCompleted) event;
        assertThat(completed.status()).isEqualTo(TestStatus.FAIL);
        assertThat(completed.failureSummary()).isEqualTo("expected 201 but was 400");
    }

    @Test
    void testCompletedErrorCarriesErrorMessage() {
        TestProgressEvent event =
                new TestProgressEvent.TestCompleted(3, "timeout test", TestStatus.ERROR, 5000L, "Connection refused");

        TestProgressEvent.TestCompleted completed = (TestProgressEvent.TestCompleted) event;
        assertThat(completed.status()).isEqualTo(TestStatus.ERROR);
        assertThat(completed.failureSummary()).isEqualTo("Connection refused");
    }

    @Test
    void suiteCompletedCarriesCountsAndDuration() {
        TestProgressEvent event = new TestProgressEvent.SuiteCompleted(8L, 2L, 3500L);

        TestProgressEvent.SuiteCompleted completed = (TestProgressEvent.SuiteCompleted) event;
        assertThat(completed.passCount()).isEqualTo(8L);
        assertThat(completed.failCount()).isEqualTo(2L);
        assertThat(completed.totalDurationMs()).isEqualTo(3500L);
    }

    @Test
    void exhaustiveSwitchCoversAllPermits() {
        TestProgressEvent[] events = {
            new TestProgressEvent.SuiteStarted("s", 1, Instant.now()),
            new TestProgressEvent.TestStarted(0, "t"),
            new TestProgressEvent.TestCompleted(0, "t", TestStatus.PASS, 10L, null),
            new TestProgressEvent.SuiteCompleted(1L, 0L, 100L)
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
