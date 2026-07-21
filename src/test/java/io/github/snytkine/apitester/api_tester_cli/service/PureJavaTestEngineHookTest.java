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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.snytkine.apitester.api_tester_cli.event.NoOpProgressListener;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.exception.HookFailedException;
import io.github.snytkine.apitester.api_tester_cli.model.BodylessRequest;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hooks;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.ScriptHook;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Tests for lifecycle-hook orchestration inside {@link PureJavaTestEngine}. */
@DisabledOnOs(OS.WINDOWS)
class PureJavaTestEngineHookTest {

    private PureJavaTestEngine engine() {
        StubClientHttpRequestFactory factory =
                new StubClientHttpRequestFactory().stub("/ok", 200, "{}", "application/json");
        return new PureJavaTestEngine(factory, new AssertionEvaluatorFactory(), new ResponseResolver());
    }

    private static Path script(Path dir, String name, String body) throws IOException {
        Path s = dir.resolve(name);
        Files.writeString(s, "#!/bin/sh\n" + body, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(
                s,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        return s;
    }

    private static TestCase okTest(String name) {
        return new TestCase(
                name,
                null,
                null,
                null,
                Map.of(),
                new BodylessRequest(HttpMethod.GET, "/ok", null, null, null),
                List.of());
    }

    private static TestSuite suite(Path dir, Hooks hooks, TestCase... tests) {
        RestClientConfig single = new RestClientConfig(null, "http://svc.test", 30000, null, null);
        return new TestSuite("s", null, single, null, null, List.of(tests), hooks, dir.resolve("suite.yml"), null);
    }

    @Test
    void beforeAllSuccessRunsTests(@TempDir Path dir) throws Exception {
        script(dir, "seed.sh", "exit 0\n");
        Hooks hooks = new Hooks(null, List.of(scriptHook(dir, "seed.sh")), null, null, null, null, null);
        TestRunResult result = engine().runConfigurationSuite(
                        suite(dir, hooks, okTest("t1")),
                        SuiteRunContext.of(Map.of(), Map.of()),
                        NoOpProgressListener.INSTANCE);
        assertThat(result.passedCount()).isEqualTo(1);
    }

    @Test
    void beforeAllFailureAbortsBeforeAnyTest(@TempDir Path dir) throws Exception {
        script(dir, "fail.sh", "exit 1\n");
        Hooks hooks = new Hooks(null, List.of(scriptHook(dir, "fail.sh")), null, null, null, null, null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> engine().runConfigurationSuite(
                                suite(dir, hooks, okTest("t1")), SuiteRunContext.of(Map.of(), Map.of()), events::add))
                .isInstanceOf(HookFailedException.class);

        assertThat(events).noneMatch(e -> e instanceof TestProgressEvent.SuiteStarted);
        assertThat(events).noneMatch(e -> e instanceof TestProgressEvent.TestCompleted);
    }

    @Test
    void beforeEachFailureMarksTestErrorAndSkipsRequest(@TempDir Path dir) throws Exception {
        script(dir, "guard.sh", "exit 1\n");
        Hooks hooks = new Hooks(null, null, List.of(scriptHook(dir, "guard.sh")), null, null, null, null);

        TestRunResult result = engine().runConfigurationSuite(
                        suite(dir, hooks, okTest("t1"), okTest("t2")),
                        SuiteRunContext.of(Map.of(), Map.of()),
                        NoOpProgressListener.INSTANCE);

        // Both tests error (request never sent, so they never passed), proving remaining tests still run.
        assertThat(result.errorCount()).isEqualTo(2);
        assertThat(result.passedCount()).isZero();
        assertThat(result.results().get(0).result()).isEqualTo(TestResult.ERROR);
    }

    @Test
    void afterEachRunsForEachTest(@TempDir Path dir) throws Exception {
        Path marker = dir.resolve("aftereach.txt");
        script(dir, "log.sh", "echo x >> \"" + marker + "\"\nexit 0\n");
        Hooks hooks = new Hooks(null, null, null, List.of(scriptHook(dir, "log.sh")), null, null, null);

        engine().runConfigurationSuite(
                        suite(dir, hooks, okTest("t1"), okTest("t2")),
                        SuiteRunContext.of(Map.of(), Map.of()),
                        NoOpProgressListener.INSTANCE);

        assertThat(Files.readAllLines(marker)).hasSize(2);
    }

    @Test
    void afterAllFailureIsNonFatal(@TempDir Path dir) throws Exception {
        script(dir, "teardown.sh", "exit 1\n");
        Hooks hooks = new Hooks(null, null, null, null, List.of(scriptHook(dir, "teardown.sh")), null, null);

        TestRunResult result = engine().runConfigurationSuite(
                        suite(dir, hooks, okTest("t1")),
                        SuiteRunContext.of(Map.of(), Map.of()),
                        NoOpProgressListener.INSTANCE);

        // The failing after-all does not change the result or throw.
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.errorCount()).isZero();
    }

    private static Hook scriptHook(Path dir, String name) {
        return new ScriptHook(null, null, null, dir.resolve(name).toString(), null);
    }
}
