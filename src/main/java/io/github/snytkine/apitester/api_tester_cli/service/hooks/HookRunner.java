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

import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressListener;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.HookPhase;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.ScriptHook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.WebHook;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the execution of a single lifecycle-hook phase: builds the ordered argument /
 * payload data each hook receives, dispatches synchronous hooks in list order (stopping the phase
 * at the first failure), fires async hooks without blocking, applies the header-masking rules for
 * web hooks, and emits the three hook progress events.
 *
 * <p>This is a stateless, thread-safe Spring singleton. All per-run state ({@link
 * HookInvocationContext}, {@link AsyncHookHandles}) is supplied by the caller and lives on the call
 * stack or in the caller's per-run objects. Resolved script parameters and web payloads are never
 * logged or placed in progress events (they may contain secrets).
 */
@Service
public class HookRunner {

    private static final Logger log = LoggerFactory.getLogger(HookRunner.class);

    /** Header names whose values are always masked in web-hook payloads (case-insensitive). */
    private static final List<String> SENSITIVE_HEADER_NAMES =
            List.of("authorization", "proxy-authorization", "cookie");

    /** Substrings that, when contained in a header name (case-insensitive), trigger masking. */
    private static final List<String> SENSITIVE_HEADER_SUBSTRINGS = List.of("api-key", "token");

    /** Replacement value used for masked header values. */
    static final String MASK = "****";

    private final ScriptHookExecutor scriptExecutor;
    private final WebHookExecutor webExecutor;

    /**
     * Constructs the runner with its executor collaborators.
     *
     * @param scriptExecutor executes script hooks
     * @param webExecutor executes web hooks
     */
    public HookRunner(ScriptHookExecutor scriptExecutor, WebHookExecutor webExecutor) {
        this.scriptExecutor = scriptExecutor;
        this.webExecutor = webExecutor;
    }

    /**
     * Immutable run-level context every hook in a run shares.
     *
     * @param suiteName the suite's {@code name}
     * @param runId the per-execution run id
     * @param interactive whether the run uses the interactive UI
     * @param reportDir the {@code --report} directory argument, or {@code null}
     * @param reportPath the resolved report file path, or {@code null}
     * @param tagFilter the active {@code --tag} value, or {@code null}
     * @param testNameFilter the active {@code --test} value, or {@code null}
     * @param envFilePath the resolved existing {@code .env} path, or {@code null}
     * @param suiteDir the suite file's directory (for resolving relative script paths), or {@code
     *     null}
     * @param envVars merged {@code .env} variables overlaid onto script child environments
     * @param restClientsById the suite's rest-clients keyed by id, for web-hook client selection
     */
    public record HookInvocationContext(
            String suiteName,
            String runId,
            boolean interactive,
            @Nullable String reportDir,
            @Nullable Path reportPath,
            @Nullable String tagFilter,
            @Nullable String testNameFilter,
            @Nullable Path envFilePath,
            @Nullable Path suiteDir,
            Map<String, String> envVars,
            Map<String, RestClientConfig> restClientsById) {}

    /**
     * Per-test data supplied for {@code before-each} / {@code after-each} hooks.
     *
     * @param testName the current test case's name
     * @param url the request's full URL
     * @param method the request's HTTP method
     * @param requestHeaders the request headers (masked before inclusion in web payloads), or {@code
     *     null}
     * @param requestBody the request body string, or {@code null} when the request has none
     * @param testStatus the test result ({@code passed}/{@code failed}/{@code error}) for {@code
     *     after-each}, or {@code null} for {@code before-each}
     */
    public record PerTestData(
            String testName,
            String url,
            HttpMethod method,
            @Nullable Map<String, String> requestHeaders,
            @Nullable String requestBody,
            @Nullable String testStatus) {}

    /**
     * Suite-summary counts supplied for {@code after-all} hooks.
     *
     * @param total total test count
     * @param passed passed count
     * @param failed failed count
     * @param errors error count
     * @param durationMs total suite duration in milliseconds
     */
    public record SummaryData(long total, long passed, long failed, long errors, long durationMs) {}

    /**
     * Outcome of running a phase.
     *
     * @param allSucceeded whether every synchronous hook in the phase succeeded
     * @param firstFailureMessage a human-readable description of the first sync failure, or {@code
     *     null} when none failed
     */
    public record HookPhaseOutcome(boolean allSucceeded, @Nullable String firstFailureMessage) {}

    /**
     * Runs all hooks declared for {@code phase} in list order.
     *
     * <p>Synchronous hooks block until finished; the first sync failure stops the phase (remaining
     * hooks in it are skipped). Async hooks are dispatched to {@code asyncHandles} and the next hook
     * proceeds immediately; their failures never affect the returned outcome. Fires {@link
     * TestProgressEvent.HookPhaseStarted} before the first hook, a {@link
     * TestProgressEvent.HookCompleted} per finished hook, and {@link
     * TestProgressEvent.HookPhaseCompleted} at the end. When {@code hooks} is empty, no events fire
     * and a successful outcome is returned.
     *
     * @param phase the lifecycle phase being run
     * @param hooks the hooks declared for the phase (in declaration order)
     * @param ctx the run-level context
     * @param perTest per-test data for {@code before-each}/{@code after-each}, or {@code null}
     * @param summary suite-summary data for {@code after-all}, or {@code null}
     * @param listener receives the hook progress events; must be thread-safe
     * @param asyncHandles registry that async hooks are submitted to
     * @return the phase outcome
     */
    public HookPhaseOutcome runPhase(
            HookPhase phase,
            List<Hook> hooks,
            HookInvocationContext ctx,
            @Nullable PerTestData perTest,
            @Nullable SummaryData summary,
            TestProgressListener listener,
            AsyncHookHandles asyncHandles) {

        if (hooks.isEmpty()) {
            return new HookPhaseOutcome(true, null);
        }

        listener.onProgress(new TestProgressEvent.HookPhaseStarted(phase, hooks.size()));
        boolean allSucceeded = true;
        String firstFailure = null;

        for (int i = 0; i < hooks.size(); i++) {
            Hook hook = hooks.get(i);
            int index = i + 1;
            String hookId = hook.effectiveId(phase, index);

            if (hook.isAsync()) {
                asyncHandles.submit(() -> {
                    HookExecutionResult result = runOne(phase, hook, hookId, ctx, perTest, summary);
                    listener.onProgress(hookCompletedEvent(phase, hookId, index, true, result));
                    if (!result.success()) {
                        log.warn("Async {} hook '{}' failed: {}", phase.yamlKey(), hookId, result.errorMessage());
                    }
                });
                continue;
            }

            HookExecutionResult result = runOne(phase, hook, hookId, ctx, perTest, summary);
            listener.onProgress(hookCompletedEvent(phase, hookId, index, false, result));
            if (!result.success()) {
                allSucceeded = false;
                firstFailure = describeFailure(phase, hookId, result);
                log.debug("{} hook '{}' failed: {}", phase.yamlKey(), hookId, result.errorMessage());
                // Skip remaining hooks in this phase once a sync hook fails.
                break;
            }
        }

        listener.onProgress(new TestProgressEvent.HookPhaseCompleted(phase, allSucceeded));
        return new HookPhaseOutcome(allSucceeded, firstFailure);
    }

    /**
     * Executes a single hook (script or web), assembling its arguments/payload from the phase and
     * context.
     *
     * @param phase the lifecycle phase
     * @param hook the hook to run
     * @param hookId the resolved hook id
     * @param ctx the run-level context
     * @param perTest per-test data, or {@code null}
     * @param summary suite-summary data, or {@code null}
     * @return the execution result
     */
    private HookExecutionResult runOne(
            HookPhase phase,
            Hook hook,
            String hookId,
            HookInvocationContext ctx,
            @Nullable PerTestData perTest,
            @Nullable SummaryData summary) {

        Map<String, String> scalars = buildScalarArgs(phase, hook, hookId, ctx, perTest, summary);

        if (hook instanceof ScriptHook script) {
            List<String> args = new ArrayList<>();
            scalars.forEach((k, v) -> args.add(k + "=" + v));
            if (script.parameters() != null) {
                script.parameters().forEach((k, v) -> args.add(k + "=" + v));
            }
            Consumer<String> outSink = line -> forwardScriptLine(ctx, line, false);
            Consumer<String> errSink = line -> forwardScriptLine(ctx, line, true);
            return scriptExecutor.execute(
                    ctx.suiteDir(),
                    script.path(),
                    args,
                    ctx.envVars(),
                    hook.effectiveTimeoutSeconds(),
                    outSink,
                    errSink);
        }

        WebHook web = (WebHook) hook;
        Map<String, Object> payload = new LinkedHashMap<>(scalars);
        if (perTest != null && (phase == HookPhase.BEFORE_EACH || phase == HookPhase.AFTER_EACH)) {
            if (perTest.requestHeaders() != null) {
                payload.put("headers", maskHeaders(perTest.requestHeaders()));
            }
            if (perTest.requestBody() != null) {
                payload.put("body", perTest.requestBody());
            }
        }
        if (web.payload() != null) {
            web.payload().forEach(payload::put);
        }

        RestClientConfig config = ctx.restClientsById()
                .getOrDefault(web.effectiveRestClient(), ctx.restClientsById().get(TestSuite.DEFAULT_REST_CLIENT_ID));
        if (config == null) {
            return HookExecutionResult.failure(
                    HookExecutionResult.NO_STATUS, 0, "Unknown rest-client: " + web.effectiveRestClient());
        }
        boolean attach = phase == HookPhase.AFTER_REPORT && web.isAttachReport();
        Path reportFile = attach ? ctx.reportPath() : null;
        return webExecutor.execute(
                config, web.effectiveMethod(), web.url(), payload, reportFile, attach, hook.effectiveTimeoutSeconds());
    }

    /**
     * Builds the ordered scalar {@code key -> value} data every hook receives, applying the
     * per-phase inclusion rules from the feature spec (which keys are present for which phases).
     *
     * @param phase the lifecycle phase
     * @param hook the hook being run
     * @param hookId the resolved hook id
     * @param ctx the run-level context
     * @param perTest per-test data, or {@code null}
     * @param summary suite-summary data, or {@code null}
     * @return an ordered, non-null map of scalar arguments
     */
    Map<String, String> buildScalarArgs(
            HookPhase phase,
            Hook hook,
            String hookId,
            HookInvocationContext ctx,
            @Nullable PerTestData perTest,
            @Nullable SummaryData summary) {

        Map<String, String> map = new LinkedHashMap<>();
        map.put("suite_name", ctx.suiteName());
        map.put("run_id", ctx.runId());
        map.put("hook_id", hookId);
        map.put("phase", phase.yamlKey());
        map.put("interactive", String.valueOf(ctx.interactive()));
        map.put("timeout_seconds", String.valueOf(hook.effectiveTimeoutSeconds()));

        if (ctx.reportDir() != null) {
            map.put("report_dir", ctx.reportDir());
        }
        if (reportPathApplies(phase) && ctx.reportPath() != null) {
            map.put("report_path", ctx.reportPath().toString());
        }

        boolean perTestPhase = phase == HookPhase.BEFORE_EACH || phase == HookPhase.AFTER_EACH;
        if (perTestPhase && perTest != null) {
            map.put("test_name", perTest.testName());
        } else if (ctx.testNameFilter() != null) {
            map.put("test_name", ctx.testNameFilter());
        }
        if (ctx.tagFilter() != null) {
            map.put("tag", ctx.tagFilter());
        }
        if (ctx.envFilePath() != null) {
            map.put("env_file", ctx.envFilePath().toString());
        }
        if (perTestPhase && perTest != null) {
            map.put("url", perTest.url());
            map.put("method", perTest.method().name());
        }
        if (phase == HookPhase.AFTER_EACH && perTest != null && perTest.testStatus() != null) {
            map.put("test_status", perTest.testStatus());
        }
        if (phase == HookPhase.AFTER_ALL && summary != null) {
            map.put("tests_total", String.valueOf(summary.total()));
            map.put("tests_passed", String.valueOf(summary.passed()));
            map.put("tests_failed", String.valueOf(summary.failed()));
            map.put("tests_errors", String.valueOf(summary.errors()));
            map.put("duration_ms", String.valueOf(summary.durationMs()));
        }
        return map;
    }

    /**
     * Returns whether {@code report_path} is passed to hooks of the given phase (only {@code
     * after-all}, {@code before-report}, {@code after-report}).
     *
     * @param phase the lifecycle phase
     * @return {@code true} when the phase receives {@code report_path}
     */
    private static boolean reportPathApplies(HookPhase phase) {
        return phase == HookPhase.AFTER_ALL || phase == HookPhase.BEFORE_REPORT || phase == HookPhase.AFTER_REPORT;
    }

    /**
     * Returns a copy of {@code headers} with sensitive values replaced by {@link #MASK}. A header is
     * sensitive when its name (case-insensitive) is {@code Authorization}, {@code
     * Proxy-Authorization}, or {@code Cookie}, or contains {@code api-key} or {@code token}.
     *
     * @param headers the raw request headers
     * @return a new map with sensitive values masked; insertion order preserved
     */
    static Map<String, String> maskHeaders(Map<String, String> headers) {
        Map<String, String> masked = new LinkedHashMap<>();
        headers.forEach((name, value) -> masked.put(name, isSensitive(name) ? MASK : value));
        return masked;
    }

    /**
     * Returns whether a header name is considered sensitive and should be masked.
     *
     * @param name the header name
     * @return {@code true} when the value should be masked
     */
    private static boolean isSensitive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (SENSITIVE_HEADER_NAMES.contains(lower)) {
            return true;
        }
        return SENSITIVE_HEADER_SUBSTRINGS.stream().anyMatch(lower::contains);
    }

    /**
     * Forwards a control-char-stripped script output line: to the debug log in interactive mode (so
     * it can never corrupt the TUI grid), or to the process stdout/stderr in non-interactive mode.
     *
     * @param ctx the run-level context (for the interactive flag)
     * @param line the already-stripped output line
     * @param isErr whether the line came from stderr
     */
    private static void forwardScriptLine(HookInvocationContext ctx, String line, boolean isErr) {
        if (ctx.interactive()) {
            log.debug("[hook script] {}", line);
        } else if (isErr) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }
    }

    /**
     * Builds a {@link TestProgressEvent.HookCompleted} event from an execution result.
     *
     * @param phase the phase
     * @param hookId the hook id
     * @param index the 1-based index
     * @param async whether the hook ran asynchronously
     * @param result the execution result
     * @return the completed event
     */
    private static TestProgressEvent.HookCompleted hookCompletedEvent(
            HookPhase phase, String hookId, int index, boolean async, HookExecutionResult result) {
        return new TestProgressEvent.HookCompleted(
                phase,
                hookId,
                index,
                async,
                result.success(),
                result.exitCodeOrStatus(),
                result.durationMs(),
                result.timedOut());
    }

    /**
     * Builds a human-readable description of a sync hook failure for the phase outcome (used by the
     * fatal {@code before-all} message and warnings).
     *
     * @param phase the phase
     * @param hookId the hook id
     * @param result the failed execution result
     * @return a description such as {@code "Before All hook 'seed-db' returned non-zero status"}
     */
    private static String describeFailure(HookPhase phase, String hookId, HookExecutionResult result) {
        String label = titleCase(phase);
        if (result.timedOut()) {
            return label + " hook '" + hookId + "' timed out";
        }
        return label + " hook '" + hookId + "' returned non-zero status";
    }

    /**
     * Converts a phase's kebab-case key to Title Case words (e.g. {@code before-all} → {@code Before
     * All}).
     *
     * @param phase the phase
     * @return the title-cased phase label
     */
    private static String titleCase(HookPhase phase) {
        String[] words = phase.yamlKey().split("-");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }
}
