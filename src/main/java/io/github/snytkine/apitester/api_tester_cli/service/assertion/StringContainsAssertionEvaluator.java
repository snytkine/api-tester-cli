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
import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StringContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates a {@link StringContainsAssertion} by resolving a {@code response.*} path to a string
 * and asserting that it contains the expected substring.
 *
 * <p>When {@link StringContainsAssertion#caseSensitive()} is {@code true} the check uses {@link
 * org.assertj.core.api.AbstractStringAssert#contains}; when {@code false} it uses {@link
 * org.assertj.core.api.AbstractStringAssert#containsIgnoringCase}.
 *
 * <p>Supported path prefixes mirror those of {@link StringMatchAssertionEvaluator}:
 *
 * <ul>
 *   <li>{@code response.statusCode}
 *   <li>{@code response.headers.<name>}
 *   <li>{@code response.body.text}
 *   <li>{@code response.body.json}
 *   <li>{@code response.body.json.<jsonpath>}
 * </ul>
 */
class StringContainsAssertionEvaluator implements AssertionEvaluator {

    private static final String PREFIX = "response.";
    private static final String BODY_JSON_PREFIX = "body.json.";

    private final StringContainsAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the string_contains assertion to evaluate
     */
    StringContainsAssertionEvaluator(StringContainsAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Resolves the assertion's {@code path} against {@code response}, converts the result to a
     * string, and asserts that it contains the expected substring.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        String path = assertion.path();
        if (!path.startsWith(PREFIX)) {
            collector.fail("Unsupported path '%s': must start with 'response.'", path);
            return;
        }
        String remaining = path.substring(PREFIX.length());
        Object resolved = resolve(response, remaining, path, collector);
        if (resolved == null) {
            return;
        }
        String actual = String.valueOf(resolved);
        boolean caseSensitive = assertion.caseSensitive() == null || assertion.caseSensitive();
        if (caseSensitive) {
            collector
                    .assertThat(actual)
                    .as("Value at path '%s' contains '%s'", path, assertion.expected())
                    .contains(assertion.expected());
        } else {
            collector
                    .assertThat(actual)
                    .as("Value at path '%s' contains (ignoring case) '%s'", path, assertion.expected())
                    .containsIgnoringCase(assertion.expected());
        }
    }

    /**
     * Navigates the {@code remaining} path segment within {@code response} and returns the resolved
     * value, or {@code null} and records a failure when the path is unsupported.
     *
     * @param response the captured HTTP response
     * @param remaining the path segment after the {@code response.} prefix
     * @param fullPath the original full path, used in failure messages
     * @param collector the shared failure collector
     * @return the resolved value, or {@code null} if resolution failed
     */
    @Nullable private Object resolve(ApiResponse response, String remaining, String fullPath, FailureCollector collector) {
        if (remaining.equals("statusCode")) {
            return response.statusCode();
        }
        if (remaining.startsWith("headers.")) {
            String name = remaining.substring("headers.".length()).toLowerCase();
            if (response.headers() == null) {
                collector.fail("No headers present in response when evaluating path '%s'", fullPath);
                return null;
            }
            return response.headers().get(name);
        }
        if (remaining.equals("body.text")) {
            return response.body() != null ? response.body().text() : null;
        }
        if (remaining.equals("body.json")) {
            return response.body() != null ? String.valueOf(response.body().json()) : null;
        }
        if (remaining.startsWith(BODY_JSON_PREFIX)) {
            String jsonPathExpr = remaining.substring(BODY_JSON_PREFIX.length());
            if (response.body() == null || response.body().text() == null) {
                collector.fail("Response body is absent when evaluating path '%s'", fullPath);
                return null;
            }
            try {
                return JsonPath.read(response.body().text(), jsonPathExpr);
            } catch (PathNotFoundException e) {
                collector.fail(
                        "JSONPath expression '%s' not found in response body (path '%s')", jsonPathExpr, fullPath);
                return null;
            } catch (Exception e) {
                collector.fail(
                        "Failed to evaluate JSONPath '%s' in path '%s': %s", jsonPathExpr, fullPath, e.getMessage());
                return null;
            }
        }
        collector.fail("Unsupported path segment '%s' in path '%s'", remaining, fullPath);
        return null;
    }
}
