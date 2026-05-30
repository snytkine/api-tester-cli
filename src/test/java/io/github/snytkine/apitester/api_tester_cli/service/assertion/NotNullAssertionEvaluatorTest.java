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
import io.github.snytkine.apitester.api_tester_cli.model.NotNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class NotNullAssertionEvaluatorTest {

  private static ApiResponse responseWithJsonBody(String text, Object json) {
    return new ApiResponse(200, Map.of(), new ApiResponse.Body(text, json));
  }

  private static ApiResponse responseWithHeader(String name, String value) {
    return new ApiResponse(200, Map.of(name, value), null);
  }

  @Test
  void nonNullJsonFieldPasses() {
    ApiResponse response = responseWithJsonBody("{\"name\":\"Alice\"}", Map.of("name", "Alice"));

    FailureCollector collector = new FailureCollector();
    new NotNullAssertionEvaluator(new NotNullAssertion("response.body.json.$.name"))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void missingJsonFieldFails() {
    ApiResponse response = responseWithJsonBody("{\"name\":\"Alice\"}", Map.of("name", "Alice"));

    FailureCollector collector = new FailureCollector();
    new NotNullAssertionEvaluator(new NotNullAssertion("response.body.json.$.missing"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void presentHeaderPasses() {
    FailureCollector collector = new FailureCollector();
    new NotNullAssertionEvaluator(new NotNullAssertion("response.headers.content-type"))
        .evaluate(responseWithHeader("content-type", "application/json"), collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void absentHeaderFails() {
    ApiResponse response = new ApiResponse(200, Map.of(), null);

    FailureCollector collector = new FailureCollector();
    new NotNullAssertionEvaluator(new NotNullAssertion("response.headers.x-custom"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void statusCodePasses() {
    FailureCollector collector = new FailureCollector();
    new NotNullAssertionEvaluator(new NotNullAssertion("response.statusCode"))
        .evaluate(new ApiResponse(200, Map.of(), null), collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void nullBodyFails() {
    ApiResponse response = new ApiResponse(200, Map.of(), null);

    FailureCollector collector = new FailureCollector();
    new NotNullAssertionEvaluator(new NotNullAssertion("response.body.json"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void unsupportedPathFails() {
    FailureCollector collector = new FailureCollector();
    new NotNullAssertionEvaluator(new NotNullAssertion("invalid.path"))
        .evaluate(new ApiResponse(200, Map.of(), null), collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }
}
