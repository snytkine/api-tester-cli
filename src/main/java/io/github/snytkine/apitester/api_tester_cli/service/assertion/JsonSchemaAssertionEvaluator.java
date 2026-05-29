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

import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.JsonSchemaAssertion;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates a {@link JsonSchemaAssertion} by validating the response body against a JSON Schema.
 *
 * <p>JSON Schema validation requires a dedicated validator library (e.g. {@code
 * networknt/json-schema-validator}) which is not yet on the classpath. Until that dependency is
 * added this evaluator records a soft assertion failure with a clear "not yet implemented" message
 * so the test case is marked failed rather than silently passing.
 */
@Slf4j
class JsonSchemaAssertionEvaluator implements AssertionEvaluator {

  private final JsonSchemaAssertion assertion;

  @SuppressWarnings("unused")
  @Nullable private final Path suiteDir;

  /**
   * Constructs the evaluator for the given assertion.
   *
   * @param assertion the json_schema assertion to evaluate
   * @param suiteDir directory of the test-suite file, used to resolve relative schema file paths;
   *     may be {@code null} when the suite was not loaded from disk
   */
  JsonSchemaAssertionEvaluator(JsonSchemaAssertion assertion, @Nullable Path suiteDir) {
    this.assertion = assertion;
    this.suiteDir = suiteDir;
  }

  /**
   * Records a soft assertion failure indicating that JSON Schema validation is not yet implemented.
   *
   * @param response the captured HTTP response (unused until implementation is complete)
   * @param soft the shared soft-assertion collector
   */
  @Override
  public void evaluate(ApiResponse response, SoftAssertions soft) {
    log.warn(
        "JSON schema validation is not yet implemented for assertion with schema '{}'",
        assertion.expected().content());
    soft.fail(
        "JSON schema validation is not yet implemented (schema: '%s')",
        assertion.expected().content());
  }
}
