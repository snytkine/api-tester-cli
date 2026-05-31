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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayContainsAllAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayIsEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayIsNotEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeMaxAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeMinAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.AssertFalseAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.AssertTrueAssertion;
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

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ArrayContainsAllAssertion.class, name = "array_contains_all"),
    @JsonSubTypes.Type(value = ArrayContainsAssertion.class, name = "array_contains"),
    @JsonSubTypes.Type(value = ArrayIsEmptyAssertion.class, name = "array_is_empty"),
    @JsonSubTypes.Type(value = ArrayIsNotEmptyAssertion.class, name = "array_is_not_empty"),
    @JsonSubTypes.Type(value = ArraySizeAssertion.class, name = "array_size"),
    @JsonSubTypes.Type(value = ArraySizeMaxAssertion.class, name = "array_size_max"),
    @JsonSubTypes.Type(value = ArraySizeMinAssertion.class, name = "array_size_min"),
    @JsonSubTypes.Type(value = AssertFalseAssertion.class, name = "assert_false"),
    @JsonSubTypes.Type(value = AssertTrueAssertion.class, name = "assert_true"),
    @JsonSubTypes.Type(value = EndsWithAssertion.class, name = "ends_with"),
    @JsonSubTypes.Type(value = GreaterThanAssertion.class, name = "greater_than"),
    @JsonSubTypes.Type(value = GreaterThanOrEqualAssertion.class, name = "greater_than_or_equal"),
    @JsonSubTypes.Type(value = HasHeaderAssertion.class, name = "has_header"),
    @JsonSubTypes.Type(value = IsNullAssertion.class, name = "is_null"),
    @JsonSubTypes.Type(value = JsonMatchAssertion.class, name = "json_match"),
    @JsonSubTypes.Type(value = JsonSchemaAssertion.class, name = "json_schema"),
    @JsonSubTypes.Type(value = LessThanAssertion.class, name = "less_than"),
    @JsonSubTypes.Type(value = LessThanOrEqualAssertion.class, name = "less_than_or_equal"),
    @JsonSubTypes.Type(value = NotEmptyAssertion.class, name = "not_empty"),
    @JsonSubTypes.Type(value = NotNullAssertion.class, name = "not_null"),
    @JsonSubTypes.Type(value = OneOfAssertion.class, name = "one_of"),
    @JsonSubTypes.Type(value = RangeAssertion.class, name = "range"),
    @JsonSubTypes.Type(value = RegexMatchAssertion.class, name = "regex_match"),
    @JsonSubTypes.Type(value = ResponseTimeAssertion.class, name = "response_time"),
    @JsonSubTypes.Type(value = StartsWithAssertion.class, name = "starts_with"),
    @JsonSubTypes.Type(value = StatusCodeAssertion.class, name = "status_code"),
    @JsonSubTypes.Type(value = StatusInAssertion.class, name = "status_in"),
    @JsonSubTypes.Type(value = StringContainsAssertion.class, name = "string_contains"),
    @JsonSubTypes.Type(value = StringMatchAssertion.class, name = "string_match"),
    @JsonSubTypes.Type(value = ValueTypeAssertion.class, name = "value_type"),
})
public sealed interface Assertion
        permits ArrayContainsAllAssertion,
                ArrayContainsAssertion,
                ArrayIsEmptyAssertion,
                ArrayIsNotEmptyAssertion,
                ArraySizeAssertion,
                ArraySizeMaxAssertion,
                ArraySizeMinAssertion,
                AssertFalseAssertion,
                AssertTrueAssertion,
                EndsWithAssertion,
                GreaterThanAssertion,
                GreaterThanOrEqualAssertion,
                HasHeaderAssertion,
                IsNullAssertion,
                JsonMatchAssertion,
                JsonSchemaAssertion,
                LessThanAssertion,
                LessThanOrEqualAssertion,
                NotEmptyAssertion,
                NotNullAssertion,
                OneOfAssertion,
                RangeAssertion,
                RegexMatchAssertion,
                ResponseTimeAssertion,
                StartsWithAssertion,
                StatusCodeAssertion,
                StatusInAssertion,
                StringContainsAssertion,
                StringMatchAssertion,
                ValueTypeAssertion {}
