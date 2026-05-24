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
import io.github.snytkine.apitester.api_tester_cli.thymeleaf.PassThroughExpressionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.thymeleaf.StrictVariableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

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
    StandardDialect dialect = new StandardDialect();
    dialect.setVariableExpressionEvaluator(new PassThroughExpressionEvaluator());
    this.templateEngine = new TemplateEngine();
    this.templateEngine.setDialect(dialect);
    this.templateEngine.setTemplateResolver(resolver);
  }

  public TestSuite load(Path filePath) throws IOException {
    return yamlMapper.readValue(filePath.toFile(), TestSuite.class);
  }

  public TestSuite load(Path filePath, CliVariables cliVariables) throws IOException {
    String templateContent = Files.readString(filePath);
    Context context = new Context();
    context.setVariable("cli", new StrictVariableMap(cliVariables.cli()));
    String processedYaml = templateEngine.process(templateContent, context);
    return yamlMapper.readValue(processedYaml, TestSuite.class);
  }
}
