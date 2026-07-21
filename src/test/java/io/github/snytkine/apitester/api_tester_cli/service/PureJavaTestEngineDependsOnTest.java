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
import io.github.snytkine.apitester.api_tester_cli.model.BodylessRequest;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SavedSession;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hooks;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.ScriptHook;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.ClientHttpRequest;

/**
 * Stub-based integration tests for the {@code depends-on} / {@code transient} execution model in
 * {@link PureJavaTestEngine}: run-once (memoized) dependencies, transitive ordering, transient tests
 * that never run standalone, failure propagation from a failed dependency, and suppression of
 * per-test hooks for transient tests.
 */
class PureJavaTestEngineDependsOnTest {

    private final TestSuiteLoader loader = new TestSuiteLoader();

    private PureJavaTestEngine engineWith(org.springframework.http.client.ClientHttpRequestFactory factory) {
        return new PureJavaTestEngine(factory, new AssertionEvaluatorFactory(), new ResponseResolver());
    }

    /**
     * A {@link StubClientHttpRequestFactory} that additionally records how many requests were
     * dispatched to each URI path, so tests can assert run-once semantics at the transport level.
     */
    private static final class CountingStubFactory extends StubClientHttpRequestFactory {
        private final Map<String, Integer> pathCounts = new ConcurrentHashMap<>();

        @Override
        public ClientHttpRequest createRequest(URI uri, org.springframework.http.HttpMethod method) {
            pathCounts.merge(uri.getPath(), 1, Integer::sum);
            return super.createRequest(uri, method);
        }

        int count(String path) {
            return pathCounts.getOrDefault(path, 0);
        }
    }

    private static TestCase tc(
            String name, String url, List<String> dependsOn, boolean transientCase, List<SavedSession> savedSession) {
        return new TestCase(
                name,
                null,
                null,
                null,
                Map.of(),
                new BodylessRequest(HttpMethod.GET, url, null, null, null),
                List.of(),
                savedSession,
                dependsOn,
                transientCase);
    }

    private static TestSuite suite(TestCase... tests) {
        RestClientConfig single = new RestClientConfig(null, "http://stub.test", 30000, null, null);
        return new TestSuite("s", null, single, null, null, List.of(tests), Path.of("suite.yml"), null);
    }

    private static TestSuite suiteWithHooks(Path dir, Hooks hooks, TestCase... tests) {
        RestClientConfig single = new RestClientConfig(null, "http://stub.test", 30000, null, null);
        return new TestSuite("s", null, single, null, null, List.of(tests), hooks, dir.resolve("suite.yml"), null);
    }

    private static TestRunResult run(PureJavaTestEngine engine, TestSuite suite) {
        return engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);
    }

    @Test
    void dependencyRunsOnceAndCapturedValueReusedByBothDependents() throws Exception {
        // 'create' (transient) captures an id; both getA and getB depend on it. Run-once means the
        // /create request is dispatched exactly once and its captured id is reused in both URLs.
        CountingStubFactory factory = new CountingStubFactory();
        factory.stub("/create", 200, "{\"id\":\"abc123\"}", "application/json");
        factory.stub("/items/", 200, "{}", "application/json");
        PureJavaTestEngine engine = engineWith(factory);
        Path path = Path.of(
                getClass().getResource("/test-suite-stub-depends-runonce.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = run(engine, suite);

        // create + getA + getB = three passing rows, and the create request was sent exactly once.
        assertThat(result.passedCount()).isEqualTo(3);
        assertThat(result.failedCount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(result.results()).hasSize(3);
        assertThat(factory.count("/create")).isEqualTo(1);

        // The single create row is labeled with the first dependent that triggered it.
        assertThat(result.results().get(0).name()).isEqualTo("create (dependency of getA)");
        assertThat(result.results().get(1).name()).isEqualTo("getA");
        assertThat(result.results().get(2).name()).isEqualTo("getB");

        // Both dependents reused the same captured id in their URLs.
        assertThat(result.results().get(1).requestInfo().url()).contains("/items/abc123?who=a");
        assertThat(result.results().get(2).requestInfo().url()).contains("/items/abc123?who=b");
    }

    @Test
    void transitiveDependenciesRunInDependencyOrder() {
        // A depends-on B depends-on C; B and C are transient. Execution order must be C, B, A.
        StubClientHttpRequestFactory factory =
                new StubClientHttpRequestFactory().stub("/ok", 200, "{}", "application/json");
        PureJavaTestEngine engine = engineWith(factory);
        TestCase c = tc("C", "/ok", null, true, null);
        TestCase b = tc("B", "/ok", List.of("C"), true, null);
        TestCase a = tc("A", "/ok", List.of("B"), false, null);

        TestRunResult result = run(engine, suite(a, b, c));

        assertThat(result.passedCount()).isEqualTo(3);
        assertThat(result.results()).hasSize(3);
        assertThat(result.results().get(0).name()).isEqualTo("C (dependency of B)");
        assertThat(result.results().get(1).name()).isEqualTo("B (dependency of A)");
        assertThat(result.results().get(2).name()).isEqualTo("A");
    }

    @Test
    void transientTestNeverRunsWhenNothingDependsOnIt() {
        // 'orphan' is transient and nothing depends on it, so it must not run. Its endpoint has no stub;
        // if it ran the stub factory would throw. Only 'main' produces a row.
        CountingStubFactory factory = new CountingStubFactory();
        factory.stub("/ok", 200, "{}", "application/json");
        PureJavaTestEngine engine = engineWith(factory);
        TestCase orphan = tc("orphan", "/never", null, true, null);
        TestCase main = tc("main", "/ok", null, false, null);

        TestRunResult result = run(engine, suite(orphan, main));

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).name()).isEqualTo("main");
        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(factory.count("/never")).isZero();
    }

    @Test
    void dependencyFailurePropagatesToDependentWithoutSendingItsRequest() {
        // 'create' has a required capture that cannot be extracted, so it fails; 'main' depends on it
        // and must be marked failed with the parent-error message, and its own request must not be sent.
        CountingStubFactory factory = new CountingStubFactory();
        factory.stub("/create", 200, "{}", "application/json");
        factory.stub("/get", 200, "{}", "application/json");
        PureJavaTestEngine engine = engineWith(factory);
        SavedSession required = new SavedSession("x", "response.body.json.$.missing", null, null, true);
        TestCase create = tc("create", "/create", null, true, List.of(required));
        TestCase main = tc("main", "/get", List.of("create"), false, null);

        TestRunResult result = run(engine, suite(create, main));

        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(result.results().get(0).name()).isEqualTo("create (dependency of main)");
        assertThat(result.results().get(0).result()).isEqualTo(TestResult.FAILED);
        assertThat(result.results().get(1).name()).isEqualTo("main");
        assertThat(result.results().get(1).result()).isEqualTo(TestResult.FAILED);
        assertThat(result.results().get(1).failures().get(0).description())
                .startsWith("Parent test 'create' failed with error")
                .contains("Failed to extract session parameter 'x'");
        // The dependent's request was never dispatched.
        assertThat(factory.count("/get")).isZero();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void transientTestDoesNotFireBeforeOrAfterEachHooks(@TempDir Path dir) throws Exception {
        // before-each and after-each each append one line per invocation. 'setup' is transient and runs
        // only as 'main's dependency, so the per-test hooks must fire once (for main), not twice.
        Path beforeMarker = dir.resolve("before.txt");
        Path afterMarker = dir.resolve("after.txt");
        script(dir, "before.sh", "echo x >> \"" + beforeMarker + "\"\nexit 0\n");
        script(dir, "after.sh", "echo x >> \"" + afterMarker + "\"\nexit 0\n");
        Hooks hooks = new Hooks(
                null,
                null,
                List.of(scriptHook(dir, "before.sh")),
                List.of(scriptHook(dir, "after.sh")),
                null,
                null,
                null);
        StubClientHttpRequestFactory factory =
                new StubClientHttpRequestFactory().stub("/ok", 200, "{}", "application/json");
        PureJavaTestEngine engine = engineWith(factory);
        TestCase setup = tc("setup", "/ok", null, true, null);
        TestCase main = tc("main", "/ok", List.of("setup"), false, null);

        TestRunResult result = run(engine, suiteWithHooks(dir, hooks, main, setup));

        // Both tests ran (setup as a dependency, main standalone) ...
        assertThat(result.passedCount()).isEqualTo(2);
        // ... but before-each and after-each fired only for the non-transient 'main'.
        assertThat(Files.readAllLines(beforeMarker)).hasSize(1);
        assertThat(Files.readAllLines(afterMarker)).hasSize(1);
    }

    private static Path script(Path dir, String name, String body) throws IOException {
        Path s = dir.resolve(name);
        Files.writeString(s, "#!/bin/sh\n" + body, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(
                s,
                EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
        return s;
    }

    private static Hook scriptHook(Path dir, String name) {
        return new ScriptHook(null, null, null, dir.resolve(name).toString(), null);
    }
}
