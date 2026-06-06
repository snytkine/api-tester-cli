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
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import io.github.snytkine.apitester.api_tester_cli.util.FileLoader;
import java.io.IOException;
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
    private final ObjectMapper yamlMapper;

    /**
     * Constructs the engine with the required collaborators. A YAML
     * {@link ObjectMapper} is created
     * here for re-parsing the suite template during per-test variable resolution;
     * it is configured to
     * ignore unknown properties so that loader-injected fields do not cause
     * failures.
     *
     * @param requestFactory   the HTTP transport factory used to back each
     *                         per-suite {@link RestClient}
     * @param evaluatorFactory maps assertion model objects to their evaluator
     *                         implementations
     * @param responseResolver converts a {@link RestClient.ResponseSpec} into an
     *                         {@link ApiResponse}
     */
    public PureJavaTestEngine(
            ClientHttpRequestFactory requestFactory,
            AssertionEvaluatorFactory evaluatorFactory,
            ResponseResolver responseResolver) {
        this.requestFactory = requestFactory;
        this.evaluatorFactory = evaluatorFactory;
        this.responseResolver = responseResolver;
        this.yamlMapper =
                new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
        RestClient restClient = buildRestClient(testSuite.restClientConfig());
        Path suiteDir = testSuite.filePath() != null ? testSuite.filePath().getParent() : null;
        Map<String, String> suiteVariables = Objects.requireNonNullElse(testSuite.variables(), Map.of());
        Map<String, Map<String, String>> configMap =
                Map.of("cli", context.cli(), "env", context.env(), "suite", suiteVariables, "test", Map.of());

        List<TestCase> tests = testSuite.tests();
        Instant suiteStart = Instant.now();
        listener.onProgress(new TestProgressEvent.SuiteStarted(testSuite.name(), tests.size(), suiteStart));

        List<TestCaseResult> results = new ArrayList<>();

        for (int i = 0; i < tests.size(); i++) {
            TestCase config = tests.get(i);
            String uniqueId = String.valueOf(i);
            listener.onProgress(new TestProgressEvent.TestStarted(uniqueId, i, config.name()));
            long testStart = System.currentTimeMillis();

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
                        restClient,
                        testSuite,
                        i,
                        suiteDir,
                        configMap,
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
        }

        Map<TestResult, Long> counts =
                results.stream().collect(Collectors.groupingBy(TestCaseResult::result, Collectors.counting()));
        long passedCount = counts.getOrDefault(TestResult.PASSED, 0L);
        long failedCount = counts.getOrDefault(TestResult.FAILED, 0L);
        long skippedCount = counts.getOrDefault(TestResult.SKIPPED, 0L);
        long errorCount = counts.getOrDefault(TestResult.ERROR, 0L);

        long totalDurationMs = Instant.now().toEpochMilli() - suiteStart.toEpochMilli();
        listener.onProgress(new TestProgressEvent.SuiteCompleted(
                passedCount, failedCount, skippedCount, errorCount, totalDurationMs));

        return new TestRunResult(passedCount, failedCount, skippedCount, errorCount, results, Map.of());
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
     * @param restClient the configured client for this suite run
     * @param testSuite  the loaded test suite containing the raw YAML template and
     *                   test cases
     * @param i          zero-based index of the test case to execute within
     *                   {@code testSuite.tests()}
     * @param suiteDir   the directory of the suite file, or {@code null} if
     *                   unavailable
     * @param configMap      suite-level variable namespaces ({@code cli}, {@code env}, {@code
     *                       suite}, {@code test}); the {@code "test"} entry is replaced per
     *                       invocation with this test's vars
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
            RestClient restClient,
            TestSuite testSuite,
            int i,
            @Nullable Path suiteDir,
            Map<String, Map<String, String>> configMap,
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
        // Skip re-parsing when there are no test-level variables — no template
        // expressions can
        // reference ${test.*}, so rawConfig is already fully resolved.
        TestCase config;
        if (testSuite.templateContent() != null && !testVariables.isEmpty()) {
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
        requestCapture.accept(new ExecutedRequestInfo(
                config.request().method(),
                config.request().url(),
                config.request().headers(),
                resolvedBody));

        RestClient.RequestBodySpec requestSpec = buildRequestSpec(restClient, config, resolvedBody);
        RestClient.ResponseSpec responseSpec = requestSpec.retrieve();

        log.debug(
                "Test [{}] '{}': evaluating {} assertion(s)",
                i,
                config.name(),
                config.assertions().size());

        ApiResponse apiResponse = responseResolver.resolve(responseSpec, config.assertions());
        log.debug("Test [{}] '{}': received status {}", i, config.name(), apiResponse.statusCode());

        responseCapture.accept(apiResponse);

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
}
