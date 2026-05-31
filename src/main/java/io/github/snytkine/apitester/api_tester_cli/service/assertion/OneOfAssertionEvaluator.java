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
package io.github.snytkine.apitester.api_tester_cli.service.assertion;

import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.OneOfAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;

/**
 * Evaluates a {@link OneOfAssertion} by resolving the value at {@code path} and asserting it equals
 * one of the items in the {@code expected} list.
 *
 * <p>Number comparison is performed as {@code double} so that integer and floating-point
 * representations of the same value (e.g. {@code 1} and {@code 1.0}) are treated as equal. Strings
 * and booleans are compared with {@link Object#equals}.
 *
 * <p>If any element of the {@code expected} list is a non-scalar type (not a {@link String}, {@link
 * Number}, or {@link Boolean}), a failure is recorded immediately without evaluating the path.
 */
class OneOfAssertionEvaluator implements AssertionEvaluator {

    private final OneOfAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the one_of assertion to evaluate
     */
    OneOfAssertionEvaluator(OneOfAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Validates the {@code expected} list contains only scalars, resolves the path, and records a
     * failure if the resolved value is not among the expected items.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        for (Object item : assertion.expected()) {
            if (item != null && !(item instanceof String) && !(item instanceof Number) && !(item instanceof Boolean)) {
                collector.fail(
                        "one_of assertion at path '%s' contains a non-scalar expected value: %s (%s)",
                        assertion.path(), item, item.getClass().getSimpleName());
                return;
            }
        }

        switch (ResponseValueExtractor.extract(response, assertion.path())) {
            case Result.Found f -> {
                if (!matchesAny(f.value(), assertion.expected())) {
                    collector.fail(
                            "Expected value at path '%s' to be one of %s but was: %s",
                            assertion.path(), assertion.expected(), f.value());
                }
            }
            case Result.Missing m ->
                collector.fail(
                        "Expected value at path '%s' to be one of %s but path does not exist",
                        assertion.path(), assertion.expected());
            case Result.Error e -> collector.fail(e.message());
        }
    }

    /**
     * Returns {@code true} if {@code actual} equals any element in {@code candidates}.
     *
     * <p>When both {@code actual} and the candidate are {@link Number} instances the comparison is
     * done as {@code double} to handle mixed integer/floating-point representations.
     *
     * @param actual the resolved response value
     * @param candidates the expected scalar values from the assertion
     * @return {@code true} if a match is found
     */
    private boolean matchesAny(Object actual, Iterable<Object> candidates) {
        for (Object candidate : candidates) {
            if (actual instanceof Number a && candidate instanceof Number c) {
                if (Double.compare(a.doubleValue(), c.doubleValue()) == 0) {
                    return true;
                }
            } else if (actual == null ? candidate == null : actual.equals(candidate)) {
                return true;
            }
        }
        return false;
    }
}
