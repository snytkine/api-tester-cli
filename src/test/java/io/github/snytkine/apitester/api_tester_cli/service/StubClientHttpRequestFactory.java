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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

/**
 * Test-only {@link ClientHttpRequestFactory} that returns pre-configured stub responses without
 * making real network connections.
 *
 * <p>Stubs are registered via {@link #stub} and {@link #stubWithDelay} and matched in registration
 * order — the first matching stub wins. Each call to {@link #createRequest} creates a fresh {@link
 * MockClientHttpResponse} from the matched stub's body bytes, so the same stub can match multiple
 * requests in the same suite without stream-exhaustion issues.
 *
 * <p>When no registered stub matches the incoming method and URI, an {@link AssertionError} is
 * thrown immediately so the test fails with a clear diagnostic.
 *
 * <p>This class is <em>not</em> thread-safe; stubs must be registered before the factory is passed
 * to a {@link io.github.snytkine.apitester.api_tester_cli.service.PureJavaTestEngine}.
 */
public class StubClientHttpRequestFactory implements ClientHttpRequestFactory {

    /**
     * Immutable description of a single stubbed HTTP interaction.
     *
     * @param methodPredicate predicate applied to the incoming Spring {@link HttpMethod}
     * @param uriPredicate predicate applied to the incoming {@link URI}
     * @param status HTTP status code to return
     * @param body response body text
     * @param contentType {@code Content-Type} header value for the response
     * @param delayMs milliseconds to sleep before returning the response; {@code 0} means no delay
     */
    public record Stub(
            Predicate<HttpMethod> methodPredicate,
            Predicate<URI> uriPredicate,
            int status,
            String body,
            String contentType,
            long delayMs) {}

    private final List<Stub> stubs = new ArrayList<>();

    /**
     * Registers a method-agnostic stub that matches any HTTP method whose URI contains {@code
     * uriSubstring}.
     *
     * @param uriSubstring substring that the request URI must contain
     * @param status HTTP status code to return
     * @param body response body text
     * @param contentType {@code Content-Type} header value
     * @return {@code this} for chaining
     */
    public StubClientHttpRequestFactory stub(String uriSubstring, int status, String body, String contentType) {
        stubs.add(new Stub(m -> true, uri -> uri.toString().contains(uriSubstring), status, body, contentType, 0));
        return this;
    }

    /**
     * Registers a method-specific stub that matches the given HTTP method and URI substring.
     *
     * @param method the exact Spring {@link HttpMethod} that must match
     * @param uriSubstring substring that the request URI must contain
     * @param status HTTP status code to return
     * @param body response body text
     * @param contentType {@code Content-Type} header value
     * @return {@code this} for chaining
     */
    public StubClientHttpRequestFactory stub(
            HttpMethod method, String uriSubstring, int status, String body, String contentType) {
        stubs.add(new Stub(
                m -> m.equals(method), uri -> uri.toString().contains(uriSubstring), status, body, contentType, 0));
        return this;
    }

    /**
     * Registers a method-specific stub that sleeps {@code delayMs} milliseconds before returning the
     * response. Use this to exercise {@code response_time} assertions.
     *
     * @param method the exact Spring {@link HttpMethod} that must match
     * @param uriSubstring substring that the request URI must contain
     * @param status HTTP status code to return
     * @param body response body text
     * @param contentType {@code Content-Type} header value
     * @param delayMs milliseconds to sleep before returning; must be positive
     * @return {@code this} for chaining
     */
    public StubClientHttpRequestFactory stubWithDelay(
            HttpMethod method, String uriSubstring, int status, String body, String contentType, long delayMs) {
        stubs.add(new Stub(
                m -> m.equals(method),
                uri -> uri.toString().contains(uriSubstring),
                status,
                body,
                contentType,
                delayMs));
        return this;
    }

    /**
     * Returns a {@link ClientHttpRequest} whose {@link ClientHttpRequest#execute()} delivers the
     * first stub whose method and URI predicates match.
     *
     * @param uri the request URI
     * @param method the HTTP method
     * @return a configured {@link MockClientHttpRequest} (or a delayed subclass when {@code delayMs}
     *     is positive)
     * @throws AssertionError when no registered stub matches the incoming request
     */
    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
        for (Stub stub : stubs) {
            if (stub.methodPredicate().test(method) && stub.uriPredicate().test(uri)) {
                return buildRequest(uri, method, stub);
            }
        }
        throw new AssertionError("StubClientHttpRequestFactory: no stub registered for " + method + " " + uri);
    }

    /**
     * Constructs the mock request/response pair for the given stub.
     *
     * <p>A fresh {@link MockClientHttpResponse} is created for every call so that each request gets
     * its own independent input stream over the body bytes.
     */
    private ClientHttpRequest buildRequest(URI uri, HttpMethod method, Stub stub) {
        MockClientHttpResponse response =
                new MockClientHttpResponse(stub.body().getBytes(StandardCharsets.UTF_8), stub.status());
        response.getHeaders().setContentType(MediaType.parseMediaType(stub.contentType()));

        if (stub.delayMs() > 0) {
            return new DelayedMockClientHttpRequest(method, uri, response, stub.delayMs());
        }
        MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
        request.setResponse(response);
        return request;
    }

    /**
     * A {@link MockClientHttpRequest} subclass that sleeps for {@code delayMs} milliseconds before
     * returning the configured response. Used to simulate slow endpoints for {@code response_time}
     * assertion tests.
     *
     * <p>If the sleep is interrupted the thread's interrupt flag is restored and the response is
     * returned immediately.
     */
    private static class DelayedMockClientHttpRequest extends MockClientHttpRequest {

        private final MockClientHttpResponse response;
        private final long delayMs;

        DelayedMockClientHttpRequest(HttpMethod method, URI uri, MockClientHttpResponse response, long delayMs) {
            super(method, uri);
            this.response = response;
            this.delayMs = delayMs;
        }

        @Override
        protected ClientHttpResponse executeInternal() throws IOException {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return response;
        }
    }
}
