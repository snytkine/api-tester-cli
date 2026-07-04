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

import io.github.snytkine.apitester.api_tester_cli.event.NoOpProgressListener;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Stub-based unit tests for {@link PureJavaTestEngine} that exercise the engine's assertion
 * evaluation logic without making real network connections.
 *
 * <p>All HTTP transport is handled by a {@link StubClientHttpRequestFactory} which returns
 * pre-configured responses. The tests verify that the engine correctly maps stub responses to pass
 * or fail outcomes for a variety of assertion types.
 */
class PureJavaTestEngineTest {

    private static final String ITEMS_JSON = "[{\"id\":1,\"name\":\"Widget\"},{\"id\":2,\"name\":\"Gadget\"}]";

    private TestSuiteLoader loader;

    @BeforeEach
    void setUp() {
        loader = new TestSuiteLoader();
    }

    private PureJavaTestEngine engineWith(ClientHttpRequestFactory factory) {
        return new PureJavaTestEngine(factory, new AssertionEvaluatorFactory(), new ResponseResolver());
    }

    @Test
    void allAssertionsPassWhenResponseMatchesExpectations() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/items", 200, ITEMS_JSON, "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(
                getClass().getResource("/test-suite-stub-assertions.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.failedCount()).isZero();
        assertThat(result.passedCount()).isEqualTo(suite.tests().size());
    }

    @Test
    void statusCodeAssertionFailsWhenServerReturns404() throws Exception {
        var factory = new StubClientHttpRequestFactory()
                .stub("/objects", 404, "{\"error\":\"not found\"}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(getClass().getResource("/test-suite-stub-pass.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.failedCount()).isEqualTo(1);
    }

    @Test
    void responseTimeAssertionPassesWhenResponseIsImmediate() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(
                getClass().getResource("/test-suite-stub-response-time.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.failedCount()).isZero();
    }

    @Test
    void responseTimeAssertionFailsWhenResponseExceedsLimit() throws Exception {
        var factory = new StubClientHttpRequestFactory()
                .stubWithDelay(HttpMethod.GET, "/objects", 200, "{}", "application/json", 50);
        var engine = engineWith(factory);
        Path path = Path.of(getClass()
                .getResource("/test-suite-stub-response-time-strict.yml")
                .toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.failedCount()).isEqualTo(1);
    }

    @Test
    void passedTestResultHasRequestInfoPopulated() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(getClass().getResource("/test-suite-stub-pass.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        var firstResult = result.results().get(0);
        assertThat(firstResult.requestInfo()).isNotNull();
        assertThat(firstResult.requestInfo().method())
                .isEqualTo(io.github.snytkine.apitester.api_tester_cli.model.HttpMethod.GET);
        // test-suite-stub-pass.yml declares rest-client.base-url = "http://stub.test" and a
        // relative request url of "/objects"; the two are now combined into the full URL.
        assertThat(firstResult.requestInfo().url()).isEqualTo("http://stub.test/objects");
        assertThat(firstResult.requestInfo().body()).isNull();
        assertThat(firstResult.requestInfo().auth()).isNull();
        assertThat(firstResult.requestInfo().restClientId()).isEqualTo("default");
    }

    @Test
    void skippedTestResultHasNullRequestInfo() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(getClass().getResource("/test-suite-stub-skip.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        var skippedResult = result.results().stream()
                .filter(r -> r.result() == TestResult.SKIPPED)
                .findFirst()
                .orElseThrow();
        assertThat(skippedResult.requestInfo()).isNull();
    }

    @Test
    void skippedTestIsCountedAsSkippedAndNotPassedOrFailed() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(getClass().getResource("/test-suite-stub-skip.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(result.errorCount()).isZero();
    }

    @Test
    void skippedTestResultHasSkippedStatusAndSkipReason() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(getClass().getResource("/test-suite-stub-skip.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        var skipped = result.results().stream()
                .filter(r -> r.result() == TestResult.SKIPPED)
                .findFirst();
        assertThat(skipped).isPresent();
        assertThat(skipped.get().skipReason()).isEqualTo("Not implemented yet");
        assertThat(skipped.get().failures()).isEmpty();
        assertThat(skipped.get().passedAssertions()).isZero();
    }

    @Test
    void skippedTestFiresSkipEventToListener() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(getClass().getResource("/test-suite-stub-skip.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        var events = new java.util.ArrayList<io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent>();
        engine.runConfigurationSuite(suite, SuiteRunContext.of(Map.of(), Map.of()), events::add);

        long skipEvents = events.stream()
                .filter(e -> e
                                instanceof
                                io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent.TestCompleted tc
                        && tc.status() == io.github.snytkine.apitester.api_tester_cli.event.TestStatus.SKIP)
                .count();
        assertThat(skipEvents).isEqualTo(1);
    }

    @Test
    void passedTestResultHasApiResponsePopulated() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(getClass().getResource("/test-suite-stub-pass.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        var firstResult = result.results().get(0);
        assertThat(firstResult.apiResponse()).isNotNull();
        assertThat(firstResult.apiResponse().statusCode()).isEqualTo(200);
        assertThat(firstResult.apiResponse().headers()).isNotNull();
    }

    @Test
    void skippedTestResultHasNullApiResponse() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(getClass().getResource("/test-suite-stub-skip.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        var skippedResult = result.results().stream()
                .filter(r -> r.result() == TestResult.SKIPPED)
                .findFirst()
                .orElseThrow();
        assertThat(skippedResult.apiResponse()).isNull();
    }
}
