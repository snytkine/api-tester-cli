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
package io.github.snytkine.apitester.api_tester_cli.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.snytkine.apitester.api_tester_cli.interfaces.TestEngine;
import io.github.snytkine.apitester.api_tester_cli.model.CliVariables;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.TestSuiteLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.shell.core.command.CommandArgument;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

/**
 * Spring Shell command group that exposes test-suite execution commands.
 *
 * <p>Registered as a Spring component so that the Spring Shell annotation command model picks it up
 * automatically via classpath scanning.
 */
@Component
public class RunSuiteCommand {

  private final TestSuiteLoader testSuiteLoader;
  private final ObjectMapper jsonMapper;
  private final TestEngine testEngine;

  /**
   * Constructs the command with its required collaborators. A dedicated JSON {@link ObjectMapper}
   * is created here rather than injected; Jackson is not auto-configured as a bean in this CLI
   * project and the mapper is an internally-owned, thread-safe singleton.
   *
   * @param testSuiteLoader loads and template-processes test-suite YAML files
   * @param testEngine executes the loaded test cases
   */
  public RunSuiteCommand(TestSuiteLoader testSuiteLoader, TestEngine testEngine) {
    this.testSuiteLoader = testSuiteLoader;
    this.testEngine = testEngine;
    this.jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

  /**
   * Loads a test suite YAML file, resolves its Thymeleaf template expressions against any supplied
   * variables, and executes the test cases using {@link PureJavaTestEngine}.
   *
   * <p>The command aborts with an error message if {@code suite} does not point to an existing
   * file. Runtime variables are passed as positional {@code key=value} arguments after {@code
   * --suite}. The {@code --} prefix must NOT be used for variable names: Spring Shell intercepts
   * tokens starting with {@code --} as named options and silently drops any that are not
   * registered, so they would never reach this method.
   *
   * <p>Example: {@code rs --suite=/path/to/suite.yml api_base_url=https://api.example.com
   * admin_system=IBM}
   *
   * @param suite absolute path to the test-suite YAML file to load
   * @param context Spring Shell command context; positional arguments are extracted from it as CLI
   *     variables forwarded to the Thymeleaf template engine
   * @throws IllegalArgumentException if {@code suite} does not refer to an existing file
   * @throws Exception if loading, template processing, or test execution fails
   */
  @Command(
      name = "run-suite",
      alias = {"rs"},
      description =
          "Loads a test-suite YAML file and executes its test cases."
              + " Pass variables as key=value positional arguments after --suite.")
  public void runSuite(
      @Option(
              longName = "suite",
              required = true,
              description = "Absolute path to the test-suite.yml file.")
          String suite,
      CommandContext context)
      throws Exception {

    Path suitePath = Path.of(suite);
    if (!Files.exists(suitePath)) {
      throw new IllegalArgumentException("Test suite file not found: " + suite);
    }

    Map<String, String> cliVars = buildCliVariables(context.parsedInput().arguments());
    TestSuite testSuite = testSuiteLoader.load(suitePath, new CliVariables(cliVars));
    testEngine.runConfigurationSuite(testSuite);
  }

  /**
   * Converts a list of positional {@link CommandArgument} values into a CLI variable map.
   *
   * <p>Each argument value is expected in {@code key=value} form. Any entry that does not contain
   * {@code =} is silently skipped.
   *
   * @param arguments positional arguments from {@link
   *     org.springframework.shell.core.command.ParsedInput#arguments()}
   * @return mutable map of variable names to their values, in encounter order
   */
  Map<String, String> buildCliVariables(List<CommandArgument> arguments) {
    Map<String, String> cliVars = new LinkedHashMap<>();
    for (CommandArgument arg : arguments) {
      String[] parts = arg.value().split("=", 2);
      if (parts.length == 2) {
        cliVars.put(parts[0].trim(), parts[1].trim());
      }
    }
    return cliVars;
  }
}
