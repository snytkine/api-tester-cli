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

import io.github.snytkine.apitester.api_tester_cli.event.NoOpProgressListener;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressListener;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;

/**
 * Contract for running a test suite and collecting results.
 *
 * <p>Implementations receive a fully-loaded {@link TestSuite} and execute its test cases, returning
 * an aggregated {@link TestRunResult} with pass/fail counts and error messages. Decoupling the
 * command layer from a concrete implementation allows alternative engines (e.g. parallel, dry-run,
 * mock) to be swapped in via Spring dependency injection.
 */
public interface TestEngine {

  /**
   * Executes all test cases in the given {@link TestSuite} and returns an aggregated result.
   *
   * <p>This convenience overload uses {@link NoOpProgressListener#INSTANCE} and is equivalent to
   * {@code runConfigurationSuite(testSuite, NoOpProgressListener.INSTANCE)}.
   *
   * @param testSuite the loaded test suite to execute
   * @return a {@link TestRunResult} containing pass count, fail count, and error messages
   * @throws Exception if a fatal error prevents the suite from running
   */
  default TestRunResult runConfigurationSuite(TestSuite testSuite) throws Exception {
    return runConfigurationSuite(testSuite, NoOpProgressListener.INSTANCE);
  }

  /**
   * Executes all test cases in the given {@link TestSuite}, firing progress events to {@code
   * listener} at each milestone (suite start, per-test start/complete, suite complete).
   *
   * @param testSuite the loaded test suite to execute
   * @param listener receives progress events; must be thread-safe
   * @return a {@link TestRunResult} containing pass count, fail count, and error messages
   * @throws Exception if a fatal error prevents the suite from running
   */
  TestRunResult runConfigurationSuite(TestSuite testSuite, TestProgressListener listener)
      throws Exception;
}
