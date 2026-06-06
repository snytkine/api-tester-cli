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

import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
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
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AssertionEvaluatorFactory}. */
class AssertionEvaluatorFactoryTest {

    private static final AssertionEvaluatorFactory FACTORY = new AssertionEvaluatorFactory();
    private static final ObjectExpectedValue INLINE_OEV = new ObjectExpectedValue("inline", "{}", List.of());

    private AssertionEvaluator create(
            io.github.snytkine.apitester.api_tester_cli.model.assertions.Assertion assertion) {
        return FACTORY.create(assertion, null, Map.of());
    }

    @Test
    void createArrayContainsAll() {
        assertThat(create(new ArrayContainsAllAssertion("response.body.json.$.items", List.of("a"))))
                .isInstanceOf(ArrayContainsAllAssertionEvaluator.class);
    }

    @Test
    void createArrayContains() {
        assertThat(create(new ArrayContainsAssertion("response.body.json.$.items", "a")))
                .isInstanceOf(ArrayContainsAssertionEvaluator.class);
    }

    @Test
    void createArrayIsEmpty() {
        assertThat(create(new ArrayIsEmptyAssertion("response.body.json.$.items")))
                .isInstanceOf(ArrayIsEmptyAssertionEvaluator.class);
    }

    @Test
    void createArrayIsNotEmpty() {
        assertThat(create(new ArrayIsNotEmptyAssertion("response.body.json.$.items")))
                .isInstanceOf(ArrayIsNotEmptyAssertionEvaluator.class);
    }

    @Test
    void createArraySize() {
        assertThat(create(new ArraySizeAssertion("response.body.json.$.items", 3)))
                .isInstanceOf(ArraySizeAssertionEvaluator.class);
    }

    @Test
    void createArraySizeMax() {
        assertThat(create(new ArraySizeMaxAssertion("response.body.json.$.items", 10)))
                .isInstanceOf(ArraySizeMaxAssertionEvaluator.class);
    }

    @Test
    void createArraySizeMin() {
        assertThat(create(new ArraySizeMinAssertion("response.body.json.$.items", 1)))
                .isInstanceOf(ArraySizeMinAssertionEvaluator.class);
    }

    @Test
    void createAssertFalse() {
        assertThat(create(new AssertFalseAssertion("response.body.json.$.deleted")))
                .isInstanceOf(AssertFalseAssertionEvaluator.class);
    }

    @Test
    void createAssertTrue() {
        assertThat(create(new AssertTrueAssertion("response.body.json.$.active")))
                .isInstanceOf(AssertTrueAssertionEvaluator.class);
    }

    @Test
    void createEndsWith() {
        assertThat(create(new EndsWithAssertion("response.body.json.$.email", "@example.com")))
                .isInstanceOf(EndsWithAssertionEvaluator.class);
    }

    @Test
    void createGreaterThan() {
        assertThat(create(new GreaterThanAssertion("response.body.json.$.count", 0)))
                .isInstanceOf(GreaterThanAssertionEvaluator.class);
    }

    @Test
    void createGreaterThanOrEqual() {
        assertThat(create(new GreaterThanOrEqualAssertion("response.body.json.$.count", 1)))
                .isInstanceOf(GreaterThanOrEqualAssertionEvaluator.class);
    }

    @Test
    void createHasHeader() {
        assertThat(create(new HasHeaderAssertion("content-type"))).isInstanceOf(HasHeaderAssertionEvaluator.class);
    }

    @Test
    void createIsNull() {
        assertThat(create(new IsNullAssertion("response.body.json.$.deleted")))
                .isInstanceOf(IsNullAssertionEvaluator.class);
    }

    @Test
    void createJsonMatch() {
        assertThat(create(new JsonMatchAssertion("response.body.json", INLINE_OEV)))
                .isInstanceOf(JsonMatchAssertionEvaluator.class);
    }

    @Test
    void createJsonSchema() {
        assertThat(create(new JsonSchemaAssertion("response.body.json", INLINE_OEV)))
                .isInstanceOf(JsonSchemaAssertionEvaluator.class);
    }

    @Test
    void createLessThan() {
        assertThat(create(new LessThanAssertion("response.body.json.$.count", 100)))
                .isInstanceOf(LessThanAssertionEvaluator.class);
    }

    @Test
    void createLessThanOrEqual() {
        assertThat(create(new LessThanOrEqualAssertion("response.body.json.$.count", 100)))
                .isInstanceOf(LessThanOrEqualAssertionEvaluator.class);
    }

    @Test
    void createNotEmpty() {
        assertThat(create(new NotEmptyAssertion("response.body.json.$.name")))
                .isInstanceOf(NotEmptyAssertionEvaluator.class);
    }

    @Test
    void createNotNull() {
        assertThat(create(new NotNullAssertion("response.body.json.$.id")))
                .isInstanceOf(NotNullAssertionEvaluator.class);
    }

    @Test
    void createOneOf() {
        assertThat(create(new OneOfAssertion("response.body.json.$.role", List.of("admin", "user"))))
                .isInstanceOf(OneOfAssertionEvaluator.class);
    }

    @Test
    void createRange() {
        assertThat(create(new RangeAssertion("response.body.json.$.score", 0, 100)))
                .isInstanceOf(RangeAssertionEvaluator.class);
    }

    @Test
    void createRegexMatch() {
        assertThat(create(new RegexMatchAssertion("response.body.json.$.id", "[0-9]+")))
                .isInstanceOf(RegexMatchAssertionEvaluator.class);
    }

    @Test
    void createResponseTime() {
        assertThat(create(new ResponseTimeAssertion(500))).isInstanceOf(ResponseTimeAssertionEvaluator.class);
    }

    @Test
    void createStartsWith() {
        assertThat(create(new StartsWithAssertion("response.body.json.$.code", "ERR")))
                .isInstanceOf(StartsWithAssertionEvaluator.class);
    }

    @Test
    void createStatusCode() {
        assertThat(create(new StatusCodeAssertion(200))).isInstanceOf(StatusCodeAssertionEvaluator.class);
    }

    @Test
    void createStatusIn() {
        assertThat(create(new StatusInAssertion(List.of(200, 201)))).isInstanceOf(StatusInAssertionEvaluator.class);
    }

    @Test
    void createStringContains() {
        assertThat(create(new StringContainsAssertion("response.body.json.$.msg", "ok", null)))
                .isInstanceOf(StringContainsAssertionEvaluator.class);
    }

    @Test
    void createStringMatch() {
        assertThat(create(new StringMatchAssertion("response.body.json.$.status", "active", null)))
                .isInstanceOf(StringMatchAssertionEvaluator.class);
    }

    @Test
    void createValueType() {
        assertThat(create(new ValueTypeAssertion("response.body.json.$.count", "integer")))
                .isInstanceOf(ValueTypeAssertionEvaluator.class);
    }

    @Test
    void describeReturnsHumanReadableString() {
        assertThat(FACTORY.describe(new StatusCodeAssertion(201))).isEqualTo("status_code equals 201");
    }
}
