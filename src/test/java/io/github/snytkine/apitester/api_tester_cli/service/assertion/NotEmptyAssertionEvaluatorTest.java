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
import io.github.snytkine.apitester.api_tester_cli.model.NotEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class NotEmptyAssertionEvaluatorTest {

  @Test
  void nonEmptyStringHeaderPasses() {
    ApiResponse response = new ApiResponse(200, Map.of("content-type", "application/json"), null);

    FailureCollector collector = new FailureCollector();
    new NotEmptyAssertionEvaluator(new NotEmptyAssertion("response.headers.content-type"))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void emptyStringHeaderFails() {
    ApiResponse response = new ApiResponse(200, Map.of("x-custom", ""), null);

    FailureCollector collector = new FailureCollector();
    new NotEmptyAssertionEvaluator(new NotEmptyAssertion("response.headers.x-custom"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void absentHeaderFails() {
    ApiResponse response = new ApiResponse(200, Map.of(), null);

    FailureCollector collector = new FailureCollector();
    new NotEmptyAssertionEvaluator(new NotEmptyAssertion("response.headers.x-custom"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void nonEmptyBodyTextPasses() {
    ApiResponse response = new ApiResponse(200, Map.of(), new ApiResponse.Body("hello", null));

    FailureCollector collector = new FailureCollector();
    new NotEmptyAssertionEvaluator(new NotEmptyAssertion("response.body.text"))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void emptyBodyTextFails() {
    ApiResponse response = new ApiResponse(200, Map.of(), new ApiResponse.Body("", null));

    FailureCollector collector = new FailureCollector();
    new NotEmptyAssertionEvaluator(new NotEmptyAssertion("response.body.text"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void nonNullNonStringJsonValuePasses() {
    ApiResponse response =
        new ApiResponse(200, Map.of(), new ApiResponse.Body("{\"count\":42}", Map.of("count", 42)));

    FailureCollector collector = new FailureCollector();
    new NotEmptyAssertionEvaluator(new NotEmptyAssertion("response.body.json.$.count"))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void missingPathFails() {
    ApiResponse response = new ApiResponse(200, Map.of(), null);

    FailureCollector collector = new FailureCollector();
    new NotEmptyAssertionEvaluator(new NotEmptyAssertion("response.body.json"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }
}
