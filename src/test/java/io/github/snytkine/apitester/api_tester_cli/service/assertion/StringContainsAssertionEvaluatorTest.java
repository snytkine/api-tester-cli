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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.StringContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class StringContainsAssertionEvaluatorTest {

    private static ApiResponse responseWithHeader(String name, String value) {
        return new ApiResponse(200, Map.of(name, value), null);
    }

    private static ApiResponse responseWithStatus(int status) {
        return new ApiResponse(status, Map.of(), null);
    }

    private static ApiResponse responseWithBodyText(String text) {
        return new ApiResponse(200, Map.of(), new ApiResponse.Body(text, null));
    }

    @Test
    void headerContainsSubstringPasses() {
        var evaluator = new StringContainsAssertionEvaluator(
                new StringContainsAssertion("response.headers.content-type", "application/json", true));

        FailureCollector collector = new FailureCollector();
        evaluator.evaluate(responseWithHeader("content-type", "application/json;charset=UTF-8"), collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void headerDoesNotContainSubstringFails() {
        var evaluator = new StringContainsAssertionEvaluator(
                new StringContainsAssertion("response.headers.content-type", "text/html", true));

        FailureCollector collector = new FailureCollector();
        evaluator.evaluate(responseWithHeader("content-type", "application/json"), collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void caseInsensitiveContainsPasses() {
        var evaluator = new StringContainsAssertionEvaluator(
                new StringContainsAssertion("response.headers.content-type", "APPLICATION/JSON", false));

        FailureCollector collector = new FailureCollector();
        evaluator.evaluate(responseWithHeader("content-type", "application/json;charset=UTF-8"), collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void caseSensitiveContainerFailsWhenCaseDiffers() {
        var evaluator = new StringContainsAssertionEvaluator(
                new StringContainsAssertion("response.headers.content-type", "APPLICATION/JSON", true));

        FailureCollector collector = new FailureCollector();
        evaluator.evaluate(responseWithHeader("content-type", "application/json"), collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void nullCaseSensitiveDefaultsToCaseSensitive() {
        // null case_sensitive → defaults to true, so case mismatch must fail
        var evaluator = new StringContainsAssertionEvaluator(
                new StringContainsAssertion("response.headers.content-type", "APPLICATION/JSON", null));

        FailureCollector collector = new FailureCollector();
        evaluator.evaluate(responseWithHeader("content-type", "application/json"), collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void statusCodePathResolvesCorrectly() {
        var evaluator =
                new StringContainsAssertionEvaluator(new StringContainsAssertion("response.statusCode", "20", null));

        FailureCollector collector = new FailureCollector();
        evaluator.evaluate(responseWithStatus(200), collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void bodyTextPathResolvesCorrectly() {
        var evaluator =
                new StringContainsAssertionEvaluator(new StringContainsAssertion("response.body.text", "hello", null));

        FailureCollector collector = new FailureCollector();
        evaluator.evaluate(responseWithBodyText("say hello world"), collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void unsupportedPathRecordsFailure() {
        var evaluator =
                new StringContainsAssertionEvaluator(new StringContainsAssertion("invalid.path", "value", null));

        FailureCollector collector = new FailureCollector();
        evaluator.evaluate(responseWithStatus(200), collector);

        assertThatThrownBy(collector::assertAll)
                .isInstanceOf(MultipleFailuresError.class)
                .hasMessageContaining("response.");
    }
}
