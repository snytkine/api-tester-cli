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
 * An HTTP request whose method conventionally supports a request body: {@code POST}, {@code PUT},
 * {@code PATCH}, or {@code DELETE}.
 *
 * <p>The {@code body} component is optional (nullable). When present, it is resolved and attached
 * to the outgoing HTTP request by the test engine. When absent, the request is sent without a body.
 *
 * <p>This record is immutable and thread-safe.
 *
 * @param method the HTTP method; must be one of POST, PUT, PATCH, DELETE
 * @param url the target URL or path
 * @param headers optional HTTP headers; {@code null} when none are declared
 * @param body optional request body descriptor; {@code null} when no body is declared
 * @param auth optional authentication configuration; {@code null} when none are declared
 * @param restClient optional id of the REST client to use; {@code null} to use the default client
 */
public record PayloadRequest(
        HttpMethod method,
        String url,
        Map<String, String> headers,
        @Nullable RequestBody body,
        @Nullable RequestAuth auth,
        @Nullable String restClient)
        implements Request {}
