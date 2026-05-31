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
import io.github.snytkine.apitester.api_tester_cli.model.IsNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;

/**
 * Evaluates an {@link IsNullAssertion} by resolving the assertion's {@code path} against the
 * response and asserting that the property either does not exist or has a {@code null} value.
 *
 * <p>Passes when:
 *
 * <ul>
 *   <li>The path does not exist in the response ({@link Result.Missing})
 *   <li>The path resolves to an explicit {@code null} value ({@link Result.Found} with {@code
 *       null})
 * </ul>
 *
 * <p>Fails when the path resolves to any non-null value, or when the path expression is unsupported
 * ({@link Result.Error}).
 */
class IsNullAssertionEvaluator implements AssertionEvaluator {

    private final IsNullAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the is_null assertion to evaluate
     */
    IsNullAssertionEvaluator(IsNullAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Resolves {@code assertion.path()} and records a failure only when the resolved value is
     * non-null.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        switch (ResponseValueExtractor.extract(response, assertion.path())) {
            case Result.Found f -> {
                if (f.value() != null) {
                    collector.fail(
                            "Expected null or absent value at path '%s' but was: %s", assertion.path(), f.value());
                }
            }
            case Result.Missing ignored -> {} // path absent counts as null — pass
            case Result.Error e -> collector.fail(e.message());
        }
    }
}
