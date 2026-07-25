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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.BaseServerResponseAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

/**
 * Unit tests for {@link BaseServerResponseAssertionEvaluator} and the {@link
 * BaseServerResponseAssertion} model record.
 *
 * <p>The evaluator's contract is deliberately narrow: any response that carries a real HTTP status
 * code passes, regardless of status class, headers or body. Only a response with no usable status
 * code (which the resolver never produces in practice) fails.
 */
class BaseServerResponseAssertionEvaluatorTest {

    private static final BaseServerResponseAssertion ASSERTION = new BaseServerResponseAssertion(30);

    private static void evaluate(ApiResponse response, FailureCollector collector) {
        new BaseServerResponseAssertionEvaluator(ASSERTION).evaluate(response, collector);
    }

    @Test
    void successfulResponsePasses() {
        FailureCollector collector = new FailureCollector();
        evaluate(new ApiResponse(200, Map.of(), new ApiResponse.Body("{}", Map.of())), collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void serverErrorResponseStillPasses() {
        FailureCollector collector = new FailureCollector();
        evaluate(new ApiResponse(500, Map.of(), new ApiResponse.Body("boom", null)), collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void clientErrorResponseWithoutBodyStillPasses() {
        FailureCollector collector = new FailureCollector();
        evaluate(new ApiResponse(404, Map.of(), null), collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void missingStatusCodeFails() {
        FailureCollector collector = new FailureCollector();
        evaluate(new ApiResponse(null, Map.of(), null), collector);

        assertThatThrownBy(collector::assertAll)
                .isInstanceOf(MultipleFailuresError.class)
                .hasMessageContaining("No HTTP response was received");
    }

    @Test
    void nonPositiveStatusCodeFails() {
        FailureCollector collector = new FailureCollector();
        evaluate(new ApiResponse(0, Map.of(), null), collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void expectedDescriptionReportsConfiguredTimeoutInSeconds() {
        assertThat(new BaseServerResponseAssertion(30).expectedDescription())
                .isEqualTo("service must respond within default timeout of 30 seconds");
        assertThat(new BaseServerResponseAssertion(5).expectedDescription())
                .isEqualTo("service must respond within default timeout of 5 seconds");
    }

    @Test
    void factoryCreatesTheEvaluatorAndDescriberReportsTheTypeName() {
        AssertionEvaluatorFactory factory = new AssertionEvaluatorFactory();

        assertThat(factory.create(ASSERTION, null, Map.of())).isInstanceOf(BaseServerResponseAssertionEvaluator.class);
        assertThat(factory.describe(ASSERTION)).isEqualTo("base_server_response");
        assertThat(BaseServerResponseAssertion.TYPE_NAME).isEqualTo("base_server_response");
    }
}
