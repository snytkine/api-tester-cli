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
 * <p>Thread-safety: immutable after construction; safe to read from any thread.
 */
public record ExecutedRequestInfo(
        /** The HTTP method used for the request. */
        HttpMethod method,

        /**
         * The full URL dispatched for this request, after template resolution. When the test declares
         * a relative URL (e.g. {@code /api/users}) and the selected rest-client (see {@link
         * #restClientId()}) declares a {@code base-url}, this is the two combined (e.g. {@code
         * https://api.example.com/api/users}). When the declared URL is already absolute, or the
         * selected rest-client has no {@code base-url}, this is the declared URL unchanged.
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
        @Nullable String body,

        /**
         * The authentication actually applied to this request — from the test case's own {@code
         * request.auth}, or falling back to the selected rest-client's {@code auth} — or {@code null}
         * when no authentication was applied (including when an explicit {@code Authorization} header
         * took precedence over a declared {@code auth}).
         *
         * <p>Holds the real, unmasked {@link RequestAuth#username()}/{@link RequestAuth#password()}.
         * Masking these values for display is a report-rendering concern (see {@code
         * HtmlReportGenerator}), not a model concern — this field must never be serialised or logged
         * verbatim.
         */
        @Nullable RequestAuth auth,

        /**
         * The id of the rest-client actually used to dispatch this request — {@code "default"} when
         * the suite uses the singular {@code rest-client} form, or when the request selects no client
         * or an unresolvable one. Always non-null: every request resolves to some client, since the
         * suite is guaranteed to declare at least one (enforced by {@code TestSuiteValidator}).
         */
        String restClientId) {}
