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

import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hooks;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.ScriptHook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.WebHook;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link TestSuiteValidator#validateHooks}. */
@DisabledOnOs(OS.WINDOWS)
class TestSuiteValidatorHooksTest {

    private final TestSuiteValidator validator = new TestSuiteValidator();

    private static Path execScript(Path dir, String name) throws IOException {
        Path script = dir.resolve(name);
        Files.writeString(script, "#!/bin/sh\nexit 0\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(
                script,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        return script;
    }

    private static TestSuite suite(Path dir, Hooks hooks) {
        RestClientConfig single = new RestClientConfig(null, "https://api.example.com", 30000, null, null);
        return new TestSuite("s", null, single, null, null, List.of(), hooks, dir.resolve("suite.yml"), null);
    }

    private static Hooks beforeAll(Hook... hooks) {
        return new Hooks(null, List.of(hooks), null, null, null, null, null);
    }

    @Test
    void nullOrEmptyHooksProduceNoErrors(@TempDir Path dir) {
        assertThat(validator.validateHooks(suite(dir, null))).isEmpty();
        assertThat(validator.validateHooks(suite(dir, new Hooks(null, null, null, null, null, null, null))))
                .isEmpty();
    }

    @Test
    void validExecutableScriptHookPasses(@TempDir Path dir) throws IOException {
        execScript(dir, "ok.sh");
        Hooks hooks = beforeAll(new ScriptHook(null, null, null, "ok.sh", null));
        assertThat(validator.validateHooks(suite(dir, hooks))).isEmpty();
    }

    @Test
    void missingScriptPathIsRejected(@TempDir Path dir) {
        Hooks hooks = beforeAll(new ScriptHook(null, null, null, "nope.sh", null));
        assertThat(validator.validateHooks(suite(dir, hooks))).anyMatch(e -> e.contains("does not exist"));
    }

    @Test
    void nonExecutableScriptIsRejected(@TempDir Path dir) throws IOException {
        Path script = dir.resolve("plain.sh");
        Files.writeString(script, "#!/bin/sh\n");
        Files.setPosixFilePermissions(script, EnumSet.of(PosixFilePermission.OWNER_READ));
        Hooks hooks = beforeAll(new ScriptHook(null, null, null, "plain.sh", null));
        assertThat(validator.validateHooks(suite(dir, hooks))).anyMatch(e -> e.contains("not executable"));
    }

    @Test
    void batAndCmdScriptsAreRejected(@TempDir Path dir) {
        Hooks bat = beforeAll(new ScriptHook(null, null, null, "run.BAT", null));
        Hooks cmd = beforeAll(new ScriptHook(null, null, null, "run.cmd", null));
        assertThat(validator.validateHooks(suite(dir, bat))).anyMatch(e -> e.contains(".bat/.cmd"));
        assertThat(validator.validateHooks(suite(dir, cmd))).anyMatch(e -> e.contains(".bat/.cmd"));
    }

    @Test
    void nulInPathOrParameterIsRejected(@TempDir Path dir) {
        Hooks nulPath = beforeAll(new ScriptHook(null, null, null, "x\0y.sh", null));
        assertThat(validator.validateHooks(suite(dir, nulPath))).anyMatch(e -> e.contains("NUL"));

        Hooks nulParam = beforeAll(new ScriptHook(null, null, null, "ok.sh", Map.of("k", "v\0w")));
        assertThat(validator.validateHooks(suite(dir, nulParam))).anyMatch(e -> e.contains("NUL"));
    }

    @Test
    void parameterKeyCollidingWithSystemKeyIsRejected(@TempDir Path dir) throws IOException {
        execScript(dir, "ok.sh");
        Hooks hooks = beforeAll(new ScriptHook(null, null, null, "ok.sh", Map.of("url", "x")));
        assertThat(validator.validateHooks(suite(dir, hooks))).anyMatch(e -> e.contains("reserved system key"));
    }

    @Test
    void nonPositiveTimeoutIsRejected(@TempDir Path dir) throws IOException {
        execScript(dir, "ok.sh");
        Hooks hooks = beforeAll(new ScriptHook(null, null, 0, "ok.sh", null));
        assertThat(validator.validateHooks(suite(dir, hooks))).anyMatch(e -> e.contains("timeout-seconds"));
    }

    @Test
    void webMethodMustBePostOrPut(@TempDir Path dir) {
        Hooks hooks = beforeAll(new WebHook(null, null, null, null, "/u", HttpMethod.GET, null, null));
        assertThat(validator.validateHooks(suite(dir, hooks))).anyMatch(e -> e.contains("POST or PUT"));
    }

    @Test
    void webUnknownRestClientIsRejected(@TempDir Path dir) {
        Hooks hooks = beforeAll(new WebHook(null, null, null, "ghost", "/u", HttpMethod.POST, null, null));
        assertThat(validator.validateHooks(suite(dir, hooks))).anyMatch(e -> e.contains("unknown rest-client"));
    }

    @Test
    void attachReportOutsideAfterReportIsRejected(@TempDir Path dir) {
        Hooks hooks = beforeAll(new WebHook(null, null, null, null, "/u", HttpMethod.POST, null, Boolean.TRUE));
        assertThat(validator.validateHooks(suite(dir, hooks))).anyMatch(e -> e.contains("attach-report"));
    }

    @Test
    void attachReportOnAfterReportIsAllowed(@TempDir Path dir) {
        Hooks hooks = new Hooks(
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new WebHook(null, null, null, null, "/u", HttpMethod.POST, null, Boolean.TRUE)));
        assertThat(validator.validateHooks(suite(dir, hooks))).isEmpty();
    }

    @Test
    void payloadKeyCollidingWithSystemKeyIsRejected(@TempDir Path dir) {
        Hooks hooks =
                beforeAll(new WebHook(null, null, null, null, "/u", HttpMethod.POST, Map.of("run_id", "x"), null));
        assertThat(validator.validateHooks(suite(dir, hooks))).anyMatch(e -> e.contains("reserved system key"));
    }

    @Test
    void duplicateHookIdIsRejected(@TempDir Path dir) {
        Hooks hooks = new Hooks(
                null,
                List.of(new WebHook("dup", null, null, null, "/a", HttpMethod.POST, null, null)),
                null,
                List.of(new WebHook("dup", null, null, null, "/b", HttpMethod.POST, null, null)),
                null,
                null,
                null);
        assertThat(validator.validateHooks(suite(dir, hooks))).anyMatch(e -> e.contains("duplicate hook id"));
    }
}
