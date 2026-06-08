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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.shell.core.NonInteractiveShellRunner;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.jline.JLineInputProvider;
import org.springframework.shell.jline.JLineShellRunner;

/**
 * Unit tests for {@link InteractiveModeRunnerConfiguration}.
 *
 * <p>Tests assert which {@link ShellRunner} type {@link
 * InteractiveModeRunnerConfiguration#resolveRunner} selects for various {@code
 * DISABLE_INTERACTIVE_MODE} values, without ever invoking {@link ShellRunner#run(String[])} (which
 * would start the shell). A {@link MockEnvironment} is used so the tests are isolated from the real
 * OS environment, and the collaborating Spring Shell beans are Mockito mocks. The interactive
 * {@link JLineShellRunner} is safe to construct with a mocked {@link JLineInputProvider} because
 * {@code run()} is never called; only the runner type is asserted.
 */
class InteractiveModeRunnerConfigurationTest {

    private InteractiveModeRunnerConfiguration config;
    private MockEnvironment environment;
    private JLineInputProvider jlineInputProvider;
    private CommandParser commandParser;
    private CommandRegistry commandRegistry;

    @BeforeEach
    void setUp() {
        config = new InteractiveModeRunnerConfiguration();
        environment = new MockEnvironment();
        jlineInputProvider = mock(JLineInputProvider.class);
        commandParser = mock(CommandParser.class);
        commandRegistry = mock(CommandRegistry.class);
    }

    private ShellRunner resolve() {
        return config.resolveRunner(jlineInputProvider, commandParser, commandRegistry, environment);
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
        assertThat(resolve()).isInstanceOf(JLineShellRunner.class);
    }

    @Test
    void resolveRunner_whenFlagFalse_returnsInteractive() {
        environment.setProperty(InteractiveModeRunnerConfiguration.DISABLE_INTERACTIVE_MODE, "false");
        assertThat(resolve()).isInstanceOf(JLineShellRunner.class);
    }

    @Test
    void resolveRunner_whenDebugEnabled_returnsInteractive() {
        environment.setProperty(InteractiveModeRunnerConfiguration.DEBUG_ENABLED_PROP, "true");
        assertThat(resolve()).isInstanceOf(JLineShellRunner.class);
    }

    @Test
    void springShellApplicationRunner_returnsNonNull() {
        ApplicationRunner runner =
                config.springShellApplicationRunner(jlineInputProvider, commandParser, commandRegistry, environment);
        assertThat(runner).isNotNull();
    }

    @Test
    void springShellApplicationRunner_invokesResolvedRunner() throws Exception {
        // Spy on config so resolveRunner can be stubbed without starting a real shell.
        InteractiveModeRunnerConfiguration configSpy = spy(config);
        ShellRunner mockRunner = mock(ShellRunner.class);
        doReturn(mockRunner).when(configSpy).resolveRunner(any(), any(), any(), any());

        ApplicationRunner appRunner =
                configSpy.springShellApplicationRunner(jlineInputProvider, commandParser, commandRegistry, environment);
        appRunner.run(new DefaultApplicationArguments());

        verify(mockRunner).run(new String[0]);
    }
}
