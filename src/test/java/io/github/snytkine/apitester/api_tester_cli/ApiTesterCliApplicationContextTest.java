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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.commands.VersionCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Full-context smoke test that boots the entire Spring application context.
 *
 * <p>This guards against bean-definition and command-name collisions between the application's own
 * commands and Spring Shell's built-in commands. In particular it covers the regression where the
 * application's {@link VersionCommand} (resolved to the bean name {@code versionCommand}) clashed
 * with Spring Shell's built-in {@code version} command — also named {@code versionCommand} — which
 * caused {@code APPLICATION FAILED TO START} until the built-in was disabled via {@code
 * spring.shell.command.version.enabled=false}.
 *
 * <p>The test runs in non-interactive mode ({@code DISABLE_INTERACTIVE_MODE=true}) so the shell
 * runner does not block on an interactive JLine terminal during the test run.
 */
@SpringBootTest(properties = "DISABLE_INTERACTIVE_MODE=true")
class ApiTesterCliApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    /**
     * Verifies the context starts and the single {@code versionCommand} bean is the application's
     * own {@link VersionCommand}, proving Spring Shell's built-in version command was disabled and
     * does not collide.
     */
    @Test
    void contextLoadsWithApplicationVersionCommand() {
        assertThat(context.getBeansOfType(VersionCommand.class)).hasSize(1);
        assertThat(context.getBean("versionCommand")).isInstanceOf(VersionCommand.class);
    }
}
