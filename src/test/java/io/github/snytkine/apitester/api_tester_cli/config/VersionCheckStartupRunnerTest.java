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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.github.snytkine.apitester.api_tester_cli.service.VersionChecker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

/** Unit tests for {@link VersionCheckStartupRunner}. */
class VersionCheckStartupRunnerTest {

    private static VersionCheckProperties properties(boolean enabled) {
        return new VersionCheckProperties(
                enabled,
                "https://example.invalid",
                "https://example.invalid",
                10,
                3,
                5,
                "Version {latestVersion} is available.");
    }

    @Test
    void run_whenEnabled_invokesCheckForUpdateOnABackgroundThread() {
        VersionChecker versionChecker = mock(VersionChecker.class);
        VersionCheckStartupRunner runner = new VersionCheckStartupRunner(versionChecker, properties(true));

        runner.run(new DefaultApplicationArguments());

        verify(versionChecker, timeout(1000)).checkForUpdate();
    }

    @Test
    void run_whenDisabled_neverInvokesCheckForUpdate() {
        VersionChecker versionChecker = mock(VersionChecker.class);
        VersionCheckStartupRunner runner = new VersionCheckStartupRunner(versionChecker, properties(false));

        runner.run(new DefaultApplicationArguments());

        verify(versionChecker, never()).checkForUpdate();
    }
}
