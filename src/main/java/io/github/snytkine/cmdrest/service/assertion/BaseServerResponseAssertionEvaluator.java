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
package io.github.snytkine.cmdrest.service.assertion;

import io.github.snytkine.cmdrest.interfaces.AssertionEvaluator;
import io.github.snytkine.cmdrest.model.ApiResponse;
import io.github.snytkine.cmdrest.model.assertions.BaseServerResponseAssertion;
import io.github.snytkine.cmdrest.util.FailureCollector;
import org.opentest4j.AssertionFailedError;

/**
 * Evaluates the implicit {@link BaseServerResponseAssertion}: the service answered the request with
 * some HTTP response before the client's timeout elapsed.
 *
 * <p>Reaching this evaluator already means a response was received and parsed — the engine only
 * evaluates assertions once {@link
 * io.github.snytkine.cmdrest.service.assertion.ResponseResolver} has produced an
 * {@link ApiResponse}. The "no response at all" case (connection refused, unknown host, TLS
 * failure, connection timeout) never gets here: the engine catches the transport exception and
 * reports this assertion as failed directly. What remains here is the defensive check that the
 * captured response carries a real HTTP status code.
 *
 * <p>The status code value, headers and body are deliberately <em>not</em> examined: a {@code 500}
 * response passes this assertion exactly like a {@code 200}.
 */
class BaseServerResponseAssertionEvaluator implements AssertionEvaluator {

    private final BaseServerResponseAssertion assertion;

    /**
     * Constructs the evaluator for the given implicit assertion.
     *
     * @param assertion the base_server_response assertion to evaluate; carries the timeout reported
     *     in the failure's expected value
     */
    BaseServerResponseAssertionEvaluator(BaseServerResponseAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Passes when {@code response} carries a valid (positive) HTTP status code, which is the case
     * for every response the resolver is able to produce.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        Integer statusCode = response.statusCode();
        if (statusCode == null || statusCode <= 0) {
            collector.fail(new AssertionFailedError(
                    "No HTTP response was received from the service",
                    assertion.expectedDescription(),
                    "no response received"));
        }
    }
}
