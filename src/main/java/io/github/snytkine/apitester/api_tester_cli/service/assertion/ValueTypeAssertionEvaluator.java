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
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ValueTypeAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates a {@link ValueTypeAssertion} by resolving the value at {@code path} and asserting that
 * its runtime type matches the expected JSON type name.
 *
 * <p>Recognised type names and their corresponding Java types:
 *
 * <ul>
 *   <li>{@code string} → {@link String}
 *   <li>{@code number} → {@link Number} (Integer, Long, Double, etc.)
 *   <li>{@code boolean} → {@link Boolean}
 *   <li>{@code array} → {@link List}
 *   <li>{@code object} → {@link Map}
 *   <li>{@code null} → {@code null} value at the path
 * </ul>
 *
 * <p>An unrecognised type name in {@code expected} records a failure immediately. A missing path
 * also records a failure.
 */
class ValueTypeAssertionEvaluator implements AssertionEvaluator {

    private static final Set<String> VALID_TYPES = Set.of("string", "number", "boolean", "array", "object", "null");

    private final ValueTypeAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the value_type assertion to evaluate
     */
    ValueTypeAssertionEvaluator(ValueTypeAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Validates the expected type name, resolves the path, and records a failure when the actual
     * runtime type does not match.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        if (!VALID_TYPES.contains(assertion.expected())) {
            collector.fail(
                    "Unknown type name '%s' in value_type assertion at path '%s'. Valid types: %s",
                    assertion.expected(), assertion.path(), VALID_TYPES);
            return;
        }

        switch (ResponseValueExtractor.extract(response, assertion.path())) {
            case Result.Found f -> {
                String actualType = typeOf(f.value());
                if (!assertion.expected().equals(actualType)) {
                    collector.fail(
                            "Expected value at path '%s' to be of type '%s' but was: '%s'",
                            assertion.path(), assertion.expected(), actualType);
                }
            }
            case Result.Missing m ->
                collector.fail(
                        "Expected value of type '%s' at path '%s' but path does not exist",
                        assertion.expected(), assertion.path());
            case Result.Error e -> collector.fail(e.message());
        }
    }

    /**
     * Returns the JSON type name for the given Java value.
     *
     * @param value the resolved response value
     * @return one of {@code string}, {@code number}, {@code boolean}, {@code array}, {@code object},
     *     or {@code null}
     */
    private static String typeOf(@Nullable Object value) {
        if (value == null) return "null";
        if (value instanceof String) return "string";
        if (value instanceof Number) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof List) return "array";
        if (value instanceof Map) return "object";
        return value.getClass().getSimpleName();
    }
}
