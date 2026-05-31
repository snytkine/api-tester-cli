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
import io.github.snytkine.apitester.api_tester_cli.model.GreaterThanOrEqualAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;

/**
 * Evaluates a {@link GreaterThanOrEqualAssertion} by resolving the value at {@code path} and
 * asserting it is greater than or equal to {@code expected}.
 *
 * <p>Number values are compared as {@code double}. String values are parsed as {@code double}
 * first. Any other type, a missing path, or a non-parseable string records a failure.
 */
class GreaterThanOrEqualAssertionEvaluator implements AssertionEvaluator {

    private final GreaterThanOrEqualAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the greater_than_or_equal assertion to evaluate
     */
    GreaterThanOrEqualAssertionEvaluator(GreaterThanOrEqualAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Resolves the path, converts the value to {@code double}, and checks it is greater than or equal
     * to the configured threshold.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        switch (ResponseValueExtractor.extract(response, assertion.path())) {
            case Result.Found f -> {
                if (f.value() == null) {
                    collector.fail(
                            "Expected numeric value at path '%s' for greater_than_or_equal but was null",
                            assertion.path());
                    return;
                }
                double numeric;
                if (f.value() instanceof Number n) {
                    numeric = n.doubleValue();
                } else if (f.value() instanceof String s) {
                    try {
                        numeric = Double.parseDouble(s);
                    } catch (NumberFormatException e) {
                        collector.fail(
                                "Expected numeric value at path '%s' for greater_than_or_equal but could not"
                                        + " parse '%s'",
                                assertion.path(), s);
                        return;
                    }
                } else {
                    collector.fail(
                            "Expected numeric value at path '%s' for greater_than_or_equal but was %s (%s)",
                            assertion.path(), f.value(), f.value().getClass().getSimpleName());
                    return;
                }
                if (numeric < assertion.expected()) {
                    collector.fail(
                            "Expected value at path '%s' to be greater than or equal to %s but was %s",
                            assertion.path(), assertion.expected(), numeric);
                }
            }
            case Result.Missing m ->
                collector.fail(
                        "Expected numeric value at path '%s' for greater_than_or_equal but path does not" + " exist",
                        assertion.path());
            case Result.Error e -> collector.fail(e.message());
        }
    }
}
