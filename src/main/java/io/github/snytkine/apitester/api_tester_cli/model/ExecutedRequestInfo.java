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

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The fully-resolved HTTP request that was dispatched for a single {@link TestCase}.
 *
 * <p>All field values reflect the final state after Thymeleaf template processing, meaning any
 * {@code [[${...}]]} expressions in the original YAML have been substituted with their runtime
 * values. This makes {@code ExecutedRequestInfo} suitable as a source of truth for report
 * generation without needing to re-run any logic.
 *
 * <p>Note: {@code url} may be a relative path (e.g. {@code /api/users}) when the selected
 * {@link RestClientConfig} declares a {@code base-url}. The suite's REST client configuration is
 * accessible separately via {@link TestSuite#restClientsById()}.
 *
 * <p>Thread-safety: immutable after construction; safe to read from any thread.
 */
public record ExecutedRequestInfo(
        /** The HTTP method used for the request. */
        HttpMethod method,

        /**
         * The URL or path used for the request, after template resolution. May be relative when the
         * suite defines a {@code base_url} in {@link RestClientConfig}.
         */
        String url,

        /**
         * Per-request headers sent with the request, after template resolution. {@code null} when no
         * per-request headers were declared on the test case. Does not include suite-level default
         * headers set via {@link RestClientConfig#headers()}.
         */
        @Nullable Map<String, String> headers,

        /**
         * The resolved request body string, or {@code null} for bodyless requests (e.g. GET, HEAD).
         * For {@code FILE}-type bodies this is the fully loaded and template-processed file content.
         */
        @Nullable String body) {}
