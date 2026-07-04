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

import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Validates a fully-parsed {@link TestSuite} for structural constraints that cannot be expressed in
 * the YAML schema.
 *
 * <p>Two groups of rules are enforced:
 *
 * <ul>
 *   <li>{@link #validate(TestSuite)} — every {@link TestCase#name()} within a suite must be unique
 *       (case-sensitive). Duplicates are reported as {@code Duplicate test name: "<name>" appears N
 *       times}.
 *   <li>{@link #validateRestClients(TestSuite)} — exactly one of {@code rest-client} /
 *       {@code rest-clients} must be present, ids must be unique, ids are required when multiple
 *       clients are configured, and a request may only reference a defined client id.
 * </ul>
 *
 * <p>This class is a Spring singleton and is thread-safe. All methods are stateless; per-call data
 * lives on the call stack.
 */
@Service
public class TestSuiteValidator {

    /**
     * Validates that all test case names within {@code testSuite} are unique.
     *
     * <p>Names are compared using their exact string value (case-sensitive). The returned list is
     * sorted by name to produce deterministic output regardless of the order tests appear in the YAML
     * file.
     *
     * @param testSuite the fully-loaded test suite to validate; must not be {@code null}
     * @return a non-null, possibly-empty list of error messages; empty means the suite is valid
     */
    public List<String> validate(TestSuite testSuite) {
        Map<String, Long> nameCounts =
                testSuite.tests().stream().collect(Collectors.groupingBy(TestCase::name, Collectors.counting()));
        List<String> errors = new ArrayList<>();
        nameCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .sorted(Map.Entry.comparingByKey())
                .forEach(e ->
                        errors.add("Duplicate test name: \"" + e.getKey() + "\" appears " + e.getValue() + " times"));
        return errors;
    }

    /**
     * Validates the suite's REST client declaration.
     *
     * <p>Composes four independent, fail-fast rules (each implemented as its own static method):
     *
     * <ol>
     *   <li>exactly one of {@code rest-client} / {@code rest-clients} must be present;
     *   <li>{@code id} values in a {@code rest-clients} list must be unique;
     *   <li>every {@code rest-clients} entry must have an {@code id} when more than one is present;
     *   <li>a request's {@code rest-client} selector must reference a defined client id.
     * </ol>
     *
     * @param suite the fully-loaded test suite to validate; must not be {@code null}
     * @return a non-null, possibly-empty list of error messages; empty means the declaration is valid
     */
    public List<String> validateRestClients(TestSuite suite) {
        List<String> errors = new ArrayList<>();
        errors.addAll(validateExactlyOnePresent(suite));
        errors.addAll(validateUniqueIds(suite));
        errors.addAll(validateIdsPresentWhenMultiple(suite));
        errors.addAll(validateRequestReferences(suite));
        return errors;
    }

    /**
     * Rule 1: exactly one of {@code rest-client} (singular) or {@code rest-clients} (plural) must be
     * declared.
     *
     * @param suite the suite to check
     * @return a singleton error list when neither or both are present, otherwise an empty list
     */
    private static List<String> validateExactlyOnePresent(TestSuite suite) {
        boolean hasSingle = suite.restClient() != null;
        boolean hasMultiple = suite.restClients() != null;
        if (!hasSingle && !hasMultiple) {
            return List.of("Test suite must define exactly one of 'rest-client' or 'rest-clients', but found neither");
        }
        if (hasSingle && hasMultiple) {
            return List.of("Test suite must define exactly one of 'rest-client' or 'rest-clients', but found both");
        }
        return List.of();
    }

    /**
     * Rule 2: {@code id} values across all entries of a {@code rest-clients} list must be unique.
     *
     * @param suite the suite to check
     * @return one error per duplicate id (in first-seen order), otherwise an empty list
     */
    private static List<String> validateUniqueIds(TestSuite suite) {
        List<RestClientConfig> clients = suite.restClients();
        if (clients == null) {
            return List.of();
        }
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RestClientConfig client : clients) {
            String id = client.id();
            if (id != null && !seen.add(id)) {
                errors.add("Duplicate rest-client id: '" + id + "'");
            }
        }
        return errors;
    }

    /**
     * Rule 3: when a {@code rest-clients} list contains more than one entry, every entry must declare
     * an {@code id}.
     *
     * @param suite the suite to check
     * @return one error per entry missing an id (with its zero-based index), otherwise an empty list
     */
    private static List<String> validateIdsPresentWhenMultiple(TestSuite suite) {
        List<RestClientConfig> clients = suite.restClients();
        if (clients == null || clients.size() <= 1) {
            return List.of();
        }
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).id() == null) {
                errors.add("rest-client at index " + i
                        + " is missing required 'id' (required when multiple rest-clients are configured)");
            }
        }
        return errors;
    }

    /**
     * Rule 4: any request that selects a {@code rest-client} id must reference a client defined in
     * the suite's {@code rest-clients} list. Only meaningful when the plural form is used.
     *
     * @param suite the suite to check
     * @return one error per request referencing an unknown id, otherwise an empty list
     */
    private static List<String> validateRequestReferences(TestSuite suite) {
        List<RestClientConfig> clients = suite.restClients();
        if (clients == null) {
            return List.of();
        }
        Set<String> definedIds = clients.stream()
                .map(RestClientConfig::id)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        List<String> errors = new ArrayList<>();
        for (TestCase test : suite.tests()) {
            String requestedId = test.request().restClient();
            if (requestedId != null && !definedIds.contains(requestedId)) {
                errors.add("Test '" + test.name() + "' references unknown rest-client id: '" + requestedId + "'");
            }
        }
        return errors;
    }
}
