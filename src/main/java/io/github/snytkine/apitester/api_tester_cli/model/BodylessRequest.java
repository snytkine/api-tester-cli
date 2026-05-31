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

/**
 * An HTTP request whose method does not conventionally carry a request body: {@code GET}, {@code
 * HEAD}, {@code OPTIONS}, or {@code TRACE}.
 *
 * <p>This record has no {@code body} component. The JSON schema enforces that a {@code body} key
 * may not appear in YAML for these methods; this class provides compile-time enforcement on the
 * Java side.
 *
 * <p>This record is immutable and thread-safe.
 *
 * @param method the HTTP method; must be one of GET, HEAD, OPTIONS, TRACE
 * @param url the target URL or path
 * @param headers optional HTTP headers; {@code null} when none are declared
 */
public record BodylessRequest(HttpMethod method, String url, Map<String, String> headers) implements Request {}
