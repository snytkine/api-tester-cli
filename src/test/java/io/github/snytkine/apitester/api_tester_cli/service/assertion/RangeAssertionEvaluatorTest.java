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
import io.github.snytkine.apitester.api_tester_cli.model.RangeAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class RangeAssertionEvaluatorTest {

  private static ApiResponse responseWithJson(Object json) {
    return new ApiResponse(200, Map.of(), new ApiResponse.Body(json.toString(), json));
  }

  @Test
  void integerAtMinBoundPasses() {
    ApiResponse response = responseWithJson(Map.of("score", 0));

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json.$.score", 0, 100))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void integerAtMaxBoundPasses() {
    ApiResponse response = responseWithJson(Map.of("score", 100));

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json.$.score", 0, 100))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void integerWithinRangePasses() {
    ApiResponse response = responseWithJson(Map.of("score", 50));

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json.$.score", 0, 100))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void integerBelowRangeFails() {
    ApiResponse response = responseWithJson(Map.of("score", -1));

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json.$.score", 0, 100))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void integerAboveRangeFails() {
    ApiResponse response = responseWithJson(Map.of("score", 101));

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json.$.score", 0, 100))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void doubleWithinRangePasses() {
    ApiResponse response = responseWithJson(Map.of("ratio", 0.5));

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json.$.ratio", 0.0, 1.0))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void numericStringWithinRangePasses() {
    ApiResponse response = responseWithJson(Map.of("score", "42"));

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json.$.score", 0, 100))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void nonNumericStringFails() {
    ApiResponse response = responseWithJson(Map.of("score", "abc"));

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json.$.score", 0, 100))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void booleanValueFails() {
    ApiResponse response = responseWithJson(Map.of("flag", true));

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json.$.flag", 0, 1))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void nullValueFails() {
    ApiResponse response = new ApiResponse(200, Map.of(), null);

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json", 0, 100))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void missingPathFails() {
    ApiResponse response = responseWithJson(Map.of("score", 50));

    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.body.json.$.missing", 0, 100))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void statusCodeWithinRangePasses() {
    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.statusCode", 200, 299))
        .evaluate(new ApiResponse(200, Map.of(), null), collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void statusCodeOutsideRangeFails() {
    FailureCollector collector = new FailureCollector();
    new RangeAssertionEvaluator(new RangeAssertion("response.statusCode", 200, 299))
        .evaluate(new ApiResponse(404, Map.of(), null), collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }
}
