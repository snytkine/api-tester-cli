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
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Stub-based integration tests verifying that a {@code saved-session} value captured from one
 * test's response is substituted into a later test's request via the {@code session} namespace.
 */
class PureJavaTestEngineSessionTest {

    private final TestSuiteLoader loader = new TestSuiteLoader();

    private PureJavaTestEngine engineWith(ClientHttpRequestFactory factory) {
        return new PureJavaTestEngine(factory, new AssertionEvaluatorFactory(), new ResponseResolver());
    }

    @Test
    void capturedSessionValueIsUsedInLaterTestUrl() throws Exception {
        // The first stub returns the id captured into session; the second stub only matches when
        // [[${session.itemId}]] has been substituted into the URL as /items/abc123.
        var factory = new StubClientHttpRequestFactory()
                .stub("/create", 200, "{\"id\":\"abc123\"}", "application/json")
                .stub("/items/abc123", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path =
                Path.of(getClass().getResource("/test-suite-stub-session.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.failedCount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(result.passedCount()).isEqualTo(2);
        // Prove the substitution actually happened rather than relying on stub matching alone.
        assertThat(result.results().get(1).result()).isEqualTo(TestResult.PASSED);
        assertThat(result.results().get(1).requestInfo().url()).endsWith("/items/abc123");
    }

    @Test
    void requiredCaptureMissingFailsThatTest() throws Exception {
        // The create response has no 'id' field, so the required capture fails the first test.
        var factory = new StubClientHttpRequestFactory()
                .stub("/create", 200, "{\"noId\":true}", "application/json")
                .stub("/items/", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path =
                Path.of(getClass().getResource("/test-suite-stub-session.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.results().get(0).result()).isEqualTo(TestResult.FAILED);
        assertThat(result.results().get(0).failures().get(0).description())
                .contains("Failed to extract session parameter 'itemId'");
    }
}
