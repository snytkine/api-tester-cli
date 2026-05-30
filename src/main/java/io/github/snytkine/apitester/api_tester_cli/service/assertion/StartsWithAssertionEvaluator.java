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
import io.github.snytkine.apitester.api_tester_cli.model.StartsWithAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;

/**
 * Evaluates a {@link StartsWithAssertion} by resolving the value at {@code path} and asserting that
 * it is a string starting with the {@code expected} prefix.
 *
 * <p>Non-string values (including {@code null}) and missing paths record a failure.
 */
class StartsWithAssertionEvaluator implements AssertionEvaluator {

  private final StartsWithAssertion assertion;

  /**
   * Constructs the evaluator for the given assertion.
   *
   * @param assertion the starts_with assertion to evaluate
   */
  StartsWithAssertionEvaluator(StartsWithAssertion assertion) {
    this.assertion = assertion;
  }

  /**
   * Resolves the path and records a failure if the value is not a string starting with {@code
   * expected}.
   *
   * @param response the captured HTTP response
   * @param collector the shared failure collector
   */
  @Override
  public void evaluate(ApiResponse response, FailureCollector collector) {
    switch (ResponseValueExtractor.extract(response, assertion.path())) {
      case Result.Found f -> {
        if (!(f.value() instanceof String actual)) {
          collector.fail(
              "Expected a string value at path '%s' for starts_with but was: %s (%s)",
              assertion.path(),
              f.value(),
              f.value() == null ? "null" : f.value().getClass().getSimpleName());
          return;
        }
        if (!actual.startsWith(assertion.expected())) {
          collector.fail(
              "Expected value at path '%s' to start with '%s' but was: '%s'",
              assertion.path(), assertion.expected(), actual);
        }
      }
      case Result.Missing m ->
          collector.fail(
              "Expected string at path '%s' to start with '%s' but path does not exist",
              assertion.path(), assertion.expected());
      case Result.Error e -> collector.fail(e.message());
    }
  }
}
