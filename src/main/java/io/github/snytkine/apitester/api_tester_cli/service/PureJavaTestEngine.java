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
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * This class is a thread-safe Spring singleton: all per-invocation state is
 * confined to the call
 * stack of {@link #runConfigurationSuite(TestSuite)}.
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
        Map<String, RestClient> restClients = new LinkedHashMap<>();
        testSuite.restClientsById().forEach((id, config) -> restClients.put(id, buildRestClient(config)));
        RestClient defaultRestClient = restClients.get(TestSuite.DEFAULT_REST_CLIENT_ID);
        Path suiteDir = testSuite.filePath() != null ? testSuite.filePath().getParent() : null;
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
            listener.onProgress(new TestProgressEvent.SuiteStarted(testSuite.name(), tests.size(), suiteStart));

            List<TestCaseResult> results = new ArrayList<>();

            for (int i = 0; i < tests.size(); i++) {
                TestCase config = tests.get(i);
                String uniqueId = String.valueOf(i);
                listener.onProgress(new TestProgressEvent.TestStarted(uniqueId, i, config.name()));
                long testStart = System.currentTimeMillis();

                boolean skipped = config.skip() != null && !config.skip().isBlank();

                // before-each runs before the request is sent (skipped tests have no request).
                // A blocking before-each failure marks this test an error and skips both the
                // request and after-each; remaining tests still run.
                if (!skipped && !beforeEachHooks.isEmpty()) {
                    HookRunner.PerTestData beforeData = beforeEachPerTest(testSuite, config);
                    HookRunner.HookPhaseOutcome be = hookRunner.runPhase(
                            HookPhase.BEFORE_EACH, beforeEachHooks, hookCtx, beforeData, null, listener, asyncHandles);
                    if (!be.allSucceeded()) {
                        long durationMs = System.currentTimeMillis() - testStart;
                        String msg = be.firstFailureMessage() != null
                                ? be.firstFailureMessage()
                                : "before-each hook returned non-zero status";
                        List<AssertionFailure> failure = List.of(new AssertionFailure(msg, null, null, null));
                        results.add(new TestCaseResult(config.name(), TestResult.ERROR, 0, failure, msg, null, null));
                        listener.onProgress(new TestProgressEvent.TestCompleted(
                                uniqueId, i, config.name(), TestStatus.ERROR, durationMs, 0, failure));
                        log.debug("Test case '{}' errored: before-each hook failed: {}", config.name(), msg);
                        continue;
                    }
                }

                // Single-element holder: written by the requestCapture callback inside
                // executeSingleTest (before retrieve()), read by all non-skip catch branches.
                // Safe because it is created fresh per iteration and the callback fires
                // synchronously on this thread — no sharing between concurrent iterations.
                @Nullable ExecutedRequestInfo[] capturedRequest = new ExecutedRequestInfo[1];

                // Single-element holder: written by the responseCapture callback inside
                // executeSingleTest (after ApiResponse is received), read by all non-skip catch branches.
                // Safe because it is created fresh per iteration and the callback fires
                // synchronously on this thread — no sharing between concurrent iterations.
                @Nullable ApiResponse[] capturedResponse = new ApiResponse[1];

                try {
                    executeSingleTest(
                            restClients,
                            defaultRestClient,
                            testSuite,
                            i,
                            suiteDir,
                            configMap,
                            sessionVars,
                            info -> capturedRequest[0] = info,
                            resp -> capturedResponse[0] = resp);
                    long durationMs = System.currentTimeMillis() - testStart;
                    int totalAssertions = config.assertions().size();
                    results.add(new TestCaseResult(
                            config.name(),
                            TestResult.PASSED,
                            totalAssertions,
                            List.of(),
                            null,
                            capturedRequest[0],
                            capturedResponse[0]));
                    listener.onProgress(new TestProgressEvent.TestCompleted(
                            uniqueId, i, config.name(), TestStatus.PASS, durationMs, totalAssertions, List.of()));
                } catch (SkipTestException e) {
                    long durationMs = System.currentTimeMillis() - testStart;
                    results.add(new TestCaseResult(
                            config.name(), TestResult.SKIPPED, 0, List.of(), e.getMessage(), null, null));
                    listener.onProgress(new TestProgressEvent.TestCompleted(
                            uniqueId, i, config.name(), TestStatus.SKIP, durationMs, 0, List.of()));
                    log.debug("Test case '{}' skipped: {}", config.name(), e.getMessage());
                } catch (SessionCaptureException e) {
                    long durationMs = System.currentTimeMillis() - testStart;
                    List<AssertionFailure> failure = List.of(new AssertionFailure(e.getMessage(), null, null, null));
                    results.add(new TestCaseResult(
                            config.name(),
                            TestResult.FAILED,
                            0,
                            failure,
                            null,
                            capturedRequest[0],
                            capturedResponse[0]));
                    listener.onProgress(new TestProgressEvent.TestCompleted(
                            uniqueId, i, config.name(), TestStatus.FAIL, durationMs, 0, failure));
                    log.debug("Test case '{}' failed: session capture error: {}", config.name(), e.getMessage());
                } catch (AssertionFailuresException e) {
                    long durationMs = System.currentTimeMillis() - testStart;
                    List<AssertionFailure> failures = e.failures();
                    int totalAssertions = config.assertions().size();
                    int passedAssertions = totalAssertions - failures.size();
                    results.add(new TestCaseResult(
                            config.name(),
                            TestResult.FAILED,
                            passedAssertions,
                            failures,
                            null,
                            capturedRequest[0],
                            capturedResponse[0]));
                    listener.onProgress(new TestProgressEvent.TestCompleted(
                            uniqueId, i, config.name(), TestStatus.FAIL, durationMs, totalAssertions, failures));
                    log.debug("Test case '{}' failed with {} assertion failure(s)", config.name(), failures.size());
                } catch (Throwable e) {
                    long durationMs = System.currentTimeMillis() - testStart;
                    results.add(new TestCaseResult(
                            config.name(),
                            TestResult.ERROR,
                            0,
                            List.of(new AssertionFailure(e.getMessage(), null, null, null)),
                            null,
                            capturedRequest[0],
                            capturedResponse[0]));
                    listener.onProgress(new TestProgressEvent.TestCompleted(
                            uniqueId,
                            i,
                            config.name(),
                            TestStatus.ERROR,
                            durationMs,
                            0,
                            List.of(new AssertionFailure(e.getMessage(), null, null, null))));
                    log.error("Test case '{}' errored: {}", config.name(), e.getMessage(), e);
                }

                // after-each runs after this test's assertions complete (not for skipped tests).
                if (!skipped && !afterEachHooks.isEmpty()) {
                    TestResult iterationResult = results.get(results.size() - 1).result();
                    HookRunner.PerTestData afterData =
                            afterEachPerTest(testSuite, config, capturedRequest[0], iterationResult);
                    hookRunner.runPhase(
                            HookPhase.AFTER_EACH, afterEachHooks, hookCtx, afterData, null, listener, asyncHandles);
                }
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
                    new HookRunner.SummaryData(tests.size(), passedCount, failedCount, errorCount, totalDurationMs);
            hookRunner.runPhase(HookPhase.AFTER_ALL, afterAllHooks, hookCtx, null, summary, listener, asyncHandles);

            listener.onProgress(new TestProgressEvent.SuiteCompleted(
                    passedCount, failedCount, skippedCount, errorCount, totalDurationMs));

            return new TestRunResult(passedCount, failedCount, skippedCount, errorCount, results, Map.of());
        }
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
     * when a tag filter is active the index {@code i} refers to a position in the
     * filtered list, while {@code templateContent} still holds the full original YAML;
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
     * @param i          zero-based index of the test case to execute within
     *                   {@code testSuite.tests()}
     * @param suiteDir   the directory of the suite file, or {@code null} if
     *                   unavailable
     * @param configMap      suite-level variable namespaces ({@code cli}, {@code env}, {@code
     *                       suite}, {@code test}, {@code session}); the {@code "test"} entry is
     *                       replaced per invocation with this test's vars
     * @param sessionVars    the suite-wide, mutable {@code session} namespace; read when resolving
     *                       this test's template and written after the response is received with any
     *                       {@code saved-session} captures declared by this test
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
            int i,
            @Nullable Path suiteDir,
            Map<String, Map<String, String>> configMap,
            Map<String, String> sessionVars,
            Consumer<ExecutedRequestInfo> requestCapture,
            Consumer<ApiResponse> responseCapture)
            throws IOException {

        TestCase rawConfig = testSuite.tests().get(i);
        if (rawConfig.skip() != null && !rawConfig.skip().isBlank()) {
            throw new SkipTestException(rawConfig.skip());
        }
        log.debug(
                "Test [{}] '{}': beginning execution, raw request {} {}",
                i,
                rawConfig.name(),
                rawConfig.request().method(),
                rawConfig.request().url());

        Map<String, String> testVariables = Objects.requireNonNullElse(rawConfig.variables(), Map.of());
        log.debug("Test [{}] '{}': {} test-level variable(s) found", i, rawConfig.name(), testVariables.size());

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
                    i,
                    rawConfig.name(),
                    testVariables.size());
            String resolvedYaml = FileLoader.parseFile(testSuite.templateContent(), testConfigMap);
            TestSuite resolvedSuite = yamlMapper.readValue(resolvedYaml, TestSuite.class);
            // Look up the resolved test case by name rather than by index.
            // When the suite has been filtered (e.g. by --tag), the index i refers to a
            // position in the filtered list, while templateContent still contains the full
            // original YAML.  Using get(i) on the re-parsed (unfiltered) suite would fetch
            // the wrong test case; a name-based lookup is always correct because
            // TestSuiteValidator guarantees unique names.
            String targetName = rawConfig.name();
            config = resolvedSuite.tests().stream()
                    .filter(tc -> targetName.equals(tc.name()))
                    .findFirst()
                    .orElse(rawConfig);
            log.debug(
                    "Test [{}] '{}': resolved request {} {}",
                    i,
                    config.name(),
                    config.request().method(),
                    config.request().url());
        } else {
            log.debug(
                    "Test [{}] '{}': skipping template re-parse ({})",
                    i,
                    rawConfig.name(),
                    testSuite.templateContent() == null ? "no templateContent" : "no test variables");
            config = rawConfig;
        }

        log.debug(
                "Test [{}] '{}': sending {} {}",
                i,
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

        RestClient selectedClient = selectRestClient(restClients, defaultRestClient, config);
        RestClient.RequestBodySpec requestSpec = buildRequestSpec(selectedClient, config, resolvedBody);
        RestClient.ResponseSpec responseSpec = requestSpec.retrieve();

        log.debug(
                "Test [{}] '{}': evaluating {} assertion(s)",
                i,
                config.name(),
                config.assertions().size());

        // Force full (body-reading) resolution when this test captures saved-session values, so the
        // response body is available even if the test's only assertion is a status_code check.
        boolean hasCaptures =
                config.savedSession() != null && !config.savedSession().isEmpty();
        ApiResponse apiResponse = responseResolver.resolve(responseSpec, config.assertions(), hasCaptures);
        log.debug("Test [{}] '{}': received status {}", i, config.name(), apiResponse.statusCode());

        responseCapture.accept(apiResponse);

        // Capture saved-session values before evaluating assertions so the suite-wide 'session'
        // namespace is populated for later tests regardless of whether this test's assertions pass.
        // A required-but-missing value, a non-primitive extraction, or a failed type conversion
        // raises a SessionCaptureException that the run loop records as a test failure.
        SessionCapturer.capture(config.name(), config.savedSession(), apiResponse, sessionVars);

        // Evaluate one assertion at a time, collecting failures as AssertionFailedError instances.
        // Each evaluator stores structured AssertionFailedError entries (with message, expected,
        // actual) in the collector. The first new entry for each assertion is extracted to build an
        // AssertionFailure: description from the assertion definition, error from the AFE message,
        // and expected/actual from the AFE structured fields.
        List<AssertionFailure> failures = new ArrayList<>();
        FailureCollector collector = new FailureCollector();
        for (Assertion assertion : config.assertions()) {
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
     * Builds a {@link RestClient} configured from the given
     * {@link RestClientConfig}.
     *
     * <p>
     * If {@code config} carries a non-blank {@code baseUrl} it is set as the
     * client's default base
     * URL. When a {@code connectTimeout} is present AND the injected factory is a
     * {@link
     * org.springframework.http.client.JdkClientHttpRequestFactory}, a new
     * JDK-backed factory is
     * created with that timeout; non-JDK factories (e.g. stub factories used in
     * tests) are never
     * replaced. When {@code headers} is non-null each entry is registered as a
     * default header applied
     * to every request built with this client.
     *
     * @param config the suite-level REST client settings
     * @return a fully configured {@link RestClient} ready for use
     */
    private RestClient buildRestClient(RestClientConfig config) {
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (StringUtils.hasText(config.baseUrl())) {
            builder.baseUrl(config.baseUrl());
        }
        if (config.connectTimeout() != null
                && requestFactory instanceof org.springframework.http.client.JdkClientHttpRequestFactory) {
            builder.requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                    java.net.http.HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(config.connectTimeout()))
                            .build()));
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
