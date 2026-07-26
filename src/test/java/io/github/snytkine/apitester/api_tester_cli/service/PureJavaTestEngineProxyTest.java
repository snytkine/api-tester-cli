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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.github.snytkine.apitester.api_tester_cli.event.NoOpProgressListener;
import io.github.snytkine.apitester.api_tester_cli.model.KeystoreConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SslConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import io.github.snytkine.apitester.api_tester_cli.util.SslContextFactory;
import io.github.snytkine.apitester.api_tester_cli.util.StubProxyServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * End-to-end tests proving that requests really do traverse a configured proxy.
 *
 * <p>These deliberately avoid {@code StubClientHttpRequestFactory}: proxy handling lives inside
 * {@link HttpClient}, below the request-factory seam, so a stubbed transport cannot observe it. A
 * real {@link StubProxyServer} sits between the engine and a real origin server and records what it
 * saw. Suites are loaded from YAML fixtures so the {@code proxy} deserializer and validator are
 * exercised on the way through rather than bypassed.
 *
 * <p>The {@code https} cases matter most. For a TLS endpoint the proxy is asked to open a {@code
 * CONNECT} tunnel, and it is the {@code CONNECT} — not the request inside it — that carries {@code
 * Proxy-Authorization}. The JDK also refuses to answer such a challenge with {@code Basic} unless
 * {@code jdk.http.auth.tunneling.disabledSchemes} has been cleared, which the Surefire
 * configuration does for the test JVM. Both behaviours are exercised here rather than assumed.
 *
 * <p>The HTTPS origin reuses the existing {@code src/test/resources/ssl} fixtures as its server
 * identity. That certificate is {@code CN=test-client} with no subject-alternative names, so the
 * fixtures that target it set {@code skip-certificate-validation} — the subject of these tests is
 * the proxy, not certificate validation.
 */
class PureJavaTestEngineProxyTest {

    private HttpServer httpOrigin;
    private HttpsServer httpsOrigin;
    private final List<String> originAuthorizationHeaders = new CopyOnWriteArrayList<>();

    /**
     * Starts a plaintext and a TLS origin server, both recording any {@code Authorization} header
     * they receive so endpoint credentials can be told apart from proxy credentials.
     *
     * @throws Exception if either server cannot be started
     */
    @BeforeEach
    void startOrigins() throws Exception {
        httpOrigin = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpOrigin.createContext("/api", this::respond);
        httpOrigin.start();

        SSLContext serverContext = SslContextFactory.create(
                new SslConfig(null, null, new KeystoreConfig("client.pem", "client.key", null)),
                Path.of("src/test/resources/ssl"));
        httpsOrigin = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpsOrigin.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        httpsOrigin.createContext("/api", this::respond);
        httpsOrigin.start();
    }

    /** Stops both origin servers. */
    @AfterEach
    void stopOrigins() {
        if (httpOrigin != null) {
            httpOrigin.stop(0);
        }
        if (httpsOrigin != null) {
            httpsOrigin.stop(0);
        }
    }

    /**
     * Records the request's {@code Authorization} header and returns a fixed JSON body.
     *
     * @param exchange the incoming exchange
     * @throws java.io.IOException if the response cannot be written
     */
    private void respond(HttpExchange exchange) throws java.io.IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        originAuthorizationHeaders.add(auth == null ? "<none>" : auth);
        byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
        exchange.close();
    }

    /**
     * A request to an {@code http://} endpoint is forwarded by the proxy rather than sent directly.
     *
     * @throws Exception if the proxy cannot be started or the fixture cannot be loaded
     */
    @Test
    void httpRequestIsForwardedThroughProxy() throws Exception {
        try (StubProxyServer proxy = StubProxyServer.open()) {
            TestRunResult result = run("/test-suite-proxy-http.yml", httpBaseUrl(), proxy.url(), Map.of());

            assertThat(result.passedCount()).isEqualTo(1);
            assertThat(proxy.requestTargets()).containsExactly(httpBaseUrl() + "/api");
        }
    }

    /**
     * A request to an {@code https://} endpoint reaches the proxy as a {@code CONNECT} tunnel and
     * still completes its TLS handshake with the origin.
     *
     * @throws Exception if the proxy cannot be started or the fixture cannot be loaded
     */
    @Test
    void httpsRequestIsTunneledThroughProxy() throws Exception {
        try (StubProxyServer proxy = StubProxyServer.open()) {
            TestRunResult result = run("/test-suite-proxy-https.yml", httpsBaseUrl(), proxy.url(), Map.of());

            assertThat(result.passedCount()).isEqualTo(1);
            assertThat(proxy.requestTargets())
                    .containsExactly("127.0.0.1:" + httpsOrigin.getAddress().getPort());
        }
    }

    /**
     * Basic proxy authentication succeeds over a {@code CONNECT} tunnel — the case the JDK disables
     * by default, and the single most important behaviour in this feature.
     *
     * @throws Exception if the proxy cannot be started or the fixture cannot be loaded
     */
    @Test
    void basicProxyAuthenticationSucceedsOverConnectTunnel() throws Exception {
        try (StubProxyServer proxy = StubProxyServer.requiringAuth("proxyuser", "proxypass")) {
            Map<String, String> env = Map.of("PROXY_USER", "proxyuser", "PROXY_PASSWORD", "proxypass");

            TestRunResult result = run("/test-suite-proxy-auth.yml", httpsBaseUrl(), proxy.url(), env);

            assertThat(result.passedCount()).isEqualTo(1);
            assertThat(proxy.challengeCount()).isGreaterThanOrEqualTo(1);
            assertThat(proxy.observedProxyAuthorization()).contains(basic("proxyuser", "proxypass"));
        }
    }

    /**
     * Proxy credentials and endpoint credentials coexist: the proxy sees only {@code
     * Proxy-Authorization} and the endpoint sees only its own, different, {@code Authorization}.
     *
     * <p>This is issue #71's explicit requirement, and it is why {@code ProxyAuthenticator} answers
     * proxy challenges only.
     *
     * @throws Exception if the proxy cannot be started or the fixture cannot be loaded
     */
    @Test
    void proxyCredentialsAndEndpointCredentialsDoNotInterfere() throws Exception {
        try (StubProxyServer proxy = StubProxyServer.requiringAuth("proxyuser", "proxypass")) {
            Map<String, String> env = Map.of("PROXY_USER", "proxyuser", "PROXY_PASSWORD", "proxypass");

            TestRunResult result = run("/test-suite-proxy-dual-auth.yml", httpsBaseUrl(), proxy.url(), env);

            assertThat(result.passedCount()).isEqualTo(1);
            assertThat(originAuthorizationHeaders).containsExactly(basic("apiuser", "apipass"));
            assertThat(proxy.observedProxyAuthorization())
                    .contains(basic("proxyuser", "proxypass"))
                    .doesNotContain(basic("apiuser", "apipass"));
        }
    }

    /**
     * With {@code HTTP_PROXY} / {@code HTTPS_PROXY} set, a client that omits {@code proxy} is
     * proxied while its sibling declaring {@code proxy: false} connects directly — and both
     * requests still succeed.
     *
     * @throws Exception if the proxy cannot be started or the fixture cannot be loaded
     */
    @Test
    void proxyFalseBypassesEnvironmentProxyWhileSiblingUsesIt() throws Exception {
        try (StubProxyServer proxy = StubProxyServer.open()) {
            Map<String, String> env = Map.of("HTTP_PROXY", proxy.url(), "HTTPS_PROXY", proxy.url());

            TestRunResult result = run("/test-suite-proxy-disabled.yml", httpBaseUrl(), proxy.url(), env);

            assertThat(result.passedCount()).isEqualTo(2);
            // Exactly one of the two tests went through the proxy; the opted-out client did not.
            assertThat(proxy.requestTargets()).containsExactly(httpBaseUrl() + "/api");
            assertThat(originAuthorizationHeaders).hasSize(2);
        }
    }

    /**
     * An unreachable proxy fails the test with a message naming the proxy, not a bare connection
     * error against the endpoint — which is healthy and was never contacted.
     *
     * @throws Exception if a free port cannot be reserved or the fixture cannot be loaded
     */
    @Test
    void unreachableProxyIsReportedAsProxyFailure() throws Exception {
        int deadPort;
        try (ServerSocket reserved = new ServerSocket(0)) {
            deadPort = reserved.getLocalPort();
        }

        TestRunResult result =
                run("/test-suite-proxy-http.yml", httpBaseUrl(), "http://127.0.0.1:" + deadPort, Map.of());

        assertThat(result.passedCount()).isZero();
        assertThat(firstFailureText(result)).contains("could not connect to the proxy at 127.0.0.1:" + deadPort);
    }

    /**
     * A proxy demanding authentication that the suite does not supply fails with a message saying
     * so, rather than a generic transport error.
     *
     * @throws Exception if the proxy cannot be started or the fixture cannot be loaded
     */
    @Test
    void missingProxyCredentialsAreReportedAsProxyAuthenticationFailure() throws Exception {
        try (StubProxyServer proxy = StubProxyServer.requiringAuth("proxyuser", "proxypass")) {
            TestRunResult result = run("/test-suite-proxy-https.yml", httpsBaseUrl(), proxy.url(), Map.of());

            assertThat(result.passedCount()).isZero();
            assertThat(firstFailureText(result))
                    .contains("requires authentication but no proxy credentials are configured");
        }
    }

    /**
     * A proxy challenging with a scheme the JDK cannot satisfy is reported as such, naming the
     * scheme, so the user is not left wondering why correct credentials were never sent.
     *
     * @throws Exception if the proxy cannot be started or the fixture cannot be loaded
     */
    @Test
    void nonBasicProxyChallengeIsReportedAsUnsupported() throws Exception {
        try (StubProxyServer proxy = new StubProxyServer("proxyuser", "proxypass", "NTLM")) {
            Map<String, String> env = Map.of("PROXY_USER", "proxyuser", "PROXY_PASSWORD", "proxypass");

            TestRunResult result = run("/test-suite-proxy-auth.yml", httpsBaseUrl(), proxy.url(), env);

            assertThat(result.passedCount()).isZero();
            assertThat(firstFailureText(result)).contains("NTLM").contains("not supported");
        }
    }

    /**
     * Loads a suite fixture and runs it against the given base URL and proxy.
     *
     * @param fixture classpath name of the suite YAML
     * @param baseUrl the origin base URL, supplied as the {@code base_url} CLI variable
     * @param proxyUrl the proxy URL, supplied as the {@code proxy_url} CLI variable
     * @param env the merged environment for the run
     * @return the aggregated run result
     * @throws Exception if the fixture cannot be loaded
     */
    private TestRunResult run(String fixture, String baseUrl, String proxyUrl, Map<String, String> env)
            throws Exception {
        PureJavaTestEngine engine = new PureJavaTestEngine(
                new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build()),
                new AssertionEvaluatorFactory(),
                new ResponseResolver());

        Path path = Path.of(getClass().getResource(fixture).toURI());
        SuiteRunContext context = SuiteRunContext.of(env, Map.of("base_url", baseUrl, "proxy_url", proxyUrl));
        TestSuite suite = new TestSuiteLoader().load(path, context);

        return engine.runConfigurationSuite(suite, context, NoOpProgressListener.INSTANCE);
    }

    /**
     * Returns the {@code actual} text of the first failure of the first test case.
     *
     * @param result the run result
     * @return the failure's actual-value text
     */
    private static String firstFailureText(TestRunResult result) {
        return result.results().get(0).failures().get(0).actual();
    }

    /**
     * Builds the {@code Basic} credential header value for a username and password.
     *
     * @param username the username
     * @param password the password
     * @return a {@code Basic <base64>} header value
     */
    private static String basic(String username, String password) {
        return "Basic "
                + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the plaintext origin's base URL.
     *
     * @return {@code http://127.0.0.1:port}
     */
    private String httpBaseUrl() {
        return "http://127.0.0.1:" + httpOrigin.getAddress().getPort();
    }

    /**
     * Returns the TLS origin's base URL.
     *
     * @return {@code https://127.0.0.1:port}
     */
    private String httpsBaseUrl() {
        return "https://127.0.0.1:" + httpsOrigin.getAddress().getPort();
    }
}
