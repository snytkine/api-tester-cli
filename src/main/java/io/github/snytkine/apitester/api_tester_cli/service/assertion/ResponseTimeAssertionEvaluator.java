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
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ResponseTimeAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import org.assertj.core.api.Assertions;
import org.opentest4j.AssertionFailedError;

/**
 * Evaluates a {@link ResponseTimeAssertion} by comparing the actual HTTP round-trip time recorded
 * in {@link ApiResponse#responseTimeMs()} against the configured threshold using an AssertJ
 * comparison assertion.
 *
 * <p>Fails when:
 *
 * <ul>
 *   <li>{@link ApiResponse#responseTimeMs()} is {@code null} (timing was not captured)
 *   <li>The actual response time exceeds {@link ResponseTimeAssertion#milliseconds()}
 * </ul>
 */
class ResponseTimeAssertionEvaluator implements AssertionEvaluator {

    private final ResponseTimeAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the response_time assertion to evaluate
     */
    ResponseTimeAssertionEvaluator(ResponseTimeAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Checks that the response was received within the configured threshold.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        if (response.responseTimeMs() == null) {
            collector.fail("Response time was not measured for this response", null);
            return;
        }
        long actual = response.responseTimeMs();
        long limit = assertion.milliseconds();
        try {
            Assertions.assertThat(actual).isLessThanOrEqualTo(limit);
        } catch (AssertionError e) {
            collector.fail(new AssertionFailedError(
                    String.format("Expected response within %d ms but took %d ms", limit, actual),
                    "<= " + limit + " ms",
                    actual + " ms"));
        }
    }
}
