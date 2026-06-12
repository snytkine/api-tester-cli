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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.snytkine.apitester.api_tester_cli.config.InteractiveModeRunnerConfiguration;
import io.github.snytkine.apitester.api_tester_cli.event.NoOpProgressListener;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.interfaces.TestEngine;
import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import io.github.snytkine.apitester.api_tester_cli.model.ReportOptions;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.HtmlReportGenerator;
import io.github.snytkine.apitester.api_tester_cli.service.TestSuiteLoader;
import io.github.snytkine.apitester.api_tester_cli.service.TestSuiteValidator;
import io.github.snytkine.apitester.api_tester_cli.ui.ErrorBox;
import io.github.snytkine.apitester.api_tester_cli.ui.TerminalUiController;
import io.github.snytkine.apitester.api_tester_cli.ui.TerminalUiListener;
import io.github.snytkine.apitester.api_tester_cli.ui.TtyDetector;
import io.github.snytkine.apitester.api_tester_cli.util.DotEnvLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.shell.core.command.CommandArgument;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.shell.jline.tui.component.ViewComponentBuilder;
import org.springframework.stereotype.Component;

/**
 * Spring Shell command group that exposes test-suite execution commands.
 *
 * <p>Registered as a Spring component so that the Spring Shell annotation command model picks it up
 * automatically via classpath scanning.
 */
@Component
public class RunSuiteCommand {

    private static final Logger log = LoggerFactory.getLogger(RunSuiteCommand.class);

    /** Process exit code when one or more test cases fail or error. */
    static final int EXIT_TEST_FAILURE = 1;

    /** Process exit code for a command-options / pre-execution validation error. */
    static final int EXIT_OPTIONS_ERROR = 2;

    private final TestSuiteLoader testSuiteLoader;
    private final TestSuiteValidator testSuiteValidator;
    private final ObjectMapper jsonMapper;
    private final TestEngine testEngine;
    private final DotEnvLoader dotEnvLoader;
    private final HtmlReportGenerator htmlReportGenerator;

    @Nullable private final ViewComponentBuilder viewComponentBuilder;

    private final Environment environment;

    /**
     * Constructs the command with its required collaborators. A dedicated JSON {@link ObjectMapper}
     * is created here rather than injected; Jackson is not auto-configured as a bean in this CLI
     * project and the mapper is an internally-owned, thread-safe singleton.
     *
     * <p>When {@code viewComponentBuilder} is {@code null} (e.g. in unit tests that do not have the
     * full Spring Shell TUI stack on the classpath), UI mode is disabled regardless of TTY detection.
     *
     * @param testSuiteLoader loads and template-processes test-suite YAML files
     * @param testSuiteValidator validates the loaded suite for structural errors such as duplicate
     *     test names
     * @param testEngine executes the loaded test cases
     * @param dotEnvLoader loads environment variables from the suite directory's {@code .env} file
     * @param htmlReportGenerator renders the post-run HTML report when {@code --report} is supplied
     * @param viewComponentBuilder Spring Shell TUI factory; {@code null} disables interactive UI
     * @param environment the application environment, read at runtime to detect non-interactive mode
     *     via {@link InteractiveModeRunnerConfiguration#DISABLE_INTERACTIVE_MODE}
     */
    public RunSuiteCommand(
            TestSuiteLoader testSuiteLoader,
            TestSuiteValidator testSuiteValidator,
            TestEngine testEngine,
            DotEnvLoader dotEnvLoader,
            HtmlReportGenerator htmlReportGenerator,
            @Nullable ViewComponentBuilder viewComponentBuilder,
            Environment environment) {
        this.testSuiteLoader = testSuiteLoader;
        this.testSuiteValidator = testSuiteValidator;
        this.testEngine = testEngine;
        this.dotEnvLoader = dotEnvLoader;
        this.htmlReportGenerator = htmlReportGenerator;
        this.jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.viewComponentBuilder = viewComponentBuilder;
        this.environment = environment;
    }

    /**
     * Returns whether the application is running in non-interactive mode, i.e. the {@code
     * DISABLE_INTERACTIVE_MODE} environment variable / property equals {@code "true"}
     * (case-insensitive). In this mode the command suppresses all terminal output (no interactive UI,
     * no JSON dump, no status messages), writing only an optional report file and — on failure — a
     * short summary to stderr, and signals the outcome through the process exit code.
     *
     * @return {@code true} when non-interactive (silent) mode is active
     */
    boolean isNonInteractive() {
        return "true"
                .equalsIgnoreCase(environment.getProperty(InteractiveModeRunnerConfiguration.DISABLE_INTERACTIVE_MODE));
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
     * <p>Output mode selection (evaluated in order):
     *
     * <ol>
     *   <li>If {@code --ui} is supplied, the interactive terminal UI is activated regardless of
     *       environment detection.
     *   <li>If {@code --no-ui} is supplied, JSON output is used even when a TTY is detected.
     *   <li>Otherwise {@link TtyDetector#shouldUseUi(boolean, boolean)} decides based on the attached
     *       terminal, {@code NO_COLOR}, {@code CI}, and terminal width.
     * </ol>
     *
     * <p>In UI mode (Phase 4+) the aggregated JSON is not written to stdout; use {@code --output} to
     * obtain the structured report as a file. In non-UI mode the JSON is always written to stdout.
     *
     * @param suite path to the test-suite YAML file to load; when {@code null} the command looks for
     *     {@code test-suite.yml} in the current working directory ({@code user.dir}) and reports an
     *     error if that file is not found
     * @param noUi when {@code true}, forces JSON output even on a TTY
     * @param forceUi when {@code true}, forces the interactive UI even when not on a TTY
     * @param tag when non-blank, only test cases whose {@code tags} list contains this value are
     *     executed; a no-match condition surfaces an {@link ErrorBox} and aborts the run; mutually
     *     exclusive with {@code testName}
     * @param testName when non-blank, only the single test case whose {@code name} exactly matches
     *     this value is executed; a no-match condition surfaces an {@link ErrorBox} and aborts the
     *     run; mutually exclusive with {@code tag}; use double quotes if the name contains spaces:
     *     {@code --test="My Test Name"}
     * @param reportDir when non-null, the directory path where the HTML execution report will be
     *     written; the filename is auto-generated as
     *     {@code test-suite_<name>_yyyyMMddHHmmss.html}
     * @param context Spring Shell command context; positional arguments are extracted from it as CLI
     *     variables forwarded to the Thymeleaf template engine
     * @throws IllegalArgumentException if {@code suite} does not refer to an existing file
     * @throws Exception if loading, template processing, or test execution fails
     */
    @Command(
            name = "run-suite",
            alias = {"rs"},
            description = "Loads a test-suite YAML file and executes its test cases."
                    + " Pass variables as key=value positional arguments after --suite.")
    public void runSuite(
            @Option(
                            longName = "suite",
                            required = false,
                            description = "Path to the test-suite.yml file."
                                    + " When omitted, test-suite.yml in the current working directory is used.")
                    @Nullable String suite,
            @Option(longName = "no-ui", description = "Disable the interactive terminal UI and write JSON to stdout.")
                    boolean noUi,
            @Option(
                            longName = "ui",
                            description =
                                    "Force the interactive terminal UI even when stdout does not look like a TTY.")
                    boolean forceUi,
            @Option(
                            longName = "tag",
                            description = "Run only test cases whose tag matches this value."
                                    + " Mutually exclusive with --test.")
                    @Nullable String tag,
            @Option(
                            longName = "test",
                            description = "Run only the single test case whose name matches this value exactly."
                                    + " Use double quotes if the name contains spaces:"
                                    + " --test=\"My Test Name\". Mutually exclusive with --tag.")
                    @Nullable String testName,
            @Option(
                            longName = "report",
                            description = "Directory where the HTML execution report will be written."
                                    + " The filename is auto-generated as"
                                    + " test-suite_<name>_yyyyMMddHHmmss.html.")
                    @Nullable String reportDir,
            CommandContext context)
            throws Exception {

        boolean nonInteractive = isNonInteractive();

        if (suite == null) {
            Path defaultSuite = Path.of(System.getProperty("user.dir"), "test-suite.yml");
            if (Files.exists(defaultSuite)) {
                suite = defaultSuite.toString();
            } else {
                reportOptionsError(
                        List.of("test-suite.yml is not found in current directory"
                                + " and path to alternate configuration file not passed in --suite"
                                + " command line argument. Type help to display available options"),
                        nonInteractive,
                        context);
                return;
            }
        }

        Path suitePath = Path.of(suite);
        if (!Files.exists(suitePath)) {
            if (nonInteractive) {
                log.debug("Options error (exit {}): test suite file not found: {}", EXIT_OPTIONS_ERROR, suite);
                System.exit(EXIT_OPTIONS_ERROR);
            }
            throw new IllegalArgumentException("Test suite file not found: " + suite);
        }

        // In non-interactive mode the interactive UI is always suppressed, regardless of TTY.
        boolean useUi = !nonInteractive && viewComponentBuilder != null && TtyDetector.shouldUseUi(forceUi, noUi);

        Map<String, String> cliVars = buildCliVariables(context.parsedInput().arguments());
        Map<String, String> envVars = dotEnvLoader.loadDotEnv(suitePath.getParent());
        SuiteRunContext suiteRunContext = SuiteRunContext.of(envVars, cliVars);
        TestSuite testSuite = testSuiteLoader.load(suitePath, suiteRunContext);

        boolean tagFilterActive = tag != null && !tag.isBlank();
        boolean testNameFilterActive = testName != null && !testName.isBlank();

        // Detect negated tag: --tag="!slow" excludes tests tagged "slow" instead of including them.
        boolean negatedTagFilter = tagFilterActive && tag.startsWith("!");
        String effectiveTag = negatedTagFilter ? tag.substring(1) : tag;
        log.debug(
                "Tag filter: active={}, negated={}, effectiveTag={}", tagFilterActive, negatedTagFilter, effectiveTag);

        // Reject the combination of --tag and --test up front, before touching the suite.
        if (tagFilterActive && testNameFilterActive) {
            reportOptionsError(
                    List.of("Options --tag and --test cannot be used together. Use one or the other."),
                    nonInteractive,
                    context);
            return;
        }

        // Apply tag filter when --tag is supplied.
        // Positive filter (--tag=smoke): include only tests whose tags contain the value.
        // Negated filter (--tag=!slow): include tests whose tags do NOT contain the value,
        // plus tests with no tags at all.
        TestSuite suiteToRun = tagFilterActive
                ? testSuite.withFilteredTests(testSuite.tests().stream()
                        .filter(tc -> negatedTagFilter
                                ? tc.tags() == null || !tc.tags().contains(effectiveTag)
                                : tc.tags() != null && tc.tags().contains(effectiveTag))
                        .toList())
                : testSuite;

        // Apply test-name filter when --test is supplied (overrides any tag filter, but
        // mutual-exclusion above ensures they are never both active simultaneously).
        if (testNameFilterActive) {
            List<TestCase> matched = testSuite.tests().stream()
                    .filter(tc -> testName.equals(tc.name()))
                    .toList();
            if (matched.isEmpty()) {
                reportOptionsError(List.of("No test found with name: \"" + testName + "\""), nonInteractive, context);
                return;
            }
            suiteToRun = testSuite.withFilteredTests(matched);
        }

        // Build the applied-options map for JSON output.
        Map<String, String> appliedOptions = new LinkedHashMap<>();
        if (tagFilterActive) appliedOptions.put("tag", tag);
        if (testNameFilterActive) appliedOptions.put("test", testName);
        if (log.isDebugEnabled()) {
            try {
                log.debug("appliedOptions: {}", jsonMapper.writeValueAsString(appliedOptions));
            } catch (JsonProcessingException e) {
                log.debug("appliedOptions: {}", appliedOptions);
            }
        }

        TestRunResult result = null;
        if (useUi) {
            LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
            TerminalUiListener uiListener = new TerminalUiListener(queue);
            TerminalUiController controller = new TerminalUiController(
                    queue,
                    TtyDetector.supportsColor(),
                    TtyDetector.getTerminalWidth(),
                    context.outputWriter(),
                    tagFilterActive ? tag : null,
                    testNameFilterActive ? testName : null);
            controller.start();
            List<String> validationErrors = testSuiteValidator.validate(suiteToRun);
            if (!validationErrors.isEmpty()) {
                uiListener.onProgress(new TestProgressEvent.ValidationFailed(validationErrors));
            } else if (tagFilterActive && suiteToRun.tests().isEmpty()) {
                String emptyMsg = negatedTagFilter
                        ? "All tests excluded by negated tag filter: \"" + tag + "\""
                        : "No tests found with tag: \"" + tag + "\"";
                uiListener.onProgress(new TestProgressEvent.ValidationFailed(List.of(emptyMsg)));
            } else {
                result = testEngine.runConfigurationSuite(suiteToRun, suiteRunContext, uiListener);
            }
            controller.await();
        } else {
            List<String> validationErrors = testSuiteValidator.validate(suiteToRun);
            if (!validationErrors.isEmpty()) {
                reportOptionsError(validationErrors, nonInteractive, context);
                return;
            }
            if (tagFilterActive && suiteToRun.tests().isEmpty()) {
                String emptyMsg = negatedTagFilter
                        ? "All tests excluded by negated tag filter: \"" + tag + "\""
                        : "No tests found with tag: \"" + tag + "\"";
                reportOptionsError(List.of(emptyMsg), nonInteractive, context);
                return;
            }
            result = testEngine.runConfigurationSuite(suiteToRun, suiteRunContext, NoOpProgressListener.INSTANCE);
        }

        if (result != null) {
            result = result.withAppliedOptions(appliedOptions);
        }

        Path reportPath = null;
        if (reportDir != null && result != null) {
            String safeName = suiteToRun.name().replaceAll("[^a-zA-Z0-9_-]", "_");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String fileName = "test-suite_" + safeName + "_" + timestamp + ".html";
            reportPath = Path.of(reportDir).resolve(fileName);
            ReportOptions reportOptions = ReportOptions.fromEnv(envVars);
            htmlReportGenerator.generate(result, suiteToRun, reportPath, reportOptions);
        }

        // Output mode: concise human-readable summary to stdout when --no-ui is passed or in
        // non-interactive mode.
        if (!useUi && result != null) {
            context.outputWriter().println(buildConciseSummary(result, reportPath));
            context.outputWriter().flush();
        }

        // In UI mode the concise summary is suppressed, but the user still needs to know where
        // the report landed — print just that line after the TUI table completes.
        if (useUi && reportPath != null) {
            context.outputWriter().println("Report written to " + reportPath.toAbsolutePath());
            context.outputWriter().flush();
        }

        // Non-interactive mode: signal the outcome via process exit code. On failure, return exit code
        // 1; on success return 0 (which causes normal exit). Options errors already exited with code 2.
        if (nonInteractive && result != null) {
            int exitCode = computeExitCode(result);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        }
    }

    /**
     * Handles a pre-execution options/validation error.
     *
     * <p>In non-interactive (silent) mode the process is terminated immediately with {@link
     * #EXIT_OPTIONS_ERROR} and the messages are written only to the debug log (no terminal output).
     * Otherwise the messages are rendered as an {@link ErrorBox} to the command output writer.
     *
     * @param messages the non-empty list of error messages describing the problem
     * @param nonInteractive whether non-interactive (silent) mode is active
     * @param context the command context whose output writer receives rendered output
     */
    private void reportOptionsError(List<String> messages, boolean nonInteractive, CommandContext context) {
        if (nonInteractive) {
            log.debug("Options error (exit {}): {}", EXIT_OPTIONS_ERROR, messages);
            System.exit(EXIT_OPTIONS_ERROR);
        }
        renderErrors(messages, context);
    }

    /**
     * Computes the process exit code for a completed run: {@link #EXIT_TEST_FAILURE} when any test
     * case failed or errored, otherwise {@code 0}. Skipped tests do not affect the exit code.
     *
     * @param result the aggregated run result
     * @return {@code 1} if there were failures or errors, {@code 0} otherwise
     */
    int computeExitCode(TestRunResult result) {
        return (result.failedCount() > 0 || result.errorCount() > 0) ? EXIT_TEST_FAILURE : 0;
    }

    /**
     * Builds a concise, human-readable summary of test results for stdout.
     *
     * <p>Format:
     *
     * <ul>
     *   <li>First line: summary counts (passed, failed, errors, skipped)
     *   <li>When any tests failed or errored: "Failed Tests: N" on the next line, followed by one
     *       block per failing test containing the test name, a "Failed assertions:" label, and each
     *       assertion's error message indented (falls back to the assertion description when the error
     *       message is absent, e.g. for network-level failures)
     *   <li>Report path (if generated): "Test report generated at &lt;path&gt;"
     * </ul>
     *
     * <p>Example:
     *
     * <pre>
     * Passed: 1, Failed: 1, Errors: 0, Skipped: 0
     * Failed Tests: 1
     *
     * Objects Test
     * Failed assertions:
     *   - Expected status 201 but was 400
     *   - Field response.body.json.id must not be null
     *
     * Test report generated at /tmp/report.html
     * </pre>
     *
     * @param result the aggregated run result
     * @param reportPath the path to the generated HTML report, or {@code null} if none was written
     * @return the formatted summary for stdout
     */
    String buildConciseSummary(TestRunResult result, @Nullable Path reportPath) {
        StringBuilder sb = new StringBuilder();

        // Summary counts line
        sb.append(String.format(
                "Passed: %d, Failed: %d, Errors: %d, Skipped: %d",
                result.passedCount(), result.failedCount(), result.errorCount(), result.skippedCount()));

        // Failed/errored test details
        List<TestCaseResult> failures = result.results().stream()
                .filter(r -> r.result() == TestResult.FAILED || r.result() == TestResult.ERROR)
                .toList();

        if (!failures.isEmpty()) {
            sb.append("\nFailed Tests: ").append(failures.size());
            for (TestCaseResult failure : failures) {
                sb.append("\n\n").append(failure.name()).append("\n");
                sb.append("Failed assertions:\n");
                for (AssertionFailure af : failure.failures()) {
                    String message = af.error() != null ? af.error() : af.description();
                    sb.append("  - ").append(message).append("\n");
                }
            }
        }

        // Report path
        if (reportPath != null) {
            sb.append("\nTest report generated at ").append(reportPath.toAbsolutePath());
        }

        return sb.toString();
    }

    /**
     * Routes pre-run error messages to the appropriate output channel.
     *
     * <p>In UI mode ({@code useUi = true}) the controller has not been started yet, so errors are
     * rendered directly via {@link ErrorBox} to the output writer. In non-UI mode the same {@link
     * ErrorBox} is used directly. This helper avoids duplicating the routing logic at every early-
     * exit point.
     *
     * @param errors the non-empty list of error messages to display
     * @param useUi whether the interactive UI mode is active for this run
     * @param context the command context whose output writer receives the rendered output
     */
    private static void renderErrors(List<String> errors, CommandContext context) {
        new ErrorBox()
                .render(errors, TtyDetector.supportsColor(), TtyDetector.getTerminalWidth(), context.outputWriter());
        context.outputWriter().flush();
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
