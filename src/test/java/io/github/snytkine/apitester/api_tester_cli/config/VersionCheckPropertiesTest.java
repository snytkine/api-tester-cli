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

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Unit tests verifying that {@link VersionCheckProperties} binds correctly from {@code
 * apitester.version-check.*} properties, and that {@link
 * VersionCheckProperties#resolveUpgradeMessage(String)} substitutes the placeholder correctly.
 *
 * <p>Uses {@link ApplicationContextRunner} rather than the full application context, since only
 * configuration-properties binding is under test here.
 */
class VersionCheckPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(VersionCheckProperties.class)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsAllPropertiesFromApplicationProperties() {
        contextRunner
                .withPropertyValues(
                        "apitester.version-check.enabled=true",
                        "apitester.version-check.url=https://api.github.com/repos/o/r/releases/latest",
                        "apitester.version-check.upgrade-page-url=https://github.com/o/r/releases/latest",
                        "apitester.version-check.timeout-seconds=7",
                        "apitester.version-check.max-retries=4",
                        "apitester.version-check.retry-interval-seconds=2",
                        "apitester.version-check.upgrade-message=Update to {latestVersion} now.")
                .run(ctx -> {
                    VersionCheckProperties props = ctx.getBean(VersionCheckProperties.class);
                    assertThat(props.enabled()).isTrue();
                    assertThat(props.url()).isEqualTo("https://api.github.com/repos/o/r/releases/latest");
                    assertThat(props.upgradePageUrl()).isEqualTo("https://github.com/o/r/releases/latest");
                    assertThat(props.timeoutSeconds()).isEqualTo(7);
                    assertThat(props.maxRetries()).isEqualTo(4);
                    assertThat(props.retryIntervalSeconds()).isEqualTo(2);
                    assertThat(props.upgradeMessage()).isEqualTo("Update to {latestVersion} now.");
                });
    }

    @Test
    void enabledDefaultsFalseWhenPropertyAbsent() {
        contextRunner
                .withPropertyValues(
                        "apitester.version-check.url=https://example.invalid",
                        "apitester.version-check.upgrade-page-url=https://example.invalid",
                        "apitester.version-check.timeout-seconds=10",
                        "apitester.version-check.max-retries=3",
                        "apitester.version-check.retry-interval-seconds=5",
                        "apitester.version-check.upgrade-message=Version {latestVersion} is available.")
                .run(ctx -> {
                    VersionCheckProperties props = ctx.getBean(VersionCheckProperties.class);
                    // A bare `boolean` record component binds to false when the key is absent,
                    // matching Java's default for a primitive rather than requiring the key.
                    assertThat(props.enabled()).isFalse();
                });
    }

    @Test
    void resolveUpgradeMessage_substitutesPlaceholder() {
        VersionCheckProperties props = new VersionCheckProperties(
                true,
                "https://example.invalid",
                "https://example.invalid",
                10,
                3,
                5,
                "Version {latestVersion} is available.");
        assertThat(props.resolveUpgradeMessage("0.5.0")).isEqualTo("Version 0.5.0 is available.");
    }
}
