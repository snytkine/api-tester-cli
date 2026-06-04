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

/**
 * Produces a human-readable description string for an {@link Assertion}, derived entirely from the
 * assertion's definition (type, path, and configured values) without consulting the API response.
 *
 * <p>Examples: {@code "status_code equals 201"}, {@code "not_null response.body.json.id"}, {@code
 * "greater_than response.body.json.count"}.
 *
 * <p>This description identifies <em>what</em> is being checked, not <em>why</em> it failed — the
 * failure reason belongs in the {@code error} field of {@link
 * io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure}.
 *
 * <p>The {@code switch} over the sealed {@link Assertion} hierarchy guarantees exhaustive handling
 * and is GraalVM native-image safe (no reflection).
 *
 * <p>Thread-safety: all methods are static and stateless; safe to call concurrently.
 */
final class AssertionDescriber {

    private AssertionDescriber() {}

    /**
     * Returns the description string for the given assertion.
     *
     * @param assertion the assertion to describe
     * @return a short human-readable string identifying the assertion type, path, and key parameter
     */
    static String describe(Assertion assertion) {
        return switch (assertion) {
            case StatusCodeAssertion a -> "status_code equals " + a.expected();
            case StatusInAssertion a -> "status_in " + a.expected();
            case ResponseTimeAssertion a -> "response_time within " + a.milliseconds() + "ms";
            case HasHeaderAssertion a -> "has_header " + a.name();
            case NotNullAssertion a -> "not_null " + a.path();
            case IsNullAssertion a -> "is_null " + a.path();
            case NotEmptyAssertion a -> "not_empty " + a.path();
            case AssertTrueAssertion a -> "assert_true " + a.path();
            case AssertFalseAssertion a -> "assert_false " + a.path();
            case StringMatchAssertion a -> "string_match " + a.path();
            case ValueTypeAssertion a -> "value_type " + a.path();
            case StartsWithAssertion a -> "starts_with " + a.path();
            case EndsWithAssertion a -> "ends_with " + a.path();
            case StringContainsAssertion a -> "string_contains " + a.path();
            case RegexMatchAssertion a -> "regex_match " + a.path();
            case GreaterThanAssertion a -> "greater_than " + a.path();
            case GreaterThanOrEqualAssertion a -> "greater_than_or_equal " + a.path();
            case LessThanAssertion a -> "less_than " + a.path();
            case LessThanOrEqualAssertion a -> "less_than_or_equal " + a.path();
            case RangeAssertion a -> "range " + a.path();
            case OneOfAssertion a -> "one_of " + a.path();
            case ArraySizeAssertion a -> "array_size " + a.path();
            case ArraySizeMinAssertion a -> "array_size_min " + a.path();
            case ArraySizeMaxAssertion a -> "array_size_max " + a.path();
            case ArrayIsEmptyAssertion a -> "array_is_empty " + a.path();
            case ArrayIsNotEmptyAssertion a -> "array_is_not_empty " + a.path();
            case ArrayContainsAssertion a -> "array_contains " + a.path();
            case ArrayContainsAllAssertion a -> "array_contains_all " + a.path();
            case JsonMatchAssertion a -> "json_match " + a.path();
            case JsonSchemaAssertion a -> "json_schema " + a.path();
        };
    }
}
