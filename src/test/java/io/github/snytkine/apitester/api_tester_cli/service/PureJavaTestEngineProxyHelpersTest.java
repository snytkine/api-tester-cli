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

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.BodylessRequest;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the proxy-related helpers in {@link PureJavaTestEngine} that decide which proxy an
 * error message should name.
 *
 * <p>These are exercised directly rather than through a full run because their interesting cases —
 * a relative URL inheriting the base URL's scheme, a base URL with no scheme at all, a response
 * with no headers — are edge conditions that a happy-path run never reaches, yet each one decides
 * whether a user sees the right proxy named in a failure.
 */
class PureJavaTestEngineProxyHelpersTest {

    private static TestCase testCaseWithUrl(String url) {
        return new TestCase(
                "t", null, null, null, Map.of(), new BodylessRequest(HttpMethod.GET, url, null, null, null), List.of());
    }

    private static RestClientConfig clientWithBaseUrl(String baseUrl) {
        return new RestClientConfig(null, baseUrl, 1000, null, null);
    }

    @Test
    void absoluteRequestUrlSuppliesItsOwnScheme() {
        assertThat(PureJavaTestEngine.targetScheme(
                        clientWithBaseUrl("http://base.example.com"), testCaseWithUrl("https://other.example.com/api")))
                .isEqualTo("https");
    }

    @Test
    void relativeRequestUrlInheritsTheBaseUrlScheme() {
        assertThat(PureJavaTestEngine.targetScheme(
                        clientWithBaseUrl("https://base.example.com"), testCaseWithUrl("/api")))
                .isEqualTo("https");
    }

    @Test
    void schemeIsLowerCasedSoMatchingIsCaseInsensitive() {
        assertThat(PureJavaTestEngine.targetScheme(
                        clientWithBaseUrl("HTTPS://base.example.com"), testCaseWithUrl("/api")))
                .isEqualTo("https");
    }

    @Test
    void baseUrlWithoutASchemeYieldsNoScheme() {
        assertThat(PureJavaTestEngine.targetScheme(clientWithBaseUrl("base.example.com"), testCaseWithUrl("/api")))
                .isNull();
    }

    @Test
    void nullBaseUrlYieldsNoScheme() {
        assertThat(PureJavaTestEngine.targetScheme(clientWithBaseUrl(null), testCaseWithUrl("/api")))
                .isNull();
    }

    @Test
    void headerLookupIsCaseInsensitive() {
        ApiResponse response = new ApiResponse(407, Map.of("proxy-authenticate", "NTLM"), null);

        assertThat(PureJavaTestEngine.headerIgnoringCase(response, "Proxy-Authenticate"))
                .isEqualTo("NTLM");
    }

    @Test
    void headerLookupReturnsNullWhenAbsentOrHeadersMissing() {
        assertThat(PureJavaTestEngine.headerIgnoringCase(new ApiResponse(407, Map.of(), null), "Proxy-Authenticate"))
                .isNull();
        assertThat(PureJavaTestEngine.headerIgnoringCase(new ApiResponse(407, null, null), "Proxy-Authenticate"))
                .isNull();
    }

    @Test
    void ignoredEnvironmentProxyDescribesAValidEnvironmentProxy() {
        assertThat(PureJavaTestEngine.ignoredEnvironmentProxy(Map.of("HTTP_PROXY", "http://proxy:8080")))
                .contains("proxy:8080")
                .contains("source=env");
    }

    @Test
    void ignoredEnvironmentProxyIsNullWhenNoneIsSet() {
        assertThat(PureJavaTestEngine.ignoredEnvironmentProxy(Map.of())).isNull();
    }

    /**
     * A client that opted out must not even be warned about an environment value it never reads, so
     * a malformed one is swallowed here rather than reported.
     */
    @Test
    void ignoredEnvironmentProxyIsNullWhenTheEnvironmentValueIsMalformed() {
        assertThat(PureJavaTestEngine.ignoredEnvironmentProxy(Map.of("HTTP_PROXY", "https://proxy:8443")))
                .isNull();
    }
}
