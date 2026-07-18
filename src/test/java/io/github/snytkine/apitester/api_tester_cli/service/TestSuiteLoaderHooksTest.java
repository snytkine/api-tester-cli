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
package io.github.snytkine.apitester.api_tester_cli.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.HookPhase;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.ScriptHook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.WebHook;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests that hook fields participate in {@link TestSuiteLoader}'s two-pass template processing. */
class TestSuiteLoaderHooksTest {

    private static final String YAML =
            """
            name: hooks-suite
            variables:
              base_url: "[[${env.BASE}]]"
            rest-client:
              base-url: http://svc.test
            tests:
              - name: t1
                variables: {}
                request:
                  method: GET
                  url: /ok
                assertions: []
            hooks:
              before-all:
                - type: script
                  path: "[[${env.SCRIPT_DIR}]]/seed.sh"
                  parameters:
                    token: "[[${env.AUTH_TOKEN}]]"
              after-each:
                - type: web
                  url: "[[${suite.base_url}]]/notify"
            """;

    @Test
    void hookTemplateExpressionsResolveDuringLoad(@TempDir Path dir) throws IOException {
        Path suiteFile = dir.resolve("suite.yml");
        Files.writeString(suiteFile, YAML, StandardCharsets.UTF_8);

        Map<String, String> env = Map.of(
                "SCRIPT_DIR", "/opt/scripts",
                "AUTH_TOKEN", "s3cr3t",
                "BASE", "https://hooks.example.com");
        TestSuite suite = new TestSuiteLoader().load(suiteFile, SuiteRunContext.of(env, Map.of()));

        assertThat(suite.hooks()).isNotNull();

        ScriptHook beforeAll =
                (ScriptHook) suite.hooks().forPhase(HookPhase.BEFORE_ALL).get(0);
        assertThat(beforeAll.path()).isEqualTo("/opt/scripts/seed.sh");
        assertThat(beforeAll.parameters()).containsEntry("token", "s3cr3t");

        // suite.* namespace (resolved from env in step 1) is available to hooks in step 2.
        WebHook afterEach =
                (WebHook) suite.hooks().forPhase(HookPhase.AFTER_EACH).get(0);
        assertThat(afterEach.url()).isEqualTo("https://hooks.example.com/notify");
    }
}
