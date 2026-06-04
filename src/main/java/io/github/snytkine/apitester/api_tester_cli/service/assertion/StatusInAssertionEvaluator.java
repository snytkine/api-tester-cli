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
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusInAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import org.assertj.core.api.Assertions;
import org.opentest4j.AssertionFailedError;

/**
 * Evaluates a {@link StatusInAssertion} by checking that the HTTP response status code is one of
 * the values in the {@code expected} list using an AssertJ containment assertion.
 *
 * <p>Fails when the response carries no status code or when the status code is not in the list.
 */
class StatusInAssertionEvaluator implements AssertionEvaluator {

    private final StatusInAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the status_in assertion to evaluate
     */
    StatusInAssertionEvaluator(StatusInAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Checks the response status code against the expected list and records a failure when it is
     * absent or not listed.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        if (response.statusCode() == null) {
            collector.fail(
                    String.format(
                            "Expected status code to be one of %s but response has no status code",
                            assertion.expected()),
                    null);
            return;
        }
        try {
            Assertions.assertThat(response.statusCode()).isIn(assertion.expected());
        } catch (AssertionError e) {
            collector.fail(new AssertionFailedError(
                    String.format(
                            "Expected status code to be one of %s but was: %d",
                            assertion.expected(), response.statusCode()),
                    "one of " + assertion.expected(),
                    String.valueOf(response.statusCode())));
        }
    }
}
