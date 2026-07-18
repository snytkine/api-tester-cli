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
package io.github.snytkine.apitester.api_tester_cli.service.hooks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.snytkine.apitester.api_tester_cli.model.AuthType;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RequestAuth;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Issues an outbound HTTP request for a web lifecycle hook and reports the outcome. Stateless and
 * thread-safe: a fresh {@link RestClient} is built per call from the supplied {@link
 * RestClientConfig}, and every per-invocation value lives on the call stack.
 *
 * <p>The request body is a JSON object (the assembled hook payload) unless {@code attachReport} is
 * set for an {@code after-report} hook, in which case a {@code multipart/form-data} request is sent
 * with a {@code payload} JSON part and a {@code report} HTML file part. The hook is considered
 * successful only on an HTTP {@code 200} or {@code 201} response; any other status, a transport
 * error, or a timeout is a failure. The call is bounded by the hook timeout via a single-use
 * executor so a hanging endpoint can never stall the run.
 */
@Service
public class WebHookExecutor {

    private static final Logger log = LoggerFactory.getLogger(WebHookExecutor.class);

    private final ClientHttpRequestFactory requestFactory;
    private final ObjectMapper jsonMapper;

    /**
     * Constructs the executor with the shared HTTP transport factory (the same bean the test engine
     * uses, so web hooks are exercised via {@code StubClientHttpRequestFactory} in tests). A private
     * JSON {@link ObjectMapper} is created internally; Jackson is not a bean in this CLI.
     *
     * @param requestFactory the HTTP transport factory backing each per-call {@link RestClient}
     */
    public WebHookExecutor(ClientHttpRequestFactory requestFactory) {
        this.requestFactory = requestFactory;
        this.jsonMapper = new ObjectMapper();
    }

    /**
     * Executes a web hook request and returns its outcome.
     *
     * @param config the rest-client configuration supplying base URL, default headers, auth, and
     *     connect timeout
     * @param method the HTTP method ({@code POST} or {@code PUT})
     * @param url the request URL; absolute or relative to the client's base URL
     * @param payload the JSON payload object (system fields plus any user {@code payload} fields);
     *     for {@code attach-report} requests this becomes the {@code payload} multipart part
     * @param reportFile the HTML report file to attach, or {@code null}
     * @param attachReport whether to send a {@code multipart/form-data} request with the report file
     * @param timeoutSeconds maximum seconds to wait before abandoning the call
     * @return the {@link HookExecutionResult} describing HTTP status / timeout / duration
     */
    public HookExecutionResult execute(
            RestClientConfig config,
            HttpMethod method,
            String url,
            Map<String, Object> payload,
            Path reportFile,
            boolean attachReport,
            int timeoutSeconds) {

        long start = System.currentTimeMillis();
        String json;
        try {
            json = jsonMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return HookExecutionResult.failure(
                    HookExecutionResult.NO_STATUS,
                    System.currentTimeMillis() - start,
                    "Failed to serialise web hook payload: " + e.getMessage());
        }

        RestClient client = buildRestClient(config);
        Callable<Integer> call = () -> exchangeStatus(client, method, url, json, reportFile, attachReport);

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "web-hook");
            t.setDaemon(true);
            return t;
        });
        Future<Integer> future = executor.submit(call);
        try {
            int status = future.get(timeoutSeconds, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - start;
            if (status == 200 || status == 201) {
                return HookExecutionResult.success(status, duration);
            }
            return HookExecutionResult.failure(status, duration, "Web hook returned HTTP " + status);
        } catch (TimeoutException e) {
            future.cancel(true);
            return HookExecutionResult.timedOut(
                    System.currentTimeMillis() - start, "Web hook exceeded timeout of " + timeoutSeconds + "s");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return HookExecutionResult.failure(
                    HookExecutionResult.NO_STATUS,
                    System.currentTimeMillis() - start,
                    "Interrupted while waiting for web hook");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return HookExecutionResult.failure(
                    HookExecutionResult.NO_STATUS,
                    System.currentTimeMillis() - start,
                    "Web hook request failed: " + cause.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Performs the HTTP exchange and returns the raw status code without throwing on non-2xx.
     *
     * @param client the configured client
     * @param method the HTTP method
     * @param url the request URL
     * @param json the serialised JSON payload
     * @param reportFile the report file to attach, or {@code null}
     * @param attachReport whether to send multipart with the report attached
     * @return the HTTP status code of the response
     */
    private int exchangeStatus(
            RestClient client, HttpMethod method, String url, String json, Path reportFile, boolean attachReport) {
        RestClient.RequestBodySpec spec = client.method(org.springframework.http.HttpMethod.valueOf(method.name()))
                .uri(url);
        RestClient.RequestHeadersSpec<?> ready;
        if (attachReport && reportFile != null) {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("payload", json, MediaType.APPLICATION_JSON);
            builder.part("report", new FileSystemResource(reportFile), MediaType.valueOf("text/html; charset=UTF-8"))
                    .filename(reportFile.getFileName().toString());
            MultiValueMap<String, org.springframework.http.HttpEntity<?>> parts = builder.build();
            ready = spec.contentType(MediaType.MULTIPART_FORM_DATA).body(parts);
        } else {
            ready = spec.contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return ready.exchange((request, response) -> response.getStatusCode().value());
    }

    /**
     * Builds a {@link RestClient} from the rest-client configuration, mirroring the engine's client
     * construction: base URL, connect timeout (only for JDK-backed factories), default headers, and
     * Basic auth.
     *
     * @param config the rest-client configuration
     * @return a configured {@link RestClient}
     */
    private RestClient buildRestClient(RestClientConfig config) {
        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (StringUtils.hasText(config.baseUrl())) {
            builder.baseUrl(config.baseUrl());
        }
        if (config.connectTimeout() != null && requestFactory instanceof JdkClientHttpRequestFactory) {
            builder.requestFactory(new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.connectTimeout()))
                    .build()));
        }
        if (config.headers() != null) {
            config.headers().forEach(builder::defaultHeader);
        }
        RequestAuth auth = config.auth();
        if (auth != null && auth.type() == AuthType.BASIC) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, basicAuthHeaderValue(auth));
        }
        return builder.build();
    }

    /**
     * Builds the {@code Basic <base64(user:pass)>} header value.
     *
     * @param auth the auth configuration
     * @return the Authorization header value
     */
    private static String basicAuthHeaderValue(RequestAuth auth) {
        String credentials = auth.username() + ":" + auth.password();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
