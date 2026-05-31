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
import io.github.snytkine.apitester.api_tester_cli.model.ArrayContainsAllAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class ArrayContainsAllAssertionEvaluatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ApiResponse responseWithJson(Object json) {
        try {
            return new ApiResponse(200, Map.of(), new ApiResponse.Body(MAPPER.writeValueAsString(json), json));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void allPresentPasses() {
        ApiResponse response = responseWithJson(Map.of("tags", List.of("java", "spring", "graal", "native")));

        FailureCollector collector = new FailureCollector();
        new ArrayContainsAllAssertionEvaluator(
                        new ArrayContainsAllAssertion("response.body.json.$.tags", List.of("java", "spring")))
                .evaluate(response, collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void oneMissingFails() {
        ApiResponse response = responseWithJson(Map.of("tags", List.of("java", "graal")));

        FailureCollector collector = new FailureCollector();
        new ArrayContainsAllAssertionEvaluator(
                        new ArrayContainsAllAssertion("response.body.json.$.tags", List.of("java", "spring")))
                .evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void allMissingFails() {
        ApiResponse response = responseWithJson(Map.of("tags", List.of("python", "rust")));

        FailureCollector collector = new FailureCollector();
        new ArrayContainsAllAssertionEvaluator(
                        new ArrayContainsAllAssertion("response.body.json.$.tags", List.of("java", "spring")))
                .evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }

    @Test
    void numbersAllPresentPasses() {
        ApiResponse response = responseWithJson(Map.of("codes", List.of(200, 201, 204)));

        FailureCollector collector = new FailureCollector();
        new ArrayContainsAllAssertionEvaluator(
                        new ArrayContainsAllAssertion("response.body.json.$.codes", List.of(200, 204)))
                .evaluate(response, collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void nonArrayValueFails() {
        ApiResponse response = responseWithJson(Map.of("tags", "not-an-array"));

        FailureCollector collector = new FailureCollector();
        new ArrayContainsAllAssertionEvaluator(
                        new ArrayContainsAllAssertion("response.body.json.$.tags", List.of("java")))
                .evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }
}
