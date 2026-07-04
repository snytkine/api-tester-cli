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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TestSuite#restClientsById()}, {@link TestSuite#defaultRestClient()}, and
 * {@link TestSuite#withFilteredTests(List)}.
 */
class TestSuiteTest {

    private static RestClientConfig rc(String id, String baseUrl) {
        return new RestClientConfig(id, baseUrl, 30000, null, null);
    }

    private static TestCase tc(String name) {
        return new TestCase(
                name,
                null,
                null,
                null,
                Map.of(),
                new BodylessRequest(HttpMethod.GET, "/", null, null, null),
                List.of());
    }

    private static TestSuite suite(RestClientConfig single, List<RestClientConfig> multiple) {
        return new TestSuite("suite", null, single, multiple, null, List.of(tc("t1")), null, null);
    }

    @Test
    void singularFormMapsToDefault() {
        RestClientConfig single = rc(null, "https://api.example.com");
        TestSuite suite = suite(single, null);

        assertThat(suite.restClientsById()).containsOnlyKeys("default");
        assertThat(suite.defaultRestClient()).isSameAs(single);
    }

    @Test
    void singleEntryListWithoutIdMapsToDefault() {
        RestClientConfig only = rc(null, "https://api.example.com");
        TestSuite suite = suite(null, List.of(only));

        assertThat(suite.restClientsById()).containsOnlyKeys("default");
        assertThat(suite.defaultRestClient()).isSameAs(only);
    }

    @Test
    void singleEntryListWithIdMapsById() {
        RestClientConfig only = rc("default", "https://api.example.com");
        TestSuite suite = suite(null, List.of(only));

        assertThat(suite.restClientsById()).containsOnlyKeys("default");
        assertThat(suite.defaultRestClient()).isSameAs(only);
    }

    @Test
    void multipleEntriesMapById() {
        RestClientConfig def = rc("default", "https://api.example.com");
        RestClientConfig payments = rc("payments", "https://payments.example.com");
        TestSuite suite = suite(null, List.of(def, payments));

        assertThat(suite.restClientsById()).containsOnlyKeys("default", "payments");
        assertThat(suite.restClientsById().get("payments")).isSameAs(payments);
        assertThat(suite.defaultRestClient()).isSameAs(def);
    }

    @Test
    void noClientDeclarationYieldsEmptyMapAndNullDefault() {
        TestSuite suite = suite(null, null);

        assertThat(suite.restClientsById()).isEmpty();
        assertThat(suite.defaultRestClient()).isNull();
    }

    @Test
    void withFilteredTestsCarriesOverClientDeclaration() {
        RestClientConfig single = rc(null, "https://api.example.com");
        TestSuite suite = suite(single, null);

        TestSuite filtered = suite.withFilteredTests(List.of(tc("only")));

        assertThat(filtered.restClient()).isSameAs(single);
        assertThat(filtered.tests()).hasSize(1);
        assertThat(filtered.tests().get(0).name()).isEqualTo("only");
    }
}
