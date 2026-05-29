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
 * Represents the full HTTP response captured after executing a single test case request.
 *
 * <p>Holds the status code, response headers, and a structured {@link Body} that exposes the
 * response payload both as raw text and as a parsed JSON object for use in assertions.
 */
public record ApiResponse(
    @Nullable Integer statusCode, @Nullable Map<String, String> headers, @Nullable Body body) {

  /**
   * The response payload in two representations: raw text and a parsed JSON object.
   *
   * <p>{@code text} always contains the raw response body string. {@code json} holds the result of
   * parsing {@code text} as JSON (a {@link java.util.Map}, {@link java.util.List}, or scalar) and
   * is {@code null} when the body is absent or not valid JSON.
   */
  public record Body(@Nullable String text, @Nullable Object json) {}
}
