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

import java.time.Instant;
import java.util.List;

/**
 * Sealed event hierarchy representing milestones during a test suite run.
 *
 * <p>Producers (test worker threads) fire these events via {@link TestProgressListener#onProgress}.
 * Consumers (e.g. the terminal UI render loop) receive them and update the display. Using a sealed
 * interface allows exhaustive {@code switch} expressions in the consumer without an {@code default}
 * branch.
 *
 * <p>All nested records are immutable value types; they are safe to publish across thread
 * boundaries without additional synchronisation.
 */
public sealed interface TestProgressEvent
        permits TestProgressEvent.SuiteStarted,
                TestProgressEvent.TestStarted,
                TestProgressEvent.TestCompleted,
                TestProgressEvent.SuiteCompleted {

    /**
     * Fired once before any test cases begin. Carries enough information for the UI to pre-allocate
     * one row per test.
     *
     * @param suiteName the {@code name} field from the suite YAML
     * @param totalTestCount number of test cases in the suite
     * @param startedAt wall-clock timestamp when the suite run began
     */
    record SuiteStarted(String suiteName, int totalTestCount, Instant startedAt) implements TestProgressEvent {}

    /**
     * Fired immediately before a test case's HTTP request is dispatched.
     *
     * @param uniqueId stable identity token assigned when the test suite is mapped to indexed test
     *     cases; used by the render loop to correlate {@link TestCompleted} events to grid rows
     *     without relying on list position alone (forward-compatible with parallel execution)
     * @param testIndex zero-based position of the test case in the suite's test list
     * @param testName the {@code name} field from the test case YAML
     */
    record TestStarted(String uniqueId, int testIndex, String testName) implements TestProgressEvent {}

    /**
     * Fired after all assertions for a test case have been evaluated (or an exception has been
     * caught).
     *
     * @param uniqueId the same token that was carried by the corresponding {@link TestStarted} event;
     *     used by the render loop to look up the correct grid row
     * @param testIndex zero-based position of the test case in the suite's test list
     * @param testName the {@code name} field from the test case YAML
     * @param status {@link TestStatus#PASS}, {@link TestStatus#FAIL}, or {@link TestStatus#ERROR}
     * @param durationMs elapsed time in milliseconds from {@link TestStarted} to this event
     * @param assertionCount total number of assertions that were evaluated for this test case; used
     *     to display "{@code N passed}" in the Result column on pass
     * @param failureMessages all failure messages when {@code status != PASS}; empty list otherwise
     */
    record TestCompleted(
            String uniqueId,
            int testIndex,
            String testName,
            TestStatus status,
            long durationMs,
            int assertionCount,
            List<String> failureMessages)
            implements TestProgressEvent {}

    /**
     * Fired once after all test cases have completed (or failed). The UI render loop uses this as its
     * shutdown signal.
     *
     * @param passCount number of test cases with status {@link TestStatus#PASS}
     * @param failCount number of test cases with status {@link TestStatus#FAIL} or {@link
     *     TestStatus#ERROR}
     * @param totalDurationMs total elapsed time in milliseconds for the entire suite run
     */
    record SuiteCompleted(long passCount, long failCount, long totalDurationMs) implements TestProgressEvent {}
}
