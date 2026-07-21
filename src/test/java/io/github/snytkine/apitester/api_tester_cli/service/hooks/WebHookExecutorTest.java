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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.model.AuthType;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RequestAuth;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.service.StubClientHttpRequestFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Unit tests for {@link WebHookExecutor} using the shared stub HTTP factory. */
class WebHookExecutorTest {

    private static final RestClientConfig CONFIG = RestClientConfig.withDefaults(null);

    @Test
    void status200IsSuccess() {
        StubClientHttpRequestFactory factory =
                new StubClientHttpRequestFactory().stub("hook", 200, "{}", "application/json");
        WebHookExecutor executor = new WebHookExecutor(factory);

        HookExecutionResult result = executor.execute(
                CONFIG, HttpMethod.POST, "http://example.test/hook", Map.of("k", "v"), null, false, 10);

        assertThat(result.success()).isTrue();
        assertThat(result.exitCodeOrStatus()).isEqualTo(200);
    }

    @Test
    void status201IsSuccess() {
        StubClientHttpRequestFactory factory =
                new StubClientHttpRequestFactory().stub("hook", 201, "{}", "application/json");
        WebHookExecutor executor = new WebHookExecutor(factory);

        HookExecutionResult result =
                executor.execute(CONFIG, HttpMethod.PUT, "http://example.test/hook", Map.of(), null, false, 10);

        assertThat(result.success()).isTrue();
        assertThat(result.exitCodeOrStatus()).isEqualTo(201);
    }

    @Test
    void non2xxStatusIsFailure() {
        StubClientHttpRequestFactory factory =
                new StubClientHttpRequestFactory().stub("hook", 500, "err", "text/plain");
        WebHookExecutor executor = new WebHookExecutor(factory);

        HookExecutionResult result =
                executor.execute(CONFIG, HttpMethod.POST, "http://example.test/hook", Map.of(), null, false, 10);

        assertThat(result.success()).isFalse();
        assertThat(result.exitCodeOrStatus()).isEqualTo(500);
        assertThat(result.errorMessage()).contains("500");
    }

    @Test
    void attachReportSendsMultipartAndSucceedsOn200(@TempDir Path dir) throws IOException {
        Path report = dir.resolve("report.html");
        Files.writeString(report, "<html>ok</html>");
        StubClientHttpRequestFactory factory =
                new StubClientHttpRequestFactory().stub("report", 200, "{}", "application/json");
        WebHookExecutor executor = new WebHookExecutor(factory);

        HookExecutionResult result = executor.execute(
                CONFIG, HttpMethod.POST, "http://example.test/report", Map.of("x", "y"), report, true, 10);

        assertThat(result.success()).isTrue();
        assertThat(result.exitCodeOrStatus()).isEqualTo(200);
    }

    @Test
    void slowEndpointExceedingTimeoutIsReportedAsTimedOut() {
        StubClientHttpRequestFactory factory = new StubClientHttpRequestFactory()
                .stubWithDelay(org.springframework.http.HttpMethod.POST, "slow", 200, "{}", "application/json", 3000);
        WebHookExecutor executor = new WebHookExecutor(factory);

        long start = System.currentTimeMillis();
        HookExecutionResult result =
                executor.execute(CONFIG, HttpMethod.POST, "http://example.test/slow", Map.of(), null, false, 1);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.timedOut()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(elapsed).isLessThan(2500L);
    }

    @Test
    void baseUrlHeadersAndBasicAuthAreApplied() {
        RestClientConfig config = new RestClientConfig(
                null,
                "http://example.test",
                30000,
                Map.of("X-Extra", "1"),
                new RequestAuth(AuthType.BASIC, "user", "pass"));
        StubClientHttpRequestFactory factory =
                new StubClientHttpRequestFactory().stub("hook", 200, "{}", "application/json");
        WebHookExecutor executor = new WebHookExecutor(factory);

        // Relative URL resolved against the config base-url; headers + Basic auth applied by builder.
        HookExecutionResult result = executor.execute(config, HttpMethod.POST, "/hook", Map.of(), null, false, 10);

        assertThat(result.success()).isTrue();
        assertThat(result.exitCodeOrStatus()).isEqualTo(200);
    }

    @Test
    void transportErrorWithJdkFactoryIsReportedAsFailure() {
        // connect-timeout present + a real JDK factory exercises the timeout-configured builder branch;
        // the unreachable port fails fast and surfaces as a (non-timeout) failure.
        RestClientConfig config = new RestClientConfig(null, "", 200, null, null);
        WebHookExecutor executor = new WebHookExecutor(new JdkClientHttpRequestFactory());

        HookExecutionResult result =
                executor.execute(config, HttpMethod.POST, "http://localhost:1/hook", Map.of(), null, false, 5);

        assertThat(result.success()).isFalse();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.errorMessage()).contains("failed");
    }

    @Test
    void unserializablePayloadIsReportedAsFailure() {
        WebHookExecutor executor =
                new WebHookExecutor(new StubClientHttpRequestFactory().stub("hook", 200, "{}", "application/json"));
        java.util.Map<String, Object> selfReferential = new java.util.HashMap<>();
        selfReferential.put("self", selfReferential); // Jackson cannot serialise a cycle.

        HookExecutionResult result =
                executor.execute(CONFIG, HttpMethod.POST, "http://example.test/hook", selfReferential, null, false, 10);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("serialise");
    }

    @Test
    void interruptedWhileWaitingIsReportedAsFailure() {
        WebHookExecutor executor = new WebHookExecutor(new StubClientHttpRequestFactory()
                .stubWithDelay(org.springframework.http.HttpMethod.POST, "hook", 200, "{}", "application/json", 2000));
        Thread.currentThread().interrupt();
        try {
            HookExecutionResult result =
                    executor.execute(CONFIG, HttpMethod.POST, "http://example.test/hook", Map.of(), null, false, 10);
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).contains("Interrupted");
        } finally {
            Thread.interrupted();
        }
    }
}
