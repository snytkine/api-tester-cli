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
import io.github.snytkine.apitester.api_tester_cli.model.AssertFalseAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class AssertFalseAssertionEvaluatorTest {

  private static ApiResponse responseWithJson(Object json) {
    return new ApiResponse(200, Map.of(), new ApiResponse.Body(json.toString(), json));
  }

  @Test
  void booleanFalsePasses() {
    ApiResponse response = responseWithJson(Map.of("deleted", false));

    FailureCollector collector = new FailureCollector();
    new AssertFalseAssertionEvaluator(new AssertFalseAssertion("response.body.json.$.deleted"))
        .evaluate(response, collector);

    assertThatCode(collector::assertAll).doesNotThrowAnyException();
  }

  @Test
  void booleanTrueFails() {
    ApiResponse response = responseWithJson(Map.of("deleted", true));

    FailureCollector collector = new FailureCollector();
    new AssertFalseAssertionEvaluator(new AssertFalseAssertion("response.body.json.$.deleted"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void stringFalseFails() {
    ApiResponse response = responseWithJson(Map.of("deleted", "false"));

    FailureCollector collector = new FailureCollector();
    new AssertFalseAssertionEvaluator(new AssertFalseAssertion("response.body.json.$.deleted"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void integerZeroFails() {
    ApiResponse response = responseWithJson(Map.of("deleted", 0));

    FailureCollector collector = new FailureCollector();
    new AssertFalseAssertionEvaluator(new AssertFalseAssertion("response.body.json.$.deleted"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void nullValueFails() {
    ApiResponse response = new ApiResponse(200, Map.of(), null);

    FailureCollector collector = new FailureCollector();
    new AssertFalseAssertionEvaluator(new AssertFalseAssertion("response.body.json"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void missingPathFails() {
    ApiResponse response = responseWithJson(Map.of("deleted", false));

    FailureCollector collector = new FailureCollector();
    new AssertFalseAssertionEvaluator(new AssertFalseAssertion("response.body.json.$.missing"))
        .evaluate(response, collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void unsupportedPathFails() {
    FailureCollector collector = new FailureCollector();
    new AssertFalseAssertionEvaluator(new AssertFalseAssertion("invalid.path"))
        .evaluate(new ApiResponse(200, Map.of(), null), collector);

    assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
  }
}
