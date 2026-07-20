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
import org.jspecify.annotations.Nullable;

/**
 * A single capture definition declared under a {@link TestCase}'s {@code saved-session} block.
 *
 * <p>When the owning test case has produced an HTTP response, each {@code SavedSession} extracts a
 * value from that response using {@link #path()} (a {@code response.*} path expression, e.g. {@code
 * response.body.json.id} or {@code response.headers.etag}) and stores it in the suite-wide {@code
 * session} namespace under {@link #name()}. The stored value is then usable by later test cases via
 * {@code [[${session.<name>}]]} in the request URL, headers, and body (including bodies loaded from
 * an external file).
 *
 * <p><strong>Primitive-only limitation.</strong> Only primitive JSON values (string, number,
 * boolean) may be captured. If the extracted value is a JSON object or array the capture is an
 * error — a session value can never hold an object or an array.
 *
 * <p>Fields:
 *
 * <ul>
 *   <li>{@code name} — the key under which the extracted value is stored in the {@code session}
 *       namespace. Uniqueness across the whole suite is the user's responsibility; a duplicate name
 *       is overwritten by the most recent extraction (last-write-wins).
 *   <li>{@code path} — the {@code response.*} expression used to extract the value.
 *   <li>{@code type} — optional {@link SessionValueType} the extracted primitive is coerced to;
 *       when omitted the value is stored as its textual representation.
 *   <li>{@code defaultValue} (YAML key {@code default}) — optional fallback used when the path
 *       extracts nothing. When present the capture always resolves and never fails.
 *   <li>{@code required} — when {@code true} <em>and no default is set</em>, a missing/unextractable
 *       value fails the owning test case.
 * </ul>
 *
 * <p>This is an immutable record and therefore inherently thread-safe.
 */
public record SavedSession(
        /** Key under which the extracted value is stored in the {@code session} namespace. */
        String name,

        /** {@code response.*} path expression used to extract the value from the response. */
        String path,

        /** Optional primitive type the extracted value is coerced to; {@code null} to store as-is. */
        @Nullable SessionValueType type,

        /**
         * Optional fallback value (YAML key {@code default}) used when the path extracts nothing.
         * When non-null the capture always resolves and never causes a failure.
         */
        @JsonProperty("default") @Nullable String defaultValue,

        /**
         * When {@code true} and no {@link #defaultValue()} is set, a missing/unextractable value fails
         * the owning test case. Defaults to {@code false}.
         */
        boolean required) {}
