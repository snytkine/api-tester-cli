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
import io.github.snytkine.apitester.api_tester_cli.model.HasHeaderAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;

/**
 * Evaluates a {@link HasHeaderAssertion} by checking that the response headers map contains the
 * specified header name (case-insensitive).
 *
 * <p>Fails when the response has no headers or the named header is absent.
 */
class HasHeaderAssertionEvaluator implements AssertionEvaluator {

  private final HasHeaderAssertion assertion;

  /**
   * Constructs the evaluator for the given assertion.
   *
   * @param assertion the has_header assertion to evaluate
   */
  HasHeaderAssertionEvaluator(HasHeaderAssertion assertion) {
    this.assertion = assertion;
  }

  /**
   * Checks that the named header is present in the response and records a failure when it is
   * absent.
   *
   * @param response the captured HTTP response
   * @param collector the shared failure collector
   */
  @Override
  public void evaluate(ApiResponse response, FailureCollector collector) {
    String normalised = assertion.name().toLowerCase();
    if (response.headers() == null || !response.headers().containsKey(normalised)) {
      collector.fail(
          "Expected response to contain header '%s' but it was absent", assertion.name());
    }
  }
}
