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
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayContainsAllAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.ArrayList;
import java.util.List;
import org.opentest4j.AssertionFailedError;

/**
 * Evaluates an {@link ArrayContainsAllAssertion} by resolving the value at {@code path} and
 * asserting that the resulting JSON array contains every item in the {@code expected} list.
 *
 * <p>Number comparison is performed as {@code double}. A single failure message lists all missing
 * items so the user sees the full picture in one run.
 *
 * <p>Fails when the path is missing, the value is not a {@link List}, or any expected item is
 * absent.
 */
class ArrayContainsAllAssertionEvaluator implements AssertionEvaluator {

    private final ArrayContainsAllAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the array_contains_all assertion to evaluate
     */
    ArrayContainsAllAssertionEvaluator(ArrayContainsAllAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Resolves the path, confirms the value is a list, and records a failure listing every expected
     * item that is absent.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        switch (ResponseValueExtractor.extract(response, assertion.path())) {
            case Result.Found f -> {
                if (!(f.value() instanceof List<?> list)) {
                    collector.fail(
                            String.format(
                                    "Expected an array at path '%s' for array_contains_all but was: %s (%s)",
                                    assertion.path(),
                                    f.value(),
                                    f.value() == null
                                            ? "null"
                                            : f.value().getClass().getSimpleName()),
                            null);
                    return;
                }
                List<Object> missing = new ArrayList<>();
                for (Object expected : assertion.expected()) {
                    if (!containsMatch(list, expected)) {
                        missing.add(expected);
                    }
                }
                if (!missing.isEmpty()) {
                    collector.fail(new AssertionFailedError(
                            String.format(
                                    "Expected array at path '%s' to contain all of %s but missing: %s",
                                    assertion.path(), assertion.expected(), missing),
                            "contains all " + assertion.expected(),
                            "missing: " + missing));
                }
            }
            case Result.Missing m ->
                collector.fail(
                        String.format(
                                "Expected array at path '%s' for array_contains_all but path does not exist",
                                assertion.path()),
                        null);
            case Result.Error e -> collector.fail(e.message(), null);
        }
    }

    /**
     * Returns {@code true} if any element of {@code list} matches {@code target}. Number comparison
     * uses {@code double} to handle mixed integer/floating-point representations.
     *
     * @param list the resolved array elements
     * @param target the expected value to search for
     * @return {@code true} if a match is found
     */
    private static boolean containsMatch(List<?> list, Object target) {
        for (Object element : list) {
            if (element instanceof Number e && target instanceof Number t) {
                if (Double.compare(e.doubleValue(), t.doubleValue()) == 0) return true;
            } else if (element == null ? target == null : element.equals(target)) {
                return true;
            }
        }
        return false;
    }
}
