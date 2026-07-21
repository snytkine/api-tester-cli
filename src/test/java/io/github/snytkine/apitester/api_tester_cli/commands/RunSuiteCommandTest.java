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

import io.github.snytkine.apitester.api_tester_cli.config.VersionCheckProperties;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressListener;
import io.github.snytkine.apitester.api_tester_cli.event.TestStatus;
import io.github.snytkine.apitester.api_tester_cli.exception.HookFailedException;
import io.github.snytkine.apitester.api_tester_cli.interfaces.TestEngine;
import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.HtmlReportGenerator;
import io.github.snytkine.apitester.api_tester_cli.service.LatestVersionHolder;
import io.github.snytkine.apitester.api_tester_cli.service.TestSuiteLoader;
import io.github.snytkine.apitester.api_tester_cli.service.TestSuiteValidator;
import io.github.snytkine.apitester.api_tester_cli.service.hooks.HookRunner;
import io.github.snytkine.apitester.api_tester_cli.util.DotEnvLoader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.shell.core.command.CommandArgument;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.ParsedInput;
import org.springframework.shell.jline.tui.component.ViewComponentBuilder;

class RunSuiteCommandTest {

    @TempDir
    Path tempDir;

    private TestEngine mockEngine;
    private HtmlReportGenerator mockReportGenerator;
    private RunSuiteCommand command;
    private RunSuiteCommand commandWithUi;

    private static VersionCheckProperties noOpVersionCheckProperties() {
        return new VersionCheckProperties(
                false,
                "https://example.invalid",
                "https://example.invalid",
                1,
                1,
                1,
                "Version {latestVersion} is available.");
    }

    @BeforeEach
    void setUp() {
        mockEngine = mock(TestEngine.class);
        mockReportGenerator = mock(HtmlReportGenerator.class);
        command = new RunSuiteCommand(
                new TestSuiteLoader(),
                new TestSuiteValidator(),
                mockEngine,
                new DotEnvLoader(),
                mockReportGenerator,
                new LatestVersionHolder(),
                noOpVersionCheckProperties(),
                mock(HookRunner.class),
                null,
                new MockEnvironment());
        commandWithUi = new RunSuiteCommand(
                new TestSuiteLoader(),
                new TestSuiteValidator(),
                mockEngine,
                new DotEnvLoader(),
                mockReportGenerator,
                new LatestVersionHolder(),
                noOpVersionCheckProperties(),
                mock(HookRunner.class),
                mock(ViewComponentBuilder.class),
                new MockEnvironment());
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
                List.of(new TestCaseResult("test", TestResult.PASSED, 1, List.of(), null, null, null)),
                Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(
                suite,
                false,
                false,
                null,
                null,
                null,
                null,
                false,
                buildContext("api_base_url=https://api.example.com", "admin_system=IBM"));

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
                List.of(new TestCaseResult("test", TestResult.PASSED, 1, List.of(), null, null, null)),
                Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, null, null, null, null, false, buildContext());

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
                List.of(new TestCaseResult("test", TestResult.PASSED, 1, List.of(), null, null, null)),
                Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, true, false, null, null, null, null, false, buildContext());

        verify(mockEngine).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void runSuiteThrowsWhenFileDoesNotExist() {
        assertThatThrownBy(() -> command.runSuite(
                        "/nonexistent/path/suite.yml", false, false, null, null, null, null, false, buildContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Test suite file not found");
    }

    @Test
    void runSuiteWithoutSuiteArgSucceedsWhenDefaultFileExists() throws Exception {
        Path suiteSource = Path.of(getClass().getResource("/test-suite-2.yml").toURI());
        Files.copy(suiteSource, tempDir.resolve("test-suite.yml"));
        TestRunResult fakeResult = new TestRunResult(
                1,
                0,
                0L,
                0L,
                List.of(new TestCaseResult("test", TestResult.PASSED, 1, List.of(), null, null, null)),
                Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            command.runSuite(null, false, false, null, null, null, null, false, buildContext());
            verify(mockEngine).runConfigurationSuite(any(), any(), any());
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void runSuiteWithoutSuiteArgShowsErrorWhenDefaultFileMissing() throws Exception {
        // tempDir has no test-suite.yml
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        try {
            command.runSuite(null, false, false, null, null, null, null, false, ctx);
            verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
            assertThat(output.toString()).contains("test-suite.yml is not found in current directory");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void tagFilterPassesOnlyMatchingTestsToEngine() throws Exception {
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        TestRunResult fakeResult = new TestRunResult(2, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, "smoke", null, null, null, false, buildContext());

        ArgumentCaptor<TestSuite> suiteCaptor = ArgumentCaptor.forClass(TestSuite.class);
        verify(mockEngine).runConfigurationSuite(suiteCaptor.capture(), any(), any());
        assertThat(suiteCaptor.getValue().tests()).hasSize(2);
        assertThat(suiteCaptor.getValue().tests())
                .allMatch(tc -> tc.tags() != null && tc.tags().contains("smoke"));
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

        command.runSuite(suite, false, false, "nonexistent-tag", null, null, null, false, ctx);

        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
        assertThat(output.toString()).contains("No tests found with tag");
    }

    @Test
    void testNameFilterRunsOnlyMatchingTest() throws Exception {
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        TestRunResult fakeResult = new TestRunResult(1, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, null, "smoke test one", null, null, false, buildContext());

        ArgumentCaptor<TestSuite> suiteCaptor = ArgumentCaptor.forClass(TestSuite.class);
        verify(mockEngine).runConfigurationSuite(suiteCaptor.capture(), any(), any());
        assertThat(suiteCaptor.getValue().tests()).hasSize(1);
        assertThat(suiteCaptor.getValue().tests().get(0).name()).isEqualTo("smoke test one");
    }

    @Test
    void testNameFilterWithNoMatchDoesNotCallEngine() throws Exception {
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        command.runSuite(suite, false, false, null, "nonexistent test name", null, null, false, ctx);

        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
        assertThat(output.toString()).contains("No test found with name");
    }

    @Test
    void tagAndTestTogetherDoesNotCallEngine() throws Exception {
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        command.runSuite(suite, false, false, "smoke", "smoke test one", null, null, false, ctx);

        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
        assertThat(output.toString()).contains("cannot be used together");
    }

    @Test
    void tagFilterMatchesSecondTagInMultiTagList() throws Exception {
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        TestRunResult fakeResult = new TestRunResult(1, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, "regression", null, null, null, false, buildContext());

        ArgumentCaptor<TestSuite> suiteCaptor = ArgumentCaptor.forClass(TestSuite.class);
        verify(mockEngine).runConfigurationSuite(suiteCaptor.capture(), any(), any());
        assertThat(suiteCaptor.getValue().tests()).hasSize(2);
        assertThat(suiteCaptor.getValue().tests())
                .allMatch(tc -> tc.tags() != null && tc.tags().contains("regression"));
    }

    @Test
    void negatedTagFilterExcludesMatchingTestsAndIncludesUntagged() throws Exception {
        // test-suite-tagged.yml: 4 tests — smoke test one (smoke), smoke test two (smoke,regression),
        // regression test (regression), untagged test (no tags).
        // --tag="!smoke" should exclude the two smoke tests, keeping regression test + untagged test.
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        TestRunResult fakeResult = new TestRunResult(2, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, "!smoke", null, null, null, false, buildContext());

        ArgumentCaptor<TestSuite> suiteCaptor = ArgumentCaptor.forClass(TestSuite.class);
        verify(mockEngine).runConfigurationSuite(suiteCaptor.capture(), any(), any());
        assertThat(suiteCaptor.getValue().tests()).hasSize(2);
        assertThat(suiteCaptor.getValue().tests())
                .noneMatch(tc -> tc.tags() != null && tc.tags().contains("smoke"));
    }

    @Test
    void negatedTagFilterWithAllExcludedDoesNotCallEngine() throws Exception {
        // test-suite-all-slow.yml: all tests tagged "slow" — negated filter excludes all of them.
        String suite = Path.of(
                        getClass().getResource("/test-suite-all-slow.yml").toURI())
                .toString();
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        command.runSuite(suite, false, false, "!slow", null, null, null, false, ctx);

        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
        assertThat(output.toString()).contains("All tests excluded by negated tag filter");
    }

    @Test
    void uiModeNegatedTagFilterWithAllExcludedSkipsEngine() throws Exception {
        String suite = Path.of(
                        getClass().getResource("/test-suite-all-slow.yml").toURI())
                .toString();
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        commandWithUi.runSuite(suite, false, true, "!slow", null, null, null, false, ctx);

        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void reportOptionCallsGenerateAndPrintsPath() throws Exception {
        String suite =
                Path.of(getClass().getResource("/test-suite-2.yml").toURI()).toString();
        TestRunResult fakeResult = new TestRunResult(
                1,
                0,
                0L,
                0L,
                List.of(new TestCaseResult("test", TestResult.PASSED, 1, List.of(), null, null, null)),
                Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        command.runSuite(suite, false, false, null, null, tempDir.toString(), null, false, ctx);

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(mockReportGenerator).generate(any(), any(), pathCaptor.capture(), any(), any());
        String generatedFileName = pathCaptor.getValue().getFileName().toString();
        assertThat(generatedFileName).matches("test-suite_.+_\\d{14}\\.html");
        assertThat(output.toString()).contains("Test report generated at");
    }

    @Test
    void uiModeReportOptionPrintsReportPath() throws Exception {
        // In UI mode (forceUi=true) the concise summary is suppressed, but "Report written to
        // <path>" must still appear on the terminal after the TUI table completes.
        String suite =
                Path.of(getClass().getResource("/test-suite-1.yml").toURI()).toString();
        TestRunResult fakeResult = new TestRunResult(
                1,
                0,
                0L,
                0L,
                List.of(new TestCaseResult("test", TestResult.PASSED, 1, List.of(), null, null, null)),
                Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenAnswer(invocation -> {
            TestProgressListener listener = invocation.getArgument(2);
            listener.onProgress(
                    new TestProgressEvent.SuiteStarted("Test Suite for My Application v1.0", 1, Instant.now()));
            listener.onProgress(new TestProgressEvent.TestStarted("uid-0", 0, "Objects Test"));
            listener.onProgress(new TestProgressEvent.TestCompleted(
                    "uid-0", 0, "Objects Test", TestStatus.PASS, 100L, 1, List.of()));
            listener.onProgress(new TestProgressEvent.SuiteCompleted(1L, 0L, 0L, 0L, 100L));
            return fakeResult;
        });
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        commandWithUi.runSuite(suite, false, true, null, null, tempDir.toString(), null, false, ctx);

        verify(mockReportGenerator).generate(any(), any(), any(), any(), any());
        assertThat(output.toString()).contains("Report written to");
    }

    @Test
    void noReportOptionDoesNotCallGenerate() throws Exception {
        String suite =
                Path.of(getClass().getResource("/test-suite-2.yml").toURI()).toString();
        TestRunResult fakeResult = new TestRunResult(
                1,
                0,
                0L,
                0L,
                List.of(new TestCaseResult("test", TestResult.PASSED, 1, List.of(), null, null, null)),
                Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, null, null, null, null, false, buildContext());

        verify(mockReportGenerator, never()).generate(any(), any(), any(), any(), any());
    }

    @Test
    void blankTagTreatedAsNoFilter() throws Exception {
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        TestRunResult fakeResult = new TestRunResult(4, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, "", null, null, null, false, buildContext());

        ArgumentCaptor<TestSuite> suiteCaptor = ArgumentCaptor.forClass(TestSuite.class);
        verify(mockEngine).runConfigurationSuite(suiteCaptor.capture(), any(), any());
        assertThat(suiteCaptor.getValue().tests()).hasSize(4);
    }

    @Test
    void blankTestNameTreatedAsNoFilter() throws Exception {
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        TestRunResult fakeResult = new TestRunResult(4, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, null, "", null, null, false, buildContext());

        ArgumentCaptor<TestSuite> suiteCaptor = ArgumentCaptor.forClass(TestSuite.class);
        verify(mockEngine).runConfigurationSuite(suiteCaptor.capture(), any(), any());
        assertThat(suiteCaptor.getValue().tests()).hasSize(4);
    }

    @Test
    void validationErrorInNonUiModeRendersErrorAndSkipsEngine() throws Exception {
        String suite = Path.of(getClass()
                        .getResource("/test-suite-duplicate-names.yml")
                        .toURI())
                .toString();
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        command.runSuite(suite, false, false, null, null, null, null, false, ctx);

        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
        assertThat(output.toString()).contains("Duplicate test name");
    }

    @Test
    void uiModeValidationFailureRendersErrorAndSkipsEngine() throws Exception {
        String suite = Path.of(getClass()
                        .getResource("/test-suite-duplicate-names.yml")
                        .toURI())
                .toString();
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        commandWithUi.runSuite(suite, false, true, null, null, null, null, false, ctx);

        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void uiModeTagFilterWithNoMatchSkipsEngine() throws Exception {
        String suite = Path.of(getClass().getResource("/test-suite-tagged.yml").toURI())
                .toString();
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        commandWithUi.runSuite(suite, false, true, "nonexistent-tag", null, null, null, false, ctx);

        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void uiModeNormalRunCallsEngine() throws Exception {
        String suite =
                Path.of(getClass().getResource("/test-suite-1.yml").toURI()).toString();
        TestRunResult fakeResult = new TestRunResult(1, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenAnswer(invocation -> {
            TestProgressListener listener = invocation.getArgument(2);
            listener.onProgress(
                    new TestProgressEvent.SuiteStarted("Test Suite for My Application v1.0", 1, Instant.now()));
            listener.onProgress(new TestProgressEvent.TestStarted("uid-0", 0, "Objects Test"));
            listener.onProgress(new TestProgressEvent.TestCompleted(
                    "uid-0", 0, "Objects Test", TestStatus.PASS, 100L, 1, List.of()));
            listener.onProgress(new TestProgressEvent.SuiteCompleted(1L, 0L, 0L, 0L, 100L));
            return fakeResult;
        });
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        commandWithUi.runSuite(suite, false, true, null, null, null, null, false, ctx);

        verify(mockEngine).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void uiModeWithReportDirAndNullResultSkipsReportGeneration() throws Exception {
        String suite = Path.of(getClass()
                        .getResource("/test-suite-duplicate-names.yml")
                        .toURI())
                .toString();
        StringWriter output = new StringWriter();
        CommandContext ctx = new CommandContext(
                new ParsedInput("run-suite", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);

        commandWithUi.runSuite(suite, false, true, null, null, tempDir.toString(), null, false, ctx);

        verify(mockReportGenerator, never()).generate(any(), any(), any(), any(), any());
        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void viewComponentBuilderWithNoUiFlagFallsBackToNonUiMode() throws Exception {
        // noUi=true forces non-UI mode even when viewComponentBuilder is non-null.
        String suite =
                Path.of(getClass().getResource("/test-suite-1.yml").toURI()).toString();
        TestRunResult fakeResult = new TestRunResult(1, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        commandWithUi.runSuite(suite, true, false, null, null, null, null, false, buildContext());

        verify(mockEngine).runConfigurationSuite(any(), any(), any());
    }

    // ---- Non-interactive mode: exit-code and failure-summary helpers ----

    private RunSuiteCommand commandWithEnv(MockEnvironment env) {
        return new RunSuiteCommand(
                new TestSuiteLoader(),
                new TestSuiteValidator(),
                mockEngine,
                new DotEnvLoader(),
                mockReportGenerator,
                new LatestVersionHolder(),
                noOpVersionCheckProperties(),
                mock(HookRunner.class),
                null,
                env);
    }

    @Test
    void isNonInteractiveTrueWhenEnvVarTrue() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DISABLE_INTERACTIVE_MODE", "true");
        assertThat(commandWithEnv(env).isNonInteractive()).isTrue();
    }

    @Test
    void isNonInteractiveCaseInsensitive() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DISABLE_INTERACTIVE_MODE", "TRUE");
        assertThat(commandWithEnv(env).isNonInteractive()).isTrue();
    }

    @Test
    void isNonInteractiveFalseWhenAbsentOrFalse() {
        assertThat(command.isNonInteractive()).isFalse();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DISABLE_INTERACTIVE_MODE", "false");
        assertThat(commandWithEnv(env).isNonInteractive()).isFalse();
    }

    @Test
    void computeExitCodeZeroWhenAllPassed() {
        TestRunResult result = new TestRunResult(2, 0, 1, 0, List.of(), Map.of());
        assertThat(command.computeExitCode(result)).isZero();
    }

    @Test
    void computeExitCodeOneWhenAnyFailed() {
        TestRunResult result = new TestRunResult(1, 1, 0, 0, List.of(), Map.of());
        assertThat(command.computeExitCode(result)).isEqualTo(1);
    }

    @Test
    void computeExitCodeOneWhenAnyError() {
        TestRunResult result = new TestRunResult(1, 0, 0, 1, List.of(), Map.of());
        assertThat(command.computeExitCode(result)).isEqualTo(1);
    }

    @Test
    void buildConciseSummaryShowsCounts() {
        TestRunResult result = new TestRunResult(2, 1, 1, 0, List.of(), Map.of());
        String summary = command.buildConciseSummary(result, null);
        assertThat(summary).startsWith("Passed: 2, Failed: 1, Errors: 0, Skipped: 1");
    }

    @Test
    void buildConciseSummaryListsFailedTestsWithAssertionErrors() {
        TestCaseResult failed = new TestCaseResult(
                "Create Item",
                TestResult.FAILED,
                0,
                List.of(
                        new AssertionFailure("status_code equals 201", "201", "400", "Expected 201 but was 400"),
                        new AssertionFailure("not_null response.body.json.id", null, null, "Value must not be null")),
                null,
                null,
                null);
        TestCaseResult passed = new TestCaseResult("Health", TestResult.PASSED, 1, List.of(), null, null, null);
        TestRunResult result = new TestRunResult(1, 1, 0, 0, List.of(passed, failed), Map.of());

        String summary = command.buildConciseSummary(result, null);

        assertThat(summary).contains("Passed: 1, Failed: 1, Errors: 0, Skipped: 0");
        assertThat(summary).contains("Failed Tests: 1");
        assertThat(summary).contains("Create Item");
        assertThat(summary).contains("Failed assertions:");
        assertThat(summary).contains("- Expected 201 but was 400");
        assertThat(summary).contains("- Value must not be null");
        assertThat(summary).doesNotContain("status_code equals 201");
        assertThat(summary).doesNotContain("not_null response.body.json.id");
        assertThat(summary).doesNotContain("Health");
    }

    @Test
    void buildConciseSummaryFallsBackToDescriptionWhenErrorIsNull() {
        TestCaseResult failed = new TestCaseResult(
                "Net Fail",
                TestResult.FAILED,
                0,
                List.of(new AssertionFailure("Connection refused", null, null, null)),
                null,
                null,
                null);
        TestRunResult result = new TestRunResult(0, 1, 0, 0, List.of(failed), Map.of());

        String summary = command.buildConciseSummary(result, null);

        assertThat(summary).contains("Failed Tests: 1");
        assertThat(summary).contains("- Connection refused");
    }

    @Test
    void buildConciseSummaryAppendsReportPathWhenPresent() {
        TestCaseResult failed = new TestCaseResult(
                "T1", TestResult.FAILED, 0, List.of(new AssertionFailure("a", null, null, null)), null, null, null);
        TestRunResult result = new TestRunResult(0, 1, 0, 0, List.of(failed), Map.of());

        String summary = command.buildConciseSummary(result, Path.of("/tmp/report.html"));

        assertThat(summary).contains("Test report generated at");
        assertThat(summary).contains("report.html");
    }

    @Test
    void buildConciseSummaryIncludesErroredTests() {
        TestCaseResult errored = new TestCaseResult(
                "E1",
                TestResult.ERROR,
                0,
                List.of(new AssertionFailure("Connection refused", null, null, null)),
                null,
                null,
                null);
        TestRunResult result = new TestRunResult(0, 0, 0, 1, List.of(errored), Map.of());

        String summary = command.buildConciseSummary(result, null);
        assertThat(summary).contains("Failed Tests: 1");
        assertThat(summary).contains("E1");
        assertThat(summary).contains("Failed assertions:");
        assertThat(summary).contains("- Connection refused");
    }

    // ---- Non-interactive mode: process exit-code signalling ----

    /**
     * Builds a non-interactive command whose process-exit handler records the requested code into
     * {@code exitCode} instead of terminating the test JVM.
     */
    private RunSuiteCommand nonInteractiveCommand(AtomicInteger exitCode) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DISABLE_INTERACTIVE_MODE", "true");
        IntConsumer recorder = exitCode::set;
        return new RunSuiteCommand(
                new TestSuiteLoader(),
                new TestSuiteValidator(),
                mockEngine,
                new DotEnvLoader(),
                mockReportGenerator,
                new LatestVersionHolder(),
                noOpVersionCheckProperties(),
                mock(HookRunner.class),
                null,
                env,
                recorder);
    }

    @Test
    void runSuiteNonInteractiveSignalsOptionsErrorWhenSuiteFileMissing() throws Exception {
        AtomicInteger exitCode = new AtomicInteger(0);
        RunSuiteCommand cmd = nonInteractiveCommand(exitCode);

        cmd.runSuite(
                tempDir.resolve("missing.yml").toString(), true, false, null, null, null, null, false, buildContext());

        assertThat(exitCode.get()).isEqualTo(2); // EXIT_OPTIONS_ERROR
        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void runSuiteNonInteractiveSignalsOptionsErrorWhenTagAndTestCombined() throws Exception {
        AtomicInteger exitCode = new AtomicInteger(0);
        RunSuiteCommand cmd = nonInteractiveCommand(exitCode);
        String suite =
                Path.of(getClass().getResource("/test-suite-1.yml").toURI()).toString();

        cmd.runSuite(suite, true, false, "smoke", "Some Test", null, null, false, buildContext());

        assertThat(exitCode.get()).isEqualTo(2); // EXIT_OPTIONS_ERROR
        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void runSuiteNonInteractiveSignalsFailureCodeWhenTestsFail() throws Exception {
        AtomicInteger exitCode = new AtomicInteger(0);
        RunSuiteCommand cmd = nonInteractiveCommand(exitCode);
        String suite =
                Path.of(getClass().getResource("/test-suite-1.yml").toURI()).toString();
        TestRunResult failing = new TestRunResult(0, 1, 0, 0, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(failing);

        cmd.runSuite(suite, true, false, null, null, null, null, false, buildContext());

        assertThat(exitCode.get()).isEqualTo(1); // EXIT_TEST_FAILURE
    }

    // ---- --env-file option and env-file resolution order ----

    @Test
    void envFileOptionLoadsVariablesFromExplicitFile() throws Exception {
        Path envFile = tempDir.resolve("staging.env");
        Files.writeString(envFile, "ENV_MARKER=from_staging\n");
        String suite =
                Path.of(getClass().getResource("/test-suite-2.yml").toURI()).toString();
        TestRunResult fakeResult = new TestRunResult(1, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        command.runSuite(suite, false, false, null, null, null, envFile.toString(), false, buildContext());

        ArgumentCaptor<SuiteRunContext> ctxCaptor = ArgumentCaptor.forClass(SuiteRunContext.class);
        verify(mockEngine).runConfigurationSuite(any(), ctxCaptor.capture(), any());
        assertThat(ctxCaptor.getValue().env()).containsEntry("ENV_MARKER", "from_staging");
    }

    @Test
    void envFileOptionWithMissingFileThrowsInInteractiveMode() {
        String suite;
        try {
            suite = Path.of(getClass().getResource("/test-suite-2.yml").toURI()).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String missing = tempDir.resolve("does-not-exist.env").toString();

        assertThatThrownBy(
                        () -> command.runSuite(suite, false, false, null, null, null, missing, false, buildContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Env file specified via --env-file not found");
    }

    @Test
    void envFileOptionWithMissingFileSignalsOptionsErrorInNonInteractiveMode() throws Exception {
        AtomicInteger exitCode = new AtomicInteger(0);
        RunSuiteCommand cmd = nonInteractiveCommand(exitCode);
        String suite =
                Path.of(getClass().getResource("/test-suite-2.yml").toURI()).toString();
        String missing = tempDir.resolve("does-not-exist.env").toString();

        cmd.runSuite(suite, true, false, null, null, null, missing, false, buildContext());

        assertThat(exitCode.get()).isEqualTo(2); // EXIT_OPTIONS_ERROR
        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void cwdDotEnvWinsOverSuiteDirDotEnv() throws Exception {
        Path cwdDir = Files.createDirectory(tempDir.resolve("cwd"));
        Files.writeString(cwdDir.resolve(".env"), "ENV_SOURCE=cwd\n");
        Path suiteDir = Files.createDirectory(tempDir.resolve("suitedir"));
        Files.writeString(suiteDir.resolve(".env"), "ENV_SOURCE=suitedir\n");
        Path suitePath = suiteDir.resolve("test-suite.yml");
        Files.copy(Path.of(getClass().getResource("/test-suite-2.yml").toURI()), suitePath);
        TestRunResult fakeResult = new TestRunResult(1, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", cwdDir.toString());
        try {
            command.runSuite(suitePath.toString(), false, false, null, null, null, null, false, buildContext());
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        ArgumentCaptor<SuiteRunContext> ctxCaptor = ArgumentCaptor.forClass(SuiteRunContext.class);
        verify(mockEngine).runConfigurationSuite(any(), ctxCaptor.capture(), any());
        assertThat(ctxCaptor.getValue().env()).containsEntry("ENV_SOURCE", "cwd");
    }

    @Test
    void suiteDirDotEnvUsedWhenNoCwdDotEnv() throws Exception {
        Path cwdDir = Files.createDirectory(tempDir.resolve("cwd")); // no .env here
        Path suiteDir = Files.createDirectory(tempDir.resolve("suitedir"));
        Files.writeString(suiteDir.resolve(".env"), "ENV_SOURCE=suitedir\n");
        Path suitePath = suiteDir.resolve("test-suite.yml");
        Files.copy(Path.of(getClass().getResource("/test-suite-2.yml").toURI()), suitePath);
        TestRunResult fakeResult = new TestRunResult(1, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", cwdDir.toString());
        try {
            command.runSuite(suitePath.toString(), false, false, null, null, null, null, false, buildContext());
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        ArgumentCaptor<SuiteRunContext> ctxCaptor = ArgumentCaptor.forClass(SuiteRunContext.class);
        verify(mockEngine).runConfigurationSuite(any(), ctxCaptor.capture(), any());
        assertThat(ctxCaptor.getValue().env()).containsEntry("ENV_SOURCE", "suitedir");
    }

    @Test
    void noDotEnvAnywhereProceedsWithSystemEnvOnly() throws Exception {
        Path cwdDir = Files.createDirectory(tempDir.resolve("cwd")); // no .env
        Path suiteDir = Files.createDirectory(tempDir.resolve("suitedir")); // no .env
        Path suitePath = suiteDir.resolve("test-suite.yml");
        Files.copy(Path.of(getClass().getResource("/test-suite-2.yml").toURI()), suitePath);
        TestRunResult fakeResult = new TestRunResult(1, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);

        String originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", cwdDir.toString());
        try {
            command.runSuite(suitePath.toString(), false, false, null, null, null, null, false, buildContext());
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }

        // No custom marker exists, but system env is still present and the run proceeds.
        ArgumentCaptor<SuiteRunContext> ctxCaptor = ArgumentCaptor.forClass(SuiteRunContext.class);
        verify(mockEngine).runConfigurationSuite(any(), ctxCaptor.capture(), any());
        assertThat(ctxCaptor.getValue().env()).doesNotContainKey("ENV_SOURCE");
    }

    @Test
    void bareSuiteFilenameWithNoDirectoryDoesNotThrowNpe() throws Exception {
        // A bare filename (no directory component) makes suitePath.getParent() null; the command
        // must resolve the env source dir via toAbsolutePath() instead of dereferencing that null.
        Path realCwd = Path.of("").toAbsolutePath();
        Path bareSuite = realCwd.resolve("bare-suite-" + java.util.UUID.randomUUID() + ".yml");
        Files.copy(Path.of(getClass().getResource("/test-suite-2.yml").toURI()), bareSuite);
        TestRunResult fakeResult = new TestRunResult(1, 0, 0L, 0L, List.of(), Map.of());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(fakeResult);
        try {
            command.runSuite(
                    bareSuite.getFileName().toString(), false, false, null, null, null, null, false, buildContext());
            verify(mockEngine).runConfigurationSuite(any(), any(), any());
        } finally {
            Files.deleteIfExists(bareSuite);
        }
    }

    // ---- Lifecycle hooks: opt-in gate and fatal before-all ----

    private static TestRunResult passingResult() {
        return new TestRunResult(
                1,
                0,
                0L,
                0L,
                List.of(new TestCaseResult("t1", TestResult.PASSED, 0, List.of(), null, null, null)),
                Map.of());
    }

    private Path writeScriptHookSuite(Path dir, String scriptPath) throws Exception {
        String yaml = "name: hook-suite\n"
                + "rest-client:\n  base-url: http://svc.test\n"
                + "tests:\n"
                + "  - name: t1\n    variables: {}\n    request:\n      method: GET\n      url: /ok\n    assertions: []\n"
                + "hooks:\n  before-all:\n    - type: script\n      path: \""
                + scriptPath
                + "\"\n";
        Path suite = dir.resolve("hook-suite.yml");
        Files.writeString(suite, yaml);
        return suite;
    }

    private Path writeNoHookSuite(Path dir) throws Exception {
        String yaml = "name: no-hooks\n"
                + "rest-client:\n  base-url: http://svc.test\n"
                + "tests:\n  - name: t1\n    variables: {}\n    request:\n      method: GET\n      url: /ok\n"
                + "    assertions: []\n";
        Path suite = dir.resolve("no-hooks.yml");
        Files.writeString(suite, yaml);
        return suite;
    }

    private static Path execScript(Path dir, String name) throws Exception {
        Path s = dir.resolve(name);
        Files.writeString(s, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(
                s,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        return s;
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void scriptHookSuiteAbortsWithoutAllowScripts() throws Exception {
        Path suite = writeScriptHookSuite(tempDir, tempDir.resolve("seed.sh").toString());

        command.runSuite(suite.toString(), false, false, null, null, null, null, false, buildContext());

        verify(mockEngine, never()).runConfigurationSuite(any(), any(), any());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void scriptHookSuiteRunsWithAllowScriptsFlag() throws Exception {
        Path script = execScript(tempDir, "seed.sh");
        Path suite = writeScriptHookSuite(tempDir, script.toString());
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(passingResult());

        command.runSuite(suite.toString(), false, false, null, null, null, null, true, buildContext());

        verify(mockEngine).runConfigurationSuite(any(), any(), any());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void scriptHookSuiteRunsWithAllowScriptsEnvVar() throws Exception {
        Path script = execScript(tempDir, "seed.sh");
        Path suite = writeScriptHookSuite(tempDir, script.toString());
        Files.writeString(tempDir.resolve(".env"), "APITESTER_ALLOW_SCRIPTS=true\n");
        when(mockEngine.runConfigurationSuite(any(), any(), any())).thenReturn(passingResult());

        command.runSuite(suite.toString(), false, false, null, null, null, null, false, buildContext());

        verify(mockEngine).runConfigurationSuite(any(), any(), any());
    }

    @Test
    void beforeAllHookFailureExitsWithCodeThreeInNonInteractiveMode() throws Exception {
        AtomicInteger exitCode = new AtomicInteger(-1);
        RunSuiteCommand cmd = nonInteractiveCommand(exitCode);
        Path suite = writeNoHookSuite(tempDir);
        when(mockEngine.runConfigurationSuite(any(), any(), any()))
                .thenThrow(new HookFailedException("Error executing hook x: boom"));

        cmd.runSuite(suite.toString(), true, false, null, null, null, null, false, buildContext());

        assertThat(exitCode.get()).isEqualTo(3);
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
