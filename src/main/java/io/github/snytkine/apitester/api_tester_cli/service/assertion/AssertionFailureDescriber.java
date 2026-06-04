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

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayContainsAllAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayIsEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayIsNotEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeMaxAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeMinAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.AssertFalseAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.AssertTrueAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.Assertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.EndsWithAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.GreaterThanAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.GreaterThanOrEqualAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.HasHeaderAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.IsNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.JsonMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.JsonSchemaAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.LessThanAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.LessThanOrEqualAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.NotEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.NotNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.OneOfAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.RangeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.RegexMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ResponseTimeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StartsWithAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusCodeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusInAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StringContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StringMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ValueTypeAssertion;
import java.util.Collection;
import org.jspecify.annotations.Nullable;

/**
 * Produces a structured {@link AssertionFailure} (assertion description, expected value, actual
 * value) for a failed {@link Assertion} evaluated against an {@link ApiResponse}.
 *
 * <p>The three display strings are derived as follows:
 *
 * <ul>
 *   <li><b>description</b> — a human-readable summary of what the assertion checks, including its
 *       path and operator (e.g. {@code "status_code equals 201"}, {@code "not_null
 *       response.body.json.id"}).
 *   <li><b>expected</b> — the value or condition the assertion required (e.g. {@code "201"}, {@code
 *       "not null"}, {@code ">= 3"}).
 *   <li><b>actual</b> — the value actually observed in the response at the assertion's path,
 *       resolved via {@link ResponseValueExtractor}. Reads {@code "(path not found)"} when the path
 *       does not resolve and {@code "null"} for an explicit null value.
 * </ul>
 *
 * <p>Centralising this logic in a single switch over the sealed {@link Assertion} hierarchy keeps
 * the individual {@link io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator}
 * implementations focused on pass/fail evaluation and guarantees every assertion type is handled
 * (the {@code switch} would not compile if a permitted subtype were missing). The approach uses no
 * reflection and is therefore GraalVM native-image safe.
 *
 * <p>Thread-safety: all methods are static and stateless, operating only on their arguments; safe
 * to call concurrently.
 */
final class AssertionFailureDescriber {

    /** Maximum number of characters rendered for an actual value before it is truncated. */
    private static final int MAX_VALUE_LENGTH = 200;

    private AssertionFailureDescriber() {}

    /**
     * Builds a structured {@link AssertionFailure} describing the given failed assertion.
     *
     * @param assertion the assertion that failed
     * @param response the response the assertion was evaluated against
     * @return an {@link AssertionFailure} with description, expected, and actual populated
     */
    static AssertionFailure describe(Assertion assertion, ApiResponse response) {
        return switch (assertion) {
            case StatusCodeAssertion a ->
                new AssertionFailure(
                        "status_code equals " + a.expected(),
                        String.valueOf(a.expected()),
                        display(response.statusCode()));
            case StatusInAssertion a ->
                new AssertionFailure(
                        "status_in " + a.expected(), "one of " + a.expected(), display(response.statusCode()));
            case ResponseTimeAssertion a ->
                new AssertionFailure(
                        "response_time within " + a.milliseconds() + "ms",
                        "<= " + a.milliseconds() + " ms",
                        response.responseTimeMs() == null ? "(not measured)" : response.responseTimeMs() + " ms");
            case HasHeaderAssertion a -> new AssertionFailure("has_header " + a.name(), "present", "absent");
            case NotNullAssertion a ->
                new AssertionFailure("not_null " + a.path(), "not null", actualAtPath(response, a.path()));
            case IsNullAssertion a ->
                new AssertionFailure("is_null " + a.path(), "null", actualAtPath(response, a.path()));
            case NotEmptyAssertion a ->
                new AssertionFailure("not_empty " + a.path(), "not empty", actualAtPath(response, a.path()));
            case AssertTrueAssertion a ->
                new AssertionFailure("assert_true " + a.path(), "true", actualAtPath(response, a.path()));
            case AssertFalseAssertion a ->
                new AssertionFailure("assert_false " + a.path(), "false", actualAtPath(response, a.path()));
            case StringMatchAssertion a ->
                new AssertionFailure("string_match " + a.path(), a.expected(), actualAtPath(response, a.path()));
            case ValueTypeAssertion a ->
                new AssertionFailure("value_type " + a.path(), a.expected(), actualAtPath(response, a.path()));
            case StartsWithAssertion a ->
                new AssertionFailure(
                        "starts_with " + a.path(),
                        "starts with " + quote(a.expected()),
                        actualAtPath(response, a.path()));
            case EndsWithAssertion a ->
                new AssertionFailure(
                        "ends_with " + a.path(), "ends with " + quote(a.expected()), actualAtPath(response, a.path()));
            case StringContainsAssertion a ->
                new AssertionFailure(
                        "string_contains " + a.path(),
                        "contains " + quote(a.expected()),
                        actualAtPath(response, a.path()));
            case RegexMatchAssertion a ->
                new AssertionFailure(
                        "regex_match " + a.path(), "matches /" + a.expected() + "/", actualAtPath(response, a.path()));
            case GreaterThanAssertion a ->
                new AssertionFailure(
                        "greater_than " + a.path(), "> " + num(a.expected()), actualAtPath(response, a.path()));
            case GreaterThanOrEqualAssertion a ->
                new AssertionFailure(
                        "greater_than_or_equal " + a.path(),
                        ">= " + num(a.expected()),
                        actualAtPath(response, a.path()));
            case LessThanAssertion a ->
                new AssertionFailure(
                        "less_than " + a.path(), "< " + num(a.expected()), actualAtPath(response, a.path()));
            case LessThanOrEqualAssertion a ->
                new AssertionFailure(
                        "less_than_or_equal " + a.path(), "<= " + num(a.expected()), actualAtPath(response, a.path()));
            case RangeAssertion a ->
                new AssertionFailure(
                        "range " + a.path(), num(a.min()) + " to " + num(a.max()), actualAtPath(response, a.path()));
            case OneOfAssertion a ->
                new AssertionFailure("one_of " + a.path(), "one of " + a.expected(), actualAtPath(response, a.path()));
            case ArraySizeAssertion a ->
                new AssertionFailure(
                        "array_size " + a.path(), String.valueOf(a.expected()), actualSize(response, a.path()));
            case ArraySizeMinAssertion a ->
                new AssertionFailure("array_size_min " + a.path(), ">= " + a.min(), actualSize(response, a.path()));
            case ArraySizeMaxAssertion a ->
                new AssertionFailure("array_size_max " + a.path(), "<= " + a.max(), actualSize(response, a.path()));
            case ArrayIsEmptyAssertion a ->
                new AssertionFailure("array_is_empty " + a.path(), "empty", actualSize(response, a.path()));
            case ArrayIsNotEmptyAssertion a ->
                new AssertionFailure("array_is_not_empty " + a.path(), "not empty", actualSize(response, a.path()));
            case ArrayContainsAssertion a ->
                new AssertionFailure(
                        "array_contains " + a.path(),
                        "contains " + display(a.expected()),
                        actualAtPath(response, a.path()));
            case ArrayContainsAllAssertion a ->
                new AssertionFailure(
                        "array_contains_all " + a.path(),
                        "contains all " + a.expected(),
                        actualAtPath(response, a.path()));
            case JsonMatchAssertion a ->
                new AssertionFailure(
                        "json_match " + a.path(), "matching expected JSON", actualAtPath(response, a.path()));
            case JsonSchemaAssertion a ->
                new AssertionFailure(
                        "json_schema " + a.path(), "valid against schema", actualAtPath(response, a.path()));
        };
    }

    /**
     * Resolves the value at {@code path} and returns its display form, distinguishing a missing path
     * from a present-but-null value.
     *
     * @param response the response to resolve against
     * @param path the {@code response.*} path expression
     * @return the display string for the resolved value, {@code "(path not found)"} when the path is
     *     absent, or {@code "(unresolved)"} on a resolution error
     */
    private static String actualAtPath(ApiResponse response, String path) {
        return switch (ResponseValueExtractor.extract(response, path)) {
            case ResponseValueExtractor.Result.Found f -> display(f.value());
            case ResponseValueExtractor.Result.Missing m -> "(path not found)";
            case ResponseValueExtractor.Result.Error e -> "(unresolved)";
        };
    }

    /**
     * Resolves the value at {@code path} and, when it is a collection, returns its element count;
     * otherwise behaves like {@link #actualAtPath(ApiResponse, String)}.
     *
     * @param response the response to resolve against
     * @param path the {@code response.*} path expression
     * @return the collection size as a string when applicable, else the value's display form
     */
    private static String actualSize(ApiResponse response, String path) {
        return switch (ResponseValueExtractor.extract(response, path)) {
            case ResponseValueExtractor.Result.Found f -> {
                if (f.value() instanceof Collection<?> c) {
                    yield "size " + c.size();
                }
                yield display(f.value());
            }
            case ResponseValueExtractor.Result.Missing m -> "(path not found)";
            case ResponseValueExtractor.Result.Error e -> "(unresolved)";
        };
    }

    /**
     * Renders a value for display: {@code "null"} for {@code null}, otherwise its {@code toString}
     * truncated to {@link #MAX_VALUE_LENGTH} characters.
     *
     * @param value the value to render; may be {@code null}
     * @return a non-null, length-bounded display string
     */
    private static String display(@Nullable Object value) {
        if (value == null) {
            return "null";
        }
        String s = String.valueOf(value);
        return s.length() > MAX_VALUE_LENGTH ? s.substring(0, MAX_VALUE_LENGTH - 3) + "..." : s;
    }

    /**
     * Formats a double without a trailing {@code .0} when it represents a whole number.
     *
     * @param d the value to format
     * @return {@code "201"} for {@code 201.0}, otherwise the standard double rendering
     */
    private static String num(double d) {
        if (!Double.isInfinite(d) && !Double.isNaN(d) && d == Math.rint(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    /**
     * Wraps a string in double quotes for display.
     *
     * @param s the string to quote
     * @return the quoted string
     */
    private static String quote(String s) {
        return "\"" + s + "\"";
    }
}
