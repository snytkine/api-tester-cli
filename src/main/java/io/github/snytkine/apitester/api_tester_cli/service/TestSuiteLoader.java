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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.util.FileLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Loads and parses test suite YAML files, optionally resolving Thymeleaf template expressions.
 *
 * <p>This class is thread-safe. The underlying {@link ObjectMapper} (Jackson) is a documented
 * thread-safe singleton held as a final field. Template processing is delegated to {@link
 * FileLoader#parseFile}, which uses its own thread-safe {@code TemplateEngine} singleton. All
 * per-call state (intermediate strings, parsed objects) is local to each method invocation. The
 * maps inside the supplied {@link SuiteRunContext} are already immutable by construction, so
 * concurrent caller mutations cannot affect in-flight template processing.
 */
@Service
public class TestSuiteLoader {

    private final ObjectMapper yamlMapper;

    /**
     * Constructs a {@code TestSuiteLoader}. The {@link ObjectMapper} is configured to ignore unknown
     * YAML properties so that loader-injected fields (e.g. {@code templateContent}) do not cause
     * deserialization failures. Template processing is delegated to {@link FileLoader}, which owns
     * the shared {@code TemplateEngine} singleton.
     */
    public TestSuiteLoader() {
        this.yamlMapper =
                new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public TestSuite load(Path filePath) throws IOException {
        String templateContent = Files.readString(filePath);
        TestSuite testSuite = yamlMapper.readValue(filePath.toFile(), TestSuite.class);
        return new TestSuite(
                testSuite.name(),
                testSuite.description(),
                RestClientConfig.withDefaults(testSuite.restClientConfig()),
                testSuite.variables(),
                testSuite.tests(),
                filePath,
                templateContent);
    }

    /**
     * Loads and template-processes a test suite YAML file using all variable namespaces supplied in
     * {@code context}.
     *
     * <p>Template processing runs in two passes:
     *
     * <ol>
     *   <li><b>Step 1</b> — processes the raw YAML with {@code cli}, {@code env}, and {@code test}
     *       from {@code context}, and an empty {@code suite} map. The resulting {@code variables}
     *       block provides the resolved suite-level variables for Step 2.
     *   <li><b>Step 2</b> — re-processes the raw YAML with the same {@code cli} and {@code env},
     *       but with {@code suite} set to the resolved variables map from Step 1, so that
     *       expressions such as {@code [[${suite.api_base_url}]]} inside test cases resolve
     *       correctly.
     * </ol>
     *
     * <p>The Thymeleaf context exposes four top-level variables:
     *
     * <ul>
     *   <li>{@code cli} — command-line variables from {@code context.cli()}
     *   <li>{@code env} — environment variables from {@code context.env()}
     *   <li>{@code suite} — flat map of resolved suite variables; empty in Step 1, populated from
     *       Step 1 result in Step 2; accessed in templates as {@code [[${suite.my_var}]]}
     *   <li>{@code test} — test-case variables from {@code context.test()} (empty for suite-level
     *       resolution)
     * </ul>
     *
     * @param filePath absolute path to the test-suite YAML file
     * @param context all variable namespaces available during template processing
     * @return a fully loaded and template-resolved {@link TestSuite}
     * @throws IOException if the file cannot be read or YAML parsing fails
     */
    public TestSuite load(Path filePath, SuiteRunContext context) throws IOException {
        String templateContent = Files.readString(filePath);

        // Step 1: process with cli + env in context; suite seeded as an empty map so that
        // suite.* references in test cases resolve to empty string rather than throwing.
        // The resulting variables map (resolved from cli/env) becomes suite for Step 2.
        String step1Yaml = FileLoader.parseFile(
                templateContent,
                Map.of("cli", context.cli(), "env", context.env(), "suite", Map.of(), "test", context.test()));
        TestSuite step1TestSuite = yamlMapper.readValue(step1Yaml, TestSuite.class);
        Map<String, String> resolvedVariables =
                step1TestSuite.variables() != null ? new LinkedHashMap<>(step1TestSuite.variables()) : Map.of();

        // Step 2: re-process with suite set to the resolved variables map from Step 1, so that
        // expressions like [[${suite.api_base_url}]] in test cases are resolved.
        String step2Yaml = FileLoader.parseFile(
                templateContent,
                Map.of("cli", context.cli(), "env", context.env(), "suite", resolvedVariables, "test", context.test()));

        TestSuite processedTestSuite = yamlMapper.readValue(step2Yaml, TestSuite.class);
        return new TestSuite(
                processedTestSuite.name(),
                processedTestSuite.description(),
                RestClientConfig.withDefaults(processedTestSuite.restClientConfig()),
                resolvedVariables,
                processedTestSuite.tests(),
                filePath,
                templateContent);
    }
}
