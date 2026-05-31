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
package io.github.snytkine.apitester.api_tester_cli.service.assertion;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a {@code response.*} path expression against an {@link ApiResponse}, returning a typed
 * result that distinguishes between a found value (which may itself be {@code null}), a missing
 * path, and a resolution error.
 *
 * <p>This utility is used by path-based evaluators that need to handle the "path not found" case
 * differently from the "found but null" case — e.g. {@code is_null} passes for both missing and
 * explicit null, while {@code not_null} fails for both.
 *
 * <p>Supported path prefixes:
 *
 * <ul>
 *   <li>{@code response.statusCode} — the HTTP status code as an {@code Integer}
 *   <li>{@code response.headers.<name>} — the lower-cased header value, or {@link Result.Missing}
 *       when the header is absent
 *   <li>{@code response.body.text} — the raw response body string
 *   <li>{@code response.body.json} — the full parsed JSON body
 *   <li>{@code response.body.json.<jsonpath>} — a JSONPath sub-expression result; returns {@link
 *       Result.Missing} when the JSONPath expression matches no node
 * </ul>
 *
 * <p>All methods are static; this class is not instantiable.
 */
final class ResponseValueExtractor {

    private static final String RESPONSE_PREFIX = "response.";
    private static final String BODY_JSON_DOT = "body.json.";

    private ResponseValueExtractor() {}

    /**
     * The outcome of resolving a path against an {@link ApiResponse}.
     *
     * <p>Three subtypes cover all cases:
     *
     * <ul>
     *   <li>{@link Found} — the path resolved; {@code value} may be {@code null} for explicit JSON
     *       nulls or absent optional fields such as missing headers
     *   <li>{@link Missing} — the path segment does not exist (e.g. JSONPath node not found, absent
     *       header, absent body)
     *   <li>{@link Error} — the path syntax is unsupported or another resolution error occurred
     * </ul>
     */
    sealed interface Result permits Result.Found, Result.Missing, Result.Error {

        /**
         * The path resolved successfully. {@code value} may be {@code null} when the response field
         * exists but carries a {@code null} or absent value.
         */
        record Found(@Nullable Object value) implements Result {}

        /**
         * The path does not resolve to any node in the response (e.g. header absent, JSONPath miss).
         */
        record Missing(String path) implements Result {}

        /** The path syntax is not supported or evaluation raised an unexpected exception. */
        record Error(String message) implements Result {}
    }

    /**
     * Resolves {@code path} against the given {@code response}.
     *
     * @param response the captured HTTP response
     * @param path the full path expression starting with {@code response.}
     * @return a {@link Result} describing the outcome
     */
    static Result extract(ApiResponse response, String path) {
        if (!path.startsWith(RESPONSE_PREFIX)) {
            return new Result.Error("Unsupported path '%s': must start with 'response.'".formatted(path));
        }
        String remaining = path.substring(RESPONSE_PREFIX.length());

        if (remaining.equals("statusCode")) {
            return new Result.Found(response.statusCode());
        }

        if (remaining.startsWith("headers.")) {
            String name = remaining.substring("headers.".length()).toLowerCase();
            if (response.headers() == null || !response.headers().containsKey(name)) {
                return new Result.Missing(path);
            }
            return new Result.Found(response.headers().get(name));
        }

        if (remaining.equals("body.text")) {
            if (response.body() == null) {
                return new Result.Missing(path);
            }
            return new Result.Found(response.body().text());
        }

        if (remaining.equals("body.json")) {
            if (response.body() == null) {
                return new Result.Missing(path);
            }
            return new Result.Found(response.body().json());
        }

        if (remaining.startsWith(BODY_JSON_DOT)) {
            String jsonPathExpr = remaining.substring(BODY_JSON_DOT.length());
            if (response.body() == null || response.body().text() == null) {
                return new Result.Missing(path);
            }
            try {
                Object value = JsonPath.read(response.body().text(), jsonPathExpr);
                return new Result.Found(value);
            } catch (PathNotFoundException e) {
                return new Result.Missing(path);
            } catch (Exception e) {
                return new Result.Error("Failed to evaluate JSONPath '%s': %s".formatted(jsonPathExpr, e.getMessage()));
            }
        }

        return new Result.Error("Unsupported path '%s'".formatted(path));
    }
}
