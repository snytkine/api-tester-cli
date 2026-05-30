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
import io.github.snytkine.apitester.api_tester_cli.model.JsonSchemaAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ObjectExpectedValue;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class JsonSchemaAssertionEvaluatorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String OBJECT_SCHEMA_DRAFT7 =
      """
      {
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "required": ["name", "age"],
        "properties": {
          "name": {"type": "string"},
          "age": {"type": "integer"}
        },
        "additionalProperties": false
      }
      """;

  private static final String OBJECT_SCHEMA_2020 =
      """
      {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "required": ["id"],
        "properties": {
          "id": {"type": "string"}
        }
      }
      """;

  private static final String ARRAY_SCHEMA =
      """
      {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "array",
        "items": {
          "type": "object",
          "required": ["id"],
          "properties": {
            "id": {"type": "integer"}
          }
        }
      }
      """;

  private static JsonSchemaAssertionEvaluator evaluatorFor(String path, String schema) {
    ObjectExpectedValue expected = new ObjectExpectedValue("inline", schema, List.of());
    return new JsonSchemaAssertionEvaluator(
        new JsonSchemaAssertion(path, expected), null, OBJECT_MAPPER);
  }

  private static ApiResponse responseWithJson(String text, Object json) {
    return new ApiResponse(200, Map.of(), new ApiResponse.Body(text, json));
  }

  @Test
  void validObjectPassesSchemaValidation() {
    ApiResponse response =
        responseWithJson("{\"name\":\"Alice\",\"age\":30}", Map.of("name", "Alice", "age", 30));

    SoftAssertions soft = new SoftAssertions();
    evaluatorFor("response.body.json", OBJECT_SCHEMA_DRAFT7).evaluate(response, soft);

    assertThatCode(soft::assertAll).doesNotThrowAnyException();
  }

  @Test
  void objectMissingRequiredFieldRecordsSchemaViolation() {
    ApiResponse response = responseWithJson("{\"name\":\"Alice\"}", Map.of("name", "Alice"));

    SoftAssertions soft = new SoftAssertions();
    evaluatorFor("response.body.json", OBJECT_SCHEMA_DRAFT7).evaluate(response, soft);

    assertThatThrownBy(soft::assertAll)
        .isInstanceOf(MultipleFailuresError.class)
        .hasMessageContaining("JSON schema violation");
  }

  @Test
  void objectWithWrongTypeRecordsSchemaViolation() {
    ApiResponse response =
        responseWithJson(
            "{\"name\":\"Alice\",\"age\":\"thirty\"}", Map.of("name", "Alice", "age", "thirty"));

    SoftAssertions soft = new SoftAssertions();
    evaluatorFor("response.body.json", OBJECT_SCHEMA_DRAFT7).evaluate(response, soft);

    assertThatThrownBy(soft::assertAll)
        .isInstanceOf(MultipleFailuresError.class)
        .hasMessageContaining("JSON schema violation");
  }

  @Test
  void validArrayPassesSchemaValidation() {
    String text = "[{\"id\":1},{\"id\":2}]";
    Object json = List.of(Map.of("id", 1), Map.of("id", 2));

    SoftAssertions soft = new SoftAssertions();
    evaluatorFor("response.body.json", ARRAY_SCHEMA).evaluate(responseWithJson(text, json), soft);

    assertThatCode(soft::assertAll).doesNotThrowAnyException();
  }

  @Test
  void draft2020SchemaIsRecognisedAndUsedCorrectly() {
    ApiResponse response = responseWithJson("{\"id\":\"abc\"}", Map.of("id", "abc"));

    SoftAssertions soft = new SoftAssertions();
    evaluatorFor("response.body.json", OBJECT_SCHEMA_2020).evaluate(response, soft);

    assertThatCode(soft::assertAll).doesNotThrowAnyException();
  }

  @Test
  void nullBodyRecordsFailure() {
    ApiResponse response = new ApiResponse(200, Map.of(), null);

    SoftAssertions soft = new SoftAssertions();
    evaluatorFor("response.body.json", OBJECT_SCHEMA_DRAFT7).evaluate(response, soft);

    assertThatThrownBy(soft::assertAll).isInstanceOf(MultipleFailuresError.class);
  }

  @Test
  void unsupportedPathRecordsFailure() {
    ApiResponse response =
        responseWithJson("{\"name\":\"Alice\",\"age\":30}", Map.of("name", "Alice", "age", 30));

    SoftAssertions soft = new SoftAssertions();
    evaluatorFor("invalid.path", OBJECT_SCHEMA_DRAFT7).evaluate(response, soft);

    assertThatThrownBy(soft::assertAll)
        .isInstanceOf(MultipleFailuresError.class)
        .hasMessageContaining("response.");
  }

  @Test
  void jsonpathSubexpressionExtractsNestedTarget() {
    String text = "{\"data\":{\"id\":\"abc\"}}";
    Object json = Map.of("data", Map.of("id", "abc"));
    ApiResponse response = responseWithJson(text, json);

    SoftAssertions soft = new SoftAssertions();
    evaluatorFor("response.body.json.$.data", OBJECT_SCHEMA_2020).evaluate(response, soft);

    assertThatCode(soft::assertAll).doesNotThrowAnyException();
  }

  @Test
  void fileNotFoundRecordsFailure() {
    ObjectExpectedValue expected =
        new ObjectExpectedValue("file", "nonexistent-schema.json", List.of());
    Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
    JsonSchemaAssertionEvaluator ev =
        new JsonSchemaAssertionEvaluator(
            new JsonSchemaAssertion("response.body.json", expected), tmpDir, OBJECT_MAPPER);

    ApiResponse response = responseWithJson("{\"name\":\"Alice\"}", Map.of("name", "Alice"));

    SoftAssertions soft = new SoftAssertions();
    ev.evaluate(response, soft);

    assertThatThrownBy(soft::assertAll).isInstanceOf(MultipleFailuresError.class);
  }
}
