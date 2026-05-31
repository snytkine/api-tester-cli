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

/**
 * The outcome of executing a single {@link TestCase}.
 *
 * <p>When {@code passed} is {@code true} the {@code failures} list is empty. When {@code passed} is
 * {@code false} the list contains one {@link AssertionFailure} per individual soft-assertion that
 * did not pass, preserving the full actual/expected detail for each.
 */
public record TestCaseResult(
        /** Name of the test case as declared in the suite YAML. */
        String name,

        /** {@code true} if all assertions passed, {@code false} otherwise. */
        boolean passed,

        /** Number of assertions that passed for this test case. */
        int passedAssertions,

        /**
         * Individual failures collected during assertion evaluation. Empty when {@code passed} is
         * {@code true}. For non-assertion errors (e.g. network failures) this contains a single entry
         * with only a message and {@code null} actual/expected values.
         */
        List<AssertionFailure> failures) {}
