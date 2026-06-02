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

import io.github.snytkine.apitester.api_tester_cli.util.LogFileActivator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Spring {@link ApplicationRunner} that activates file-based logging as early as possible in the
 * application lifecycle, before any user-facing commands execute.
 *
 * <p>This runner is ordered at {@link Ordered#HIGHEST_PRECEDENCE} to ensure it runs before all
 * other {@link ApplicationRunner} implementations — in particular before Spring Shell's interactive
 * shell loop, which starts in its own {@link ApplicationRunner} at a lower precedence. As a result,
 * any log statements produced during command execution are captured in the log file.
 *
 * <p>The actual activation logic (environment variable reading, directory creation, Logback
 * configuration) lives in {@link LogFileActivator}. This class is intentionally thin: it exists
 * only to participate in the Spring lifecycle so that activation is triggered at the right moment,
 * after Spring Boot has finalised its own logging initialisation.
 *
 * <p>This class is thread-safe; it holds no mutable state and each invocation of {@link
 * #run(ApplicationArguments)} is idempotent with respect to the environment variables.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingActivatorRunner implements ApplicationRunner {

    /**
     * Constructs a {@code LoggingActivatorRunner}. Spring uses this constructor when creating the
     * managed singleton; tests may also instantiate directly.
     */
    public LoggingActivatorRunner() {}

    /**
     * Delegates to {@link LogFileActivator#activate()} to conditionally set up a log file based on
     * the {@code CLI_LOG_LEVEL} and {@code CLI_LOG_DIR} environment variables.
     *
     * <p>If the environment variables are absent or invalid, this method returns immediately with no
     * side effects.
     *
     * @param args application arguments (not used)
     */
    @Override
    public void run(ApplicationArguments args) {
        LogFileActivator.activate();
    }
}
