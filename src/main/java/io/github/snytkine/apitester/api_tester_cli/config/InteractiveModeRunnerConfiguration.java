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

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.shell.core.ConsoleInputProvider;
import org.springframework.shell.core.NonInteractiveShellRunner;
import org.springframework.shell.core.ShellRunner;
import org.springframework.shell.core.SystemShellRunner;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;

/**
 * Selects Spring Shell's interactive vs non-interactive runner <em>at runtime</em>, so the choice
 * works identically whether the application runs on the JVM or as a GraalVM native image.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Spring Shell's own {@code ShellRunnerAutoConfiguration} picks the runner with
 * {@code @ConditionalOnProperty(prefix = "spring.shell.interactive", name = "enabled", ...)}: a
 * {@link SystemShellRunner} when the property is {@code true}/absent ({@code matchIfMissing = true})
 * and a {@link NonInteractiveShellRunner} when it is {@code false}. In a GraalVM native image,
 * {@code @Conditional} evaluations are performed at AOT <em>build</em> time and frozen into the
 * generated bean definitions. Because {@code DISABLE_INTERACTIVE_MODE} is not set during the build,
 * the interactive runner is baked in and no runtime environment variable can change it. This is why
 * setting {@code DISABLE_INTERACTIVE_MODE} (or even {@code SPRING_SHELL_INTERACTIVE_ENABLED}) had no
 * effect on the native binary, even though it worked on the JVM.
 *
 * <h2>How it works</h2>
 *
 * <p>Spring Shell's {@code springShellApplicationRunner} bean is declared with
 * {@code @ConditionalOnMissingBean(name = "springShellApplicationRunner")}. By contributing our own
 * {@link ApplicationRunner} bean with that exact name, Spring Shell backs off and this runner takes
 * over driving the shell. At invocation time it reads {@code DISABLE_INTERACTIVE_MODE} from the
 * {@link Environment} (a live, non-frozen lookup against the {@code systemEnvironment} property
 * source) and dispatches to the appropriate {@link ShellRunner}. The runner selection is therefore
 * a runtime decision rather than a build-time condition.
 *
 * <p>Both runners are constructed directly with {@code new} from collaborator beans that are always
 * present ({@link ConsoleInputProvider}, {@link CommandParser}, {@link CommandRegistry}). This
 * deliberately avoids depending on Spring Shell's own {@code systemShellRunner} /
 * {@code nonInteractiveShellRunner} beans, which are {@code @ConditionalOnProperty}-gated and
 * therefore not reliably generated in an AOT/native build. Constructing the runners with {@code new}
 * also keeps both classes statically reachable for native compilation (no reflection required).
 *
 * <p>Thread-safety: this configuration holds no mutable state. The produced {@link ApplicationRunner}
 * is invoked once, on the main thread, during application startup; all per-invocation values live on
 * the call stack.
 */
@Configuration
public class InteractiveModeRunnerConfiguration {

    /**
     * Environment variable (or property) that, when equal to {@code "true"} (case-insensitive),
     * forces the shell into non-interactive mode.
     */
    public static final String DISABLE_INTERACTIVE_MODE = "DISABLE_INTERACTIVE_MODE";

    /**
     * Bean name that must match Spring Shell's {@code @ConditionalOnMissingBean(name = ...)} so that
     * its default application runner backs off in favour of this one.
     */
    static final String RUNNER_BEAN_NAME = "springShellApplicationRunner";

    /** Spring Shell property controlling debug mode on the interactive runner. */
    static final String DEBUG_ENABLED_PROP = "spring.shell.debug.enabled";

    /**
     * Contributes the {@link ApplicationRunner} that drives the shell, choosing the runner at runtime.
     *
     * <p>The bean is named {@value #RUNNER_BEAN_NAME} so that Spring Shell's own
     * {@code springShellApplicationRunner} (declared {@code @ConditionalOnMissingBean(name =
     * "springShellApplicationRunner")}) is suppressed. User configuration is processed before
     * auto-configuration, so this bean is registered first and the back-off applies cleanly.
     *
     * @param consoleInputProvider the shared console input provider bean, used to build the
     *     interactive runner
     * @param commandParser the shared command parser bean, used to build either runner
     * @param commandRegistry the shared command registry bean, used to build either runner
     * @param environment the application environment, read at runtime for {@code
     *     DISABLE_INTERACTIVE_MODE}
     * @return an {@link ApplicationRunner} that dispatches to the interactive or non-interactive
     *     runner based on the runtime value of {@code DISABLE_INTERACTIVE_MODE}
     */
    @Bean(name = RUNNER_BEAN_NAME)
    public ApplicationRunner springShellApplicationRunner(
            ConsoleInputProvider consoleInputProvider,
            CommandParser commandParser,
            CommandRegistry commandRegistry,
            Environment environment) {
        return args -> {
            ShellRunner runner = resolveRunner(consoleInputProvider, commandParser, commandRegistry, environment);
            runner.run(args.getSourceArgs());
        };
    }

    /**
     * Resolves which {@link ShellRunner} to execute based on the runtime value of {@code
     * DISABLE_INTERACTIVE_MODE}.
     *
     * <p>When the variable equals {@code "true"} (case-insensitive) a freshly constructed {@link
     * NonInteractiveShellRunner} is returned. Otherwise a {@link SystemShellRunner} is constructed
     * for interactive use, with its debug mode set from {@value #DEBUG_ENABLED_PROP} (mirroring
     * Spring Shell's own configuration of that runner).
     *
     * @param consoleInputProvider the console input provider used when building the interactive runner
     * @param commandParser the command parser used when building either runner
     * @param commandRegistry the command registry used when building either runner
     * @param environment the environment read for {@code DISABLE_INTERACTIVE_MODE}
     * @return the {@link ShellRunner} to execute
     */
    ShellRunner resolveRunner(
            ConsoleInputProvider consoleInputProvider,
            CommandParser commandParser,
            CommandRegistry commandRegistry,
            Environment environment) {
        boolean disableInteractive = "true".equalsIgnoreCase(environment.getProperty(DISABLE_INTERACTIVE_MODE));
        if (disableInteractive) {
            return new NonInteractiveShellRunner(commandParser, commandRegistry);
        }
        SystemShellRunner runner = new SystemShellRunner(consoleInputProvider, commandParser, commandRegistry);
        runner.setDebugMode(environment.getProperty(DEBUG_ENABLED_PROP, Boolean.class, Boolean.FALSE));
        return runner;
    }
}
