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
import io.github.snytkine.cmdrest.model.SuiteRunContext;
import io.github.snytkine.cmdrest.model.TestCase;
import io.github.snytkine.cmdrest.model.TestResult;
import io.github.snytkine.cmdrest.model.TestRunResult;
import io.github.snytkine.cmdrest.model.TestSuite;
import io.github.snytkine.cmdrest.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.cmdrest.service.assertion.ResponseResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Tests that a test case's {@code assertions} list is optional: it may be omitted entirely from the
 * suite YAML, in which case the test still runs and is still verified by the implicit {@code
 * base_server_response} assertion.
 *
 * <p>Covers the whole path — YAML deserialization ({@link TestCase} normalization), suite loading,
 * and execution — because a {@code null} assertions list would otherwise reach the engine and fail
 * with a {@link NullPointerException}.
 */
class OptionalAssertionsTest {

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

    private TestSuite loadSuite(String yaml) throws Exception {
        Path suiteFile = tempDir.resolve("optional-assertions.yml");
        Files.writeString(suiteFile, yaml);
        return loader.load(suiteFile, SuiteRunContext.of(Map.of(), Map.of()));
    }

    @Test
    void omittedAssertionsKeyDeserializesToAnEmptyList() throws Exception {
        TestSuite suite = loadSuite("---\n"
                + "name: \"No Assertions Suite\"\n"
                + "rest-client:\n"
                + "  base-url: \"http://stub.test\"\n"
                + "tests:\n"
                + "- name: \"GET objects\"\n"
                + "  request:\n"
                + "    method: \"GET\"\n"
                + "    url: \"/objects\"\n");

        assertThat(suite.tests()).hasSize(1);
        assertThat(suite.tests().get(0).assertions()).isNotNull().isEmpty();
    }

    @Test
    void constructingATestCaseWithNullAssertionsYieldsAnEmptyList() {
        TestCase testCase = new TestCase("t", null, null, null, Map.of(), null, null);

        assertThat(testCase.assertions()).isNotNull().isEmpty();
    }

    @Test
    void testWithoutAssertionsPassesWhenTheServiceResponds() throws Exception {
        TestSuite suite = loadSuite("---\n"
                + "name: \"No Assertions Suite\"\n"
                + "rest-client:\n"
                + "  base-url: \"http://stub.test\"\n"
                + "tests:\n"
                + "- name: \"GET objects\"\n"
                + "  request:\n"
                + "    method: \"GET\"\n"
                + "    url: \"/objects\"\n");
        var factory = new StubClientHttpRequestFactory().stub("/objects", 418, "teapot", "text/plain");

        TestRunResult result = engineWith(factory)
                .runConfigurationSuite(suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        // Any response passes: only the implicit base_server_response assertion was evaluated.
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.results().get(0).passedAssertions()).isEqualTo(1);
    }

    @Test
    void testWithoutAssertionsFailsWhenTheServiceDoesNotRespond() throws Exception {
        TestSuite suite = loadSuite("---\n"
                + "name: \"No Assertions Suite\"\n"
                + "rest-client:\n"
                + "  base-url: \"http://stub.test\"\n"
                + "tests:\n"
                + "- name: \"GET objects\"\n"
                + "  request:\n"
                + "    method: \"GET\"\n"
                + "    url: \"/objects\"\n");
        var factory = new StubClientHttpRequestFactory().stubIoFailure("/objects", "Connection refused");

        TestRunResult result = engineWith(factory)
                .runConfigurationSuite(suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.results().get(0).failures().get(0).description()).isEqualTo("base_server_response");
    }

    @Test
    void assertionLessTransientParentStillCapturesSessionValuesForItsDependent() throws Exception {
        // The motivating case from the issue: a login test that exists only to feed depends-on no
        // longer needs a token assertion just to satisfy the schema.
        TestSuite suite = loadSuite("---\n"
                + "name: \"Depends Suite\"\n"
                + "rest-client:\n"
                + "  base-url: \"http://stub.test\"\n"
                + "tests:\n"
                + "- name: \"login\"\n"
                + "  transient: true\n"
                + "  request:\n"
                + "    method: \"POST\"\n"
                + "    url: \"/login\"\n"
                + "  saved-session:\n"
                + "  - name: \"token\"\n"
                + "    path: \"response.body.json.token\"\n"
                + "- name: \"profile\"\n"
                + "  depends-on:\n"
                + "  - \"login\"\n"
                + "  request:\n"
                + "    method: \"GET\"\n"
                + "    url: \"/profile\"\n"
                + "    headers:\n"
                + "      Authorization: \"Bearer [[${session.token}]]\"\n"
                + "  assertions:\n"
                + "  - type: \"status_code\"\n"
                + "    expected: 200\n");
        var factory = new StubClientHttpRequestFactory()
                .stub("/login", 200, "{\"token\":\"abc123\"}", "application/json")
                .stub("/profile", 200, "{}", "application/json");

        TestRunResult result = engineWith(factory)
                .runConfigurationSuite(suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(2);
        assertThat(result.results().get(0).result()).isEqualTo(TestResult.PASSED);
        assertThat(result.results().get(1).requestInfo().headers()).containsEntry("Authorization", "Bearer abc123");
    }
}
