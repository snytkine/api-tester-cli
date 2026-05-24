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

import io.github.snytkine.apitester.api_tester_cli.model.CliVariables;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestSuiteLoaderTest {

  private TestSuiteLoader loader;

  @BeforeEach
  void setUp() {
    loader = new TestSuiteLoader();
  }

  @Test
  void loadWithCliVariablesReplacesTemplateVariables() throws Exception {
    Path path = Path.of(getClass().getResource("/test-suite.yml").toURI());
    CliVariables cliVariables =
        new CliVariables(
            Map.of(
                "api_base_url", "https://api.example.com",
                "admin_system", "admin-prod"));

    TestSuite testSuite = loader.load(path, cliVariables);

    Map<String, String> variables = testSuite.variables();
    assertThat(variables.get("api_base_url")).isEqualTo("https://api.example.com");
    assertThat(variables.get("admin_system")).isEqualTo("admin-prod");
    assertThat(variables.get("last_updated")).isEqualTo(LocalDate.now().toString());
    assertThat(variables.get("request_id")).hasSize(12).matches("[A-Z0-9]{12}");
  }

  @Test
  void loadWithPartialCliVariablesLeavesUnresolvedExpressionsAsIs() throws Exception {
    Path path = Path.of(getClass().getResource("/test-suite-partial.yml").toURI());
    CliVariables cliVariables = new CliVariables(Map.of("api_base_url", "https://api.example.com"));

    TestSuite testSuite = loader.load(path, cliVariables);

    Map<String, String> variables = testSuite.variables();
    assertThat(variables.get("api_base_url")).isEqualTo("https://api.example.com");
    assertThat(variables.get("admin_system")).isEqualTo("${cli.admin_system}");
    assertThat(variables.get("environment")).isEqualTo("${cli.environment}");
    assertThat(variables.get("last_updated")).isEqualTo(LocalDate.now().toString());
    assertThat(variables.get("request_id")).hasSize(12).matches("[A-Z0-9]{12}");
  }
}
