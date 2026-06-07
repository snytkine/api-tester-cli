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
import io.github.snytkine.apitester.api_tester_cli.event.NoOpProgressListener;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.interfaces.TestEngine;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
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
import java.io.IOException;
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

    private final TestSuiteLoader testSuiteLoader;
    private final TestSuiteValidator testSuiteValidator;
    private final ObjectMapper jsonMapper;
    private final TestEngine testEngine;
    private final DotEnvLoader dotEnvLoader;
    private final HtmlReportGenerator htmlReportGenerator;

    @Nullable private final ViewComponentBuilder viewComponentBuilder;

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
     */
    public RunSuiteCommand(
            TestSuiteLoader testSuiteLoader,
            TestSuiteValidator testSuiteValidator,
            TestEngine testEngine,
            DotEnvLoader dotEnvLoader,
            HtmlReportGenerator htmlReportGenerator,
            @Nullable ViewComponentBuilder viewComponentBuilder) {
        this.testSuiteLoader = testSuiteLoader;
        this.testSuiteValidator = testSuiteValidator;
        this.testEngine = testEngine;
        this.dotEnvLoader = dotEnvLoader;
        this.htmlReportGenerator = htmlReportGenerator;
        this.jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.viewComponentBuilder = viewComponentBuilder;
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
     * @param suite absolute path to the test-suite YAML file to load
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
            @Option(longName = "suite", required = true, description = "Absolute path to the test-suite.yml file.")
                    String suite,
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

        Path suitePath = Path.of(suite);
        if (!Files.exists(suitePath)) {
            throw new IllegalArgumentException("Test suite file not found: " + suite);
        }

        boolean useUi = viewComponentBuilder != null && TtyDetector.shouldUseUi(forceUi, noUi);

        Map<String, String> cliVars = buildCliVariables(context.parsedInput().arguments());
        Map<String, String> envVars = dotEnvLoader.loadDotEnv(suitePath.getParent());
        SuiteRunContext suiteRunContext = SuiteRunContext.of(envVars, cliVars);
        TestSuite testSuite = testSuiteLoader.load(suitePath, suiteRunContext);

        boolean tagFilterActive = tag != null && !tag.isBlank();
        boolean testNameFilterActive = testName != null && !testName.isBlank();

        // Reject the combination of --tag and --test up front, before touching the suite.
        if (tagFilterActive && testNameFilterActive) {
            renderErrors(List.of("Options --tag and --test cannot be used together. Use one or the other."), context);
            return;
        }

        // Apply tag filter when --tag is supplied.
        TestSuite suiteToRun = tagFilterActive
                ? testSuite.withFilteredTests(testSuite.tests().stream()
                        .filter(tc -> tc.tags() != null && tc.tags().contains(tag))
                        .toList())
                : testSuite;

        // Apply test-name filter when --test is supplied (overrides any tag filter, but
        // mutual-exclusion above ensures they are never both active simultaneously).
        if (testNameFilterActive) {
            List<TestCase> matched = testSuite.tests().stream()
                    .filter(tc -> testName.equals(tc.name()))
                    .toList();
            if (matched.isEmpty()) {
                renderErrors(List.of("No test found with name: \"" + testName + "\""), context);
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
                uiListener.onProgress(
                        new TestProgressEvent.ValidationFailed(List.of("No tests found with tag: \"" + tag + "\"")));
            } else {
                result = testEngine.runConfigurationSuite(suiteToRun, suiteRunContext, uiListener);
            }
            controller.await();
        } else {
            List<String> validationErrors = testSuiteValidator.validate(suiteToRun);
            if (!validationErrors.isEmpty()) {
                new ErrorBox()
                        .render(
                                validationErrors,
                                TtyDetector.supportsColor(),
                                TtyDetector.getTerminalWidth(),
                                context.outputWriter());
                context.outputWriter().flush();
                return;
            }
            if (tagFilterActive && suiteToRun.tests().isEmpty()) {
                new ErrorBox()
                        .render(
                                List.of("No tests found with tag: \"" + tag + "\""),
                                TtyDetector.supportsColor(),
                                TtyDetector.getTerminalWidth(),
                                context.outputWriter());
                context.outputWriter().flush();
                return;
            }
            result = testEngine.runConfigurationSuite(suiteToRun, suiteRunContext, NoOpProgressListener.INSTANCE);
        }

        if (result != null) {
            result = result.withAppliedOptions(appliedOptions);
        }

        if (!useUi && result != null) {
            context.outputWriter().println(toJson(result));
            context.outputWriter().flush();
        }

        if (reportDir != null && result != null) {
            String safeName = suiteToRun.name().replaceAll("[^a-zA-Z0-9_-]", "_");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String fileName = "test-suite_" + safeName + "_" + timestamp + ".html";
            Path out = Path.of(reportDir).resolve(fileName);
            htmlReportGenerator.generate(result, suiteToRun, out);
            context.outputWriter().println("Report written to " + out.toAbsolutePath());
            context.outputWriter().flush();
        }
    }

    /**
     * Serializes {@code value} to an indented JSON string.
     *
     * @param value the object to serialize
     * @return pretty-printed JSON
     * @throws IOException if Jackson serialization fails
     */
    private String toJson(Object value) throws IOException {
        return jsonMapper.writeValueAsString(value);
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
