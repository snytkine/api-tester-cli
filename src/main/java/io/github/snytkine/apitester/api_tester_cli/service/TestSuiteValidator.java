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

import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Validates a fully-parsed {@link TestSuite} for structural constraints that cannot be expressed in
 * the YAML schema.
 *
 * <p>Currently enforces one rule: every {@link TestCase#name()} within a suite must be unique
 * (case-sensitive comparison). If duplicates are found, one error message per duplicate name is
 * returned, formatted as: {@code Duplicate test name: "<name>" appears N times}.
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
}
