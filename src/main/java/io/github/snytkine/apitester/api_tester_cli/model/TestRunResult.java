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
package io.github.snytkine.apitester.api_tester_cli.model;

import java.util.List;
import java.util.Map;

/**
 * Aggregated outcome of executing all test cases in a {@link TestSuite}.
 *
 * <p>The {@code results} list contains one {@link TestCaseResult} per test case, in execution
 * order. Callers that only need summary counts can use the individual count fields directly.
 * Callers that need per-assertion detail can filter {@code results} by {@link
 * TestCaseResult#result()} and inspect each {@link TestCaseResult#failures()}.
 */
public record TestRunResult(
        /** Number of test cases where all assertions passed ({@link TestResult#PASSED}). */
        long passedCount,

        /** Number of test cases where at least one assertion failed ({@link TestResult#FAILED}). */
        long failedCount,

        /** Number of test cases that were skipped ({@link TestResult#SKIPPED}). */
        long skippedCount,

        /** Number of test cases that threw an unexpected exception ({@link TestResult#ERROR}). */
        long errorCount,

        /** Per-test-case results in execution order. */
        List<TestCaseResult> results,

        /**
         * Named CLI options that were actively applied during this run (e.g. {@code {"tag":
         * "smoke"}}). Populated by the command layer, not the engine; empty when no extra options
         * were used.
         */
        Map<String, String> appliedOptions) {

    /**
     * Returns a new {@link TestRunResult} identical to this one except that {@code appliedOptions} is
     * replaced by an immutable copy of {@code options}. Intended for use in the command layer to stamp
     * the active CLI options after the engine returns its result.
     *
     * @param options the options map to record; must not be {@code null}
     * @return a new {@link TestRunResult} with the supplied options
     */
    public TestRunResult withAppliedOptions(Map<String, String> options) {
        return new TestRunResult(passedCount, failedCount, skippedCount, errorCount, results, Map.copyOf(options));
    }
}
