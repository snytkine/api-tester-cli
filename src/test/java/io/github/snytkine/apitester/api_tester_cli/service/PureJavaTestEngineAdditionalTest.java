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
import io.github.snytkine.apitester.api_tester_cli.model.AuthType;
import io.github.snytkine.apitester.api_tester_cli.model.BodylessRequest;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RequestAuth;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusCodeAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Additional stub-based unit tests for {@link PureJavaTestEngine} covering branches not exercised
 * by the primary test class: the {@code Throwable} catch path, template re-parse with per-test
 * variables, {@code buildRestClient} configuration branches (base URL, default headers, suite
 * auth, JDK connect timeout), and {@code buildRequestSpec} branches (request headers, body, and
 * request-level auth).
 *
 * <p>All HTTP transport uses {@link StubClientHttpRequestFactory} or {@link
 * JdkClientHttpRequestFactory} with all-skipped suites (so no real network connection is made).
 */
class PureJavaTestEngineAdditionalTest {

    @TempDir
    Path tempDir;

    private TestSuiteLoader loader;

    @BeforeEach
    void setUp() {
        loader = new TestSuiteLoader();
    }

    private PureJavaTestEngine engineWith(org.springframework.http.client.ClientHttpRequestFactory factory) {
        return new PureJavaTestEngine(factory, new AssertionEvaluatorFactory(), new ResponseResolver());
    }

    // ---- Throwable catch block (lines 247-265) ----------------------------------------

    /**
     * Verifies that an unexpected {@link java.io.IOException} (caused by a missing body file)
     * propagates to the {@code catch (Throwable)} block and is recorded as an ERROR result rather
     * than crashing the suite run.
     */
    @Test
    void ioExceptionFromMissingBodyFileResultsInErrorTestResult() throws Exception {
        Path suiteFile = tempDir.resolve("error-suite.yml");
        Files.writeString(
                suiteFile,
                "---\n"
                        + "name: \"Error Suite\"\n"
                        + "tests:\n"
                        + "- name: \"POST with missing body\"\n"
                        + "  request:\n"
                        + "    method: \"POST\"\n"
                        + "    url: \"/items\"\n"
                        + "    body:\n"
                        + "      type: \"file\"\n"
                        + "      content: \"nonexistent_body.json\"\n"
                        + "  assertions:\n"
                        + "  - type: \"status_code\"\n"
                        + "    expected: 201\n");
        TestSuite suite = loader.load(suiteFile, SuiteRunContext.of(Map.of(), Map.of()));
        var factory = new StubClientHttpRequestFactory();
        var engine = engineWith(factory);

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.passedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(result.results().get(0).result()).isEqualTo(TestResult.ERROR);
    }

    // ---- Template re-parse path (lines 376-401) ---------------------------------------

    /**
     * Verifies that per-test {@code variables} are resolved in the request URL via the two-pass
     * Thymeleaf re-parse path inside {@link PureJavaTestEngine#executeSingleTest}.
     */
    @Test
    void testVariablesAreResolvedInRequestUrlViaTemplateReparse() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(
                getClass().getResource("/test-suite-stub-test-variables.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.results().get(0).requestInfo().url()).isEqualTo("/objects");
    }

    // ---- templateContent == null + filePath == null branches (lines 174, 376, 406) -----

    /**
     * Verifies the code path where a {@link TestSuite} is constructed without a {@code filePath} or
     * {@code templateContent}. This covers: (a) the {@code filePath == null} ternary at line 174;
     * (b) the {@code templateContent == null} branch of the {@code &&} at line 376; (c) the {@code
     * "no templateContent"} side of the debug ternary at line 406.
     */
    @Test
    void suiteWithNullTemplateContentFallsBackToRawConfig() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        TestCase testCase = new TestCase(
                "get objects",
                null,
                null,
                null,
                Map.of("path", "/objects"),
                new BodylessRequest(HttpMethod.GET, "/objects", null, null, null),
                List.of(new StatusCodeAssertion(200)));
        TestSuite suite = new TestSuite(
                "no-template-suite",
                null,
                RestClientConfig.withDefaults(null),
                null,
                Map.of(),
                List.of(testCase),
                null,
                null);

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
    }

    // ---- blank skip field (line 351 branch 3) -----------------------------------------

    /**
     * Verifies that a {@code skip} field containing only whitespace is treated as absent — i.e. the
     * test is executed, not skipped. This covers the {@code rawConfig.skip().isBlank()} true branch.
     */
    @Test
    void blankSkipFieldIsNotTreatedAsSkip() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        TestCase testCase = new TestCase(
                "blank skip test",
                null,
                null,
                "   ",
                Map.of(),
                new BodylessRequest(HttpMethod.GET, "/objects", null, null, null),
                List.of(new StatusCodeAssertion(200)));
        TestSuite suite = new TestSuite(
                "blank-skip-suite",
                null,
                RestClientConfig.withDefaults(null),
                null,
                Map.of(),
                List.of(testCase),
                null,
                null);

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
    }

    // ---- buildRequestSpec: headers loop (lines 494-497) --------------------------------

    /**
     * Verifies that request-level headers declared in the test-case YAML are forwarded to the
     * outgoing HTTP request, exercising the header-iteration loop in {@code buildRequestSpec}.
     */
    @Test
    void requestHeadersAreLoopedAndAddedToSpec() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/items", 201, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(
                getClass().getResource("/test-suite-stub-post-str-body.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
    }

    // ---- buildRequestSpec: body (lines 420-421, 507-508) --------------------------------

    /**
     * Verifies that a {@code string}-typed request body is resolved and attached to a POST request,
     * covering the {@link io.github.snytkine.apitester.api_tester_cli.model.PayloadRequest}
     * instanceof check and the body-attachment branch.
     */
    @Test
    void postStringBodyIsAttachedToRequest() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/items", 201, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(
                getClass().getResource("/test-suite-stub-post-str-body.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.results().get(0).requestInfo().body()).isEqualTo("{\"name\":\"test\"}");
    }

    // ---- buildRequestSpec: request auth, basicAuthHeaderValue, hasAuthorizationHeader(null) -----

    /**
     * Verifies that a request-level BASIC auth declaration causes an {@code Authorization} header to
     * be computed via {@code basicAuthHeaderValue} and added to the spec when no explicit
     * Authorization header exists (covers {@code hasAuthorizationHeader(null)} → false path).
     */
    @Test
    void requestLevelBasicAuthAddsAuthorizationHeader() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(
                getClass().getResource("/test-suite-stub-request-auth.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.results().get(0).requestInfo().auth())
                .isEqualTo(new RequestAuth(AuthType.BASIC, "user", "pass"));
    }

    // ---- buildRequestSpec: auth skipped when Authorization header present (line 503, 642) ----

    /**
     * Verifies that when an explicit {@code Authorization} header is already present in the request
     * headers, the request-level BASIC auth is NOT applied. This exercises {@code
     * hasAuthorizationHeader} with a non-null headers map containing an {@code Authorization} key,
     * covering the {@code anyMatch} branch that returns {@code true}.
     */
    @Test
    void requestAuthIsSkippedWhenExplicitAuthorizationHeaderPresent() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(getClass()
                .getResource("/test-suite-stub-request-auth-existing-header.yml")
                .toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
        // The explicit Authorization header wins, so the declared auth is not what was actually
        // sent — it must not be reported in ExecutedRequestInfo either.
        assertThat(result.results().get(0).requestInfo().auth()).isNull();
    }

    // ---- buildRestClient: default headers (lines 594-595) -------------------------------

    /**
     * Verifies that {@code rest_client.headers} declared at the suite level are registered as
     * default headers on the built {@link org.springframework.web.client.RestClient}, covering the
     * {@code config.headers() != null} branch and the {@code forEach} lambda.
     */
    @Test
    void suiteDefaultHeadersAreRegisteredOnRestClient() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(
                getClass().getResource("/test-suite-stub-suite-headers.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
    }

    // ---- buildRestClient: suite BASIC auth (lines 598-599, 624-626) ---------------------

    /**
     * Verifies that {@code rest_client.auth} with type {@code basic} causes a default {@code
     * Authorization} header to be registered on the built {@link
     * org.springframework.web.client.RestClient}, covering the suite-auth branch and
     * {@code basicAuthHeaderValue}.
     */
    @Test
    void suiteBasicAuthIsAddedAsDefaultAuthorizationHeader() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(
                getClass().getResource("/test-suite-stub-suite-auth.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.results().get(0).requestInfo().auth())
                .isEqualTo(new RequestAuth(AuthType.BASIC, "admin", "secret"));
    }

    // ---- resolveEffectiveAuth: request-level auth overrides suite-level auth -----------

    /**
     * Verifies that when both {@code rest_client.auth} and the request's own {@code auth} are
     * declared, the captured {@link io.github.snytkine.apitester.api_tester_cli.model.ExecutedRequestInfo#auth()}
     * is the request-level auth, matching the precedence already applied when building the actual
     * {@code Authorization} header.
     */
    @Test
    void requestLevelAuthOverridesSuiteLevelAuthInCapturedRequestInfo() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path =
                Path.of(getClass().getResource("/test-suite-stub-both-auth.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.results().get(0).requestInfo().auth())
                .isEqualTo(new RequestAuth(AuthType.BASIC, "requser", "reqpass"));
    }

    // ---- resolveFullUrl / resolveRestClientId (Issue #61) -------------------------------

    /**
     * Verifies that an absolute request URL is left unchanged even when the selected rest-client
     * declares a {@code base-url} — the base-url must only be prepended to relative URLs.
     */
    @Test
    void absoluteRequestUrlIsNotCombinedWithRestClientBaseUrl() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path = Path.of(getClass()
                .getResource("/test-suite-stub-absolute-url-with-base.yml")
                .toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.results().get(0).requestInfo().url()).isEqualTo("http://other-host.test/objects");
        assertThat(result.results().get(0).requestInfo().restClientId()).isEqualTo("default");
    }

    /**
     * Verifies that in a multi-client suite, a request selecting a non-default rest-client by id
     * reports that id, and its full URL is combined using *that* client's {@code base-url}, not the
     * default client's.
     */
    @Test
    void nonDefaultRestClientSelectionReportsItsOwnIdAndBaseUrl() throws Exception {
        var factory = new StubClientHttpRequestFactory()
                .stub("/users", 200, "{}", "application/json")
                .stub("/invoices/pay", 200, "{}", "application/json");
        var engine = engineWith(factory);
        Path path =
                Path.of(getClass().getResource("/test-suite-multi-client.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(2);
        var listUsers = result.results().get(0);
        assertThat(listUsers.requestInfo().restClientId()).isEqualTo("default");
        assertThat(listUsers.requestInfo().url()).isEqualTo("https://api.example.com/users");
        var payInvoice = result.results().get(1);
        assertThat(payInvoice.requestInfo().restClientId()).isEqualTo("payments");
        assertThat(payInvoice.requestInfo().url()).isEqualTo("https://payments.example.com/invoices/pay");
    }

    /**
     * Verifies that a request selecting an unresolvable rest-client id falls back to {@code
     * "default"} for the reported {@code restClientId}, matching {@code selectRestClient}'s existing
     * warning-and-fallback behavior for the actual HTTP dispatch.
     */
    @Test
    void unknownRestClientIdFallsBackToDefaultInCapturedRequestInfo() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = engineWith(factory);
        TestCase testCase = new TestCase(
                "get objects",
                null,
                null,
                null,
                Map.of(),
                new BodylessRequest(HttpMethod.GET, "/objects", null, null, "unresolvable-client"),
                List.of(new StatusCodeAssertion(200)));
        TestSuite suite = new TestSuite(
                "unknown-client-suite",
                null,
                RestClientConfig.withDefaults(null),
                null,
                Map.of(),
                List.of(testCase),
                null,
                null);

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
        assertThat(result.results().get(0).requestInfo().restClientId()).isEqualTo("default");
    }

    // ---- buildRestClient: JDK factory + connect timeout (lines 589-592) -----------------

    /**
     * Verifies that when the engine is backed by a {@link JdkClientHttpRequestFactory} and the suite
     * declares a {@code connect_timeout}, a new factory with that timeout is built — covering lines
     * 589-592. No real HTTP connection is made because all tests are skipped.
     */
    @Test
    void buildRestClientWithJdkFactoryAndConnectTimeoutCreatesNewFactory() throws Exception {
        var engine = new PureJavaTestEngine(
                new JdkClientHttpRequestFactory(), new AssertionEvaluatorFactory(), new ResponseResolver());
        Path path = Path.of(
                getClass().getResource("/test-suite-stub-all-skipped.yml").toURI());
        TestSuite suite = loader.load(path, SuiteRunContext.of(Map.of(), Map.of()));

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.passedCount()).isZero();
    }

    // ---- per-request rest-client selection (selectRestClient) --------------------------

    /**
     * Verifies that a request declaring {@code rest-client: <id>} is dispatched through the matching
     * client (identified here by a distinct base URL/host), while a request without a selector uses
     * the {@code default} client. Correct routing is proven by giving each host a stub that returns a
     * different status code and asserting both status-code assertions pass.
     */
    @Test
    void perRequestSelectorRoutesToTheChosenClient() throws Exception {
        Path suiteFile = tempDir.resolve("multi-client-suite.yml");
        Files.writeString(
                suiteFile,
                "---\n"
                        + "name: \"Multi client suite\"\n"
                        + "rest-clients:\n"
                        + "- id: \"default\"\n"
                        + "  base-url: \"http://api.stub.test\"\n"
                        + "- id: \"payments\"\n"
                        + "  base-url: \"http://payments.stub.test\"\n"
                        + "tests:\n"
                        + "- name: \"List users\"\n"
                        + "  request:\n"
                        + "    method: \"GET\"\n"
                        + "    url: \"/users\"\n"
                        + "  assertions:\n"
                        + "  - type: \"status_code\"\n"
                        + "    expected: 200\n"
                        + "- name: \"Pay invoice\"\n"
                        + "  request:\n"
                        + "    rest-client: \"payments\"\n"
                        + "    method: \"POST\"\n"
                        + "    url: \"/invoices/pay\"\n"
                        + "  assertions:\n"
                        + "  - type: \"status_code\"\n"
                        + "    expected: 201\n");
        TestSuite suite = loader.load(suiteFile, SuiteRunContext.of(Map.of(), Map.of()));
        var factory = new StubClientHttpRequestFactory()
                .stub("api.stub.test", 200, "{}", "application/json")
                .stub("payments.stub.test", 201, "{}", "application/json");
        var engine = engineWith(factory);

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isZero();
    }

    /**
     * Verifies that when the suite uses the singular {@code rest-client} form, a request that still
     * declares a {@code rest-client} selector is ignored and falls back to the default client (rather
     * than failing). This covers the unknown-id branch of {@code selectRestClient}.
     */
    @Test
    void selectorIsIgnoredUnderSingularFormAndFallsBackToDefault() throws Exception {
        Path suiteFile = tempDir.resolve("singular-suite.yml");
        Files.writeString(
                suiteFile,
                "---\n"
                        + "name: \"Singular client suite\"\n"
                        + "rest-client:\n"
                        + "  base-url: \"http://api.stub.test\"\n"
                        + "tests:\n"
                        + "- name: \"List users\"\n"
                        + "  request:\n"
                        + "    rest-client: \"payments\"\n"
                        + "    method: \"GET\"\n"
                        + "    url: \"/users\"\n"
                        + "  assertions:\n"
                        + "  - type: \"status_code\"\n"
                        + "    expected: 200\n");
        TestSuite suite = loader.load(suiteFile, SuiteRunContext.of(Map.of(), Map.of()));
        var factory = new StubClientHttpRequestFactory().stub("api.stub.test", 200, "{}", "application/json");
        var engine = engineWith(factory);

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.passedCount()).isEqualTo(1);
    }
}
