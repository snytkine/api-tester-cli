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
import io.github.snytkine.apitester.api_tester_cli.model.assertions.GreaterThanAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import org.assertj.core.api.Assertions;
import org.opentest4j.AssertionFailedError;

/**
 * Evaluates a {@link GreaterThanAssertion} by resolving the value at {@code path} and asserting it
 * is strictly greater than {@code expected} using an AssertJ comparison assertion.
 *
 * <p>Number values are compared as {@code double}. String values are parsed as {@code double}
 * first. Any other type, a missing path, or a non-parseable string records a failure.
 */
class GreaterThanAssertionEvaluator implements AssertionEvaluator {

    private final GreaterThanAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the greater_than assertion to evaluate
     */
    GreaterThanAssertionEvaluator(GreaterThanAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Resolves the path, converts the value to {@code double}, and checks it is strictly greater
     * than the configured threshold.
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
                            String.format(
                                    "Expected numeric value at path '%s' for greater_than but was null",
                                    assertion.path()),
                            null);
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
                                String.format(
                                        "Expected numeric value at path '%s' for greater_than but could not"
                                                + " parse '%s'",
                                        assertion.path(), s),
                                e);
                        return;
                    }
                } else {
                    collector.fail(
                            String.format(
                                    "Expected numeric value at path '%s' for greater_than but was %s (%s)",
                                    assertion.path(),
                                    f.value(),
                                    f.value().getClass().getSimpleName()),
                            null);
                    return;
                }
                try {
                    Assertions.assertThat(numeric).isGreaterThan(assertion.expected());
                } catch (AssertionError e) {
                    collector.fail(new AssertionFailedError(
                            String.format(
                                    "Expected value at path '%s' to be greater than %s but was %s",
                                    assertion.path(), assertion.expected(), numeric),
                            "> " + assertion.expected(),
                            String.valueOf(numeric)));
                }
            }
            case Result.Missing m ->
                collector.fail(
                        String.format(
                                "Expected numeric value at path '%s' for greater_than but path does not exist",
                                assertion.path()),
                        null);
            case Result.Error e -> collector.fail(e.message(), null);
        }
    }
}
