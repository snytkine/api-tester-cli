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
import io.github.snytkine.apitester.api_tester_cli.model.RangeAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;

/**
 * Evaluates a {@link RangeAssertion} by resolving the value at {@code path} and asserting it falls
 * within [{@code min}, {@code max}] inclusive.
 *
 * <p>Number values are compared directly as {@code double}. String values are parsed as {@code
 * double} before comparison. Any other type, a missing path, or a string that cannot be parsed
 * records a failure.
 */
class RangeAssertionEvaluator implements AssertionEvaluator {

  private final RangeAssertion assertion;

  /**
   * Constructs the evaluator for the given assertion.
   *
   * @param assertion the range assertion to evaluate
   */
  RangeAssertionEvaluator(RangeAssertion assertion) {
    this.assertion = assertion;
  }

  /**
   * Resolves the path, converts the value to {@code double}, and checks it is within the configured
   * range.
   *
   * @param response the captured HTTP response
   * @param collector the shared failure collector
   */
  @Override
  public void evaluate(ApiResponse response, FailureCollector collector) {
    switch (ResponseValueExtractor.extract(response, assertion.path())) {
      case Result.Found f -> {
        if (f.value() == null) {
          collector.fail(
              "Expected numeric value in range at path '%s' but was null", assertion.path());
          return;
        }
        double numeric;
        if (f.value() instanceof Number n) {
          numeric = n.doubleValue();
        } else if (f.value() instanceof String s) {
          try {
            numeric = Double.parseDouble(s);
          } catch (NumberFormatException e) {
            collector.fail(
                "Expected numeric value in range at path '%s' but could not parse '%s' as a number",
                assertion.path(), s);
            return;
          }
        } else {
          collector.fail(
              "Expected numeric value in range at path '%s' but was %s (%s)",
              assertion.path(), f.value(), f.value().getClass().getSimpleName());
          return;
        }
        if (numeric < assertion.min() || numeric > assertion.max()) {
          collector.fail(
              "Expected value at path '%s' to be in range [%s, %s] but was %s",
              assertion.path(), assertion.min(), assertion.max(), numeric);
        }
      }
      case Result.Missing m ->
          collector.fail(
              "Expected numeric value in range at path '%s' but path does not exist",
              assertion.path());
      case Result.Error e -> collector.fail(e.message());
    }
  }
}
