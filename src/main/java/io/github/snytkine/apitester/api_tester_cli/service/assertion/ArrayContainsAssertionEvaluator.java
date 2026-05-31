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
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.List;

/**
 * Evaluates an {@link ArrayContainsAssertion} by resolving the value at {@code path} and asserting
 * that the resulting JSON array contains the {@code expected} item.
 *
 * <p>Number comparison is performed as {@code double} so that integer and floating-point
 * representations of the same value are treated as equal. Strings and booleans are compared with
 * {@link Object#equals}.
 *
 * <p>Fails when the path is missing, the value is not a {@link List}, or the item is not found.
 */
class ArrayContainsAssertionEvaluator implements AssertionEvaluator {

    private final ArrayContainsAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the array_contains assertion to evaluate
     */
    ArrayContainsAssertionEvaluator(ArrayContainsAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Resolves the path, confirms the value is a list, and checks whether {@code expected} appears in
     * the list.
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
                            "Expected an array at path '%s' for array_contains but was: %s (%s)",
                            assertion.path(),
                            f.value(),
                            f.value() == null ? "null" : f.value().getClass().getSimpleName());
                    return;
                }
                if (!containsMatch(list, assertion.expected())) {
                    collector.fail(
                            "Expected array at path '%s' to contain %s but it did not",
                            assertion.path(), assertion.expected());
                }
            }
            case Result.Missing m ->
                collector.fail(
                        "Expected array at path '%s' for array_contains but path does not exist", assertion.path());
            case Result.Error e -> collector.fail(e.message());
        }
    }

    /**
     * Returns {@code true} if any element of {@code list} matches {@code target}. Number comparison
     * uses {@code double} to handle mixed integer/floating-point representations.
     *
     * @param list the resolved array elements
     * @param target the expected value from the assertion
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
