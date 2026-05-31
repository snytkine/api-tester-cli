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
import io.github.snytkine.apitester.api_tester_cli.model.AssertTrueAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;

/**
 * Evaluates an {@link AssertTrueAssertion} by resolving the value at {@code path} and asserting it
 * is the boolean literal {@code true}.
 *
 * <p>Only {@link Boolean#TRUE} passes. Any other value — including the string {@code "true"}, the
 * integer {@code 1}, {@code null}, or a missing path — records a failure.
 */
class AssertTrueAssertionEvaluator implements AssertionEvaluator {

    private final AssertTrueAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the assert_true assertion to evaluate
     */
    AssertTrueAssertionEvaluator(AssertTrueAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Resolves the path and records a failure if the value is not exactly {@code Boolean.TRUE}.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        switch (ResponseValueExtractor.extract(response, assertion.path())) {
            case Result.Found f -> {
                if (!Boolean.TRUE.equals(f.value())) {
                    collector.fail("Expected boolean true at path '%s' but was: %s", assertion.path(), f.value());
                }
            }
            case Result.Missing m ->
                collector.fail("Expected boolean true at path '%s' but path does not exist", assertion.path());
            case Result.Error e -> collector.fail(e.message());
        }
    }
}
