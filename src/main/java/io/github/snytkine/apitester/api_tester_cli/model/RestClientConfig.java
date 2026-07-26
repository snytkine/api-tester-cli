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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Optional HTTP client settings declared at the top of a test-suite YAML under either the {@code
 * rest-client} (singular) key or as an entry in the {@code rest-clients} (plural) list.
 *
 * <p>When present, these values configure an underlying HTTP client for the suite (e.g. a base URL
 * so test cases can use relative paths, a connect timeout, default headers, or authentication
 * applied to every request that uses this client).
 *
 * <p>The optional {@code id} identifies the client within a {@code rest-clients} list so that a
 * request can select it via its own {@code rest-client} property. It is {@code null} for the
 * singular {@code rest-client} form (which is always the {@code default} client) and for a
 * single-entry {@code rest-clients} list that omits it.
 *
 * <p>Use {@link #withDefaults(RestClientConfig)} to obtain an instance where {@code baseUrl},
 * {@code connectTimeout} and {@code followRedirects} are guaranteed non-null. {@code id}, {@code
 * headers}, {@code auth}, {@code ssl} and {@code proxy} have no defaults and remain {@code null}
 * when absent from the YAML. For {@code proxy} that {@code null} is meaningful rather than merely
 * unset — see {@link #proxy()}.
 *
 * <p>Configs obtained from {@link TestSuite#restClientsById()} are the raw parsed values and do
 * <em>not</em> pass through {@link #withDefaults(RestClientConfig)}, so read the redirect setting
 * via {@link #followRedirectsOrDefault()} rather than {@link #followRedirects()}.
 */
public record RestClientConfig(
        /**
         * Unique identifier of this client within a {@code rest-clients} list. May be {@code null} for
         * the singular {@code rest-client} form or a single-entry {@code rest-clients} list.
         */
        @JsonProperty("id") @Nullable String id,

        /**
         * Base URL prepended to all relative request URLs in the suite. Defaults to an empty string,
         * meaning every test case must supply a fully-qualified URL.
         */
        @JsonProperty("base-url") String baseUrl,

        /** Connection timeout in milliseconds. Defaults to {@value #DEFAULT_CONNECT_TIMEOUT_MS}. */
        @JsonProperty("connect-timeout") Integer connectTimeout,

        /**
         * Default HTTP headers added to every request in the suite. Individual test-case headers take
         * precedence when the same header name appears in both places. May be {@code null} when the
         * {@code headers} key is absent from the YAML.
         */
        @JsonProperty("headers") @Nullable Map<String, String> headers,

        /**
         * Optional suite-wide authentication. When non-null, the auth credentials are applied as a
         * default {@code Authorization} header on every request built by this suite's
         * {@link org.springframework.web.client.RestClient}. Per-request authentication and explicit
         * {@code Authorization} headers take precedence. May be {@code null} when the {@code auth}
         * key is absent from the YAML.
         */
        @JsonProperty("auth") @Nullable RequestAuth auth,

        /**
         * Optional custom SSL/TLS settings (skip validation, custom truststore, and/or client
         * keystore for mTLS) applied when building this client's {@link
         * org.springframework.web.client.RestClient}. May be {@code null} when the {@code ssl} key is
         * absent from the YAML.
         */
        @JsonProperty("ssl") @Nullable SslConfig ssl,

        /**
         * Whether this client automatically follows HTTP redirect (3xx) responses. When {@code false}
         * a redirect response is delivered to the test as the final response, so assertions can check
         * the 3xx status code and the {@code Location} header. When {@code null} (the {@code
         * follow-redirects} key is absent from the YAML) the default {@link
         * #DEFAULT_FOLLOW_REDIRECTS} applies.
         *
         * <p>This setting exists only at the rest-client level; it cannot be overridden per test
         * case. To mix behaviours within one suite, declare a second rest-client that differs only in
         * this flag and select it from the tests that need it via the request's {@code rest-client}
         * property.
         */
        @JsonProperty("follow-redirects") @Nullable Boolean followRedirects,

        /**
         * Optional HTTP proxy settings for this client. Three states are distinguished and must not
         * be conflated: {@code null} means the {@code proxy} key was absent, so an {@code
         * HTTP_PROXY} / {@code HTTPS_PROXY} environment proxy applies automatically; {@link
         * ProxyConfig#DISABLED} means {@code proxy: false} was declared and this client must never
         * use a proxy; anything else is an explicitly configured proxy.
         *
         * <p>Resolution of these three states against the environment is performed by {@code
         * ProxyResolver}, not here.
         */
        @JsonProperty("proxy") @Nullable ProxyConfig proxy) {

    /** Default connection timeout applied when the YAML omits {@code connect_timeout}. */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;

    /** Default base URL applied when the YAML omits {@code base_url}. */
    public static final String DEFAULT_BASE_URL = "";

    /**
     * Default redirect-following behaviour applied when the YAML omits {@code follow-redirects}.
     * Redirects are followed by default, preserving the behaviour of suites written before this
     * option existed.
     */
    public static final boolean DEFAULT_FOLLOW_REDIRECTS = true;

    /**
     * Backwards-compatible constructor for a config with no {@code ssl} block, delegating to the
     * canonical constructor with {@code ssl = null} and {@code followRedirects = null}. Retained so
     * existing call sites (and tests) that predate the SSL feature continue to compile unchanged.
     *
     * @param id optional client id
     * @param baseUrl base URL prepended to relative request URLs
     * @param connectTimeout connection timeout in milliseconds
     * @param headers optional default headers
     * @param auth optional default authentication
     */
    public RestClientConfig(
            @Nullable String id,
            String baseUrl,
            Integer connectTimeout,
            @Nullable Map<String, String> headers,
            @Nullable RequestAuth auth) {
        this(id, baseUrl, connectTimeout, headers, auth, null, null, null);
    }

    /**
     * Backwards-compatible constructor for a config with no {@code follow-redirects} key, delegating
     * to the canonical constructor with {@code followRedirects = null}. Retained so call sites (and
     * tests) written against the SSL-era six-argument shape continue to compile unchanged.
     *
     * @param id optional client id
     * @param baseUrl base URL prepended to relative request URLs
     * @param connectTimeout connection timeout in milliseconds
     * @param headers optional default headers
     * @param auth optional default authentication
     * @param ssl optional custom SSL/TLS settings
     */
    public RestClientConfig(
            @Nullable String id,
            String baseUrl,
            Integer connectTimeout,
            @Nullable Map<String, String> headers,
            @Nullable RequestAuth auth,
            @Nullable SslConfig ssl) {
        this(id, baseUrl, connectTimeout, headers, auth, ssl, null, null);
    }

    /**
     * Backwards-compatible constructor for a config with no {@code proxy} key, delegating to the
     * canonical constructor with {@code proxy = null}. Retained so call sites (and tests) written
     * against the redirect-era seven-argument shape continue to compile unchanged.
     *
     * @param id optional client id
     * @param baseUrl base URL prepended to relative request URLs
     * @param connectTimeout connection timeout in milliseconds
     * @param headers optional default headers
     * @param auth optional default authentication
     * @param ssl optional custom SSL/TLS settings
     * @param followRedirects whether redirects are followed
     */
    public RestClientConfig(
            @Nullable String id,
            String baseUrl,
            Integer connectTimeout,
            @Nullable Map<String, String> headers,
            @Nullable RequestAuth auth,
            @Nullable SslConfig ssl,
            @Nullable Boolean followRedirects) {
        this(id, baseUrl, connectTimeout, headers, auth, ssl, followRedirects, null);
    }

    /**
     * Returns whether this client should follow HTTP redirects, resolving the {@code null} (absent)
     * case to {@link #DEFAULT_FOLLOW_REDIRECTS}.
     *
     * <p>Callers should prefer this over reading {@link #followRedirects()} directly, because configs
     * obtained from {@link TestSuite#restClientsById()} are the raw parsed values and have not passed
     * through {@link #withDefaults(RestClientConfig)}.
     *
     * @return {@code true} when redirects should be followed
     */
    public boolean followRedirectsOrDefault() {
        return followRedirects == null ? DEFAULT_FOLLOW_REDIRECTS : followRedirects;
    }

    /**
     * Returns a {@link RestClientConfig} with {@code baseUrl} and {@code connectTimeout} guaranteed
     * non-null.
     *
     * <p>If {@code raw} is {@code null}, a fully-defaulted instance is returned. Otherwise the
     * non-null fields of {@code raw} are preserved and only the missing scalar fields are filled in
     * with their defaults. {@code id}, {@code headers}, {@code auth} and {@code ssl} are always
     * passed through as-is: they are {@code null} when the respective keys were absent from the YAML
     * and non-null otherwise.
     *
     * @param raw the config parsed from YAML, or {@code null} if the key was absent
     * @return a non-null {@link RestClientConfig} with {@code baseUrl} and {@code connectTimeout}
     *     populated
     */
    public static RestClientConfig withDefaults(@Nullable RestClientConfig raw) {
        if (raw == null) {
            return new RestClientConfig(
                    null,
                    DEFAULT_BASE_URL,
                    DEFAULT_CONNECT_TIMEOUT_MS,
                    null,
                    null,
                    null,
                    DEFAULT_FOLLOW_REDIRECTS,
                    null);
        }
        return new RestClientConfig(
                raw.id(),
                raw.baseUrl() != null ? raw.baseUrl() : DEFAULT_BASE_URL,
                raw.connectTimeout() != null ? raw.connectTimeout() : DEFAULT_CONNECT_TIMEOUT_MS,
                raw.headers(),
                raw.auth(),
                raw.ssl(),
                raw.followRedirects() != null ? raw.followRedirects() : DEFAULT_FOLLOW_REDIRECTS,
                raw.proxy());
    }
}
