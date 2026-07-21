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
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TestSuiteValidator}. */
class TestSuiteValidatorTest {

    private final TestSuiteValidator validator = new TestSuiteValidator();

    private static TestCase tc(String name) {
        return tc(name, null);
    }

    private static TestCase tc(String name, String restClientId) {
        return new TestCase(
                name,
                null,
                null,
                null,
                Map.of(),
                new BodylessRequest(HttpMethod.GET, "/", null, null, restClientId),
                List.of());
    }

    private static RestClientConfig rc(String id) {
        return new RestClientConfig(id, "https://api.example.com", 30000, null, null);
    }

    private static TestSuite suite(TestCase... cases) {
        return new TestSuite("test-suite", null, null, null, null, List.of(cases), null, null);
    }

    private static TestSuite suiteWithSingle(RestClientConfig single, TestCase... cases) {
        return new TestSuite("test-suite", null, single, null, null, List.of(cases), null, null);
    }

    private static TestSuite suiteWithMultiple(List<RestClientConfig> clients, TestCase... cases) {
        return new TestSuite("test-suite", null, null, clients, null, List.of(cases), null, null);
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

    // ---- validateRestClients ----------------------------------------------------------

    @Test
    void restClientSingularFormIsValid() {
        List<String> errors = validator.validateRestClients(suiteWithSingle(rc(null), tc("login")));

        assertThat(errors).isEmpty();
    }

    @Test
    void singleEntryListWithoutIdIsValid() {
        List<String> errors = validator.validateRestClients(suiteWithMultiple(List.of(rc(null)), tc("login")));

        assertThat(errors).isEmpty();
    }

    @Test
    void singleEntryListWithIdIsValid() {
        List<String> errors = validator.validateRestClients(suiteWithMultiple(List.of(rc("default")), tc("login")));

        assertThat(errors).isEmpty();
    }

    @Test
    void multipleClientsAllWithIdsAreValid() {
        List<String> errors = validator.validateRestClients(
                suiteWithMultiple(List.of(rc("default"), rc("payments")), tc("login", "payments")));

        assertThat(errors).isEmpty();
    }

    @Test
    void neitherRestClientNorRestClientsIsError() {
        List<String> errors = validator.validateRestClients(suite(tc("login")));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .isEqualTo("Test suite must define exactly one of 'rest-client' or 'rest-clients', but found neither");
    }

    @Test
    void bothRestClientAndRestClientsIsError() {
        TestSuite suite = new TestSuite(
                "test-suite", null, rc(null), List.of(rc("default")), null, List.of(tc("login")), null, null);

        List<String> errors = validator.validateRestClients(suite);

        assertThat(errors)
                .contains("Test suite must define exactly one of 'rest-client' or 'rest-clients', but found both");
    }

    @Test
    void duplicateRestClientIdIsError() {
        List<String> errors =
                validator.validateRestClients(suiteWithMultiple(List.of(rc("payments"), rc("payments")), tc("login")));

        assertThat(errors).contains("Duplicate rest-client id: 'payments'");
    }

    @Test
    void missingIdWhenMultipleClientsIsError() {
        List<String> errors =
                validator.validateRestClients(suiteWithMultiple(List.of(rc("default"), rc(null)), tc("login")));

        assertThat(errors)
                .contains("rest-client at index 1 is missing required 'id' "
                        + "(required when multiple rest-clients are configured)");
    }

    @Test
    void requestReferencingUnknownRestClientIsError() {
        List<String> errors = validator.validateRestClients(
                suiteWithMultiple(List.of(rc("default"), rc("payments")), tc("login", "billing")));

        assertThat(errors).contains("Test 'login' references unknown rest-client id: 'billing'");
    }

    /**
     * Builds a test case with a {@code depends-on} list and no captures.
     *
     * @param name the test name
     * @param deps the names of tests this one depends on
     * @return a test case declaring the given dependencies
     */
    private static TestCase tcDep(String name, String... deps) {
        return new TestCase(
                name,
                null,
                null,
                null,
                Map.of(),
                new BodylessRequest(HttpMethod.GET, "/", null, null, null),
                List.of(),
                null,
                List.of(deps),
                false);
    }

    @Test
    void noDependencyErrorsWhenAllReferencesResolve() {
        List<String> errors = validator.validateDependencies(
                suite(tcDep("create"), tcDep("read", "create"), tcDep("update", "read")));

        assertThat(errors).isEmpty();
    }

    @Test
    void detectsUnknownDependencyReference() {
        List<String> errors = validator.validateDependencies(suite(tcDep("read", "create")));

        assertThat(errors).containsExactly("Test 'read' depends-on unknown test: 'create'");
    }

    @Test
    void detectsSelfCycle() {
        List<String> errors = validator.validateDependencies(suite(tcDep("loop", "loop")));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("Circular depends-on dependency detected: loop -> loop");
    }

    @Test
    void detectsTransitiveCycle() {
        List<String> errors = validator.validateDependencies(suite(tcDep("a", "b"), tcDep("b", "c"), tcDep("c", "a")));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Circular depends-on dependency detected:");
        assertThat(errors.get(0)).contains("a").contains("b").contains("c");
    }

    @Test
    void unknownReferenceIsReportedBeforeCycleDetection() {
        // 'b' is undefined; the dangling edge must be reported as unknown, not as a cycle.
        List<String> errors = validator.validateDependencies(suite(tcDep("a", "b")));

        assertThat(errors).containsExactly("Test 'a' depends-on unknown test: 'b'");
    }

    @Test
    void sharedDependencyIsNotACycle() {
        // Both 'b' and 'c' depend on 'a'; a diamond is acyclic.
        List<String> errors = validator.validateDependencies(
                suite(tcDep("a"), tcDep("b", "a"), tcDep("c", "a"), tcDep("d", "b", "c")));

        assertThat(errors).isEmpty();
    }
}
