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
package io.github.snytkine.apitester.api_tester_cli.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RestClientConfig#withDefaults(RestClientConfig)}, verifying that {@code
 * baseUrl} and {@code connectTimeout} receive defaults when absent, while {@code headers} and
 * {@code auth} are always passed through as-is (null when absent, non-null when present).
 */
class RestClientConfigTest {

    @Test
    void withDefaultsOnNullRawReturnsAllDefaults() {
        RestClientConfig result = RestClientConfig.withDefaults(null);

        assertThat(result.baseUrl()).isEqualTo(RestClientConfig.DEFAULT_BASE_URL);
        assertThat(result.connectTimeout()).isEqualTo(RestClientConfig.DEFAULT_CONNECT_TIMEOUT_MS);
        assertThat(result.headers()).isNull();
        assertThat(result.auth()).isNull();
    }

    @Test
    void withDefaultsPreservesExplicitValues() {
        RestClientConfig raw = new RestClientConfig(null, "https://api.example.com", 1000, null, null);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.baseUrl()).isEqualTo("https://api.example.com");
        assertThat(result.connectTimeout()).isEqualTo(1000);
    }

    @Test
    void withDefaultsFillsMissingBaseUrlAndTimeout() {
        RestClientConfig raw = new RestClientConfig(null, null, null, null, null);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.baseUrl()).isEqualTo(RestClientConfig.DEFAULT_BASE_URL);
        assertThat(result.connectTimeout()).isEqualTo(RestClientConfig.DEFAULT_CONNECT_TIMEOUT_MS);
    }

    @Test
    void withDefaultsPassesThroughIdWhenPresent() {
        RestClientConfig raw = new RestClientConfig("payments", null, null, null, null);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.id()).isEqualTo("payments");
    }

    @Test
    void withDefaultsPassesThroughHeadersWhenPresent() {
        Map<String, String> headers = Map.of("x-api-key", "secret", "Accept", "application/json");
        RestClientConfig raw = new RestClientConfig(null, null, null, headers, null);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.headers()).containsExactlyInAnyOrderEntriesOf(headers);
    }

    @Test
    void withDefaultsPassesThroughNullHeadersWhenAbsent() {
        RestClientConfig raw = new RestClientConfig(null, "https://api.example.com", 5000, null, null);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.headers()).isNull();
    }

    @Test
    void withDefaultsPassesThroughAuthWhenPresent() {
        RequestAuth auth = new RequestAuth(AuthType.BASIC, "user", "pass");
        RestClientConfig raw = new RestClientConfig(null, null, null, null, auth);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.auth()).isEqualTo(auth);
    }

    @Test
    void withDefaultsPassesThroughNullAuthWhenAbsent() {
        RestClientConfig raw = new RestClientConfig(null, null, null, null, null);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.auth()).isNull();
    }

    @Test
    void withDefaultsPassesThroughSslWhenPresent() {
        SslConfig ssl = new SslConfig(true, null, null);
        RestClientConfig raw = new RestClientConfig(null, null, null, null, null, ssl);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.ssl()).isEqualTo(ssl);
    }

    @Test
    void backwardsCompatibleConstructorLeavesSslNull() {
        RestClientConfig raw = new RestClientConfig("id", "https://api.example.com", 1000, null, null);

        assertThat(raw.ssl()).isNull();
    }

    // --- follow-redirects ---

    @Test
    void withDefaultsOnNullRawEnablesFollowRedirects() {
        RestClientConfig result = RestClientConfig.withDefaults(null);

        assertThat(result.followRedirects()).isEqualTo(RestClientConfig.DEFAULT_FOLLOW_REDIRECTS);
        assertThat(result.followRedirectsOrDefault()).isTrue();
    }

    @Test
    void withDefaultsFillsMissingFollowRedirectsWithTrue() {
        RestClientConfig raw = new RestClientConfig(null, null, null, null, null, null, null);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.followRedirects()).isTrue();
    }

    @Test
    void withDefaultsPreservesExplicitFollowRedirectsFalse() {
        RestClientConfig raw = new RestClientConfig(null, "https://api.example.com", 1000, null, null, null, false);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.followRedirects()).isFalse();
        assertThat(result.followRedirectsOrDefault()).isFalse();
    }

    @Test
    void withDefaultsPreservesExplicitFollowRedirectsTrue() {
        RestClientConfig raw = new RestClientConfig(null, "https://api.example.com", 1000, null, null, null, true);

        RestClientConfig result = RestClientConfig.withDefaults(raw);

        assertThat(result.followRedirects()).isTrue();
    }

    @Test
    void followRedirectsOrDefaultTreatsAbsentValueAsTrue() {
        RestClientConfig raw = new RestClientConfig(null, "https://api.example.com", 1000, null, null, null, null);

        assertThat(raw.followRedirects()).isNull();
        assertThat(raw.followRedirectsOrDefault()).isTrue();
    }

    @Test
    void backwardsCompatibleConstructorsLeaveFollowRedirectsNull() {
        RestClientConfig fiveArg = new RestClientConfig("id", "https://api.example.com", 1000, null, null);
        RestClientConfig sixArg = new RestClientConfig("id", "https://api.example.com", 1000, null, null, null);

        assertThat(fiveArg.followRedirects()).isNull();
        assertThat(sixArg.followRedirects()).isNull();
        assertThat(fiveArg.followRedirectsOrDefault()).isTrue();
        assertThat(sixArg.followRedirectsOrDefault()).isTrue();
    }
}
