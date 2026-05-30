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
import io.github.snytkine.apitester.api_tester_cli.model.NotEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;

/**
 * Evaluates a {@link NotEmptyAssertion} by resolving the assertion's {@code path} against the
 * response and asserting that the value is present, not {@code null}, and — when the value is a
 * string — not an empty string.
 *
 * <p>Fails when:
 *
 * <ul>
 *   <li>The path does not exist ({@link Result.Missing})
 *   <li>The resolved value is {@code null}
 *   <li>The resolved value is a string and is empty ({@code ""})
 *   <li>The path expression is unsupported ({@link Result.Error})
 * </ul>
 *
 * <p>Non-string non-null values (numbers, booleans, objects, arrays) always pass the emptiness
 * check.
 */
class NotEmptyAssertionEvaluator implements AssertionEvaluator {

  private final NotEmptyAssertion assertion;

  /**
   * Constructs the evaluator for the given assertion.
   *
   * @param assertion the not_empty assertion to evaluate
   */
  NotEmptyAssertionEvaluator(NotEmptyAssertion assertion) {
    this.assertion = assertion;
  }

  /**
   * Resolves {@code assertion.path()} and records a failure if the value is missing, {@code null},
   * or an empty string.
   *
   * @param response the captured HTTP response
   * @param collector the shared failure collector
   */
  @Override
  public void evaluate(ApiResponse response, FailureCollector collector) {
    switch (ResponseValueExtractor.extract(response, assertion.path())) {
      case Result.Found f -> {
        if (f.value() == null) {
          collector.fail("Expected non-empty value at path '%s' but was null", assertion.path());
        } else if (f.value() instanceof String s && s.isEmpty()) {
          collector.fail(
              "Expected non-empty value at path '%s' but was an empty string", assertion.path());
        }
      }
      case Result.Missing m ->
          collector.fail(
              "Expected non-empty value at path '%s' but path does not exist", assertion.path());
      case Result.Error e -> collector.fail(e.message());
    }
  }
}
