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
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusCodeAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import org.assertj.core.api.Assertions;

/**
 * Evaluates a {@link StatusCodeAssertion} by comparing the response's HTTP status code against the
 * expected value using an AssertJ equality assertion.
 *
 * <p>When the assertion fails, AssertJ produces an {@link org.opentest4j.AssertionFailedError} with
 * structured expected and actual fields, which are preserved in the failure record via {@link
 * FailureCollector#rewrap(String, AssertionError)}.
 */
class StatusCodeAssertionEvaluator implements AssertionEvaluator {

    private final StatusCodeAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the status code assertion to evaluate
     */
    StatusCodeAssertionEvaluator(StatusCodeAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Asserts that the response status code equals the expected value.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        try {
            Assertions.assertThat(response.statusCode()).isEqualTo(assertion.expected());
        } catch (AssertionError e) {
            String msg =
                    String.format("Expected status code %d but was %d", assertion.expected(), response.statusCode());
            collector.fail(FailureCollector.rewrap(msg, e));
        }
    }
}
