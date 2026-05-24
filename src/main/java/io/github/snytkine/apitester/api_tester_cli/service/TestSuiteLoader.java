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
import io.github.snytkine.apitester.api_tester_cli.model.CliVariables;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Loads and parses test suite YAML files, optionally resolving Thymeleaf template expressions.
 *
 * <p>This class is thread-safe. Both the underlying {@link ObjectMapper} (Jackson) and {@link
 * TemplateEngine} (Thymeleaf) are documented thread-safe singletons. All fields are final and
 * written only during construction. All per-call state ({@link org.thymeleaf.context.Context},
 * intermediate strings, parsed objects) is local to each method invocation. The {@code cli} map
 * supplied via {@link CliVariables} is defensively copied to an immutable snapshot at the start of
 * each call, so concurrent mutations by the caller cannot affect in-flight template processing.
 */
@Service
public class TestSuiteLoader {

  private final ObjectMapper yamlMapper;
  private final TemplateEngine templateEngine;

  public TestSuiteLoader() {
    this.yamlMapper =
        new ObjectMapper(new YAMLFactory())
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    StringTemplateResolver resolver = new StringTemplateResolver();
    resolver.setTemplateMode(TemplateMode.TEXT);
    this.templateEngine = new TemplateEngine();
    this.templateEngine.setTemplateResolver(resolver);
  }

  public TestSuite load(Path filePath) throws IOException {
    return yamlMapper.readValue(filePath.toFile(), TestSuite.class);
  }

  public TestSuite load(Path filePath, CliVariables cliVariables) throws IOException {
    String templateContent = Files.readString(filePath);
    // Immutable snapshot — shields in-flight processing from concurrent caller mutations.
    Map<String, String> cli = Map.copyOf(cliVariables.cli());

    // Step 1: process the full YAML template with only cli in context.
    // suite.variables is seeded as an empty map so that any suite.variables.* references
    // in test cases resolve to empty string rather than throwing a null access error.
    // The resulting variables map (resolved from cli) becomes suite.variables for Step 2.
    Context step1Context = new Context();
    step1Context.setVariable("cli", cli);
    step1Context.setVariable("suite", Map.of("variables", Map.of()));
    String step1Yaml = templateEngine.process(templateContent, step1Context);
    TestSuite step1TestSuite = yamlMapper.readValue(step1Yaml, TestSuite.class);
    Map<String, String> resolvedVariables =
        step1TestSuite.variables() != null
            ? new LinkedHashMap<>(step1TestSuite.variables())
            : Map.of();

    // Step 2: process the full YAML template again with suite.variables populated from Step 1,
    // so that expressions like [[${suite.variables.api_base_url}]] in test cases are resolved.
    Context step2Context = new Context();
    step2Context.setVariable("cli", cli);
    step2Context.setVariable("suite", Map.of("variables", resolvedVariables));
    String step2Yaml = templateEngine.process(templateContent, step2Context);

    TestSuite processedTestSuite = yamlMapper.readValue(step2Yaml, TestSuite.class);
    return new TestSuite(
        processedTestSuite.name(),
        processedTestSuite.description(),
        resolvedVariables,
        processedTestSuite.tests());
  }
}
