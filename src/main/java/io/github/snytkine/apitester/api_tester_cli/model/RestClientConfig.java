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
 * Optional HTTP client settings declared at the top of a test-suite YAML under the {@code
 * rest_client} key.
 *
 * <p>When present, these values configure the underlying HTTP client for the entire suite (e.g. a
 * base URL so test cases can use relative paths, a connect timeout, or default headers applied to
 * every request).
 *
 * <p>Use {@link #withDefaults(RestClientConfig)} to obtain an instance where {@code baseUrl} and
 * {@code connectTimeout} are guaranteed non-null. {@code headers} has no default and remains
 * {@code null} when the {@code headers} key is absent from the YAML.
 */
public record RestClientConfig(
        /**
         * Base URL prepended to all relative request URLs in the suite. Defaults to an empty string,
         * meaning every test case must supply a fully-qualified URL.
         */
        @JsonProperty("base_url") String baseUrl,

        /** Connection timeout in milliseconds. Defaults to {@value #DEFAULT_CONNECT_TIMEOUT_MS}. */
        @JsonProperty("connect_timeout") Integer connectTimeout,

        /**
         * Default HTTP headers added to every request in the suite. Individual test-case headers take
         * precedence when the same header name appears in both places. May be {@code null} when the
         * {@code headers} key is absent from the YAML.
         */
        @JsonProperty("headers") @Nullable Map<String, String> headers) {

    /** Default connection timeout applied when the YAML omits {@code connect_timeout}. */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;

    /** Default base URL applied when the YAML omits {@code base_url}. */
    public static final String DEFAULT_BASE_URL = "";

    /**
     * Returns a {@link RestClientConfig} with {@code baseUrl} and {@code connectTimeout} guaranteed
     * non-null.
     *
     * <p>If {@code raw} is {@code null} (i.e. the {@code rest_client} key was absent from the YAML),
     * a fully-defaulted instance is returned. Otherwise the non-null fields of {@code raw} are
     * preserved and only the missing scalar fields are filled in with their defaults. {@code headers}
     * is always passed through as-is: it is {@code null} when the {@code headers} key was absent from
     * the YAML and non-null otherwise.
     *
     * @param raw the config parsed from YAML, or {@code null} if the key was absent
     * @return a non-null {@link RestClientConfig} with {@code baseUrl} and {@code connectTimeout}
     *     populated
     */
    public static RestClientConfig withDefaults(@Nullable RestClientConfig raw) {
        if (raw == null) {
            return new RestClientConfig(DEFAULT_BASE_URL, DEFAULT_CONNECT_TIMEOUT_MS, null);
        }
        return new RestClientConfig(
                raw.baseUrl() != null ? raw.baseUrl() : DEFAULT_BASE_URL,
                raw.connectTimeout() != null ? raw.connectTimeout() : DEFAULT_CONNECT_TIMEOUT_MS,
                raw.headers());
    }
}
