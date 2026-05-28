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
package io.github.snytkine.apitester.api_tester_cli.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.config.HttpClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.CliVariables;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Integration test that loads {@code test-suite-1.yml} and runs it through {@link
 * PureJavaTestEngine} against the live {@code https://api.restful-api.dev} endpoint. The purpose is
 * to surface any exception thrown during request execution so the root cause can be identified.
 */
class PureJavaTestEngineTest {

  private TestSuiteLoader loader;
  private PureJavaTestEngine engine;

  @BeforeEach
  void setUp() {
    loader = new TestSuiteLoader();
    HttpClientConfig config = new HttpClientConfig();
    RestClient restClient = config.restClient(config.defaultClientHttpRequestFactory());
    engine = new PureJavaTestEngine(restClient);
  }

  @Test
  void runTestSuite1ReportsResultsWithoutUnexpectedFailures() throws Exception {
    Path path = Path.of(getClass().getResource("/test-suite-1.yml").toURI());
    TestSuite testSuite = loader.load(path, new CliVariables(Map.of()));

    TestRunResult result = engine.runConfigurationSuite(testSuite.tests());

    assertThat(result.getFailedCount())
        .as(
            "Expected no test failures but got %d. Errors: %s",
            result.getFailedCount(), result.getErrorMessages())
        .isZero();
    assertThat(result.getPassedCount()).isEqualTo(testSuite.tests().size());
  }
}
