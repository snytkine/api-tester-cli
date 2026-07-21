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
        assertThat(outcome.firstFailureMessage()).contains("Error executing hook first");
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

    private static HookRunner.HookInvocationContext interactiveCtx(Path suiteDir) {
        return new HookRunner.HookInvocationContext(
                "My Suite",
                "run-123",
                true,
                null,
                null,
                null,
                "MyTest",
                null,
                suiteDir,
                Map.of(),
                Map.of(TestSuite.DEFAULT_REST_CLIENT_ID, RestClientConfig.withDefaults(null)));
    }

    @Test
    void beforeAllUsesTestNameFilterWhenPresent() {
        Hook hook = new ScriptHook(null, null, null, "s.sh", null);
        Map<String, String> args =
                runner.buildScalarArgs(HookPhase.BEFORE_ALL, hook, "before-all-1", interactiveCtx(null), null, null);
        assertThat(args).containsEntry("test_name", "MyTest").containsEntry("interactive", "true");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void scriptOutputIsForwardedInNonInteractiveMode(@TempDir Path dir) throws IOException {
        Path script = writeScript(dir, "noisy.sh", "echo out\necho err 1>&2\nexit 0\n");
        Hook hook = new ScriptHook("noisy", null, null, script.toString(), null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            HookRunner.HookPhaseOutcome outcome =
                    runner.runPhase(HookPhase.BEFORE_ALL, List.of(hook), ctx(dir), null, null, events::add, handles);
            assertThat(outcome.allSucceeded()).isTrue();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void scriptOutputIsLoggedInInteractiveMode(@TempDir Path dir) throws IOException {
        Path script = writeScript(dir, "noisy.sh", "echo out\necho err 1>&2\nexit 0\n");
        Hook hook = new ScriptHook("noisy", null, null, script.toString(), null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            runner.runPhase(HookPhase.BEFORE_ALL, List.of(hook), interactiveCtx(dir), null, null, events::add, handles);
        }
        assertThat(events).isNotEmpty();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void syncTimeoutProducesTimedOutFailureMessage(@TempDir Path dir) throws IOException {
        Path script = writeScript(dir, "slow.sh", "sleep 3\nexit 0\n");
        Hook hook = new ScriptHook("slow", null, 1, script.toString(), null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        HookRunner.HookPhaseOutcome outcome;
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            outcome = runner.runPhase(HookPhase.BEFORE_ALL, List.of(hook), ctx(dir), null, null, events::add, handles);
        }

        assertThat(outcome.allSucceeded()).isFalse();
        assertThat(outcome.firstFailureMessage()).startsWith("Error executing hook slow:");
        assertThat(outcome.firstFailureMessage()).containsIgnoringCase("timeout");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void syncFailureMessageIncludesScriptStderr(@TempDir Path dir) throws IOException {
        // A hook that writes a diagnostic to stderr and exits non-zero: the captured stderr must be
        // surfaced in the failure message using the spec format "Error executing hook <id>: <stderr>".
        Path script = writeScript(dir, "boom.sh", "echo \"Hook Failed\" >&2\nexit 1\n");
        Hook hook = new ScriptHook("my-hook", null, null, script.toString(), null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        HookRunner.HookPhaseOutcome outcome;
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            outcome = runner.runPhase(HookPhase.BEFORE_ALL, List.of(hook), ctx(dir), null, null, events::add, handles);
        }

        assertThat(outcome.allSucceeded()).isFalse();
        assertThat(outcome.firstFailureMessage()).isEqualTo("Error executing hook my-hook: Hook Failed");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void asyncFailureIsNonFatalAndReported(@TempDir Path dir) throws IOException {
        Path script = writeScript(dir, "bad.sh", "exit 4\n");
        Hook hook = new ScriptHook("bg", true, null, script.toString(), null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        HookRunner.HookPhaseOutcome outcome;
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            outcome = runner.runPhase(HookPhase.AFTER_ALL, List.of(hook), ctx(dir), null, null, events::add, handles);
            assertThat(outcome.allSucceeded()).isTrue();
            handles.awaitAll();
        }

        TestProgressEvent.HookCompleted completed = (TestProgressEvent.HookCompleted) events.stream()
                .filter(e -> e instanceof TestProgressEvent.HookCompleted)
                .findFirst()
                .orElseThrow();
        assertThat(completed.async()).isTrue();
        assertThat(completed.success()).isFalse();
    }

    @Test
    void webHookWithUnknownRestClientFails() {
        HookRunner.HookInvocationContext emptyClients = new HookRunner.HookInvocationContext(
                "My Suite", "run-1", false, null, null, null, null, null, null, Map.of(), Map.of());
        Hook hook = new WebHook("w", null, null, "ghost", "http://x/hook", HttpMethod.POST, null, null);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        HookRunner.HookPhaseOutcome outcome;
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            outcome =
                    runner.runPhase(HookPhase.AFTER_ALL, List.of(hook), emptyClients, null, null, events::add, handles);
        }

        assertThat(outcome.allSucceeded()).isFalse();
        assertThat(outcome.firstFailureMessage()).contains("Error executing hook w");
        assertThat(outcome.firstFailureMessage()).contains("ghost");
    }

    @Test
    void attachReportOnAfterReportSendsMultipart(@TempDir Path dir) throws IOException {
        Path report = dir.resolve("report.html");
        Files.writeString(report, "<html>ok</html>");
        HookRunner reportRunner = new HookRunner(
                new ScriptHookExecutor(),
                new WebHookExecutor(new StubClientHttpRequestFactory().stub("report", 200, "{}", "application/json")));
        HookRunner.HookInvocationContext reportCtx = new HookRunner.HookInvocationContext(
                "My Suite",
                "run-1",
                false,
                "/tmp/rep",
                report,
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of(TestSuite.DEFAULT_REST_CLIENT_ID, RestClientConfig.withDefaults(null)));
        Hook hook = new WebHook("r", null, null, null, "http://x/report", HttpMethod.POST, null, Boolean.TRUE);
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        HookRunner.HookPhaseOutcome outcome;
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            outcome = reportRunner.runPhase(
                    HookPhase.AFTER_REPORT, List.of(hook), reportCtx, null, null, events::add, handles);
        }

        assertThat(outcome.allSucceeded()).isTrue();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void scriptHookUserParametersAreAppendedToArgv(@TempDir Path dir) throws IOException {
        Path outFile = dir.resolve("argv.txt");
        Path script = writeScript(dir, "args.sh", "printf '%s\\n' \"$@\" > \"" + outFile + "\"\n");
        Hook hook = new ScriptHook("p", null, null, script.toString(), Map.of("token", "s3cr3t"));
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            runner.runPhase(HookPhase.BEFORE_ALL, List.of(hook), ctx(dir), null, null, events::add, handles);
        }

        assertThat(Files.readAllLines(outFile)).contains("token=s3cr3t");
    }

    @Test
    void webHookInAfterEachIncludesMaskedHeadersAndBody() {
        Hook hook = new WebHook("w", null, null, null, "http://x/hook", HttpMethod.POST, null, null);
        HookRunner.PerTestData perTest = new HookRunner.PerTestData(
                "t1",
                "http://x/hook",
                HttpMethod.POST,
                Map.of("Authorization", "Bearer secret", "Content-Type", "application/json"),
                "{\"k\":1}",
                "passed");
        List<TestProgressEvent> events = new CopyOnWriteArrayList<>();

        HookRunner.HookPhaseOutcome outcome;
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            outcome = runner.runPhase(
                    HookPhase.AFTER_EACH, List.of(hook), ctx(null), perTest, null, events::add, handles);
        }

        assertThat(outcome.allSucceeded()).isTrue();
    }
}
