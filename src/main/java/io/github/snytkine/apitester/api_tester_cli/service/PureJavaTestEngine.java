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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressListener;
import io.github.snytkine.apitester.api_tester_cli.event.TestStatus;
import io.github.snytkine.apitester.api_tester_cli.exception.AssertionFailuresException;
import io.github.snytkine.apitester.api_tester_cli.exception.HookFailedException;
import io.github.snytkine.apitester.api_tester_cli.exception.NoServerResponseException;
import io.github.snytkine.apitester.api_tester_cli.exception.SessionCaptureException;
import io.github.snytkine.apitester.api_tester_cli.exception.SkipTestException;
import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.interfaces.TestEngine;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import io.github.snytkine.apitester.api_tester_cli.model.AuthType;
import io.github.snytkine.apitester.api_tester_cli.model.ExecutedRequestInfo;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.PayloadRequest;
import io.github.snytkine.apitester.api_tester_cli.model.RequestAuth;
import io.github.snytkine.apitester.api_tester_cli.model.RequestBody;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.Assertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.BaseServerResponseAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.HookPhase;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hooks;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import io.github.snytkine.apitester.api_tester_cli.service.hooks.AsyncHookHandles;
import io.github.snytkine.apitester.api_tester_cli.service.hooks.HookRunner;
import io.github.snytkine.apitester.api_tester_cli.service.hooks.ScriptHookExecutor;
import io.github.snytkine.apitester.api_tester_cli.service.hooks.WebHookExecutor;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import io.github.snytkine.apitester.api_tester_cli.util.FileLoader;
import io.github.snytkine.apitester.api_tester_cli.util.ProxyAuthenticator;
import io.github.snytkine.apitester.api_tester_cli.util.ProxyConfigurationException;
import io.github.snytkine.apitester.api_tester_cli.util.ProxyErrorClassifier;
import io.github.snytkine.apitester.api_tester_cli.util.ProxyResolver;
import io.github.snytkine.apitester.api_tester_cli.util.ProxySettings;
import io.github.snytkine.apitester.api_tester_cli.util.SslContextFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Executes the test cases in a {@link TestSuite} sequentially using Spring's
 * {@link RestClient},
 * collecting pass/fail counts and error messages into a {@link TestRunResult}.
 *
 * <p>
 * A fresh {@link RestClient} is built for each suite run using the
 * {@link RestClientConfig}
 * embedded in the suite (base URL and connect timeout). The underlying HTTP
 * transport is supplied
 * as a {@link ClientHttpRequestFactory} at construction time, keeping transport
 * configuration
 * separate from per-suite settings.
 *
 * <p>
 * Assertions are evaluated via {@link AssertionEvaluatorFactory}, which maps
 * each assertion type
 * to its evaluator. All assertion failures within a single test case are
 * collected by {@link
 * io.github.snytkine.apitester.api_tester_cli.util.FailureCollector} and
 * surfaced together rather
 * than stopping at the first failure.
 *
 * <p>
 * Every test case additionally carries one implicit {@link BaseServerResponseAssertion}, injected
 * here rather than declared in the YAML, asserting only that the service answered the request with
 * some HTTP response before the rest-client's timeout elapsed. It is evaluated first and counted in
 * the test's assertion total. When no response arrives at all the transport exception is converted
 * into that assertion's failure ({@link NoServerResponseException}) and the test is recorded FAILED
 * with zero passed assertions, rather than surfacing as an opaque ERROR.
 *
 * <p>
 * Execution order honours {@code depends-on} and {@code transient}: before any test runs, {@link
 * #buildExecutionPlan} resolves an ordered plan in which each dependency precedes its dependents and
 * every test appears at most once per suite run (run-once). A depended-on test's result — including any
 * {@code saved-session} values — is reused by every dependent rather than re-executed; a failed
 * dependency propagates failure to its dependents without sending their requests. Transient tests run
 * only as another test's dependency and fire neither {@code before-each} nor {@code after-each} hooks.
 *
 * <p>
 * This class is a thread-safe Spring singleton: all per-invocation state (the plan, the mutable {@code
 * session} map, and the per-test outcome map used for failure propagation) is confined to the call
 * stack of {@link #runConfigurationSuite(TestSuite, SuiteRunContext, TestProgressListener)}.
 */
@Service
public class PureJavaTestEngine implements TestEngine {

    private static final Logger log = LoggerFactory.getLogger(PureJavaTestEngine.class);

    private final ClientHttpRequestFactory requestFactory;
    private final AssertionEvaluatorFactory evaluatorFactory;
    private final ResponseResolver responseResolver;
    private final HookRunner hookRunner;
    private final ObjectMapper yamlMapper;

    /**
     * Constructs the engine with all required collaborators, including the {@link HookRunner} used to
     * dispatch lifecycle hooks. This is the constructor Spring uses.
     *
     * @param requestFactory the HTTP transport factory used to back each per-suite {@link RestClient}
     * @param evaluatorFactory maps assertion model objects to their evaluator implementations
     * @param responseResolver converts a {@link RestClient.ResponseSpec} into an {@link ApiResponse}
     * @param hookRunner orchestrates lifecycle-hook phases
     */
    @org.springframework.beans.factory.annotation.Autowired
    public PureJavaTestEngine(
            ClientHttpRequestFactory requestFactory,
            AssertionEvaluatorFactory evaluatorFactory,
            ResponseResolver responseResolver,
            HookRunner hookRunner) {
        this.requestFactory = requestFactory;
        this.evaluatorFactory = evaluatorFactory;
        this.responseResolver = responseResolver;
        this.hookRunner = hookRunner;
        this.yamlMapper =
                new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Convenience constructor that builds a default {@link HookRunner} from the supplied transport
     * factory. Retained so pre-hooks unit tests that construct the engine with three collaborators
     * continue to compile and to exercise hook dispatch with real executors.
     *
     * @param requestFactory the HTTP transport factory used to back each per-suite {@link RestClient}
     * @param evaluatorFactory maps assertion model objects to their evaluator implementations
     * @param responseResolver converts a {@link RestClient.ResponseSpec} into an {@link ApiResponse}
     */
    public PureJavaTestEngine(
            ClientHttpRequestFactory requestFactory,
            AssertionEvaluatorFactory evaluatorFactory,
            ResponseResolver responseResolver) {
        this(
                requestFactory,
                evaluatorFactory,
                responseResolver,
                new HookRunner(new ScriptHookExecutor(), new WebHookExecutor(requestFactory)));
    }

    /**
     * Runs all test cases in the provided {@link TestSuite} sequentially, firing
     * progress events to
     * {@code listener} at each milestone, and returns an aggregated result.
     *
     * <p>
     * A {@link RestClient} is built from the suite's {@link RestClientConfig} (base
     * URL and
     * connect timeout) before iteration begins and shared across all test cases in
     * the suite. The
     * suite's file path (when present) is used to resolve relative file references
     * in assertions.
     *
     * <p>
     * Events fired (in order):
     *
     * <ol>
     * <li>{@link TestProgressEvent.SuiteStarted} — once, before any test runs
     * <li>{@link TestProgressEvent.TestStarted} — before each test's HTTP request
     * <li>{@link TestProgressEvent.TestCompleted} — after each test's assertions
     * are evaluated
     * <li>{@link TestProgressEvent.SuiteCompleted} — once, after all tests finish
     * </ol>
     *
     * @param testSuite the loaded test suite whose {@link TestSuite#tests()} are
     *                  executed
     * @param context   all variable namespaces ({@code env}, {@code cli}) used when
     *                  building request
     *                  bodies and evaluating assertions; {@code suite} and
     *                  {@code test} are added internally
     * @param listener  receives progress events; must be thread-safe
     * @return a {@link TestRunResult} with per-test-case results including
     *         structured failure detail
     */
    @Override
    public TestRunResult runConfigurationSuite(
            TestSuite testSuite, SuiteRunContext context, TestProgressListener listener) {
        Path suiteDir = testSuite.filePath() != null ? testSuite.filePath().getParent() : null;
        Map<String, RestClient> restClients = new LinkedHashMap<>();
        testSuite
                .restClientsById()
                .forEach((id, config) -> restClients.put(id, buildRestClient(config, suiteDir, context.env())));
        RestClient defaultRestClient = restClients.get(TestSuite.DEFAULT_REST_CLIENT_ID);
        Map<String, String> suiteVariables = Objects.requireNonNullElse(testSuite.variables(), Map.of());

        // Suite-wide, mutable 'session' namespace. Values are captured from test responses (via
        // saved-session) and accumulate across the run, so later tests can reference
        // [[${session.<name>}]]. Confined to this call stack: this engine is a stateless singleton
        // and execution is sequential, so no synchronization is required.
        Map<String, String> sessionVars = new LinkedHashMap<>();
        Map<String, Map<String, String>> configMap = Map.of(
                "cli", context.cli(),
                "env", context.env(),
                "suite", suiteVariables,
                "test", Map.of(),
                "session", sessionVars);

        Hooks hooks = testSuite.hooks();
        HookRunner.HookInvocationContext hookCtx = buildHookContext(testSuite, context, suiteDir);
        List<Hook> beforeAllHooks = phaseHooks(hooks, HookPhase.BEFORE_ALL);
        List<Hook> beforeEachHooks = phaseHooks(hooks, HookPhase.BEFORE_EACH);
        List<Hook> afterEachHooks = phaseHooks(hooks, HookPhase.AFTER_EACH);
        List<Hook> afterAllHooks = phaseHooks(hooks, HookPhase.AFTER_ALL);

        List<TestCase> tests = testSuite.tests();

        // Resolve the depends-on / transient execution plan before any test runs. Each test that will
        // actually execute is represented by exactly one ExecutionStep (run-once semantics): dependencies
        // are ordered before their dependents, transient tests appear only when pulled in as a dependency,
        // and a test named by several dependents appears a single time. The plan size is the exact number
        // of result rows, so it drives the pre-allocated UI grid via SuiteStarted below.
        List<ExecutionStep> plan = buildExecutionPlan(tests);

        try (AsyncHookHandles asyncHandles = new AsyncHookHandles()) {
            // before-all runs before SuiteStarted; a blocking failure aborts the run fatally.
            HookRunner.HookPhaseOutcome beforeAll = hookRunner.runPhase(
                    HookPhase.BEFORE_ALL, beforeAllHooks, hookCtx, null, null, listener, asyncHandles);
            if (!beforeAll.allSucceeded()) {
                throw new HookFailedException(
                        beforeAll.firstFailureMessage() != null
                                ? beforeAll.firstFailureMessage()
                                : "Before All hook returned non-zero status");
            }

            Instant suiteStart = Instant.now();
            listener.onProgress(new TestProgressEvent.SuiteStarted(testSuite.name(), plan.size(), suiteStart));

            List<TestCaseResult> results = new ArrayList<>();

            // Execute the plan in order. Each step runs at most once; a per-name outcome map lets a
            // dependent detect a failed dependency (failure propagation) and reuse already-captured
            // session values without re-running the dependency. The map lives on this call stack only.
            Map<String, TestOutcome> outcomes = new LinkedHashMap<>();
            for (int rowIndex = 0; rowIndex < plan.size(); rowIndex++) {
                ExecutionStep step = plan.get(rowIndex);
                TestOutcome outcome = executePlanStep(
                        step,
                        rowIndex,
                        restClients,
                        defaultRestClient,
                        testSuite,
                        suiteDir,
                        configMap,
                        sessionVars,
                        outcomes,
                        beforeEachHooks,
                        afterEachHooks,
                        hookCtx,
                        asyncHandles,
                        listener,
                        results);
                outcomes.put(step.test().name(), outcome);
            }

            Map<TestResult, Long> counts =
                    results.stream().collect(Collectors.groupingBy(TestCaseResult::result, Collectors.counting()));
            long passedCount = counts.getOrDefault(TestResult.PASSED, 0L);
            long failedCount = counts.getOrDefault(TestResult.FAILED, 0L);
            long skippedCount = counts.getOrDefault(TestResult.SKIPPED, 0L);
            long errorCount = counts.getOrDefault(TestResult.ERROR, 0L);

            long totalDurationMs = Instant.now().toEpochMilli() - suiteStart.toEpochMilli();

            // after-all runs after the last test, before SuiteCompleted, so the UI can render it
            // below the summary. Failures here are warnings only and never change the result.
            HookRunner.SummaryData summary =
                    new HookRunner.SummaryData(plan.size(), passedCount, failedCount, errorCount, totalDurationMs);
            hookRunner.runPhase(HookPhase.AFTER_ALL, afterAllHooks, hookCtx, null, summary, listener, asyncHandles);

            listener.onProgress(new TestProgressEvent.SuiteCompleted(
                    passedCount, failedCount, skippedCount, errorCount, totalDurationMs));

            return new TestRunResult(passedCount, failedCount, skippedCount, errorCount, results, Map.of());
        }
    }

    /**
     * A single planned execution: the raw {@link TestCase} to run and the display label under which its
     * result row and progress events are reported.
     *
     * <p>The label carries the triggering context: a test run standalone uses its plain name, while a
     * test first reached as another test's dependency uses {@code "<name> (dependency of <dependent>)"}.
     * Because dependencies run at most once, each executed test contributes exactly one step.
     *
     * @param test the raw (pre-template-resolution) test case to execute
     * @param label the display label for the result row and {@link TestProgressEvent}s
     */
    private record ExecutionStep(TestCase test, String label) {}

    /**
     * The terminal outcome of one executed plan step, recorded per test name so a dependent test can
     * detect a failed dependency (failure propagation).
     *
     * @param result the four-way terminal status of the execution
     * @param errorMessage a human-readable failure message when {@code result} is {@link
     *     TestResult#FAILED} or {@link TestResult#ERROR}; {@code null} otherwise
     */
    private record TestOutcome(TestResult result, @Nullable String errorMessage) {}

    /**
     * Resolves the ordered {@code depends-on} / {@code transient} execution plan for a suite run.
     *
     * <p>Semantics implemented here (see {@code depends-on-feature.md}):
     *
     * <ul>
     *   <li><b>Transient tests</b> ({@link TestCase#transientCase()}) are never scheduled as standalone
     *       top-level tests; they appear only when pulled in as another test's dependency.
     *   <li><b>Dependencies run first</b>, in the order listed in {@link TestCase#dependsOn()}, resolved
     *       transitively via depth-first post-order traversal.
     *   <li><b>Run-once</b>: a {@code planned} set keyed by test name guarantees each test is scheduled
     *       at most once per suite run, no matter how many tests depend on it (or whether it also appears
     *       standalone). The first time a test is reached fixes its label — standalone if reached as a
     *       top-level test, otherwise labeled with the first dependent that triggered it.
     * </ul>
     *
     * <p>Every {@code depends-on} name is guaranteed to reference a test present in {@code tests}: {@link
     * io.github.snytkine.apitester.api_tester_cli.service.TestSuiteValidator#validateDependencies} runs
     * on the same (possibly filtered) suite before execution and rejects unknown references and cycles.
     * The {@code null} guard on a missing dependency is therefore defensive only.
     *
     * @param tests the suite's test cases in file order (already tag/name filtered when applicable)
     * @return the ordered list of executions; its size is the exact number of result rows
     */
    private List<ExecutionStep> buildExecutionPlan(List<TestCase> tests) {
        Map<String, TestCase> byName = new LinkedHashMap<>();
        for (TestCase test : tests) {
            byName.put(test.name(), test);
        }
        List<ExecutionStep> plan = new ArrayList<>();
        Set<String> planned = new HashSet<>();
        for (TestCase test : tests) {
            if (test.transientCase()) {
                // Transient tests never run standalone — only when depended upon.
                continue;
            }
            addToPlan(test, null, byName, planned, plan);
        }
        return plan;
    }

    /**
     * Recursively adds {@code test} and its transitive dependencies to {@code plan}, dependencies first.
     *
     * <p>Guarded by {@code planned} so each test is added at most once (run-once semantics). The first
     * reach determines the label: {@code dependentName} is {@code null} for a top-level standalone reach
     * and the triggering dependent's name when reached as a dependency. Cycles cannot occur — they are
     * rejected by validation before execution — so the top-level {@code planned} guard is sufficient to
     * terminate the recursion.
     *
     * @param test the test to schedule
     * @param dependentName the name of the test that triggered this one as a dependency, or {@code null}
     *     when scheduled as a standalone top-level test
     * @param byName lookup of every test case in the run keyed by name
     * @param planned the set of already-scheduled test names (mutated)
     * @param plan the accumulating execution plan (mutated)
     */
    private void addToPlan(
            TestCase test,
            @Nullable String dependentName,
            Map<String, TestCase> byName,
            Set<String> planned,
            List<ExecutionStep> plan) {
        if (planned.contains(test.name())) {
            return;
        }
        List<String> deps = test.dependsOn();
        if (deps != null) {
            for (String depName : deps) {
                TestCase dep = byName.get(depName);
                if (dep != null) {
                    addToPlan(dep, test.name(), byName, planned, plan);
                }
            }
        }
        // Re-check after resolving dependencies (defensive; a well-formed, acyclic graph cannot have
        // scheduled this test while resolving its own dependencies).
        if (planned.add(test.name())) {
            String label = dependentName == null ? test.name() : test.name() + " (dependency of " + dependentName + ")";
            plan.add(new ExecutionStep(test, label));
        }
    }

    /**
     * Executes one {@link ExecutionStep}: fires its {@link TestProgressEvent}s, runs its lifecycle hooks
     * (subject to the transient/skip rules below), sends its request, evaluates assertions, appends its
     * {@link TestCaseResult} to {@code results}, and returns the terminal {@link TestOutcome}.
     *
     * <p>Ordering within a step:
     *
     * <ol>
     *   <li><b>Failure propagation</b> — if any {@code depends-on} dependency already ended {@link
     *       TestResult#FAILED} or {@link TestResult#ERROR} (looked up in {@code outcomes}), this test is
     *       recorded {@code FAILED} as a parent-failure result (its {@link
     *       TestCaseResult#failedParentName()} set to the failed parent's name, message from {@link
     *       TestCaseResult#parentFailureMessage(String)}) and neither hooks nor request run.
     *   <li><b>before-each hooks</b> — run only when the test is neither skipped nor {@link
     *       TestCase#transientCase() transient}. A transient test fires no per-test hooks: those belong
     *       to the dependent test that triggered it. A blocking before-each failure records {@code ERROR}
     *       and skips the request and after-each.
     *   <li><b>request + assertions + saved-session capture</b> via {@link #executeSingleTest}. A
     *       {@link NoServerResponseException} raised here means the service returned no response at
     *       all: the test is recorded {@code FAILED} with zero passed assertions and the single
     *       {@code base_server_response} failure.
     *   <li><b>after-each hooks</b> — same transient/skip gate as before-each.
     * </ol>
     *
     * @param step the planned execution (test + display label)
     * @param rowIndex the plan row index, used as the progress-event {@code uniqueId}/{@code testIndex}
     * @param restClients the configured clients for this suite run, keyed by id
     * @param defaultRestClient the default client
     * @param testSuite the loaded suite (template content, rest-client configs)
     * @param suiteDir the suite file's directory, or {@code null}
     * @param configMap the suite-level variable namespaces
     * @param sessionVars the suite-wide mutable {@code session} namespace
     * @param outcomes per-test-name outcomes recorded so far (read for dependency-failure propagation)
     * @param beforeEachHooks the suite's {@code before-each} hooks
     * @param afterEachHooks the suite's {@code after-each} hooks
     * @param hookCtx the run-level hook invocation context
     * @param asyncHandles the async-hook lifecycle handle for this run
     * @param listener the progress listener
     * @param results the accumulating result list (mutated: exactly one row appended)
     * @return the terminal outcome of this step, keyed later by the caller under the test's name
     */
    private TestOutcome executePlanStep(
            ExecutionStep step,
            int rowIndex,
            Map<String, RestClient> restClients,
            RestClient defaultRestClient,
            TestSuite testSuite,
            @Nullable Path suiteDir,
            Map<String, Map<String, String>> configMap,
            Map<String, String> sessionVars,
            Map<String, TestOutcome> outcomes,
            List<Hook> beforeEachHooks,
            List<Hook> afterEachHooks,
            HookRunner.HookInvocationContext hookCtx,
            AsyncHookHandles asyncHandles,
            TestProgressListener listener,
            List<TestCaseResult> results) {
        TestCase config = step.test();
        String label = step.label();
        String uniqueId = String.valueOf(rowIndex);
        listener.onProgress(new TestProgressEvent.TestStarted(uniqueId, rowIndex, label));
        long testStart = System.currentTimeMillis();

        boolean skipped = config.skip() != null && !config.skip().isBlank();

        // 1. Failure propagation: a dependent inherits the first failed/errored dependency's failure and
        // its own request is never sent. Dependencies always precede dependents in the plan, so their
        // outcomes are already recorded here.
        if (config.dependsOn() != null) {
            for (String depName : config.dependsOn()) {
                TestOutcome depOutcome = outcomes.get(depName);
                if (depOutcome != null
                        && (depOutcome.result() == TestResult.FAILED || depOutcome.result() == TestResult.ERROR)) {
                    long durationMs = System.currentTimeMillis() - testStart;
                    String msg = TestCaseResult.parentFailureMessage(depName);
                    List<AssertionFailure> failure = List.of(new AssertionFailure(msg, null, null, null));
                    results.add(new TestCaseResult(label, TestResult.FAILED, 0, failure, null, null, null, depName));
                    listener.onProgress(new TestProgressEvent.TestCompleted(
                            uniqueId, rowIndex, label, TestStatus.FAIL, durationMs, 0, failure, depName));
                    log.debug("Test case '{}' failed: dependency '{}' failed: {}", config.name(), depName, msg);
                    return new TestOutcome(TestResult.FAILED, msg);
                }
            }
        }

        // Per-test hooks fire only for a non-skipped, non-transient test. A transient test's before/after
        // -each hooks belong to the dependent test that triggered it, so they are suppressed here.
        boolean runHooks = !skipped && !config.transientCase();

        // 2. before-each runs before the request is sent. A blocking failure marks this test an error and
        // skips both the request and after-each; remaining tests still run.
        if (runHooks && !beforeEachHooks.isEmpty()) {
            HookRunner.PerTestData beforeData = beforeEachPerTest(testSuite, config);
            HookRunner.HookPhaseOutcome be = hookRunner.runPhase(
                    HookPhase.BEFORE_EACH, beforeEachHooks, hookCtx, beforeData, null, listener, asyncHandles);
            if (!be.allSucceeded()) {
                long durationMs = System.currentTimeMillis() - testStart;
                String msg = be.firstFailureMessage() != null
                        ? be.firstFailureMessage()
                        : "before-each hook returned non-zero status";
                List<AssertionFailure> failure = List.of(new AssertionFailure(msg, null, null, null));
                results.add(new TestCaseResult(label, TestResult.ERROR, 0, failure, msg, null, null));
                listener.onProgress(new TestProgressEvent.TestCompleted(
                        uniqueId, rowIndex, label, TestStatus.ERROR, durationMs, 0, failure));
                log.debug("Test case '{}' errored: before-each hook failed: {}", config.name(), msg);
                return new TestOutcome(TestResult.ERROR, msg);
            }
        }

        // Single-element holders written by the capture callbacks inside executeSingleTest and read by
        // the catch branches below. Safe because they are created fresh per step and the callbacks fire
        // synchronously on this thread.
        @Nullable ExecutedRequestInfo[] capturedRequest = new ExecutedRequestInfo[1];
        @Nullable ApiResponse[] capturedResponse = new ApiResponse[1];

        // 3. request + assertions + saved-session capture.
        TestOutcome outcome;
        try {
            executeSingleTest(
                    restClients,
                    defaultRestClient,
                    testSuite,
                    config,
                    rowIndex,
                    suiteDir,
                    configMap,
                    sessionVars,
                    info -> capturedRequest[0] = info,
                    resp -> capturedResponse[0] = resp);
            long durationMs = System.currentTimeMillis() - testStart;
            int totalAssertions = totalAssertionCount(config);
            results.add(new TestCaseResult(
                    label,
                    TestResult.PASSED,
                    totalAssertions,
                    List.of(),
                    null,
                    capturedRequest[0],
                    capturedResponse[0]));
            listener.onProgress(new TestProgressEvent.TestCompleted(
                    uniqueId, rowIndex, label, TestStatus.PASS, durationMs, totalAssertions, List.of()));
            outcome = new TestOutcome(TestResult.PASSED, null);
        } catch (SkipTestException e) {
            long durationMs = System.currentTimeMillis() - testStart;
            results.add(new TestCaseResult(label, TestResult.SKIPPED, 0, List.of(), e.getMessage(), null, null));
            listener.onProgress(new TestProgressEvent.TestCompleted(
                    uniqueId, rowIndex, label, TestStatus.SKIP, durationMs, 0, List.of()));
            log.debug("Test case '{}' skipped: {}", config.name(), e.getMessage());
            outcome = new TestOutcome(TestResult.SKIPPED, null);
        } catch (SessionCaptureException e) {
            long durationMs = System.currentTimeMillis() - testStart;
            List<AssertionFailure> failure = List.of(new AssertionFailure(e.getMessage(), null, null, null));
            results.add(new TestCaseResult(
                    label, TestResult.FAILED, 0, failure, null, capturedRequest[0], capturedResponse[0]));
            listener.onProgress(new TestProgressEvent.TestCompleted(
                    uniqueId, rowIndex, label, TestStatus.FAIL, durationMs, 0, failure));
            log.debug("Test case '{}' failed: session capture error: {}", config.name(), e.getMessage());
            outcome = new TestOutcome(TestResult.FAILED, e.getMessage());
        } catch (NoServerResponseException e) {
            // No response at all: the implicit base_server_response assertion is the only one that
            // could be evaluated, and it failed, so no declared assertion passed.
            long durationMs = System.currentTimeMillis() - testStart;
            List<AssertionFailure> failures = List.of(e.failure());
            results.add(new TestCaseResult(
                    label, TestResult.FAILED, 0, failures, null, capturedRequest[0], capturedResponse[0]));
            listener.onProgress(new TestProgressEvent.TestCompleted(
                    uniqueId, rowIndex, label, TestStatus.FAIL, durationMs, totalAssertionCount(config), failures));
            log.debug("Test case '{}' failed: no response from server: {}", config.name(), e.getMessage());
            outcome = new TestOutcome(TestResult.FAILED, e.getMessage());
        } catch (AssertionFailuresException e) {
            long durationMs = System.currentTimeMillis() - testStart;
            List<AssertionFailure> failures = e.failures();
            int totalAssertions = totalAssertionCount(config);
            int passedAssertions = totalAssertions - failures.size();
            results.add(new TestCaseResult(
                    label,
                    TestResult.FAILED,
                    passedAssertions,
                    failures,
                    null,
                    capturedRequest[0],
                    capturedResponse[0]));
            listener.onProgress(new TestProgressEvent.TestCompleted(
                    uniqueId, rowIndex, label, TestStatus.FAIL, durationMs, totalAssertions, failures));
            log.debug("Test case '{}' failed with {} assertion failure(s)", config.name(), failures.size());
            outcome = new TestOutcome(TestResult.FAILED, firstFailureMessage(failures));
        } catch (Throwable e) {
            long durationMs = System.currentTimeMillis() - testStart;
            List<AssertionFailure> failure = List.of(new AssertionFailure(e.getMessage(), null, null, null));
            results.add(new TestCaseResult(
                    label, TestResult.ERROR, 0, failure, null, capturedRequest[0], capturedResponse[0]));
            listener.onProgress(new TestProgressEvent.TestCompleted(
                    uniqueId, rowIndex, label, TestStatus.ERROR, durationMs, 0, failure));
            log.error("Test case '{}' errored: {}", config.name(), e.getMessage(), e);
            outcome = new TestOutcome(TestResult.ERROR, e.getMessage());
        }

        // 4. after-each runs after this test's assertions complete (same transient/skip gate).
        if (runHooks && !afterEachHooks.isEmpty()) {
            HookRunner.PerTestData afterData =
                    afterEachPerTest(testSuite, config, capturedRequest[0], outcome.result());
            hookRunner.runPhase(HookPhase.AFTER_EACH, afterEachHooks, hookCtx, afterData, null, listener, asyncHandles);
        }

        return outcome;
    }

    /**
     * Builds the concise failure message stored on a failed test's {@link TestOutcome} for dependency
     * propagation, using the first collected assertion failure (its {@code error} when present, else its
     * {@code description}).
     *
     * @param failures the non-empty list of assertion failures; an empty list yields a generic message
     * @return a short human-readable failure message
     */
    private static String firstFailureMessage(List<AssertionFailure> failures) {
        if (failures.isEmpty()) {
            return "assertion failed";
        }
        AssertionFailure first = failures.get(0);
        return first.error() != null ? first.error() : first.description();
    }

    /**
     * Builds the run-level {@link HookRunner.HookInvocationContext} from the suite and run context.
     *
     * @param testSuite the suite being run
     * @param context the run context (carries the runID and hook run metadata)
     * @param suiteDir the suite file's directory, or {@code null}
     * @return the immutable hook invocation context
     */
    private static HookRunner.HookInvocationContext buildHookContext(
            TestSuite testSuite, SuiteRunContext context, @Nullable Path suiteDir) {
        var meta = context.hookRunMetadata();
        return new HookRunner.HookInvocationContext(
                testSuite.name(),
                context.getRunID(),
                meta.interactive(),
                meta.reportDir(),
                meta.reportPath(),
                meta.tagFilter(),
                meta.testNameFilter(),
                meta.envFilePath(),
                suiteDir,
                context.env(),
                testSuite.restClientsById());
    }

    /**
     * Returns the hooks declared for {@code phase}, or an empty list when the suite declares no
     * {@code hooks} block.
     *
     * @param hooks the suite's hooks block, or {@code null}
     * @param phase the phase whose hooks are requested
     * @return a non-null list of hooks for the phase
     */
    private static List<Hook> phaseHooks(@Nullable Hooks hooks, HookPhase phase) {
        return hooks != null ? hooks.forPhase(phase) : List.of();
    }

    /**
     * Builds {@link HookRunner.PerTestData} for a {@code before-each} hook using the pre-request
     * (raw) test-case data: the resolved full URL, method, and declared headers. The body is not yet
     * resolved at this point and is reported as {@code null}.
     *
     * @param testSuite the suite (for rest-client resolution)
     * @param config the test case about to run
     * @return the per-test data for {@code before-each}
     */
    private static HookRunner.PerTestData beforeEachPerTest(TestSuite testSuite, TestCase config) {
        String id = resolveRestClientId(testSuite, config);
        RestClientConfig rc = testSuite.restClientsById().get(id);
        String url = resolveFullUrl(rc, config.request().url());
        return new HookRunner.PerTestData(
                config.name(), url, config.request().method(), config.request().headers(), null, null);
    }

    /**
     * Builds {@link HookRunner.PerTestData} for an {@code after-each} hook, preferring the fully
     * resolved request captured during execution (URL, headers, body) and including the test's result
     * status.
     *
     * @param testSuite the suite (for rest-client resolution fallback)
     * @param config the test case that ran
     * @param captured the captured resolved request, or {@code null} when the request was never sent
     * @param result the test's result
     * @return the per-test data for {@code after-each}
     */
    private static HookRunner.PerTestData afterEachPerTest(
            TestSuite testSuite, TestCase config, @Nullable ExecutedRequestInfo captured, TestResult result) {
        String status =
                switch (result) {
                    case PASSED -> "passed";
                    case FAILED -> "failed";
                    case ERROR -> "error";
                    case SKIPPED -> "skipped";
                };
        if (captured != null) {
            return new HookRunner.PerTestData(
                    config.name(), captured.url(), captured.method(), captured.headers(), captured.body(), status);
        }
        String id = resolveRestClientId(testSuite, config);
        RestClientConfig rc = testSuite.restClientsById().get(id);
        String url = resolveFullUrl(rc, config.request().url());
        return new HookRunner.PerTestData(
                config.name(), url, config.request().method(), config.request().headers(), null, status);
    }

    /**
     * Executes a single test case identified by its index in the suite.
     *
     * <p>
     * The method first fetches the raw {@link TestCase} at position {@code i} from
     * {@code
     * testSuite} and extracts its per-test {@code variables}. Those variables are
     * merged into a new
     * {@code testConfigMap} under the {@code "test"} key (replacing the
     * initially-empty placeholder
     * that was set in {@link #runConfigurationSuite}).
     *
     * <p>
     * If the suite carries a {@code templateContent} (i.e. it was loaded via
     * {@link io.github.snytkine.apitester.api_tester_cli.service.TestSuiteLoader#load(java.nio.file.Path,
     * io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext)}), the raw
     * YAML template is
     * re-processed through Thymeleaf using {@code testConfigMap} so that per-test
     * variable
     * expressions (e.g. {@code [[${test.username}]]} in the request URL or headers)
     * are resolved.
     * The resolved {@link TestCase} is then located in the re-parsed suite by matching
     * {@link TestCase#name()} rather than by position index. This is necessary because
     * {@code rawConfig} originates from the (possibly filtered, possibly dependency-reordered)
     * execution plan, while {@code templateContent} still holds the full original YAML;
     * looking up by name is always correct because
     * {@link io.github.snytkine.apitester.api_tester_cli.service.TestSuiteValidator}
     * guarantees unique names.
     * When {@code templateContent} is absent the raw test case is used as-is.
     *
     * <p>
     * HTTP errors propagate as unchecked exceptions; assertion failures surface as
     * {@link
     * MultipleFailuresError}. Both are caught by the caller in
     * {@link #runConfigurationSuite}.
     *
     * @param restClients     the configured clients for this suite run, keyed by id
     * @param defaultRestClient the default client, used when a request selects no client or
     *                          selects an unknown one
     * @param testSuite  the loaded test suite containing the raw YAML template and
     *                   test cases
     * @param rawConfig  the raw (pre-template-resolution) test case to execute, taken from the
     *                   execution plan
     * @param rowIndex   the plan row index of this execution, used only for log correlation
     * @param suiteDir   the directory of the suite file, or {@code null} if
     *                   unavailable
     * @param configMap      suite-level variable namespaces ({@code cli}, {@code env}, {@code
     *                       suite}, {@code test}, {@code session}); the {@code "test"} entry is
     *                       replaced per invocation with this test's vars
     * @param sessionVars    the suite-wide, mutable {@code session} namespace; read when resolving
     *                       this test's template and written with any {@code saved-session} captures
     *                       declared by this test only after all of its assertions have passed, so a
     *                       failed test never contributes values to the namespace
     * @param requestCapture callback invoked with the fully-resolved {@link ExecutedRequestInfo}
     *                       immediately before the HTTP request is dispatched; always called for
     *                       non-skipped tests regardless of whether assertions later pass or fail,
     *                       allowing the caller to capture request details for both outcomes
     * @param responseCapture callback invoked with the {@link ApiResponse} immediately after the
     *                        HTTP response is received and parsed; called before assertions are
     *                        evaluated, allowing the caller to capture response details regardless
     *                        of assertion outcome
     * @throws IOException if the template cannot be re-parsed or a file-type request body cannot
     *     be read from disk
     */
    private void executeSingleTest(
            Map<String, RestClient> restClients,
            RestClient defaultRestClient,
            TestSuite testSuite,
            TestCase rawConfig,
            int rowIndex,
            @Nullable Path suiteDir,
            Map<String, Map<String, String>> configMap,
            Map<String, String> sessionVars,
            Consumer<ExecutedRequestInfo> requestCapture,
            Consumer<ApiResponse> responseCapture)
            throws IOException {

        if (rawConfig.skip() != null && !rawConfig.skip().isBlank()) {
            throw new SkipTestException(rawConfig.skip());
        }
        log.debug(
                "Test [{}] '{}': beginning execution, raw request {} {}",
                rowIndex,
                rawConfig.name(),
                rawConfig.request().method(),
                rawConfig.request().url());

        Map<String, String> testVariables = Objects.requireNonNullElse(rawConfig.variables(), Map.of());
        log.debug("Test [{}] '{}': {} test-level variable(s) found", rowIndex, rawConfig.name(), testVariables.size());

        Map<String, Map<String, String>> mutableConfigMap = new LinkedHashMap<>(configMap);
        mutableConfigMap.put("test", testVariables);
        Map<String, Map<String, String>> testConfigMap = Map.copyOf(mutableConfigMap);

        // Re-parse the suite template with per-test variables in context so that
        // expressions like
        // [[${test.username}]] in URLs, headers, or bodies are resolved for this
        // specific test.
        // Skip re-parsing when there are neither test-level variables nor any captured
        // session values — with both empty, no [[${test.*}]] or [[${session.*}]]
        // expression can resolve to anything, so rawConfig is already fully resolved.
        TestCase config;
        if (testSuite.templateContent() != null && (!testVariables.isEmpty() || !sessionVars.isEmpty())) {
            log.debug(
                    "Test [{}] '{}': re-parsing template with {} test variable(s)",
                    rowIndex,
                    rawConfig.name(),
                    testVariables.size());
            String resolvedYaml = FileLoader.parseFile(testSuite.templateContent(), testConfigMap);
            TestSuite resolvedSuite = yamlMapper.readValue(resolvedYaml, TestSuite.class);
            // Look up the resolved test case by name rather than by position.
            // rawConfig comes from the execution plan (filtered and/or dependency-reordered),
            // while templateContent still contains the full original YAML. A positional lookup
            // on the re-parsed (unfiltered, file-ordered) suite could fetch the wrong test case;
            // a name-based lookup is always correct because TestSuiteValidator guarantees unique
            // names.
            String targetName = rawConfig.name();
            config = resolvedSuite.tests().stream()
                    .filter(tc -> targetName.equals(tc.name()))
                    .findFirst()
                    .orElse(rawConfig);
            log.debug(
                    "Test [{}] '{}': resolved request {} {}",
                    rowIndex,
                    config.name(),
                    config.request().method(),
                    config.request().url());
        } else {
            log.debug(
                    "Test [{}] '{}': skipping template re-parse ({})",
                    rowIndex,
                    rawConfig.name(),
                    testSuite.templateContent() == null ? "no templateContent" : "no test variables");
            config = rawConfig;
        }

        log.debug(
                "Test [{}] '{}': sending {} {}",
                rowIndex,
                config.name(),
                config.request().method(),
                config.request().url());

        // Resolve body before building the request spec so the same string can be
        // captured in ExecutedRequestInfo without loading the file twice.
        @Nullable String resolvedBody = null;
        if (config.request() instanceof PayloadRequest pr && pr.body() != null) {
            resolvedBody = loadBodyContent(pr.body(), suiteDir, testConfigMap);
        }

        // Fire callback before retrieve() so the caller has request details for both
        // PASS and FAIL outcomes (MultipleFailuresError is thrown after this point).
        String restClientId = resolveRestClientId(testSuite, config);
        RestClientConfig selectedRestClientConfig = testSuite.restClientsById().get(restClientId);
        requestCapture.accept(new ExecutedRequestInfo(
                config.request().method(),
                resolveFullUrl(selectedRestClientConfig, config.request().url()),
                config.request().headers(),
                resolvedBody,
                resolveEffectiveAuth(testSuite, config),
                restClientId));

        // Every test case implicitly asserts that the service answered at all. The implicit
        // assertion is prepended to the declared ones so it is evaluated (and counted) first;
        // it is never read back from the test case, which still holds only the declared list.
        List<Assertion> effectiveAssertions = withBaseServerResponseAssertion(config, selectedRestClientConfig);

        RestClient selectedClient = selectRestClient(restClients, defaultRestClient, config);
        RestClient.RequestBodySpec requestSpec = buildRequestSpec(selectedClient, config, resolvedBody);

        log.debug("Test [{}] '{}': evaluating {} assertion(s)", rowIndex, config.name(), effectiveAssertions.size());

        // Force full (body-reading) resolution when this test captures saved-session values, so the
        // response body is available even if the test's only assertion is a status_code check.
        boolean hasCaptures =
                config.savedSession() != null && !config.savedSession().isEmpty();

        // Sending the request and reading the response is the window in which the implicit
        // base_server_response assertion can fail: any transport-level exception here (connection
        // refused, unknown host, TLS failure, connection timeout) means no response was received,
        // so none of the declared assertions can be evaluated and the test fails on this one
        // assertion alone.
        ApiResponse apiResponse;
        try {
            RestClient.ResponseSpec responseSpec = requestSpec.retrieve();
            apiResponse = responseResolver.resolve(responseSpec, effectiveAssertions, hasCaptures);
        } catch (RuntimeException e) {
            BaseServerResponseAssertion baseAssertion = (BaseServerResponseAssertion) effectiveAssertions.get(0);
            log.debug("Test [{}] '{}': no response received: {}", rowIndex, config.name(), e.toString());
            String proxyReason = proxyFailureReason(e, selectedRestClientConfig, config, configMap.get("env"));
            if (proxyReason != null) {
                log.warn("Test [{}] '{}': proxy failure: {}", rowIndex, config.name(), proxyReason);
            }
            throw new NoServerResponseException(baseServerResponseFailure(baseAssertion, e, proxyReason), e);
        }
        log.debug("Test [{}] '{}': received status {}", rowIndex, config.name(), apiResponse.statusCode());

        // A proxy that rejects the request answers it rather than failing the connection, so a 407
        // arrives here as an ordinary response. Left alone it would fail an unrelated status-code
        // assertion with "expected 200 but was 407", hiding the fact that the service was never
        // reached at all. Only reinterpreted when this client actually goes through a proxy.
        if (Integer.valueOf(407).equals(apiResponse.statusCode())) {
            String proxyAuthReason =
                    proxyAuthResponseReason(selectedRestClientConfig, config, configMap.get("env"), apiResponse);
            if (proxyAuthReason != null) {
                BaseServerResponseAssertion baseAssertion = (BaseServerResponseAssertion) effectiveAssertions.get(0);
                log.warn("Test [{}] '{}': proxy failure: {}", rowIndex, config.name(), proxyAuthReason);
                throw new NoServerResponseException(
                        baseServerResponseFailure(baseAssertion, null, proxyAuthReason), null);
            }
        }

        responseCapture.accept(apiResponse);

        // Evaluate one assertion at a time, collecting failures as AssertionFailedError instances.
        // Each evaluator stores structured AssertionFailedError entries (with message, expected,
        // actual) in the collector. The first new entry for each assertion is extracted to build an
        // AssertionFailure: description from the assertion definition, error from the AFE message,
        // and expected/actual from the AFE structured fields.
        List<AssertionFailure> failures = new ArrayList<>();
        FailureCollector collector = new FailureCollector();
        for (Assertion assertion : effectiveAssertions) {
            AssertionEvaluator evaluator = evaluatorFactory.create(assertion, suiteDir, testConfigMap);
            int failuresBefore = collector.getFailures().size();
            evaluator.evaluate(apiResponse, collector);
            if (collector.getFailures().size() > failuresBefore) {
                AssertionFailedError afe = collector.getFailures().get(failuresBefore);
                String description = evaluatorFactory.describe(assertion);
                String expected = afe.isExpectedDefined()
                        ? String.valueOf(afe.getExpected().getValue())
                        : null;
                String actual =
                        afe.isActualDefined() ? String.valueOf(afe.getActual().getValue()) : null;
                failures.add(new AssertionFailure(description, expected, actual, afe.getMessage()));
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionFailuresException(failures);
        }

        // Capture saved-session values only after every assertion has passed, so a test whose
        // assertions fail never leaks its extracted values into the suite-wide 'session' namespace
        // for later (dependent) tests to consume. A required-but-missing value, a non-primitive
        // extraction, or a failed type conversion raises a SessionCaptureException that the run loop
        // records as a test failure.
        SessionCapturer.capture(config.name(), config.savedSession(), apiResponse, sessionVars);
    }

    /**
     * Returns the assertion list actually evaluated for a test case: the implicit {@link
     * BaseServerResponseAssertion} followed by the assertions declared in the YAML.
     *
     * <p>The implicit assertion is added to <em>every</em> test case, including one that declares no
     * assertions at all, so that every test at minimum verifies the service responded. It is placed
     * first so it is evaluated (and reported) before any declared assertion.
     *
     * @param config the resolved test case being executed
     * @param selectedRestClientConfig the configuration of the rest-client dispatching this request,
     *     or {@code null} when the suite declares no matching client
     * @return an unmodifiable list of {@code declared + 1} assertions, the implicit one first
     */
    private static List<Assertion> withBaseServerResponseAssertion(
            TestCase config, @Nullable RestClientConfig selectedRestClientConfig) {
        List<Assertion> effective = new ArrayList<>(config.assertions().size() + 1);
        effective.add(new BaseServerResponseAssertion(timeoutSeconds(selectedRestClientConfig)));
        effective.addAll(config.assertions());
        return List.copyOf(effective);
    }

    /**
     * Returns the number of assertions reported for a test case: its declared assertions plus the
     * one implicit {@link BaseServerResponseAssertion} the engine always evaluates.
     *
     * @param config the test case whose assertion count is reported
     * @return the declared assertion count incremented by one
     */
    private static int totalAssertionCount(TestCase config) {
        return config.assertions().size() + 1;
    }

    /**
     * Resolves the timeout, in whole seconds, reported by the implicit {@code base_server_response}
     * assertion for a request dispatched through {@code restClientConfig}.
     *
     * <p>The value is the client's {@code connect-timeout}, falling back to {@link
     * RestClientConfig#DEFAULT_CONNECT_TIMEOUT_MS} when the suite declares no explicit timeout (or
     * no matching client at all), which is the timeout the underlying HTTP client actually applies.
     *
     * @param restClientConfig the resolved rest-client configuration, or {@code null}
     * @return the effective timeout in seconds
     */
    private static int timeoutSeconds(@Nullable RestClientConfig restClientConfig) {
        int timeoutMs = restClientConfig != null && restClientConfig.connectTimeout() != null
                ? restClientConfig.connectTimeout()
                : RestClientConfig.DEFAULT_CONNECT_TIMEOUT_MS;
        return timeoutMs / 1000;
    }

    /**
     * Builds the structured failure recorded when no HTTP response could be obtained, reporting the
     * implicit assertion's expected text against the transport error that occurred instead.
     *
     * <p>When the request was routed through a proxy and the failure is recognisably the proxy's
     * doing, {@code proxyReason} replaces the raw transport message. A bare "connection refused"
     * against a healthy endpoint is deeply misleading when the thing that actually refused the
     * connection is the proxy in front of it.
     *
     * @param assertion the implicit assertion that failed, carrying the effective timeout
     * @param cause the transport exception thrown while sending the request or reading the response
     * @param proxyReason a proxy-specific explanation of the failure, or {@code null} when the
     *     failure is not proxy-related
     * @return the {@link AssertionFailure} to attach to the failed test case
     */
    private static AssertionFailure baseServerResponseFailure(
            BaseServerResponseAssertion assertion, @Nullable Throwable cause, @Nullable String proxyReason) {
        String reason =
                proxyReason != null ? proxyReason : (cause != null ? rootCauseMessage(cause) : "no cause reported");
        String detail = proxyReason != null
                ? "The request did not reach the service: " + reason
                : "The service did not return a response: " + reason;
        return new AssertionFailure(
                BaseServerResponseAssertion.TYPE_NAME,
                assertion.expectedDescription(),
                "no response received: " + reason,
                detail);
    }

    /**
     * Returns a proxy-specific explanation for a transport failure, or {@code null} when the
     * request was not proxied or the failure is not recognisably proxy-related.
     *
     * <p>The proxy settings are re-resolved here rather than threaded down from client
     * construction: resolution is a pure function of the client config and the environment, and
     * doing it on the error path only keeps the success path untouched. It cannot fail here —
     * {@link #buildRestClient} already resolved the same config against the same environment before
     * any request was sent, so an unusable proxy configuration has aborted the run long before this
     * point.
     *
     * @param cause the transport exception
     * @param clientConfig the rest-client the request used, or {@code null} when unknown
     * @param testCase the test case being executed, used to determine the target scheme
     * @param env the merged environment
     * @return a proxy-specific message, or {@code null}
     */
    private static @Nullable String proxyFailureReason(
            Throwable cause,
            @Nullable RestClientConfig clientConfig,
            TestCase testCase,
            @Nullable Map<String, String> env) {
        if (clientConfig == null) {
            return null;
        }
        ProxySettings settings = ProxyResolver.resolve(clientConfig, env);
        return ProxyErrorClassifier.classify(cause, settings, targetScheme(clientConfig, testCase));
    }

    /**
     * Returns a proxy-specific explanation for a {@code 407} response, or {@code null} when the
     * request was not proxied.
     *
     * @param clientConfig the rest-client the request used, or {@code null} when unknown
     * @param testCase the test case being executed, used to determine the target scheme
     * @param env the merged environment
     * @param response the {@code 407} response, whose {@code Proxy-Authenticate} header names the
     *     challenged scheme
     * @return a proxy-specific message, or {@code null}
     */
    private static @Nullable String proxyAuthResponseReason(
            @Nullable RestClientConfig clientConfig,
            TestCase testCase,
            @Nullable Map<String, String> env,
            ApiResponse response) {
        if (clientConfig == null) {
            return null;
        }
        ProxySettings settings = ProxyResolver.resolve(clientConfig, env);
        return ProxyErrorClassifier.classifyProxyAuthResponse(
                settings, targetScheme(clientConfig, testCase), headerIgnoringCase(response, "Proxy-Authenticate"));
    }

    /**
     * Looks up a response header by case-insensitive name.
     *
     * @param response the response whose headers to search
     * @param name the header name
     * @return the header value, or {@code null} when absent
     */
    static @Nullable String headerIgnoringCase(ApiResponse response, String name) {
        Map<String, String> headers = response.headers();
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Determines the URI scheme a test case's request actually targets, so the matching proxy can
     * be named in an error message.
     *
     * <p>An absolute request URL carries its own scheme; a relative one inherits the rest-client's
     * base URL.
     *
     * @param clientConfig the rest-client used for the request
     * @param testCase the test case being executed
     * @return {@code "https"}, {@code "http"}, or {@code null} when neither URL declares a scheme
     */
    static @Nullable String targetScheme(RestClientConfig clientConfig, TestCase testCase) {
        String requestUrl = testCase.request().url();
        String candidate = requestUrl != null && requestUrl.contains("://") ? requestUrl : clientConfig.baseUrl();
        if (candidate == null) {
            return null;
        }
        int separator = candidate.indexOf("://");
        return separator < 0 ? null : candidate.substring(0, separator).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Extracts the most specific available message from a transport exception chain.
     *
     * <p>Transport failures arrive wrapped by Spring (e.g. {@code ResourceAccessException} around a
     * {@code ConnectException}), and the wrapper's own message only restates the request URL —
     * often with a literal {@code null} interpolated where the cause had no message. The search
     * therefore starts at the first cause when there is one and keeps the deepest non-blank message
     * found from there down. A refused connection carries no message anywhere in its chain on some
     * JDKs, in which case the wrapped exception's type name is the reason reported.
     *
     * @param throwable the exception to unwrap
     * @return the deepest non-blank message below the outermost wrapper, or the wrapped exception's
     *     class name when the chain carries no message at all
     */
    private static String rootCauseMessage(Throwable throwable) {
        Throwable start =
                throwable.getCause() != null && throwable.getCause() != throwable ? throwable.getCause() : throwable;
        String message = null;
        for (Throwable current = start; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return message != null ? message : start.getClass().getSimpleName();
    }

    /**
     * Builds a {@link RestClient.RequestBodySpec} from the test case's request
     * definition, applying headers and, when applicable, attaching the pre-resolved body string.
     *
     * <p>Body content is resolved by the caller ({@link #executeSingleTest}) via {@link
     * #loadBodyContent} before this method is invoked, so the same resolved string can be captured
     * in {@link ExecutedRequestInfo} without reading the file twice.
     *
     * @param restClient   the client to use for building the request
     * @param config       the test case whose request is being built
     * @param resolvedBody the fully-resolved body string to attach, or {@code null} for bodyless
     *     requests
     * @return a fully configured request spec ready for {@link
     *     RestClient.RequestBodySpec#retrieve()}
     */
    private RestClient.RequestBodySpec buildRequestSpec(
            RestClient restClient, TestCase config, @Nullable String resolvedBody) {
        RestClient.RequestBodySpec requestSpec = restClient
                .method(toSpringHttpMethod(config.request().method()))
                .uri(config.request().url());

        if (config.request().headers() != null) {
            for (Map.Entry<String, String> header : config.request().headers().entrySet()) {
                requestSpec.header(header.getKey(), header.getValue());
            }
        }

        RequestAuth auth = config.request().auth();
        if (auth != null
                && auth.type() == AuthType.BASIC
                && !hasAuthorizationHeader(config.request().headers())) {
            requestSpec.header(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue(auth));
        }

        if (resolvedBody != null) {
            requestSpec.body(resolvedBody);
        }

        return requestSpec;
    }

    /**
     * Resolves the body content from a {@link RequestBody} descriptor.
     *
     * <p>
     * For {@code FILE} bodies the file at {@link RequestBody#content()} is read
     * relative to
     * {@code suiteDir} and then processed through the Thymeleaf TEXT-mode template
     * engine with all
     * variable namespaces from {@code configMap} ({@code suite}, {@code test},
     * {@code cli},
     * {@code env}) available as top-level context variables.
     *
     * <p>
     * For {@code STRING} bodies the {@link RequestBody#content()} value is returned
     * as-is,
     * without any template processing.
     *
     * @param body      the request-body descriptor from the test case
     * @param suiteDir  the directory of the suite file; required when
     *                  {@code body.type()} is {@code
     *     FILE}
     * @param configMap all variable namespaces; each entry's key becomes a
     *                  top-level Thymeleaf
     *                  variable
     * @return the resolved body string ready to be sent with the HTTP request
     * @throws IOException                   if the file cannot be read
     * @throws IllegalStateException         if {@code type} is {@code FILE} but
     *                                       {@code suiteDir} is {@code
     *     null}
     * @throws UnsupportedOperationException if the body type is not yet supported
     */
    static String loadBodyContent(RequestBody body, @Nullable Path suiteDir, Map<String, Map<String, String>> configMap)
            throws IOException {
        return switch (body.type()) {
            case STRING -> body.content();
            case FILE -> {
                if (suiteDir == null) {
                    throw new IllegalStateException(
                            "Suite directory is required to resolve file body: " + body.content());
                }
                String raw = FileLoader.loadFile(suiteDir, body.content());
                yield FileLoader.parseFile(raw, configMap);
            }
            default ->
                throw new UnsupportedOperationException("Request body type '" + body.type() + "' is not yet supported");
        };
    }

    /**
     * Logs how a rest-client's proxy was resolved.
     *
     * <p>An active proxy is reported at INFO, since routing every request through a third party is
     * something a user should see without enabling debug logging. A client that opts out with
     * {@code proxy: false} while an environment proxy <em>is</em> present is reported at DEBUG:
     * that combination is deliberate, but it is also exactly what someone stares at when wondering
     * why one client behaves differently from another.
     *
     * <p>Only {@link ProxySettings#describe()} is logged, which names hosts and ports and never
     * credentials.
     *
     * @param config the rest-client being built
     * @param settings the resolved settings, or {@code null} when this client connects directly
     * @param env the merged environment, consulted only to detect the opt-out-with-env case
     */
    private static void logProxySelection(
            RestClientConfig config, @Nullable ProxySettings settings, Map<String, String> env) {
        String clientId = config.id() != null ? config.id() : TestSuite.DEFAULT_REST_CLIENT_ID;
        if (settings != null) {
            log.info(
                    "rest-client '{}': routing requests through proxy {}, authentication {}",
                    clientId,
                    settings.describe(),
                    settings.requiresAuthentication() ? "enabled" : "not configured");
            return;
        }
        if (config.proxy() != null && config.proxy().isDisabled() && log.isDebugEnabled()) {
            String ignored = ignoredEnvironmentProxy(env);
            if (ignored != null) {
                log.debug(
                        "rest-client '{}': proxy explicitly disabled (proxy: false); ignoring environment proxy {}",
                        clientId,
                        ignored);
            }
        }
    }

    /**
     * Describes the environment proxy that a {@code proxy: false} client is choosing to ignore, or
     * {@code null} when there is nothing to report.
     *
     * <p>A malformed environment value yields {@code null} rather than an error: the opt-out is
     * absolute, so such a client never consults the environment and must not be failed — or even
     * warned — by a value that is irrelevant to it. Clients that would actually use the value have
     * it validated before the run.
     *
     * @param env the merged environment
     * @return a credential-free description of the ignored proxy, or {@code null}
     */
    static @Nullable String ignoredEnvironmentProxy(Map<String, String> env) {
        try {
            ProxySettings environmentProxy = ProxyResolver.fromEnvironment(env);
            return environmentProxy == null ? null : environmentProxy.describe();
        } catch (ProxyConfigurationException e) {
            return null;
        }
    }

    /**
     * Builds a {@link RestClient} configured from the given
     * {@link RestClientConfig}.
     *
     * <p>
     * If {@code config} carries a non-blank {@code baseUrl} it is set as the
     * client's default base
     * URL. When a {@code connectTimeout}, a custom {@code ssl} configuration and/or
     * {@code follow-redirects: false} is present AND the injected factory is a {@link
     * org.springframework.http.client.JdkClientHttpRequestFactory}, a new
     * JDK-backed factory is created whose {@link java.net.http.HttpClient} carries
     * the connect timeout, the custom {@link javax.net.ssl.SSLContext} and the
     * redirect policy; non-JDK factories (e.g. stub factories used in tests) are
     * never replaced. When {@code headers} is non-null each entry is registered as a
     * default header applied
     * to every request built with this client.
     *
     * <p>
     * {@code follow-redirects: false} maps to {@link
     * java.net.http.HttpClient.Redirect#NEVER}, which makes a 3xx response the final
     * response seen by assertions rather than being transparently followed. The
     * default ({@code true}) maps to {@link
     * java.net.http.HttpClient.Redirect#NORMAL}.
     *
     * <p>
     * When a proxy is resolved for this client (see {@link ProxyResolver}) the JDK
     * client is additionally given a scheme-aware {@link java.net.ProxySelector} and,
     * if the proxy needs credentials, a {@link ProxyAuthenticator} that answers only
     * proxy challenges. A client that resolves to no proxy — including one declaring
     * {@code proxy: false} — is built exactly as it would be without this feature, so
     * opting out never changes redirect or protocol behaviour as a side effect.
     *
     * @param config   the suite-level REST client settings
     * @param suiteDir the directory of the suite file, used to resolve relative
     *                 SSL certificate/key paths; may be {@code null}
     * @param env      the merged environment used to resolve {@code HTTP_PROXY},
     *                 {@code HTTPS_PROXY} and {@code PROXY_USE_ENV}
     * @return a fully configured {@link RestClient} ready for use
     */
    private RestClient buildRestClient(
            RestClientConfig config,
            java.nio.file.@org.jspecify.annotations.Nullable Path suiteDir,
            Map<String, String> env) {
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (StringUtils.hasText(config.baseUrl())) {
            builder.baseUrl(config.baseUrl());
        }
        javax.net.ssl.SSLContext sslContext = SslContextFactory.create(config.ssl(), suiteDir);
        ProxySettings proxySettings = ProxyResolver.resolve(config, env);
        if (proxySettings != null && proxySettings.isEmpty()) {
            proxySettings = null;
        }
        logProxySelection(config, proxySettings, env);
        boolean followRedirects = config.followRedirectsOrDefault();
        boolean needsCustomHttpClient =
                (config.connectTimeout() != null || sslContext != null || !followRedirects || proxySettings != null)
                        && requestFactory instanceof org.springframework.http.client.JdkClientHttpRequestFactory;
        if (needsCustomHttpClient) {
            // Mirror the defaults of the application's default factory (HttpClientConfig) so that
            // enabling a connect timeout or custom SSL does not silently change redirect/protocol
            // behavior. NEVER makes the JDK client surface a 3xx response as the final response,
            // so assertions can inspect its status code and Location header.
            java.net.http.HttpClient.Builder httpClientBuilder = java.net.http.HttpClient.newBuilder()
                    .version(java.net.http.HttpClient.Version.HTTP_2)
                    .followRedirects(
                            followRedirects
                                    ? java.net.http.HttpClient.Redirect.NORMAL
                                    : java.net.http.HttpClient.Redirect.NEVER);
            if (config.connectTimeout() != null) {
                httpClientBuilder.connectTimeout(Duration.ofMillis(config.connectTimeout()));
            }
            if (sslContext != null) {
                httpClientBuilder.sslContext(sslContext);
            }
            if (proxySettings != null) {
                httpClientBuilder.proxy(proxySettings.toProxySelector());
                if (proxySettings.requiresAuthentication()) {
                    httpClientBuilder.authenticator(new ProxyAuthenticator(proxySettings));
                }
            }
            builder.requestFactory(
                    new org.springframework.http.client.JdkClientHttpRequestFactory(httpClientBuilder.build()));
        }
        if (config.headers() != null) {
            config.headers().forEach((name, value) -> builder.defaultHeader(name, value));
        }
        RequestAuth suiteAuth = config.auth();
        if (suiteAuth != null && suiteAuth.type() == AuthType.BASIC) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue(suiteAuth));
        }
        return builder.build();
    }

    /**
     * Selects the {@link RestClient} a test case's request should use.
     *
     * <p>When the request declares no {@code rest-client} id, or declares one that is not among the
     * suite's configured clients (which can happen when the suite uses the singular {@code
     * rest-client} form, where the selector is ignored), the {@code defaultRestClient} is returned. A
     * warning is logged when a declared id cannot be resolved. When the suite uses the plural {@code
     * rest-clients} form, {@link TestSuiteValidator} has already guaranteed that any declared id
     * exists.
     *
     * @param restClients       the configured clients keyed by id
     * @param defaultRestClient the fallback client
     * @param config            the test case whose request selects the client
     * @return the {@link RestClient} to use for this request
     */
    private RestClient selectRestClient(
            Map<String, RestClient> restClients, RestClient defaultRestClient, TestCase config) {
        String requestedId = config.request().restClient();
        if (requestedId == null) {
            return defaultRestClient;
        }
        RestClient selected = restClients.get(requestedId);
        if (selected != null) {
            return selected;
        }
        log.warn(
                "Test '{}' request selects rest-client '{}' which is not defined; using the default client",
                config.name(),
                requestedId);
        return defaultRestClient;
    }

    /**
     * Converts this project's {@link HttpMethod} enum to a Spring {@link
     * org.springframework.http.HttpMethod}.
     *
     * @param method the HTTP method from the test case model
     * @return the corresponding Spring HTTP method
     */
    private org.springframework.http.HttpMethod toSpringHttpMethod(HttpMethod method) {
        return org.springframework.http.HttpMethod.valueOf(method.name());
    }

    /**
     * Builds the {@code Basic <base64(user:pass)>} header value for Basic authentication.
     *
     * <p>This method is stateless and thread-safe.
     *
     * @param auth the authentication configuration with username and password
     * @return the HTTP Authorization header value for Basic auth
     */
    private static String basicAuthHeaderValue(RequestAuth auth) {
        String credentials = auth.username() + ":" + auth.password();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Checks whether the given headers map contains an Authorization header (case-insensitive).
     *
     * <p>This method is stateless and thread-safe.
     *
     * @param headers the request headers map, or {@code null}
     * @return {@code true} when an {@code Authorization} header is present (case-insensitive),
     *     {@code false} otherwise
     */
    private static boolean hasAuthorizationHeader(@Nullable Map<String, String> headers) {
        if (headers == null) {
            return false;
        }
        return headers.keySet().stream().anyMatch(h -> h.equalsIgnoreCase(HttpHeaders.AUTHORIZATION));
    }

    /**
     * Resolves the authentication that actually applies to a test case's request, mirroring the
     * precedence implemented across {@link #buildRestClient} and {@link #buildRequestSpec}: a
     * request-level {@code auth} wins when present and not itself overridden by an explicit {@code
     * Authorization} header (in which case the header wins and no auth is reported); otherwise the
     * selected rest-client's suite-level {@code auth} applies, falling back to the suite's default
     * rest-client the same way {@link #selectRestClient} does when the request selects no client or
     * an unresolvable one.
     *
     * @param testSuite the suite whose {@link TestSuite#restClientsById()} provides the rest-client
     *     configurations to fall back to
     * @param config the test case whose request's effective auth is being resolved
     * @return the {@link RequestAuth} actually applied to this request, or {@code null} when none was
     *     applied
     */
    private static @Nullable RequestAuth resolveEffectiveAuth(TestSuite testSuite, TestCase config) {
        RequestAuth requestAuth = config.request().auth();
        if (requestAuth != null && !hasAuthorizationHeader(config.request().headers())) {
            return requestAuth;
        }
        String id = config.request().restClient();
        RestClientConfig restClientConfig = testSuite
                .restClientsById()
                .getOrDefault(id, testSuite.restClientsById().get(TestSuite.DEFAULT_REST_CLIENT_ID));
        return restClientConfig != null ? restClientConfig.auth() : null;
    }

    /**
     * Resolves the id of the rest-client that will actually handle a test case's request, mirroring
     * the exact fallback rule implemented in {@link #selectRestClient}: the request's declared {@code
     * rest-client} id when it names a client configured in the suite, otherwise {@link
     * TestSuite#DEFAULT_REST_CLIENT_ID}.
     *
     * @param testSuite the suite whose {@link TestSuite#restClientsById()} is checked for the
     *     declared id
     * @param config the test case whose request's rest-client id is being resolved
     * @return the resolved, always non-null rest-client id
     */
    private static String resolveRestClientId(TestSuite testSuite, TestCase config) {
        String requestedId = config.request().restClient();
        if (requestedId != null && testSuite.restClientsById().containsKey(requestedId)) {
            return requestedId;
        }
        return TestSuite.DEFAULT_REST_CLIENT_ID;
    }

    /**
     * Combines a rest-client's {@code base-url} with a request's declared URL when that URL is
     * relative, producing the full URL actually dispatched. Absolute declared URLs, and declared
     * URLs paired with a rest-client that has no {@code base-url}, are returned unchanged.
     *
     * @param restClientConfig the resolved rest-client configuration, or {@code null} if somehow
     *     unresolvable (defensive; every suite declares at least one client)
     * @param requestUrl the request's declared URL, after template resolution
     * @return the full URL to report, per the combination rule above
     */
    private static String resolveFullUrl(@Nullable RestClientConfig restClientConfig, String requestUrl) {
        String baseUrl = restClientConfig != null ? restClientConfig.baseUrl() : null;
        if (!StringUtils.hasText(baseUrl) || isAbsolute(requestUrl)) {
            return requestUrl;
        }
        String trimmedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String trimmedPath = requestUrl.startsWith("/") ? requestUrl : "/" + requestUrl;
        return trimmedBase + trimmedPath;
    }

    /**
     * Determines whether {@code url} is absolute (carries a scheme, e.g. {@code http://...}) using
     * {@link URI#isAbsolute()}. Unparsable strings are treated as relative so {@link
     * #resolveFullUrl} still attempts to combine them with a base-url rather than silently leaving a
     * broken URL in the report.
     *
     * @param url the URL string to check
     * @return {@code true} when {@code url} is absolute, {@code false} otherwise (including when it
     *     cannot be parsed as a {@link URI})
     */
    private static boolean isAbsolute(String url) {
        try {
            return URI.create(url).isAbsolute();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
