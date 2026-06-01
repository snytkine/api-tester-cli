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
import io.github.snytkine.apitester.api_tester_cli.event.NoOpProgressListener;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.interfaces.TestEngine;
import io.github.snytkine.apitester.api_tester_cli.model.CliVariables;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.TestSuiteLoader;
import io.github.snytkine.apitester.api_tester_cli.ui.TerminalUiController;
import io.github.snytkine.apitester.api_tester_cli.ui.TerminalUiListener;
import io.github.snytkine.apitester.api_tester_cli.ui.TtyDetector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import org.jspecify.annotations.Nullable;
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

    private final TestSuiteLoader testSuiteLoader;
    private final ObjectMapper jsonMapper;
    private final TestEngine testEngine;

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
     * @param testEngine executes the loaded test cases
     * @param viewComponentBuilder Spring Shell TUI factory; {@code null} disables interactive UI
     */
    public RunSuiteCommand(
            TestSuiteLoader testSuiteLoader,
            TestEngine testEngine,
            @Nullable ViewComponentBuilder viewComponentBuilder) {
        this.testSuiteLoader = testSuiteLoader;
        this.testEngine = testEngine;
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
            CommandContext context)
            throws Exception {

        Path suitePath = Path.of(suite);
        if (!Files.exists(suitePath)) {
            throw new IllegalArgumentException("Test suite file not found: " + suite);
        }

        boolean useUi = viewComponentBuilder != null && TtyDetector.shouldUseUi(forceUi, noUi);

        Map<String, String> cliVars = buildCliVariables(context.parsedInput().arguments());
        TestSuite testSuite = testSuiteLoader.load(suitePath, new CliVariables(cliVars));

        if (useUi) {
            LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
            TerminalUiListener uiListener = new TerminalUiListener(queue);
            TerminalUiController controller = new TerminalUiController(
                    queue, TtyDetector.supportsColor(), TtyDetector.getTerminalWidth(), context.outputWriter());
            controller.start();
            testEngine.runConfigurationSuite(testSuite, uiListener);
            controller.await();
        } else {
            TestRunResult result = testEngine.runConfigurationSuite(testSuite, NoOpProgressListener.INSTANCE);
            context.outputWriter().println(toJson(result));
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
