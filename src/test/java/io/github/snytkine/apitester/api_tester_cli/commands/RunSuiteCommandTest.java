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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.snytkine.apitester.api_tester_cli.interfaces.TestEngine;
import io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.TestSuiteLoader;
import io.github.snytkine.apitester.api_tester_cli.service.TestSuiteValidator;
import io.github.snytkine.apitester.api_tester_cli.util.DotEnvLoader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.shell.core.command.CommandArgument;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.ParsedInput;

class RunSuiteCommandTest {

    private TestEngine mockEngine;
    private RunSuiteCommand command;

    @BeforeEach
    void setUp() {
        mockEngine = mock(TestEngine.class);
        command = new RunSuiteCommand(
                new TestSuiteLoader(), new TestSuiteValidator(), mockEngine, new DotEnvLoader(), null);
    }

    @Test
    void buildCliVariablesParsesKeyValueFormat() {
        List<CommandArgument> args = List.of(
                new CommandArgument(0, "api_base_url=https://api.example.com"),
                new CommandArgument(1, "admin_system=IBM"));

        Map<String, String> result = command.buildCliVariables(args);

        assertThat(result)
                .containsEntry("api_base_url", "https://api.example.com")
                .containsEntry("admin_system", "IBM");
    }

    @Test
    void buildCliVariablesSkipsEntriesWithoutEqualsSign() {
        List<CommandArgument> args = List.of(
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
    void runSuitePassesResolvedVariablesToEngine() throws Exception {
        String suite =
                Path.of(getClass().getResource("/test-suite-2.yml").toURI()).toString();
        TestRunResult fakeResult = new TestRunResult(
                1,
                0,
                0L,
                0L,
                List.of(new TestCaseResult("test", TestResult.PASSED, 1, List.of(), null, null)),
                Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(
                suite, false, false, null, buildContext("api_base_url=https://api.example.com", "admin_system=IBM"));

        verify(mockEngine).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void runSuiteInvokesEngineEvenWithNoVariables() throws Exception {
        String suite =
                Path.of(getClass().getResource("/test-suite-2.yml").toURI()).toString();
        TestRunResult fakeResult = new TestRunResult(
                1,
                0,
                0L,
                0L,
                List.of(new TestCaseResult("test", TestResult.PASSED, 1, List.of(), null, null)),
                Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, null, buildContext());

        verify(mockEngine).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void runSuiteWithNoUiFlagStillPrintsJson() throws Exception {
        String suite =
                Path.of(getClass().getResource("/test-suite-2.yml").toURI()).toString();
        TestRunResult fakeResult = new TestRunResult(
                1,
                0,
                0L,
                0L,
                List.of(new TestCaseResult("test", TestResult.PASSED, 1, List.of(), null, null)),
                Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, true, false, null, buildContext());

        verify(mockEngine).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void runSuiteThrowsWhenFileDoesNotExist() {
        assertThatThrownBy(() -> command.runSuite("/nonexistent/path/suite.yml", false, false, null, buildContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Test suite file not found");
    }

    @Test
    void tagFilterPassesOnlyMatchingTestsToEngine() throws Exception {
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        TestRunResult fakeResult = new TestRunResult(2, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, "smoke", buildContext());

        ArgumentCaptor<TestSuite> suiteCaptor = ArgumentCaptor.forClass(TestSuite.class);
        verify(mockEngine).runConfigurationSuite(suiteCaptor.capture(), any(), any());
        assertThat(suiteCaptor.getValue().tests()).hasSize(2);
        assertThat(suiteCaptor.getValue().tests()).allMatch(tc -> "smoke".equals(tc.tag()));
    }

    @Test
    void tagFilterWithNoMatchDoesNotCallEngine() throws Exception {
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new org.springframework.shell.core.command.ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new org.springframework.shell.core.command.CommandRegistry(),
                new PrintWriter(output),
                null);

        command.runSuite(suite, false, false, "nonexistent-tag", ctx);

        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
        assertThat(output.toString()).contains("No tests found with tag");
    }

    private CommandContext buildContext(String... argValues) {
        List<CommandArgument> arguments = new ArrayList<>();
        for (int i = 0; i < argValues.length; i++) {
            arguments.add(new CommandArgument(i, argValues[i]));
        }
        ParsedInput parsedInput = new ParsedInput("run-suite", List.of(), List.of(), arguments);
        return new CommandContext(parsedInput, new CommandRegistry(), new PrintWriter(System.out), null);
    }
}
