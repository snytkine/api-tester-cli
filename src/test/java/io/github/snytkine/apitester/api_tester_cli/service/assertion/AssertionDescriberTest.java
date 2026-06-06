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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.model.ObjectExpectedValue;
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
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AssertionDescriber}. */
class AssertionDescriberTest {

    @Test
    void statusCodeIncludesExpectedCode() {
        assertThat(AssertionDescriber.describe(new StatusCodeAssertion(201))).isEqualTo("status_code equals 201");
    }

    @Test
    void statusInIncludesExpectedList() {
        assertThat(AssertionDescriber.describe(new StatusInAssertion(List.of(200, 201, 204))))
                .isEqualTo("status_in [200, 201, 204]");
    }

    @Test
    void responseTimeIncludesMilliseconds() {
        assertThat(AssertionDescriber.describe(new ResponseTimeAssertion(500))).isEqualTo("response_time within 500ms");
    }

    @Test
    void hasHeaderIncludesHeaderName() {
        assertThat(AssertionDescriber.describe(new HasHeaderAssertion("Content-Type")))
                .isEqualTo("has_header Content-Type");
    }

    @Test
    void notNullIncludesPath() {
        assertThat(AssertionDescriber.describe(new NotNullAssertion("response.body.json.id")))
                .isEqualTo("not_null response.body.json.id");
    }

    @Test
    void isNullIncludesPath() {
        assertThat(AssertionDescriber.describe(new IsNullAssertion("response.body.json.deletedAt")))
                .isEqualTo("is_null response.body.json.deletedAt");
    }

    @Test
    void notEmptyIncludesPath() {
        assertThat(AssertionDescriber.describe(new NotEmptyAssertion("response.body.json.name")))
                .isEqualTo("not_empty response.body.json.name");
    }

    @Test
    void assertTrueIncludesPath() {
        assertThat(AssertionDescriber.describe(new AssertTrueAssertion("response.body.json.active")))
                .isEqualTo("assert_true response.body.json.active");
    }

    @Test
    void assertFalseIncludesPath() {
        assertThat(AssertionDescriber.describe(new AssertFalseAssertion("response.body.json.deleted")))
                .isEqualTo("assert_false response.body.json.deleted");
    }

    @Test
    void stringMatchIncludesPath() {
        assertThat(AssertionDescriber.describe(new StringMatchAssertion("response.body.json.status", "active", null)))
                .isEqualTo("string_match response.body.json.status");
    }

    @Test
    void valueTypeIncludesPath() {
        assertThat(AssertionDescriber.describe(new ValueTypeAssertion("response.body.json.count", "integer")))
                .isEqualTo("value_type response.body.json.count");
    }

    @Test
    void startsWithIncludesPath() {
        assertThat(AssertionDescriber.describe(new StartsWithAssertion("response.body.json.code", "ERR")))
                .isEqualTo("starts_with response.body.json.code");
    }

    @Test
    void endsWithIncludesPath() {
        assertThat(AssertionDescriber.describe(new EndsWithAssertion("response.body.json.email", "@example.com")))
                .isEqualTo("ends_with response.body.json.email");
    }

    @Test
    void stringContainsIncludesPath() {
        assertThat(AssertionDescriber.describe(
                        new StringContainsAssertion("response.body.json.message", "success", null)))
                .isEqualTo("string_contains response.body.json.message");
    }

    @Test
    void regexMatchIncludesPath() {
        assertThat(AssertionDescriber.describe(new RegexMatchAssertion("response.body.json.uuid", "[0-9a-f-]+")))
                .isEqualTo("regex_match response.body.json.uuid");
    }

    @Test
    void greaterThanIncludesPath() {
        assertThat(AssertionDescriber.describe(new GreaterThanAssertion("response.body.json.count", 0)))
                .isEqualTo("greater_than response.body.json.count");
    }

    @Test
    void greaterThanOrEqualIncludesPath() {
        assertThat(AssertionDescriber.describe(new GreaterThanOrEqualAssertion("response.body.json.total", 1)))
                .isEqualTo("greater_than_or_equal response.body.json.total");
    }

    @Test
    void lessThanIncludesPath() {
        assertThat(AssertionDescriber.describe(new LessThanAssertion("response.body.json.errors", 1)))
                .isEqualTo("less_than response.body.json.errors");
    }

    @Test
    void lessThanOrEqualIncludesPath() {
        assertThat(AssertionDescriber.describe(new LessThanOrEqualAssertion("response.body.json.retries", 3)))
                .isEqualTo("less_than_or_equal response.body.json.retries");
    }

    @Test
    void rangeIncludesPath() {
        assertThat(AssertionDescriber.describe(new RangeAssertion("response.body.json.score", 0, 100)))
                .isEqualTo("range response.body.json.score");
    }

    @Test
    void oneOfIncludesPath() {
        assertThat(AssertionDescriber.describe(new OneOfAssertion("response.body.json.role", List.of("admin", "user"))))
                .isEqualTo("one_of response.body.json.role");
    }

    @Test
    void arraySizeIncludesPath() {
        assertThat(AssertionDescriber.describe(new ArraySizeAssertion("response.body.json.items", 3)))
                .isEqualTo("array_size response.body.json.items");
    }

    @Test
    void arraySizeMinIncludesPath() {
        assertThat(AssertionDescriber.describe(new ArraySizeMinAssertion("response.body.json.results", 1)))
                .isEqualTo("array_size_min response.body.json.results");
    }

    @Test
    void arraySizeMaxIncludesPath() {
        assertThat(AssertionDescriber.describe(new ArraySizeMaxAssertion("response.body.json.errors", 0)))
                .isEqualTo("array_size_max response.body.json.errors");
    }

    @Test
    void arrayIsEmptyIncludesPath() {
        assertThat(AssertionDescriber.describe(new ArrayIsEmptyAssertion("response.body.json.warnings")))
                .isEqualTo("array_is_empty response.body.json.warnings");
    }

    @Test
    void arrayIsNotEmptyIncludesPath() {
        assertThat(AssertionDescriber.describe(new ArrayIsNotEmptyAssertion("response.body.json.items")))
                .isEqualTo("array_is_not_empty response.body.json.items");
    }

    @Test
    void arrayContainsIncludesPath() {
        assertThat(AssertionDescriber.describe(new ArrayContainsAssertion("response.body.json.tags", "featured")))
                .isEqualTo("array_contains response.body.json.tags");
    }

    @Test
    void arrayContainsAllIncludesPath() {
        assertThat(AssertionDescriber.describe(
                        new ArrayContainsAllAssertion("response.body.json.permissions", List.of("read", "write"))))
                .isEqualTo("array_contains_all response.body.json.permissions");
    }

    @Test
    void jsonMatchIncludesPath() {
        assertThat(AssertionDescriber.describe(
                        new JsonMatchAssertion("response.body.json", new ObjectExpectedValue("inline", "{}", null))))
                .isEqualTo("json_match response.body.json");
    }

    @Test
    void jsonSchemaIncludesPath() {
        assertThat(AssertionDescriber.describe(new JsonSchemaAssertion(
                        "response.body.json", new ObjectExpectedValue("file", "schema.json", null))))
                .isEqualTo("json_schema response.body.json");
    }
}
