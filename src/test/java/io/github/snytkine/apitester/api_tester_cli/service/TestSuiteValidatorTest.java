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
package io.github.snytkine.apitester.api_tester_cli.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.model.BodylessRequest;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TestSuiteValidator}. */
class TestSuiteValidatorTest {

    private final TestSuiteValidator validator = new TestSuiteValidator();

    private static TestCase tc(String name) {
        return new TestCase(
                name, null, null, null, Map.of(), new BodylessRequest(HttpMethod.GET, "/", null), List.of());
    }

    private static TestSuite suite(TestCase... cases) {
        return new TestSuite("test-suite", null, null, null, List.of(cases), null, null);
    }

    @Test
    void noErrorsWhenAllNamesUnique() {
        List<String> errors = validator.validate(suite(tc("login"), tc("get users"), tc("logout")));

        assertThat(errors).isEmpty();
    }

    @Test
    void detectsSingleDuplicate() {
        List<String> errors = validator.validate(suite(tc("login"), tc("get users"), tc("login")));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("login").contains("2 times");
    }

    @Test
    void detectsMultipleDuplicates() {
        List<String> errors = validator.validate(suite(tc("login"), tc("get users"), tc("login"), tc("get users")));

        assertThat(errors).hasSize(2);
        assertThat(errors).anyMatch(e -> e.contains("get users") && e.contains("2 times"));
        assertThat(errors).anyMatch(e -> e.contains("login") && e.contains("2 times"));
    }

    @Test
    void detectsTriplicate() {
        List<String> errors = validator.validate(suite(tc("login"), tc("login"), tc("login")));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("login").contains("3 times");
    }

    @Test
    void returnsEmptyListForSingleTestCase() {
        List<String> errors = validator.validate(suite(tc("login")));

        assertThat(errors).isEmpty();
    }

    @Test
    void comparisonIsCaseSensitive() {
        List<String> errors = validator.validate(suite(tc("Login"), tc("login")));

        assertThat(errors).isEmpty();
    }
}
