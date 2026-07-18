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
package io.github.snytkine.apitester.api_tester_cli.service.hooks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.HookPhase;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.ScriptHook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.WebHook;
import io.github.snytkine.apitester.api_tester_cli.service.StubClientHttpRequestFactory;
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

/** Unit tests for {@link HookRunner}. */
class HookRunnerTest {

    private final HookRunner runner = new HookRunner(
            new ScriptHookExecutor(),
            new WebHookExecutor(new StubClientHttpRequestFactory().stub("hook", 200, "{}", "application/json")));

    private static HookRunner.HookInvocationContext ctx(Path suiteDir) {
        return new HookRunner.HookInvocationContext(
                "My Suite",
                "run-123",
                false,
                null,
                null,
                null,
                null,
                null,
                suiteDir,
                Map.of(),
                Map.of(TestSuite.DEFAULT_REST_CLIENT_ID, RestClientConfig.withDefaults(null)));
    }

    private static Path writeScript(Path dir, String name, String body) throws IOException {
        Path script = dir.resolve(name);
        Files.writeString(script, "#!/bin/sh\n" + body, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(
                script,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        return script;
    }

    // ---- maskHeaders ----

    @Test
    void maskHeadersMasksSensitiveNamesOnly() {
        Map<String, String> masked = HookRunner.maskHeaders(Map.of(
                "Authorization", "Bearer abc",
                "Cookie", "sid=1",
                "X-Api-Key", "k",
                "X-Auth-Token", "t",
                "Content-Type", "application/json"));
        assertThat(masked)
                .containsEntry("Authorization", HookRunner.MASK)
                .containsEntry("Cookie", HookRunner.MASK)
                .containsEntry("X-Api-Key", HookRunner.MASK)
                .containsEntry("X-Auth-Token", HookRunner.MASK)
                .containsEntry("Content-Type", "application/json");
    }

    // ---- buildScalarArgs ----

    @Test
    void scalarArgsAlwaysIncludeCoreKeys() {
        Hook hook = new ScriptHook(null, null, null, "s.sh", null);
        Map<String, String> args =
                runner.buildScalarArgs(HookPhase.BEFORE_ALL, hook, "before-all-1", ctx(null), null, null);
        assertThat(args)
                .containsEntry("suite_name", "My Suite")
                .containsEntry("run_id", "run-123")
                .containsEntry("hook_id", "before-all-1")
                .containsEntry("phase", "before-all")
                .containsEntry("interactive", "false")
                .containsEntry("timeout_seconds", "10");
        assertThat(args).doesNotContainKeys("url", "method", "test_status", "report_path");
    }

    @Test
    void scalarArgsForBeforeEachIncludeUrlAndMethodButNotStatus() {
        Hook hook = new ScriptHook(null, null, null, "s.sh", null);
        HookRunner.PerTestData perTest =
                new HookRunner.PerTestData("t1", "http://x/y", HttpMethod.GET, Map.of(), null, null);
        Map<String, String> args =
                runner.buildScalarArgs(HookPhase.BEFORE_EACH, hook, "before-each-1", ctx(null), perTest, null);
        assertThat(args)
                .containsEntry("test_name", "t1")
                .containsEntry("url", "http://x/y")
                .containsEntry("method", "GET");
        assertThat(args).doesNotContainKey("test_status");
    }

    @Test
    void scalarArgsForAfterEachIncludeStatus() {
        Hook hook = new ScriptHook(null, null, null, "s.sh", null);
        HookRunner.PerTestData perTest =
                new HookRunner.PerTestData("t1", "http://x/y", HttpMethod.POST, Map.of(), null, "passed");
        Map<String, String> args =
                runner.buildScalarArgs(HookPhase.AFTER_EACH, hook, "after-each-1", ctx(null), perTest, null);
        assertThat(args).containsEntry("test_status", "passed");
    }

    @Test
    void scalarArgsForAfterAllIncludeSummaryAndReportPath() {
        Hook hook = new ScriptHook(null, null, null, "s.sh", null);
        HookRunner.HookInvocationContext base = ctx(null);
        HookRunner.HookInvocationContext withReport = new HookRunner.HookInvocationContext(
                base.suiteName(),
                base.runId(),
                base.interactive(),
                "/tmp/reports",
                Path.of("/tmp/reports/r.html"),
                "smoke",
                null,
                Path.of("/env/.env"),
                null,
                Map.of(),
                base.restClientsById());
        HookRunner.SummaryData summary = new HookRunner.SummaryData(4, 3, 1, 0, 250);
        Map<String, String> args =
                runner.buildScalarArgs(HookPhase.AFTER_ALL, hook, "after-all-1", withReport, null, summary);
        assertThat(args)
                .containsEntry("report_dir", "/tmp/reports")
                .containsEntry("report_path", "/tmp/reports/r.html")
                .containsEntry("tag", "smoke")
                .containsEntry("env_file", Path.of("/env/.env").toString())
                .containsEntry("tests_total", "4")
                .containsEntry("tests_passed", "3")
                .containsEntry("tests_failed", "1")
                .containsEntry("tests_errors", "0")
                .containsEntry("duration_ms", "250");
    }

    // ---- runPhase ----

    @Test
    void emptyPhaseFiresNoEventsAndSucceeds() {
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            HookRunner.HookPhaseOutcome outcome =
                    runner.runPhase(HookPhase.BEFORE_ALL, List.of(), ctx(null), null, null, events::add, handles);
            assertThat(outcome.allSucceeded()).isTrue();
        }
        assertThat(events).isEmpty();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void syncScriptSuccessFiresEventsAndSucceeds(@TempDir Path dir) throws IOException {
        Path script = writeScript(dir, "ok.sh", "exit 0\n");
        Hook hook = new ScriptHook("seed", null, null, script.toString(), null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            HookRunner.HookPhaseOutcome outcome =
                    runner.runPhase(HookPhase.BEFORE_ALL, List.of(hook), ctx(dir), null, null, events::add, handles);
            assertThat(outcome.allSucceeded()).isTrue();
        }

        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(TestProgressEvent.HookPhaseStarted.class);
        assertThat(events.get(1)).isInstanceOf(TestProgressEvent.HookCompleted.class);
        TestProgressEvent.HookCompleted completed = (TestProgressEvent.HookCompleted) events.get(1);
        assertThat(completed.success()).isTrue();
        assertThat(completed.hookId()).isEqualTo("seed");
        assertThat(events.get(2)).isInstanceOf(TestProgressEvent.HookPhaseCompleted.class);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void syncFailureSkipsRemainingHooksInPhase(@TempDir Path dir) throws IOException {
        Path failing = writeScript(dir, "fail.sh", "exit 3\n");
        Path marker = dir.resolve("ran.txt");
        Path second = writeScript(dir, "second.sh", "touch \"" + marker + "\"\nexit 0\n");
        Hook h1 = new ScriptHook("first", null, null, failing.toString(), null);
        Hook h2 = new ScriptHook("second", null, null, second.toString(), null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        HookRunner.HookPhaseOutcome outcome;
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            outcome =
                    runner.runPhase(HookPhase.BEFORE_ALL, List.of(h1, h2), ctx(dir), null, null, events::add, handles);
        }

        assertThat(outcome.allSucceeded()).isFalse();
        assertThat(outcome.firstFailureMessage()).contains("Before All hook 'first'");
        assertThat(Files.exists(marker)).isFalse();
        // Only one HookCompleted (the failing first hook); the second was skipped.
        assertThat(events.stream().filter(e -> e instanceof TestProgressEvent.HookCompleted))
                .hasSize(1);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void asyncHookDoesNotBlockAndIsAwaited(@TempDir Path dir) throws IOException {
        Path marker = dir.resolve("async.txt");
        Path script = writeScript(dir, "async.sh", "sleep 1\ntouch \"" + marker + "\"\nexit 0\n");
        Hook hook = new ScriptHook("bg", true, null, script.toString(), null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            HookRunner.HookPhaseOutcome outcome =
                    runner.runPhase(HookPhase.AFTER_ALL, List.of(hook), ctx(dir), null, null, events::add, handles);
            // Returns immediately; the async marker file is not created yet.
            assertThat(outcome.allSucceeded()).isTrue();
            handles.awaitAll();
        }

        assertThat(Files.exists(marker)).isTrue();
        assertThat(events.stream().anyMatch(e -> e instanceof TestProgressEvent.HookCompleted c && c.async()))
                .isTrue();
    }

    @Test
    void syncWebHookSuccess() {
        Hook hook = new WebHook(
                "notify", null, null, null, "http://example.test/hook", HttpMethod.POST, Map.of("team", "x"), null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        HookRunner.HookPhaseOutcome outcome;
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            outcome = runner.runPhase(HookPhase.AFTER_ALL, List.of(hook), ctx(null), null, null, events::add, handles);
        }

        assertThat(outcome.allSucceeded()).isTrue();
        TestProgressEvent.HookCompleted completed = (TestProgressEvent.HookCompleted) events.stream()
                .filter(e -> e instanceof TestProgressEvent.HookCompleted)
                .findFirst()
                .orElseThrow();
        assertThat(completed.success()).isTrue();
        assertThat(completed.exitCodeOrStatus()).isEqualTo(200);
    }
}
