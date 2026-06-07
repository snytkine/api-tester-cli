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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Unit tests for {@link ShellModeEnvironmentPostProcessor}.
 *
 * <p>Each test constructs a fresh {@link StandardEnvironment}, pre-populates it with a {@link
 * MapPropertySource} carrying the {@code DISABLE_INTERACTIVE_MODE} value under test, then invokes
 * the post-processor and asserts the resulting value of {@code spring.shell.interactive.enabled}.
 *
 * <p>The real OS environment and JVM system-properties sources are removed in {@link #setUp()} so
 * that the tests are deterministic regardless of whether the developer's shell (or a CI job) has
 * {@code DISABLE_INTERACTIVE_MODE} exported. Without this isolation, a real {@code
 * DISABLE_INTERACTIVE_MODE=true} would leak into {@link StandardEnvironment} and break the
 * "flag absent" / "flag false" assertions.
 */
class ShellModeEnvironmentPostProcessorTest {

    private ShellModeEnvironmentPostProcessor processor;
    private StandardEnvironment environment;

    @BeforeEach
    void setUp() {
        processor = new ShellModeEnvironmentPostProcessor();
        environment = new StandardEnvironment();
        // Isolate from the real OS environment / system properties so tests do not depend on the
        // developer's shell. Leaves an empty property-source stack the tests fully control.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    }

    /** Helper that sets {@code DISABLE_INTERACTIVE_MODE} in the test environment. */
    private void setFlag(String value) {
        environment
                .getPropertySources()
                .addLast(new MapPropertySource(
                        "test", java.util.Map.of(ShellModeEnvironmentPostProcessor.ENV_VAR, value)));
    }

    @Test
    void whenFlagIsTrue_interactiveModeIsDisabled() {
        setFlag("true");
        processor.postProcessEnvironment(environment, null);
        assertThat(environment.getProperty(ShellModeEnvironmentPostProcessor.INTERACTIVE_ENABLED_PROP))
                .isEqualTo("false");
    }

    @Test
    void whenFlagIsTrueUpperCase_interactiveModeIsDisabled() {
        setFlag("TRUE");
        processor.postProcessEnvironment(environment, null);
        assertThat(environment.getProperty(ShellModeEnvironmentPostProcessor.INTERACTIVE_ENABLED_PROP))
                .isEqualTo("false");
    }

    @Test
    void whenFlagIsMixedCase_interactiveModeIsDisabled() {
        setFlag("True");
        processor.postProcessEnvironment(environment, null);
        assertThat(environment.getProperty(ShellModeEnvironmentPostProcessor.INTERACTIVE_ENABLED_PROP))
                .isEqualTo("false");
    }

    @Test
    void whenFlagIsFalse_interactiveModeIsNotDisabled() {
        setFlag("false");
        processor.postProcessEnvironment(environment, null);
        assertThat(environment.getProperty(ShellModeEnvironmentPostProcessor.INTERACTIVE_ENABLED_PROP))
                .isNull();
    }

    @Test
    void whenFlagIsAbsent_interactiveModeIsNotDisabled() {
        processor.postProcessEnvironment(environment, null);
        assertThat(environment.getProperty(ShellModeEnvironmentPostProcessor.INTERACTIVE_ENABLED_PROP))
                .isNull();
    }

    @Test
    void whenFlagIsTrue_overridesExistingInteractiveProperty() {
        setFlag("true");
        environment
                .getPropertySources()
                .addLast(new MapPropertySource(
                        "existing",
                        java.util.Map.of(ShellModeEnvironmentPostProcessor.INTERACTIVE_ENABLED_PROP, "true")));
        processor.postProcessEnvironment(environment, null);
        assertThat(environment.getProperty(ShellModeEnvironmentPostProcessor.INTERACTIVE_ENABLED_PROP))
                .isEqualTo("false");
    }

    @Test
    void whenFlagIsTrue_propertySourceIsNamedCorrectly() {
        setFlag("true");
        processor.postProcessEnvironment(environment, null);
        assertThat(environment.getPropertySources().contains(ShellModeEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isTrue();
    }

    @Test
    void whenFlagIsAbsent_noExtraPropertySourceAdded() {
        int sourceCountBefore = environment.getPropertySources().size();
        processor.postProcessEnvironment(environment, null);
        assertThat(environment.getPropertySources().size()).isEqualTo(sourceCountBefore);
    }
}
