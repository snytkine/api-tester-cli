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
package io.github.snytkine.apitester.api_tester_cli.interfaces;

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import org.assertj.core.api.SoftAssertions;

/**
 * Contract for evaluating a single assertion against a captured HTTP response.
 *
 * <p>Implementations receive a fully-resolved {@link ApiResponse} and a shared {@link
 * SoftAssertions} collector. All assertion failures must be reported through {@code soft} rather
 * than thrown directly so that every assertion in a test case is evaluated before failures are
 * surfaced.
 *
 * <p>Implementations must be stateless — all input comes through method parameters — so they are
 * safe to call concurrently across multiple test suite runs.
 */
@FunctionalInterface
public interface AssertionEvaluator {

  /**
   * Evaluates this assertion against the given response, recording any failures in {@code soft}.
   *
   * @param response the captured HTTP response to assert against
   * @param soft the shared soft-assertion collector for this test case
   */
  void evaluate(ApiResponse response, SoftAssertions soft);
}
