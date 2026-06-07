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

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * Unit tests for {@link LoggingActivatorRunner}.
 *
 * <p>The runner's sole responsibility is to call {@link
 * io.github.snytkine.apitester.api_tester_cli.util.LogFileActivator#activate()} on every
 * invocation. Because {@code LogFileActivator.activate()} reads environment variables that are not
 * set in the test JVM, it exits quickly without side effects. Tests therefore verify that {@link
 * LoggingActivatorRunner#run} completes without throwing under normal and edge conditions.
 */
class LoggingActivatorRunnerTest {

    @Test
    void run_withNullArgs_doesNotThrow() {
        LoggingActivatorRunner runner = new LoggingActivatorRunner();
        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    }

    @Test
    void run_withEmptyArgs_doesNotThrow() {
        LoggingActivatorRunner runner = new LoggingActivatorRunner();
        DefaultApplicationArguments args = new DefaultApplicationArguments();
        assertThatCode(() -> runner.run(args)).doesNotThrowAnyException();
    }

    @Test
    void run_calledMultipleTimes_doesNotThrow() {
        LoggingActivatorRunner runner = new LoggingActivatorRunner();
        assertThatCode(() -> {
                    runner.run(null);
                    runner.run(null);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void constructor_doesNotThrow() {
        assertThatCode(LoggingActivatorRunner::new).doesNotThrowAnyException();
    }
}
