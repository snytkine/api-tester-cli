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
import io.github.snytkine.apitester.api_tester_cli.model.EndsWithAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class EndsWithAssertionEvaluatorTest {

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
  void matchingSuffixPasses() {
    ApiResponse response = responseWithJson(Map.of("email", "user@example.com"));

    FailureCollector collector = new FailureCollector();
    new EndsWithAssertionEvaluator(
            new EndsWithAssertion("response.body.json.$.email", "@example.com"))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void nonMatchingSuffixFails() {
    ApiResponse response = responseWithJson(Map.of("email", "user@other.com"));

    FailureCollector collector = new FailureCollector();
    new EndsWithAssertionEvaluator(
            new EndsWithAssertion("response.body.json.$.email", "@example.com"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void numberValueFails() {
    ApiResponse response = responseWithJson(Map.of("count", 42));

    FailureCollector collector = new FailureCollector();
    new EndsWithAssertionEvaluator(new EndsWithAssertion("response.body.json.$.count", "2"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void missingPathFails() {
    ApiResponse response = responseWithJson(Map.of("email", "user@example.com"));

    FailureCollector collector = new FailureCollector();
    new EndsWithAssertionEvaluator(new EndsWithAssertion("response.body.json.$.missing", ".com"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void headerMatchPasses() {
    ApiResponse response = new ApiResponse(200, Map.of("content-type", "application/json"), null);

    FailureCollector collector = new FailureCollector();
    new EndsWithAssertionEvaluator(new EndsWithAssertion("response.headers.content-type", "/json"))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }
}
