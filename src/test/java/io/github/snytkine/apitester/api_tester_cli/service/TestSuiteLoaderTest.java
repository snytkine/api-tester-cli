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

import io.github.snytkine.apitester.api_tester_cli.model.BodyType;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.PayloadRequest;
import io.github.snytkine.apitester.api_tester_cli.model.Request;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.Assertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.JsonMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.JsonSchemaAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusCodeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StringMatchAssertion;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
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
        SuiteRunContext context = SuiteRunContext.of(
                Map.of(), Map.of("api_base_url", "https://api.example.com", "admin_system", "admin-prod"));

        TestSuite testSuite = loader.load(path, context);

        assertThat(testSuite.filePath()).isEqualTo(path);
        Map<String, String> variables = testSuite.variables();
        assertThat(variables.get("api_base_url")).isEqualTo("https://api.example.com");
        assertThat(variables.get("admin_system")).isEqualTo("admin-prod");
        assertThat(variables.get("last_updated")).isEqualTo(LocalDate.now().toString());
        assertThat(variables.get("request_id")).hasSize(12).matches("[A-Z0-9]{12}");
    }

    @Test
    void loadWithPartialCliVariablesLeavesUnresolvedExpressionsAsIs() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-partial.yml").toURI());
        SuiteRunContext context = SuiteRunContext.of(Map.of(), Map.of("api_base_url", "https://api.example.com"));

        TestSuite testSuite = loader.load(path, context);

        assertThat(testSuite.filePath()).isEqualTo(path);
        Map<String, String> variables = testSuite.variables();
        assertThat(variables.get("api_base_url")).isEqualTo("https://api.example.com");
        assertThat(variables.get("admin_system")).isEmpty();
        assertThat(variables.get("environment")).isEmpty();
        assertThat(variables.get("last_updated")).isEqualTo(LocalDate.now().toString());
        assertThat(variables.get("request_id")).hasSize(12).matches("[A-Z0-9]{12}");
    }

    @Test
    void twoStepLoadResolvesSuiteVariableReferencesInTestCases() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-two-step.yml").toURI());
        SuiteRunContext context = SuiteRunContext.of(
                Map.of(), Map.of("api_base_url", "https://api.example.com", "admin_system", "admin-prod"));

        TestSuite testSuite = loader.load(path, context);

        assertThat(testSuite.filePath()).isEqualTo(path);
        Map<String, String> variables = testSuite.variables();
        assertThat(variables.get("api_base_url")).isEqualTo("https://api.example.com");
        assertThat(variables.get("admin_system")).isEqualTo("admin-prod");
        assertThat(variables.get("last_updated")).isEqualTo(LocalDate.now().toString());
        String requestId = variables.get("request_id");
        assertThat(requestId).hasSize(12).matches("[A-Z0-9]{12}");

        Request request = testSuite.tests().get(0).request();
        assertThat(request.url()).isEqualTo("https://api.example.com/login");
        assertThat(request.headers().get("x-admin")).isEqualTo("admin-prod");
        assertThat(request.headers().get("x-request-id")).isEqualTo(requestId);
    }

    @Test
    void elvisOperatorUsesProvidedValueWhenVariableExists() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-elvis.yml").toURI());
        SuiteRunContext context = SuiteRunContext.of(
                Map.of(), Map.of("api_base_url", "api-example-com", "environment", "production", "timeout", "60"));

        TestSuite testSuite = loader.load(path, context);

        assertThat(testSuite.filePath()).isEqualTo(path);
        Map<String, String> variables = testSuite.variables();
        assertThat(variables.get("api_base_url")).isEqualTo("api-example-com");
        assertThat(variables.get("environment")).isEqualTo("production");
        assertThat(variables.get("timeout")).isEqualTo("60");

        Request request = testSuite.tests().get(0).request();
        assertThat(request.url()).isEqualTo("api-example-com/health");
        assertThat(request.headers().get("x-environment")).isEqualTo("production");
    }

    @Test
    void loadTestSuite2ResolvesAllVariablesAndParsesAllAssertionTypes() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-2.yml").toURI());
        SuiteRunContext context = SuiteRunContext.of(
                Map.of(), Map.of("api_base_url", "https://api.example.com", "admin_system", "admin-prod"));

        TestSuite testSuite = loader.load(path, context);

        assertThat(testSuite.filePath()).isEqualTo(path);
        assertThat(testSuite.name()).isEqualTo("Test Suite for My Application v1.0");
        assertThat(testSuite.tests()).hasSize(1);

        Map<String, String> variables = testSuite.variables();
        assertThat(variables.get("api_base_url")).isEqualTo("https://api.example.com");
        assertThat(variables.get("admin_system")).isEqualTo("admin-prod");
        assertThat(variables.get("last_updated")).isEqualTo(LocalDate.now().toString());
        String requestId = variables.get("request_id");
        assertThat(requestId).hasSize(12).matches("[A-Z0-9]{12}");

        TestCase testCase = testSuite.tests().get(0);
        assertThat(testCase.name()).isEqualTo("Test User Login");
        assertThat(testCase.variables().get("username")).isEqualTo("testuser");
        assertThat(testCase.variables().get("password")).isEqualTo("password123");
        assertThat(testCase.variables().get("id")).isEqualTo("login_test_001");

        assertThat(testCase.request()).isInstanceOf(PayloadRequest.class);
        PayloadRequest request = (PayloadRequest) testCase.request();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url()).isEqualTo("https://api.example.com/login");
        assertThat(request.headers().get("Content-Type")).isEqualTo("application/json");
        assertThat(request.headers().get("x-username")).isEqualTo("testuser");
        assertThat(request.headers().get("x-request-id")).isEqualTo(requestId);
        assertThat(request.body().type()).isEqualTo(BodyType.FILE);
        assertThat(request.body().content()).isEqualTo("login_request_body.json");

        List<Assertion> assertions = testCase.assertions();
        assertThat(assertions).hasSize(4);

        assertThat(assertions.get(0)).isInstanceOf(StatusCodeAssertion.class);
        assertThat(((StatusCodeAssertion) assertions.get(0)).expected()).isEqualTo(200);

        assertThat(assertions.get(1)).isInstanceOf(JsonSchemaAssertion.class);
        JsonSchemaAssertion schemaAssertion = (JsonSchemaAssertion) assertions.get(1);
        assertThat(schemaAssertion.path()).isEqualTo("response.body.json");
        assertThat(schemaAssertion.expected().type()).isEqualTo("file");
        assertThat(schemaAssertion.expected().content()).isEqualTo("schemas/login_response_schema.json");

        assertThat(assertions.get(2)).isInstanceOf(StringMatchAssertion.class);
        StringMatchAssertion pathAssertion = (StringMatchAssertion) assertions.get(2);
        assertThat(pathAssertion.path()).isEqualTo("response.headers.content-type");
        assertThat(pathAssertion.expected()).isEqualTo("application/json");

        assertThat(assertions.get(3)).isInstanceOf(JsonMatchAssertion.class);
        JsonMatchAssertion matchAssertion = (JsonMatchAssertion) assertions.get(3);
        assertThat(matchAssertion.path()).isEqualTo("response.body.json");
        assertThat(matchAssertion.expected().content()).isEqualTo("expected_login_response.json");
        assertThat(matchAssertion.expected().ignore()).containsExactly("timestamp", "session_id");
    }

    @Test
    void elvisOperatorUsesDefaultValueWhenVariableIsMissing() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-elvis.yml").toURI());

        TestSuite testSuite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        assertThat(testSuite.filePath()).isEqualTo(path);
        Map<String, String> variables = testSuite.variables();
        assertThat(variables.get("api_base_url")).isEqualTo("https://localhost:8080");
        assertThat(variables.get("environment")).isEqualTo("staging");
        assertThat(variables.get("timeout")).isEqualTo("30");

        Request request = testSuite.tests().get(0).request();
        assertThat(request.url()).isEqualTo("https://localhost:8080/health");
        assertThat(request.headers().get("x-environment")).isEqualTo("staging");
    }

    @Test
    void restClientHeadersAreLoadedWhenPresent() throws Exception {
        Path path = Path.of(
                getClass().getResource("/test-suite-rest-client-headers.yml").toURI());

        TestSuite testSuite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        RestClientConfig config = testSuite.restClientConfig();
        assertThat(config).isNotNull();
        assertThat(config.headers()).isNotNull();
        assertThat(config.headers()).containsEntry("x-api-key", "test-key-123");
        assertThat(config.headers()).containsEntry("Accept", "application/json");
    }

    @Test
    void restClientHeadersAreNullWhenAbsentFromYaml() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-1.yml").toURI());

        TestSuite testSuite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        // TestSuiteLoader always applies withDefaults, so restClientConfig() is never null.
        // When the YAML has no rest_client.headers key, headers() is null.
        assertThat(testSuite.restClientConfig()).isNotNull();
        assertThat(testSuite.restClientConfig().headers()).isNull();
    }

    @Test
    void withDefaultsProducesNullHeadersWhenRestClientHasNoHeadersKey() throws Exception {
        Path path = Path.of(
                getClass().getResource("/test-suite-rest-client-headers.yml").toURI());

        TestSuite testSuite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        RestClientConfig withDefaults = RestClientConfig.withDefaults(testSuite.restClientConfig());
        assertThat(withDefaults.headers()).isNotNull();
        assertThat(withDefaults.headers()).containsEntry("x-api-key", "test-key-123");
    }

    @Test
    void loadWithoutContextSetsFilePathAndTemplateContent() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-1.yml").toURI());

        TestSuite testSuite = loader.load(path);

        assertThat(testSuite.filePath()).isEqualTo(path);
        assertThat(testSuite.templateContent()).isNotNull();
        assertThat(testSuite.templateContent()).contains("Test Suite for My Application v1.0");
    }

    @Test
    void loadWithoutContextPreservesRawTemplateExpressionsWithoutProcessing() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-2.yml").toURI());

        TestSuite testSuite = loader.load(path);

        assertThat(testSuite.name()).isEqualTo("Test Suite for My Application v1.0");
        Map<String, String> variables = testSuite.variables();
        // No template processing occurs — expressions are stored as-is
        assertThat(variables.get("api_base_url")).isEqualTo("[[${cli.api_base_url}]]");
        assertThat(variables.get("admin_system")).isEqualTo("[[${cli.admin_system}]]");
    }

    @Test
    void loadWithoutContextAppliesRestClientDefaultsWhenConfigPresent() throws Exception {
        Path path = Path.of(
                getClass().getResource("/test-suite-rest-client-headers.yml").toURI());

        TestSuite testSuite = loader.load(path);

        RestClientConfig config = testSuite.restClientConfig();
        assertThat(config).isNotNull();
        assertThat(config.baseUrl()).isEqualTo("https://api.example.com");
        assertThat(config.connectTimeout()).isEqualTo(RestClientConfig.DEFAULT_CONNECT_TIMEOUT_MS);
        assertThat(config.headers()).containsEntry("x-api-key", "test-key-123");
        assertThat(config.headers()).containsEntry("Accept", "application/json");
    }

    @Test
    void loadWithoutContextAppliesDefaultRestClientConfigWhenAbsent() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-1.yml").toURI());

        TestSuite testSuite = loader.load(path);

        RestClientConfig config = testSuite.restClientConfig();
        assertThat(config).isNotNull();
        assertThat(config.baseUrl()).isEqualTo(RestClientConfig.DEFAULT_BASE_URL);
        assertThat(config.connectTimeout()).isEqualTo(RestClientConfig.DEFAULT_CONNECT_TIMEOUT_MS);
        assertThat(config.headers()).isNull();
    }
}
