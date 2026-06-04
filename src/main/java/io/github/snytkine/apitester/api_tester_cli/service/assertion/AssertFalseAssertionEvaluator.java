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
import io.github.snytkine.apitester.api_tester_cli.model.assertions.AssertFalseAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import org.assertj.core.api.Assertions;

/**
 * Evaluates an {@link AssertFalseAssertion} by resolving the value at {@code path} and asserting it
 * equals {@link Boolean#FALSE} via an AssertJ equality assertion.
 *
 * <p>Only {@link Boolean#FALSE} passes. Any other value — including the string {@code "false"}, the
 * integer {@code 0}, {@code null}, or a missing path — records a failure. The AssertJ equality
 * check produces a structured {@link org.opentest4j.AssertionFailedError} with expected and actual
 * fields, which are preserved via {@link FailureCollector#rewrap(String, AssertionError)}.
 */
class AssertFalseAssertionEvaluator implements AssertionEvaluator {

    private final AssertFalseAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the assert_false assertion to evaluate
     */
    AssertFalseAssertionEvaluator(AssertFalseAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Resolves the path and records a failure if the value is not exactly {@code Boolean.FALSE}.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        switch (ResponseValueExtractor.extract(response, assertion.path())) {
            case Result.Found f -> {
                try {
                    Assertions.assertThat((Object) f.value()).isEqualTo(Boolean.FALSE);
                } catch (AssertionError e) {
                    String msg = String.format(
                            "Expected boolean false at path '%s' but was: %s", assertion.path(), f.value());
                    collector.fail(FailureCollector.rewrap(msg, e));
                }
            }
            case Result.Missing m ->
                collector.fail(
                        String.format("Expected boolean false at path '%s' but path does not exist", assertion.path()),
                        null);
            case Result.Error e -> collector.fail(e.message(), null);
        }
    }
}
