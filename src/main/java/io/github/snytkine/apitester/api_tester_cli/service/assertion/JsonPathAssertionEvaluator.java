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

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.JsonPathAssertion;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates a {@link JsonPathAssertion} by navigating a dot-notation path into the captured {@link
 * ApiResponse} and comparing the resolved value to the expected string.
 *
 * <p>Supported path prefixes (all starting with {@code response.}):
 *
 * <ul>
 *   <li>{@code response.statusCode} — the HTTP status code as a string
 *   <li>{@code response.headers.<name>} — value of the named header (case-insensitive)
 *   <li>{@code response.body.text} — the raw response body text
 *   <li>{@code response.body.json} — the entire JSON body serialized back to a string
 *   <li>{@code response.body.json.<jsonpath>} — a JSONPath expression (e.g. {@code $.users[0].id})
 *       evaluated against the parsed JSON body
 * </ul>
 */
class JsonPathAssertionEvaluator implements AssertionEvaluator {

  private static final String PREFIX = "response.";
  private static final String BODY_JSON_PREFIX = "body.json.";

  private final JsonPathAssertion assertion;

  /**
   * Constructs the evaluator for the given assertion.
   *
   * @param assertion the json_path assertion to evaluate
   */
  JsonPathAssertionEvaluator(JsonPathAssertion assertion) {
    this.assertion = assertion;
  }

  /**
   * Resolves the assertion's {@code path} against {@code response} and asserts that the resolved
   * value equals the expected string.
   *
   * @param response the captured HTTP response
   * @param soft the shared soft-assertion collector
   */
  @Override
  public void evaluate(ApiResponse response, SoftAssertions soft) {
    String path = assertion.path();
    if (!path.startsWith(PREFIX)) {
      soft.fail("Unsupported path '%s': must start with 'response.'", path);
      return;
    }
    String remaining = path.substring(PREFIX.length());
    String actual = String.valueOf(resolve(response, remaining, path, soft));
    soft.assertThat(actual).as("Value at path '%s'", path).isEqualTo(assertion.expected());
  }

  /**
   * Navigates the {@code remaining} path segment (everything after {@code response.}) within {@code
   * response} and returns the resolved value as a string.
   *
   * <p>Reports an assertion failure via {@code soft} and returns {@code null} when the path is
   * unsupported or cannot be resolved.
   *
   * @param response the captured HTTP response
   * @param remaining the path segment after the {@code response.} prefix
   * @param fullPath the original full path, used in failure messages
   * @param soft the shared soft-assertion collector
   * @return the resolved value as a string, or {@code null} if resolution failed
   */
  @Nullable private Object resolve(
      ApiResponse response, String remaining, String fullPath, SoftAssertions soft) {
    if (remaining.equals("statusCode")) {
      return response.statusCode();
    }
    if (remaining.startsWith("headers.")) {
      String name = remaining.substring("headers.".length()).toLowerCase();
      if (response.headers() == null) {
        soft.fail("No headers present in response when evaluating path '%s'", fullPath);
        return null;
      }
      return response.headers().get(name);
    }
    if (remaining.equals("body.text")) {
      return response.body() != null ? response.body().text() : null;
    }
    if (remaining.equals("body.json")) {
      return response.body() != null ? String.valueOf(response.body().json()) : null;
    }
    if (remaining.startsWith(BODY_JSON_PREFIX)) {
      String jsonPathExpr = remaining.substring(BODY_JSON_PREFIX.length());
      return evaluateJsonPath(response, jsonPathExpr, fullPath, soft);
    }
    soft.fail("Unsupported path segment '%s' in path '%s'", remaining, fullPath);
    return null;
  }

  /**
   * Evaluates a JSONPath expression against the parsed JSON body of the response.
   *
   * @param response the captured HTTP response
   * @param jsonPathExpr the JSONPath expression (e.g. {@code $.users[0].name})
   * @param fullPath the original full path, used in failure messages
   * @param soft the shared soft-assertion collector
   * @return the value at the JSONPath expression, or {@code null} if evaluation failed
   */
  @Nullable private Object evaluateJsonPath(
      ApiResponse response, String jsonPathExpr, String fullPath, SoftAssertions soft) {
    if (response.body() == null || response.body().text() == null) {
      soft.fail("Response body is absent when evaluating path '%s'", fullPath);
      return null;
    }
    try {
      return JsonPath.read(response.body().text(), jsonPathExpr);
    } catch (PathNotFoundException e) {
      soft.fail(
          "JSONPath expression '%s' not found in response body (path '%s')",
          jsonPathExpr, fullPath);
      return null;
    } catch (Exception e) {
      soft.fail(
          "Failed to evaluate JSONPath '%s' in path '%s': %s",
          jsonPathExpr, fullPath, e.getMessage());
      return null;
    }
  }
}
