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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.snytkine.apitester.api_tester_cli.service.TestSuiteLoader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.shell.core.command.CommandArgument;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.ParsedInput;

class RunSuiteCommandTest {

  private RunSuiteCommand command;
  private PrintStream originalOut;
  private ByteArrayOutputStream capturedOut;

  @BeforeEach
  void setUp() {
    command = new RunSuiteCommand(new TestSuiteLoader());
    capturedOut = new ByteArrayOutputStream();
    originalOut = System.out;
    System.setOut(new PrintStream(capturedOut));
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
  }

  @Test
  void buildCliVariablesParsesKeyValueFormat() {
    List<CommandArgument> args =
        List.of(
            new CommandArgument(0, "api_base_url=https://api.example.com"),
            new CommandArgument(1, "admin_system=IBM"));

    Map<String, String> result = command.buildCliVariables(args);

    assertThat(result)
        .containsEntry("api_base_url", "https://api.example.com")
        .containsEntry("admin_system", "IBM");
  }

  @Test
  void buildCliVariablesSkipsEntriesWithoutEqualsSign() {
    List<CommandArgument> args =
        List.of(
            new CommandArgument(0, "api_base_url=https://api.example.com"),
            new CommandArgument(1, "not-a-variable"),
            new CommandArgument(2, "admin_system=IBM"));

    Map<String, String> result = command.buildCliVariables(args);

    assertThat(result)
        .hasSize(2)
        .containsEntry("api_base_url", "https://api.example.com")
        .containsEntry("admin_system", "IBM");
  }

  @Test
  void buildCliVariablesReturnsEmptyMapForEmptyArguments() {
    assertThat(command.buildCliVariables(List.of())).isEmpty();
  }

  @Test
  void runSuiteResolvesVariablesFromCommandContext() throws Exception {
    String suite = Path.of(getClass().getResource("/test-suite-2.yml").toURI()).toString();
    CommandContext context =
        buildContext("api_base_url=https://api.example.com", "admin_system=IBM");

    command.runSuite(suite, context);

    String output = capturedOut.toString();
    assertThat(output).contains("\"api_base_url\" : \"https://api.example.com\"");
    assertThat(output).contains("\"admin_system\" : \"IBM\"");
  }

  @Test
  void runSuiteProducesEmptyStringsWhenNoVariablesPassed() throws Exception {
    String suite = Path.of(getClass().getResource("/test-suite-2.yml").toURI()).toString();

    command.runSuite(suite, buildContext());

    String output = capturedOut.toString();
    assertThat(output).contains("\"api_base_url\" : \"\"");
    assertThat(output).contains("\"admin_system\" : \"\"");
  }

  @Test
  void runSuiteThrowsWhenFileDoesNotExist() {
    assertThatThrownBy(() -> command.runSuite("/nonexistent/path/suite.yml", buildContext()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Test suite file not found");
  }

  private CommandContext buildContext(String... argValues) {
    List<CommandArgument> arguments = new ArrayList<>();
    for (int i = 0; i < argValues.length; i++) {
      arguments.add(new CommandArgument(i, argValues[i]));
    }
    ParsedInput parsedInput = new ParsedInput("run-suite", List.of(), List.of(), arguments);
    return new CommandContext(
        parsedInput, new CommandRegistry(), new PrintWriter(System.out), null);
  }
}
