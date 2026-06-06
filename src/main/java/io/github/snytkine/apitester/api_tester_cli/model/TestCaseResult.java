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
import org.jspecify.annotations.Nullable;

/**
 * The outcome of executing a single {@link TestCase}.
 *
 * <p>The {@code result} enum captures the four-way terminal status: {@link TestResult#PASSED} when
 * all assertions evaluated without failure; {@link TestResult#FAILED} when one or more soft
 * assertions did not pass; {@link TestResult#SKIPPED} when the test declared a non-blank {@code
 * skip} field and was bypassed entirely; {@link TestResult#ERROR} when an unexpected exception
 * occurred before or during assertion evaluation.
 *
 * <p>When {@code result} is {@link TestResult#PASSED} the {@code failures} list is empty. When
 * {@code result} is {@link TestResult#FAILED} the list contains one {@link AssertionFailure} per
 * individual soft-assertion that did not pass. When {@code result} is {@link TestResult#SKIPPED}
 * the {@code skipReason} field holds the verbatim value of the test case's {@code skip} field.
 *
 * <p>The {@code requestInfo} field holds the fully-resolved HTTP request details for use in report
 * generation. It is populated for {@link TestResult#PASSED} and {@link TestResult#FAILED}
 * outcomes, and for {@link TestResult#ERROR} outcomes where the error occurred after the request
 * was constructed. It is {@code null} for {@link TestResult#SKIPPED} outcomes (no request sent)
 * and for {@link TestResult#ERROR} outcomes where the error occurred before request construction
 * (e.g. a missing body file).
 *
 * <p>The {@code apiResponse} field holds the HTTP response details received from the server. It is
 * populated for {@link TestResult#PASSED} and {@link TestResult#FAILED} outcomes, and for {@link
 * TestResult#ERROR} outcomes where the response was received before the failure occurred. It is
 * {@code null} for {@link TestResult#SKIPPED} outcomes (no request sent) and for {@link
 * TestResult#ERROR} outcomes where the request never completed.
 */
public record TestCaseResult(
        /** Name of the test case as declared in the suite YAML. */
        String name,

        /** Four-way terminal status of this test case execution. */
        TestResult result,

        /** Number of assertions that passed for this test case (0 for SKIPPED and ERROR). */
        int passedAssertions,

        /**
         * Individual failures collected during assertion evaluation. Empty when {@code result} is
         * {@link TestResult#PASSED} or {@link TestResult#SKIPPED}. For {@link TestResult#ERROR} this
         * contains a single entry with only a message and {@code null} actual/expected values.
         */
        List<AssertionFailure> failures,

        /**
         * The skip reason taken from the test case's {@code skip} field. Non-null only when {@code
         * result} is {@link TestResult#SKIPPED}.
         */
        @Nullable String skipReason,

        /**
         * The fully-resolved HTTP request that was dispatched. Non-null for {@link
         * TestResult#PASSED} and {@link TestResult#FAILED}; may be non-null for {@link
         * TestResult#ERROR} if the error occurred after request construction; always {@code null}
         * for {@link TestResult#SKIPPED}.
         */
        @Nullable ExecutedRequestInfo requestInfo,

        /**
         * The HTTP response received for this test case. Non-null for {@link TestResult#PASSED} and
         * {@link TestResult#FAILED}; may be non-null for {@link TestResult#ERROR} if the response
         * was received before the failure; always {@code null} for {@link TestResult#SKIPPED} and
         * for {@link TestResult#ERROR} cases where the request never completed.
         */
        @Nullable ApiResponse apiResponse) {}
