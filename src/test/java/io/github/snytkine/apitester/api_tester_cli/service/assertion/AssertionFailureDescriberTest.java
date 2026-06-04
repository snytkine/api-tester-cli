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

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.GreaterThanAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.HasHeaderAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.NotNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ResponseTimeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusCodeAssertion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AssertionFailureDescriber}. */
class AssertionFailureDescriberTest {

    @Test
    void statusCodeDecomposesIntoDescriptionExpectedActual() {
        ApiResponse response = new ApiResponse(400, Map.of(), null);

        AssertionFailure f = AssertionFailureDescriber.describe(new StatusCodeAssertion(201), response);

        assertThat(f.description()).isEqualTo("status_code equals 201");
        assertThat(f.expected()).isEqualTo("201");
        assertThat(f.actual()).isEqualTo("400");
    }

    @Test
    void notNullOnExplicitJsonNullReportsNullActual() {
        ApiResponse response = new ApiResponse(200, Map.of(), new ApiResponse.Body("{\"id\":null}", null));

        AssertionFailure f =
                AssertionFailureDescriber.describe(new NotNullAssertion("response.body.json.$.id"), response);

        assertThat(f.description()).isEqualTo("not_null response.body.json.$.id");
        assertThat(f.expected()).isEqualTo("not null");
        assertThat(f.actual()).isEqualTo("null");
    }

    @Test
    void missingPathReportsPathNotFoundActual() {
        ApiResponse response = new ApiResponse(200, Map.of(), new ApiResponse.Body("{\"name\":\"Alice\"}", null));

        AssertionFailure f =
                AssertionFailureDescriber.describe(new NotNullAssertion("response.body.json.$.missing"), response);

        assertThat(f.actual()).isEqualTo("(path not found)");
    }

    @Test
    void greaterThanFormatsWholeNumberExpectedWithoutDecimal() {
        ApiResponse response = new ApiResponse(200, Map.of(), null);

        AssertionFailure f =
                AssertionFailureDescriber.describe(new GreaterThanAssertion("response.statusCode", 500.0), response);

        assertThat(f.description()).isEqualTo("greater_than response.statusCode");
        assertThat(f.expected()).isEqualTo("> 500");
        assertThat(f.actual()).isEqualTo("200");
    }

    @Test
    void responseTimeUsesMeasuredDuration() {
        ApiResponse response = new ApiResponse(200, Map.of(), null, 250L);

        AssertionFailure f = AssertionFailureDescriber.describe(new ResponseTimeAssertion(100L), response);

        assertThat(f.description()).isEqualTo("response_time within 100ms");
        assertThat(f.expected()).isEqualTo("<= 100 ms");
        assertThat(f.actual()).isEqualTo("250 ms");
    }

    @Test
    void responseTimeNotMeasuredReportsMarker() {
        ApiResponse response = new ApiResponse(200, Map.of(), null);

        AssertionFailure f = AssertionFailureDescriber.describe(new ResponseTimeAssertion(100L), response);

        assertThat(f.actual()).isEqualTo("(not measured)");
    }

    @Test
    void hasHeaderReportsPresentExpectedAndAbsentActual() {
        ApiResponse response = new ApiResponse(200, Map.of(), null);

        AssertionFailure f = AssertionFailureDescriber.describe(new HasHeaderAssertion("X-Request-Id"), response);

        assertThat(f.description()).isEqualTo("has_header X-Request-Id");
        assertThat(f.expected()).isEqualTo("present");
        assertThat(f.actual()).isEqualTo("absent");
    }

    @Test
    void arraySizeReportsActualCollectionSize() {
        ApiResponse response = new ApiResponse(200, Map.of(), new ApiResponse.Body("{\"items\":[1,2]}", null));

        AssertionFailure f =
                AssertionFailureDescriber.describe(new ArraySizeAssertion("response.body.json.$.items", 5), response);

        assertThat(f.description()).isEqualTo("array_size response.body.json.$.items");
        assertThat(f.expected()).isEqualTo("5");
        assertThat(f.actual()).isEqualTo("size 2");
    }

    @Test
    void unresolvableExpectedAndActualAreNeverNullForAssertionMismatch() {
        ApiResponse response = new ApiResponse(500, Map.of(), null);

        AssertionFailure f = AssertionFailureDescriber.describe(new StatusCodeAssertion(200), response);

        assertThat(f.expected()).isNotNull();
        assertThat(f.actual()).isNotNull();
    }

    @Test
    void statusInListsExpectedValues() {
        ApiResponse response = new ApiResponse(418, Map.of(), null);

        AssertionFailure f = AssertionFailureDescriber.describe(
                new io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusInAssertion(List.of(200, 201)),
                response);

        assertThat(f.expected()).isEqualTo("one of [200, 201]");
        assertThat(f.actual()).isEqualTo("418");
    }
}
