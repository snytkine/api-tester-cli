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
package io.github.snytkine.cmdrest.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.cmdrest.event.NoOpProgressListener;
import io.github.snytkine.cmdrest.event.TestProgressEvent;
import io.github.snytkine.cmdrest.event.TestProgressListener;
import io.github.snytkine.cmdrest.event.TestStatus;
import io.github.snytkine.cmdrest.model.AssertionFailure;
import io.github.snytkine.cmdrest.model.SuiteRunContext;
import io.github.snytkine.cmdrest.model.TestCaseResult;
import io.github.snytkine.cmdrest.model.TestResult;
import io.github.snytkine.cmdrest.model.TestRunResult;
import io.github.snytkine.cmdrest.model.TestSuite;
import io.github.snytkine.cmdrest.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.cmdrest.service.assertion.ResponseResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Tests for the implicit {@code base_server_response} assertion that {@link PureJavaTestEngine}
 * adds to every test case.
 *
 * <p>Two behaviours are covered: the assertion is counted alongside the declared assertions on a
 * successful run, and it is the single reported failure — with the {@code "service must respond
 * within default timeout of <n> seconds"} expected text — when the transport never delivers a
 * response.
 */
class PureJavaTestEngineBaseServerResponseTest {

    @TempDir
    Path tempDir;

    private TestSuiteLoader loader;

    @BeforeEach
    void setUp() {
        loader = new TestSuiteLoader();
    }

    private PureJavaTestEngine engineWith(ClientHttpRequestFactory factory) {
        return new PureJavaTestEngine(factory, new AssertionEvaluatorFactory(), new ResponseResolver());
    }

    /** Writes a one-test suite whose optional {@code connect-timeout} line is supplied verbatim. */
    private TestSuite suiteWith(String connectTimeoutLine, String assertionsBlock) throws Exception {
        Path suiteFile = tempDir.resolve("base-response-suite.yml");
        Files.writeString(
                suiteFile,
                "---\n"
                        + "name: \"Base Response Suite\"\n"
                        + "rest-client:\n"
                        + "  base-url: \"http://stub.test\"\n"
                        + connectTimeoutLine
                        + "tests:\n"
                        + "- name: \"GET objects\"\n"
                        + "  request:\n"
                        + "    method: \"GET\"\n"
                        + "    url: \"/objects\"\n"
                        + assertionsBlock);
        return loader.load(suiteFile, SuiteRunContext.of(Map.of(), Map.of()));
    }

    private TestRunResult run(TestSuite suite, ClientHttpRequestFactory factory) {
        return engineWith(factory)
                .runConfigurationSuite(suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);
    }

    @Test
    void implicitAssertionIsCountedAlongsideDeclaredAssertions() throws Exception {
        TestSuite suite = suiteWith("", "  assertions:\n  - type: \"status_code\"\n    expected: 200\n");
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");

        TestRunResult result = run(suite, factory);

        assertThat(result.passedCount()).isEqualTo(1);
        // one declared status_code assertion + the implicit base_server_response assertion
        assertThat(result.results().get(0).passedAssertions()).isEqualTo(2);
    }

    @Test
    void testWithoutDeclaredAssertionsStillEvaluatesTheImplicitOne() throws Exception {
        TestSuite suite = suiteWith("", "  assertions: []\n");
        var factory = new StubClientHttpRequestFactory().stub("/objects", 500, "boom", "text/plain");

        TestRunResult result = run(suite, factory);

        // Status code is irrelevant to base_server_response: a 500 response still passes it.
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.results().get(0).passedAssertions()).isEqualTo(1);
    }

    @Test
    void noResponseFailsTheTestWithTheImplicitAssertionOnly() throws Exception {
        TestSuite suite = suiteWith("", "  assertions:\n  - type: \"status_code\"\n    expected: 200\n");
        var factory = new StubClientHttpRequestFactory().stubIoFailure("/objects", "Connection refused");

        TestRunResult result = run(suite, factory);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.errorCount()).isZero();

        TestCaseResult testCase = result.results().get(0);
        assertThat(testCase.result()).isEqualTo(TestResult.FAILED);
        // Nothing could be evaluated, so no assertion — declared or implicit — passed.
        assertThat(testCase.passedAssertions()).isZero();
        assertThat(testCase.failures()).hasSize(1);

        AssertionFailure failure = testCase.failures().get(0);
        assertThat(failure.description()).isEqualTo("base_server_response");
        assertThat(failure.expected()).isEqualTo("service must respond within default timeout of 30 seconds");
        assertThat(failure.actual()).contains("Connection refused");
        assertThat(failure.error()).contains("The service did not return a response");
    }

    @Test
    void expectedTextReportsTheSuiteConfiguredConnectTimeout() throws Exception {
        TestSuite suite =
                suiteWith("  connect-timeout: 5000\n", "  assertions:\n  - type: \"status_code\"\n    expected: 200\n");
        var factory = new StubClientHttpRequestFactory().stubIoFailure("/objects", "Connection refused");

        TestRunResult result = run(suite, factory);

        assertThat(result.results().get(0).failures().get(0).expected())
                .isEqualTo("service must respond within default timeout of 5 seconds");
    }

    @Test
    void noResponseFiresAFailProgressEventCountingTheImplicitAssertion() throws Exception {
        TestSuite suite = suiteWith("", "  assertions:\n  - type: \"status_code\"\n    expected: 200\n");
        var factory = new StubClientHttpRequestFactory().stubIoFailure("/objects", "Connection refused");
        List<TestProgressEvent> events = new ArrayList<>();
        TestProgressListener listener = events::add;

        engineWith(factory).runConfigurationSuite(suite, SuiteRunContext.of(Map.of(), Map.of()), listener);

        TestProgressEvent.TestCompleted completed = events.stream()
                .filter(TestProgressEvent.TestCompleted.class::isInstance)
                .map(TestProgressEvent.TestCompleted.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(completed.status()).isEqualTo(TestStatus.FAIL);
        assertThat(completed.assertionCount()).isEqualTo(2);
        assertThat(completed.failures()).hasSize(1);
        assertThat(completed.failures().get(0).description()).isEqualTo("base_server_response");
    }

    @Test
    void dependentTestInheritsFailureWhenItsDependencyGotNoResponse() throws Exception {
        Path suiteFile = tempDir.resolve("depends-suite.yml");
        Files.writeString(
                suiteFile,
                "---\n"
                        + "name: \"Depends Suite\"\n"
                        + "rest-client:\n"
                        + "  base-url: \"http://stub.test\"\n"
                        + "tests:\n"
                        + "- name: \"login\"\n"
                        + "  request:\n"
                        + "    method: \"GET\"\n"
                        + "    url: \"/login\"\n"
                        + "  assertions: []\n"
                        + "- name: \"profile\"\n"
                        + "  depends-on:\n"
                        + "  - \"login\"\n"
                        + "  request:\n"
                        + "    method: \"GET\"\n"
                        + "    url: \"/profile\"\n"
                        + "  assertions: []\n");
        TestSuite suite = loader.load(suiteFile, SuiteRunContext.of(Map.of(), Map.of()));
        var factory = new StubClientHttpRequestFactory()
                .stubIoFailure("/login", "Connection refused")
                .stub("/profile", 200, "{}", "application/json");

        TestRunResult result = run(suite, factory);

        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(result.results().get(1).failedParentName()).isEqualTo("login");
    }
}
