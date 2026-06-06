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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.RegexMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class RegexMatchAssertionEvaluatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ApiResponse responseWithJson(Object json) {
        try {
            return new ApiResponse(200, Map.of(), new ApiResponse.Body(MAPPER.writeValueAsString(json), json));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ApiResponse responseWithHeader(String name, String value) {
        return new ApiResponse(200, Map.of(name, value), null);
    }

    @Test
    void exactMatchPasses() {
        ApiResponse response = responseWithJson(Map.of("code", "ABC123"));

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.body.json.$.code", "ABC123"))
                .evaluate(response, collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void regexPatternMatchPasses() {
        ApiResponse response = responseWithJson(Map.of("code", "ABC123"));

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.body.json.$.code", "[A-Z]{3}\\d{3}"))
                .evaluate(response, collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void partialMatchWithWildcardPasses() {
        ApiResponse response = responseWithJson(Map.of("message", "Hello World"));

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.body.json.$.message", ".*World.*"))
                .evaluate(response, collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void nonMatchingPatternFails() {
        ApiResponse response = responseWithJson(Map.of("code", "xyz999"));

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.body.json.$.code", "[A-Z]{3}\\d{3}"))
                .evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void partialMatchWithoutWildcardFails() {
        ApiResponse response = responseWithJson(Map.of("message", "Hello World"));

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.body.json.$.message", "World"))
                .evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void numberValueFails() {
        ApiResponse response = responseWithJson(Map.of("count", 42));

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.body.json.$.count", "\\d+"))
                .evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void booleanValueFails() {
        ApiResponse response = responseWithJson(Map.of("flag", true));

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.body.json.$.flag", "true"))
                .evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void nullValueFails() {
        ApiResponse response = new ApiResponse(200, Map.of(), null);

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.body.json", ".*"))
                .evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void missingPathFails() {
        ApiResponse response = responseWithJson(Map.of("code", "ABC123"));

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.body.json.$.missing", ".*"))
                .evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void invalidRegexPatternFails() {
        ApiResponse response = responseWithJson(Map.of("code", "ABC123"));

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.body.json.$.code", "[invalid("))
                .evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void headerMatchPasses() {
        ApiResponse response = responseWithHeader("content-type", "application/json; charset=utf-8");

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("response.headers.content-type", "application/json.*"))
                .evaluate(response, collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void emailPatternMatchPasses() {
        ApiResponse response = responseWithJson(Map.of("email", "user@example.com"));

        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion(
                        "response.body.json.$.email", "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"))
                .evaluate(response, collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void unsupportedPathRecordsError() {
        FailureCollector collector = new FailureCollector();
        new RegexMatchAssertionEvaluator(new RegexMatchAssertion("invalid.path", "[0-9]+"))
                .evaluate(new ApiResponse(200, Map.of(), null), collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }
}
