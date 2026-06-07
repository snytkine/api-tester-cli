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
package io.github.snytkine.apitester.api_tester_cli.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.shell.core.ConsoleInputProvider;
import org.springframework.shell.core.NonInteractiveShellRunner;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.SystemShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;

/**
 * Unit tests for {@link InteractiveModeRunnerConfiguration}.
 *
 * <p>Tests assert which {@link ShellRunner} type {@link
 * InteractiveModeRunnerConfiguration#resolveRunner} selects for various {@code
 * DISABLE_INTERACTIVE_MODE} values, without ever invoking {@link ShellRunner#run(String[])} (which
 * would start the shell). A {@link MockEnvironment} is used so the tests are isolated from the real
 * OS environment, and the collaborating Spring Shell beans are Mockito mocks. The interactive
 * {@link SystemShellRunner} is safe to construct here because its constructor merely stores the
 * (mocked) console.
 */
class InteractiveModeRunnerConfigurationTest {

    private InteractiveModeRunnerConfiguration config;
    private MockEnvironment environment;
    private ConsoleInputProvider consoleInputProvider;
    private CommandParser commandParser;
    private CommandRegistry commandRegistry;

    @BeforeEach
    void setUp() {
        config = new InteractiveModeRunnerConfiguration();
        environment = new MockEnvironment();
        consoleInputProvider = mock(ConsoleInputProvider.class);
        commandParser = mock(CommandParser.class);
        commandRegistry = mock(CommandRegistry.class);
    }

    private ShellRunner resolve() {
        return config.resolveRunner(consoleInputProvider, commandParser, commandRegistry, environment);
    }

    @Test
    void resolveRunner_whenFlagTrue_returnsNonInteractive() {
        environment.setProperty(InteractiveModeRunnerConfiguration.DISABLE_INTERACTIVE_MODE, "true");
        assertThat(resolve()).isInstanceOf(NonInteractiveShellRunner.class);
    }

    @Test
    void resolveRunner_whenFlagTrueUpperCase_returnsNonInteractive() {
        environment.setProperty(InteractiveModeRunnerConfiguration.DISABLE_INTERACTIVE_MODE, "TRUE");
        assertThat(resolve()).isInstanceOf(NonInteractiveShellRunner.class);
    }

    @Test
    void resolveRunner_whenFlagAbsent_returnsInteractive() {
        assertThat(resolve()).isInstanceOf(SystemShellRunner.class);
    }

    @Test
    void resolveRunner_whenFlagFalse_returnsInteractive() {
        environment.setProperty(InteractiveModeRunnerConfiguration.DISABLE_INTERACTIVE_MODE, "false");
        assertThat(resolve()).isInstanceOf(SystemShellRunner.class);
    }

    @Test
    void resolveRunner_whenDebugEnabled_returnsInteractive() {
        environment.setProperty(InteractiveModeRunnerConfiguration.DEBUG_ENABLED_PROP, "true");
        assertThat(resolve()).isInstanceOf(SystemShellRunner.class);
    }

    @Test
    void springShellApplicationRunner_returnsNonNull() {
        ApplicationRunner runner =
                config.springShellApplicationRunner(consoleInputProvider, commandParser, commandRegistry, environment);
        assertThat(runner).isNotNull();
    }
}
