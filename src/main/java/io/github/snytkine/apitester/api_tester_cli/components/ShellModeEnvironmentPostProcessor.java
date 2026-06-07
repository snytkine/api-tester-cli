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
package io.github.snytkine.apitester.api_tester_cli.components;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Disables Spring Shell interactive mode when the {@code DISABLE_INTERACTIVE_MODE} environment
 * variable is set to {@code true} (case-insensitive).
 *
 * <p>This post-processor injects a high-priority {@link MapPropertySource} that sets
 * {@code spring.shell.interactive.enabled=false}, overriding any value already present in the
 * environment. It runs late in the post-processor chain ({@link Ordered#LOWEST_PRECEDENCE}) so
 * that all standard property sources — including the OS environment and application properties
 * files — have already been added and are available for reading.
 *
 * <p>Registration: this class is listed in {@code META-INF/spring.factories} under the
 * {@code org.springframework.boot.EnvironmentPostProcessor} key. That is the mechanism mandated by
 * the interface's own Javadoc: <em>"EnvironmentPostProcessor implementations have to be registered
 * in {@code META-INF/spring.factories}, using the fully qualified name of this class as the key."</em>
 * The {@code META-INF/spring/*.imports} file mechanism applies only to {@code @AutoConfiguration}
 * classes (via {@code AutoConfiguration.imports}); it is <em>not</em> consulted for
 * {@code EnvironmentPostProcessor}, whose discovery goes through
 * {@code SpringFactoriesLoader.forDefaultResourceLocation} (i.e. {@code spring.factories}).
 *
 * <p>This implements the non-deprecated {@code org.springframework.boot.EnvironmentPostProcessor}
 * (added in Spring Boot 4.0.0). The legacy {@code org.springframework.boot.env.EnvironmentPostProcessor}
 * is deprecated for removal in 4.2.0; Spring Boot still adapts it at runtime, but new code should use
 * the top-level interface.
 *
 * <p>GraalVM: Spring Boot's environment-post-processor AOT contribution discovers
 * {@code spring.factories}-registered processors at build time, so this class works in a native image
 * without an explicit {@code reflect-config.json} entry.
 *
 * <p>Thread-safety: instances are stateless and used only during application startup on the main
 * thread; thread-safety is not a concern.
 *
 * <p>Usage: set the environment variable before launching the binary:
 *
 * <pre>{@code
 * DISABLE_INTERACTIVE_MODE=true ./api-tester-cli
 * }</pre>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class ShellModeEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /**
     * Name of the OS environment variable that triggers non-interactive mode when set to {@code
     * true}.
     */
    static final String ENV_VAR = "DISABLE_INTERACTIVE_MODE";

    /** Name of the {@link MapPropertySource} injected into the environment when the flag is set. */
    static final String PROPERTY_SOURCE_NAME = "shellModeOverrides";

    /** Spring Shell property that controls whether the interactive REPL is started. */
    static final String INTERACTIVE_ENABLED_PROP = "spring.shell.interactive.enabled";

    /**
     * Checks {@code DISABLE_INTERACTIVE_MODE} and, when {@code true}, inserts a highest-priority
     * property source that forces {@code spring.shell.interactive.enabled=false}.
     *
     * @param environment the application environment to inspect and modify
     * @param application the running {@link SpringApplication} (unused)
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String flag = environment.getProperty(ENV_VAR);
        if ("true".equalsIgnoreCase(flag)) {
            environment
                    .getPropertySources()
                    .addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(INTERACTIVE_ENABLED_PROP, "false")));
        }
    }
}
