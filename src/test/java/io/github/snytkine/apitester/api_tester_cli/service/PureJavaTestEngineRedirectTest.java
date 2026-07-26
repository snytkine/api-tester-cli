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

import com.sun.net.httpserver.HttpServer;
import io.github.snytkine.apitester.api_tester_cli.event.NoOpProgressListener;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * End-to-end tests for the rest-client {@code follow-redirects} option (issue #72).
 *
 * <p>Unlike the other engine tests these do not stub the transport: a real {@link HttpServer} from
 * the JDK is started on an ephemeral port, serving {@code /redirect} (a 301 pointing at {@code
 * /target}) and {@code /target} (a 200 JSON body). Only a real HTTP round trip can demonstrate that
 * a redirect was or was not followed, because redirect handling lives inside {@link HttpClient} and
 * is invisible to a stub factory.
 *
 * <p>The injected {@link JdkClientHttpRequestFactory} mirrors the application's default factory
 * ({@code HttpClientConfig}) by explicitly selecting {@link HttpClient.Redirect#NORMAL}. This
 * matters: a bare {@code HttpClient.newHttpClient()} defaults to {@link HttpClient.Redirect#NEVER},
 * so a test built on the JDK default would pass even if the feature were broken.
 *
 * <p>The server port is only known at runtime, so the suite fixtures take their base URL from a
 * {@code cli} variable rather than hard-coding it.
 */
class PureJavaTestEngineRedirectTest {

    private HttpServer server;
    private String baseUrl;

    /**
     * Starts a local HTTP server exposing a redirect endpoint and its target.
     *
     * @throws Exception if the server cannot be bound or started
     */
    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/target");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.createContext("/target", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Stops the local HTTP server. */
    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * Runs a suite fixture against the local server.
     *
     * @param fixture classpath name of the suite YAML
     * @return the aggregated run result
     * @throws Exception if the fixture cannot be loaded
     */
    private TestRunResult runFixture(String fixture) throws Exception {
        // Mirror HttpClientConfig's production defaults, including Redirect.NORMAL.
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        var engine = new PureJavaTestEngine(
                new JdkClientHttpRequestFactory(httpClient), new AssertionEvaluatorFactory(), new ResponseResolver());

        Path path = Path.of(getClass().getResource(fixture).toURI());
        SuiteRunContext context = SuiteRunContext.of(Map.of(), Map.of("base_url", baseUrl));
        TestSuite suite = new TestSuiteLoader().load(path, context);

        return engine.runConfigurationSuite(suite, context, NoOpProgressListener.INSTANCE);
    }

    @Test
    void followRedirectsFalseSurfacesThe301AndItsLocationHeader() throws Exception {
        TestRunResult result = runFixture("/test-suite-redirect-no-follow.yml");

        assertThat(result.failedCount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(result.passedCount()).isEqualTo(1);
    }

    @Test
    void omittingFollowRedirectsFollowsTheRedirectToTheTarget() throws Exception {
        TestRunResult result = runFixture("/test-suite-redirect-follow-default.yml");

        assertThat(result.failedCount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(result.passedCount()).isEqualTo(1);
    }

    @Test
    void followRedirectsFalseIsParsedFromTheSuiteYaml() throws Exception {
        Path path = Path.of(
                getClass().getResource("/test-suite-redirect-no-follow.yml").toURI());
        SuiteRunContext context = SuiteRunContext.of(Map.of(), Map.of("base_url", baseUrl));

        TestSuite suite = new TestSuiteLoader().load(path, context);

        RestClientConfig defaultClient = suite.defaultRestClient();
        assertThat(defaultClient).isNotNull();
        assertThat(defaultClient.followRedirects()).isFalse();
        assertThat(defaultClient.followRedirectsOrDefault()).isFalse();
    }

    /**
     * A suite that omits {@code follow-redirects} resolves to {@code true}. The value is non-null
     * rather than null because {@link TestSuiteLoader} normalises every rest-client through {@link
     * RestClientConfig#withDefaults(RestClientConfig)} at load time; the raw null-means-default
     * behaviour is covered by {@code RestClientConfigTest}.
     */
    @Test
    void omittedFollowRedirectsDefaultsToTrueAfterLoading() throws Exception {
        Path path = Path.of(getClass()
                .getResource("/test-suite-redirect-follow-default.yml")
                .toURI());
        SuiteRunContext context = SuiteRunContext.of(Map.of(), Map.of("base_url", baseUrl));

        TestSuite suite = new TestSuiteLoader().load(path, context);

        RestClientConfig defaultClient = suite.defaultRestClient();
        assertThat(defaultClient).isNotNull();
        assertThat(defaultClient.followRedirects()).isTrue();
        assertThat(defaultClient.followRedirectsOrDefault()).isTrue();
    }
}
