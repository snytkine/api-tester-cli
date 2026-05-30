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
import io.github.snytkine.apitester.api_tester_cli.model.JsonMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ObjectExpectedValue;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class JsonMatchAssertionEvaluatorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static ApiResponse responseWithJson(String text, Object json) {
    return new ApiResponse(200, Map.of(), new ApiResponse.Body(text, json));
  }

  private static JsonMatchAssertionEvaluator evaluator(
      String expectedContent, List<String> ignore) {
    return evaluator(expectedContent, ignore, Map.of(), Map.of());
  }

  private static JsonMatchAssertionEvaluator evaluator(
      String expectedContent,
      List<String> ignore,
      Map<String, String> suiteVars,
      Map<String, String> testVars) {
    ObjectExpectedValue expected = new ObjectExpectedValue("inline", expectedContent, ignore);
    JsonMatchAssertion assertion = new JsonMatchAssertion("response.body.json", expected);
    return new JsonMatchAssertionEvaluator(assertion, null, OBJECT_MAPPER, suiteVars, testVars);
  }

  @Test
  void objectBodyExactMatchPasses() {
    LinkedHashMap<String, Object> json = new LinkedHashMap<>();
    json.put("name", "Alice");
    json.put("age", 30);

    SoftAssertions soft = new SoftAssertions();
    evaluator("{\"name\":\"Alice\",\"age\":30}", List.of())
        .evaluate(responseWithJson("{\"name\":\"Alice\",\"age\":30}", json), soft);

    assertThatCode(soft::assertAll).doesNotThrowAnyException();
  }

  @Test
  void objectBodyMismatchFails() {
    SoftAssertions soft = new SoftAssertions();
    evaluator("{\"name\":\"Bob\"}", List.of())
        .evaluate(responseWithJson("{\"name\":\"Alice\"}", Map.of("name", "Alice")), soft);

    assertThatThrownBy(soft::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void arrayBodyMatchPasses() {
    String jsonText = "[{\"id\":1},{\"id\":2}]";
    Object json = List.of(Map.of("id", 1), Map.of("id", 2));

    SoftAssertions soft = new SoftAssertions();
    evaluator("[{\"id\":1},{\"id\":2}]", List.of())
        .evaluate(responseWithJson(jsonText, json), soft);

    assertThatCode(soft::assertAll).doesNotThrowAnyException();
  }

  @Test
  void ignoredFieldsAreExcludedFromComparison() {
    String actualText = "{\"name\":\"Alice\",\"timestamp\":\"2026-01-01T00:00:00Z\"}";
    Object actualJson = Map.of("name", "Alice", "timestamp", "2026-01-01T00:00:00Z");
    String expectedContent = "{\"name\":\"Alice\",\"timestamp\":\"different-value\"}";

    SoftAssertions soft = new SoftAssertions();
    evaluator(expectedContent, List.of("timestamp"))
        .evaluate(responseWithJson(actualText, actualJson), soft);

    assertThatCode(soft::assertAll).doesNotThrowAnyException();
  }

  @Test
  void ignoredFieldsRemovedFromEachArrayElement() {
    String actualText = "[{\"id\":1,\"ts\":\"old\"},{\"id\":2,\"ts\":\"also-old\"}]";
    Object actualJson = List.of(Map.of("id", 1, "ts", "old"), Map.of("id", 2, "ts", "also-old"));
    String expectedContent = "[{\"id\":1,\"ts\":\"ignored\"},{\"id\":2,\"ts\":\"also-ignored\"}]";

    SoftAssertions soft = new SoftAssertions();
    evaluator(expectedContent, List.of("ts"))
        .evaluate(responseWithJson(actualText, actualJson), soft);

    assertThatCode(soft::assertAll).doesNotThrowAnyException();
  }

  @Test
  void nullBodyRecordsFailure() {
    ApiResponse response = new ApiResponse(200, Map.of(), null);

    SoftAssertions soft = new SoftAssertions();
    evaluator("{\"name\":\"Alice\"}", List.of()).evaluate(response, soft);

    assertThatThrownBy(soft::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void bodyWithNullJsonRecordsFailure() {
    ApiResponse response = new ApiResponse(200, Map.of(), new ApiResponse.Body("not-json", null));

    SoftAssertions soft = new SoftAssertions();
    evaluator("{\"name\":\"Alice\"}", List.of()).evaluate(response, soft);

    assertThatThrownBy(soft::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void fileNotFoundRecordsFailure() {
    ObjectExpectedValue expected =
        new ObjectExpectedValue("file", "nonexistent-file.json", List.of());
    JsonMatchAssertion assertion = new JsonMatchAssertion("response.body.json", expected);
    Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
    JsonMatchAssertionEvaluator ev =
        new JsonMatchAssertionEvaluator(assertion, tmpDir, OBJECT_MAPPER, Map.of(), Map.of());

    SoftAssertions soft = new SoftAssertions();
    ev.evaluate(responseWithJson("{\"a\":1}", Map.of("a", 1)), soft);

    assertThatThrownBy(soft::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void thymeleafSuiteVariablesResolvedInInlineExpectedContent() {
    Map<String, String> suiteVars = Map.of("userId", "42");
    String template = "{\"id\":\"[[${suite.variables.userId}]]\"}";

    SoftAssertions soft = new SoftAssertions();
    evaluator(template, List.of(), suiteVars, Map.of())
        .evaluate(responseWithJson("{\"id\":\"42\"}", Map.of("id", "42")), soft);

    assertThatCode(soft::assertAll).doesNotThrowAnyException();
  }

  @Test
  void thymeleafTestVariablesResolvedInInlineExpectedContent() {
    Map<String, String> testVars = Map.of("token", "abc123");
    String template = "{\"token\":\"[[${variables.token}]]\"}";

    SoftAssertions soft = new SoftAssertions();
    evaluator(template, List.of(), Map.of(), testVars)
        .evaluate(responseWithJson("{\"token\":\"abc123\"}", Map.of("token", "abc123")), soft);

    assertThatCode(soft::assertAll).doesNotThrowAnyException();
  }
}
