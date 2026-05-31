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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Map;

/**
 * Sealed interface representing an HTTP request in a test case.
 *
 * <p>The two permitted subtypes encode body-support at the type level:
 *
 * <ul>
 *   <li>{@link PayloadRequest} — for methods that conventionally carry a body (POST, PUT, PATCH,
 *       DELETE); exposes an optional {@code body()} accessor.
 *   <li>{@link BodylessRequest} — for methods that do not carry a body (GET, HEAD, OPTIONS,
 *       TRACE); has no {@code body()} accessor.
 * </ul>
 *
 * <p>Jackson selects the correct subtype during deserialization via {@link RequestDeserializer},
 * which inspects the {@code method} field value.
 *
 * <p>This interface is immutable and thread-safe; all implementations are records.
 */
@JsonDeserialize(using = RequestDeserializer.class)
public sealed interface Request permits BodylessRequest, PayloadRequest {

    /**
     * Returns the HTTP method for this request.
     *
     * @return the HTTP method, never {@code null}
     */
    HttpMethod method();

    /**
     * Returns the target URL or path (relative to {@code rest_client.base_url} when set).
     *
     * @return the URL string, never {@code null}
     */
    String url();

    /**
     * Returns the HTTP headers to include in the request, or {@code null} when none are declared.
     *
     * @return a map of header name to value, or {@code null}
     */
    Map<String, String> headers();
}
