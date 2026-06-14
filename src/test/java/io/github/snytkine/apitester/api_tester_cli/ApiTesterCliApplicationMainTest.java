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
package io.github.snytkine.apitester.api_tester_cli;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.github.snytkine.apitester.api_tester_cli.config.InteractiveModeRunnerConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests {@link ApiTesterCliApplication#main(String[])} — the application entry point.
 *
 * <p>By default the shell launches an interactive JLine reader that blocks on terminal input, which
 * would hang the test indefinitely. Setting {@value
 * InteractiveModeRunnerConfiguration#DISABLE_INTERACTIVE_MODE} to {@code "true"} selects Spring
 * Shell's non-interactive runner, which executes the supplied command and returns. The flag is
 * applied as a system property because {@link InteractiveModeRunnerConfiguration} reads it through
 * the Spring {@code Environment} at runtime, which exposes system properties.
 *
 * <p>The {@link Timeout} is a safety net: should a regression ever re-enable interactive mode, the
 * test fails fast instead of hanging the whole build.
 */
class ApiTesterCliApplicationMainTest {

    /** Clears the flag after the test so it cannot leak into other tests sharing the JVM. */
    @AfterEach
    void clearInteractiveModeFlag() {
        System.clearProperty(InteractiveModeRunnerConfiguration.DISABLE_INTERACTIVE_MODE);
    }

    /**
     * Boots the full application through {@code main} in non-interactive mode and runs the {@code
     * version} command, verifying the entry point starts, executes a command, and returns cleanly
     * without throwing or hanging.
     */
    @Test
    @Timeout(value = 60, unit = SECONDS)
    void mainExecutesCommandInNonInteractiveModeWithoutHanging() {
        System.setProperty(InteractiveModeRunnerConfiguration.DISABLE_INTERACTIVE_MODE, "true");
        assertThatCode(() -> ApiTesterCliApplication.main(new String[] {"version"}))
                .doesNotThrowAnyException();
    }
}
