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
 * <p>Use {@link #withDefaults(RestClientConfig)} to obtain an instance where {@code baseUrl} and
 * {@code connectTimeout} are guaranteed non-null. {@code id}, {@code headers} and {@code auth} have
 * no defaults and remain {@code null} when absent from the YAML.
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
        @JsonProperty("auth") @Nullable RequestAuth auth) {

    /** Default connection timeout applied when the YAML omits {@code connect_timeout}. */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;

    /** Default base URL applied when the YAML omits {@code base_url}. */
    public static final String DEFAULT_BASE_URL = "";

    /**
     * Returns a {@link RestClientConfig} with {@code baseUrl} and {@code connectTimeout} guaranteed
     * non-null.
     *
     * <p>If {@code raw} is {@code null}, a fully-defaulted instance is returned. Otherwise the
     * non-null fields of {@code raw} are preserved and only the missing scalar fields are filled in
     * with their defaults. {@code id}, {@code headers} and {@code auth} are always passed through
     * as-is: they are {@code null} when the respective keys were absent from the YAML and non-null
     * otherwise.
     *
     * @param raw the config parsed from YAML, or {@code null} if the key was absent
     * @return a non-null {@link RestClientConfig} with {@code baseUrl} and {@code connectTimeout}
     *     populated
     */
    public static RestClientConfig withDefaults(@Nullable RestClientConfig raw) {
        if (raw == null) {
            return new RestClientConfig(null, DEFAULT_BASE_URL, DEFAULT_CONNECT_TIMEOUT_MS, null, null);
        }
        return new RestClientConfig(
                raw.id(),
                raw.baseUrl() != null ? raw.baseUrl() : DEFAULT_BASE_URL,
                raw.connectTimeout() != null ? raw.connectTimeout() : DEFAULT_CONNECT_TIMEOUT_MS,
                raw.headers(),
                raw.auth());
    }
}
