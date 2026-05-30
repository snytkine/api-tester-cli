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
import io.github.snytkine.apitester.api_tester_cli.model.OneOfAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class OneOfAssertionEvaluatorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static ApiResponse responseWithJson(Object json) {
    try {
      return new ApiResponse(
          200, Map.of(), new ApiResponse.Body(MAPPER.writeValueAsString(json), json));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void stringMatchesOneOfPasses() {
    ApiResponse response = responseWithJson(Map.of("status", "active"));

    FailureCollector collector = new FailureCollector();
    new OneOfAssertionEvaluator(
            new OneOfAssertion(
                "response.body.json.$.status", List.of("active", "inactive", "pending")))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void stringNotInListFails() {
    ApiResponse response = responseWithJson(Map.of("status", "unknown"));

    FailureCollector collector = new FailureCollector();
    new OneOfAssertionEvaluator(
            new OneOfAssertion("response.body.json.$.status", List.of("active", "inactive")))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void integerMatchesOneOfPasses() {
    ApiResponse response = responseWithJson(Map.of("code", 200));

    FailureCollector collector = new FailureCollector();
    new OneOfAssertionEvaluator(
            new OneOfAssertion("response.body.json.$.code", List.of(200, 201, 204)))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void integerNotInListFails() {
    ApiResponse response = responseWithJson(Map.of("code", 404));

    FailureCollector collector = new FailureCollector();
    new OneOfAssertionEvaluator(
            new OneOfAssertion("response.body.json.$.code", List.of(200, 201, 204)))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void integerMatchesDoubleEquivalentPasses() {
    ApiResponse response = responseWithJson(Map.of("score", 1));

    FailureCollector collector = new FailureCollector();
    new OneOfAssertionEvaluator(
            new OneOfAssertion("response.body.json.$.score", List.of(1.0, 2.0, 3.0)))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void booleanMatchesPasses() {
    ApiResponse response = responseWithJson(Map.of("flag", true));

    FailureCollector collector = new FailureCollector();
    new OneOfAssertionEvaluator(
            new OneOfAssertion("response.body.json.$.flag", List.of(true, false)))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void nonScalarExpectedItemFails() {
    ApiResponse response = responseWithJson(Map.of("status", "active"));

    FailureCollector collector = new FailureCollector();
    new OneOfAssertionEvaluator(
            new OneOfAssertion(
                "response.body.json.$.status", Arrays.asList("active", Map.of("nested", "object"))))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void missingPathFails() {
    ApiResponse response = responseWithJson(Map.of("status", "active"));

    FailureCollector collector = new FailureCollector();
    new OneOfAssertionEvaluator(
            new OneOfAssertion("response.body.json.$.missing", List.of("active", "inactive")))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void statusCodeMatchesPasses() {
    FailureCollector collector = new FailureCollector();
    new OneOfAssertionEvaluator(new OneOfAssertion("response.statusCode", List.of(200, 201, 204)))
        .evaluate(new ApiResponse(201, Map.of(), null), collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void statusCodeNotInListFails() {
    FailureCollector collector = new FailureCollector();
    new OneOfAssertionEvaluator(new OneOfAssertion("response.statusCode", List.of(200, 201, 204)))
        .evaluate(new ApiResponse(400, Map.of(), null), collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }
}
