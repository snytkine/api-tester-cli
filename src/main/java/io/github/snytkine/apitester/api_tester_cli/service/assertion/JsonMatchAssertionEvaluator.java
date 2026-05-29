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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.JsonMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ObjectExpectedValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates a {@link JsonMatchAssertion} by comparing the response's JSON body to an expected JSON
 * document, with optional top-level field exclusions.
 *
 * <p>The expected JSON is loaded from an inline string or from a file path relative to the suite
 * directory. Fields named in {@link ObjectExpectedValue#ignore()} are removed from both the actual
 * and expected JSON trees before comparison, allowing volatile values (timestamps, generated IDs)
 * to be ignored.
 *
 * <p>Current limitation: only top-level field names are supported in the ignore list. Nested
 * JSONPath expressions are not yet evaluated.
 */
@Slf4j
class JsonMatchAssertionEvaluator implements AssertionEvaluator {

  private final JsonMatchAssertion assertion;
  @Nullable private final Path suiteDir;
  private final ObjectMapper objectMapper;

  /**
   * Constructs the evaluator for the given assertion.
   *
   * @param assertion the json_match assertion to evaluate
   * @param suiteDir directory of the test-suite file, used to resolve relative expected-file paths;
   *     may be {@code null} when the suite was not loaded from disk
   * @param objectMapper the Jackson mapper used to parse and compare JSON trees
   */
  JsonMatchAssertionEvaluator(
      JsonMatchAssertion assertion, @Nullable Path suiteDir, ObjectMapper objectMapper) {
    this.assertion = assertion;
    this.suiteDir = suiteDir;
    this.objectMapper = objectMapper;
  }

  /**
   * Compares the response JSON body to the expected JSON document, recording any mismatch in {@code
   * soft}.
   *
   * @param response the captured HTTP response
   * @param soft the shared soft-assertion collector
   */
  @Override
  public void evaluate(ApiResponse response, SoftAssertions soft) {
    if (response.body() == null || response.body().json() == null) {
      soft.fail("Response body is absent or not valid JSON for json_match assertion");
      return;
    }

    String expectedJson;
    try {
      expectedJson = loadContent(assertion.expected());
    } catch (IOException e) {
      soft.fail(
          "Failed to load expected JSON content '%s': %s",
          assertion.expected().content(), e.getMessage());
      return;
    }

    try {
      ObjectNode actualNode = (ObjectNode) objectMapper.valueToTree(response.body().json());
      ObjectNode expectedNode = (ObjectNode) objectMapper.readTree(expectedJson);

      removeIgnoredFields(actualNode, assertion.expected().ignore());
      removeIgnoredFields(expectedNode, assertion.expected().ignore());

      String actualSerialized = objectMapper.writeValueAsString(actualNode);
      String expectedSerialized = objectMapper.writeValueAsString(expectedNode);

      soft.assertThat(actualSerialized)
          .as("JSON body match (ignoring: %s)", assertion.expected().ignore())
          .isEqualTo(expectedSerialized);
    } catch (Exception e) {
      soft.fail("Failed to compare JSON bodies: %s", e.getMessage());
    }
  }

  /**
   * Loads the expected JSON content from an inline string or from a file relative to the suite
   * directory.
   *
   * @param expected the content reference from the assertion
   * @return the raw expected JSON string
   * @throws IOException if the file cannot be read
   * @throws IllegalStateException if {@code type} is {@code file} but {@code suiteDir} is null
   */
  private String loadContent(ObjectExpectedValue expected) throws IOException {
    if ("file".equals(expected.type())) {
      if (suiteDir == null) {
        throw new IllegalStateException(
            "Suite directory is required to resolve file reference: " + expected.content());
      }
      return Files.readString(suiteDir.resolve(expected.content()));
    }
    return expected.content();
  }

  /**
   * Removes the named top-level fields from a JSON object node in place.
   *
   * @param node the object node to modify
   * @param fields the field names to remove
   */
  private void removeIgnoredFields(ObjectNode node, @Nullable List<String> fields) {
    if (fields == null || fields.isEmpty()) {
      return;
    }
    fields.forEach(node::remove);
  }
}
