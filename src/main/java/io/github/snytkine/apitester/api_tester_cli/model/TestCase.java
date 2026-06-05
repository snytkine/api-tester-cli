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

import io.github.snytkine.apitester.api_tester_cli.model.assertions.Assertion;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A single HTTP test case within a {@link TestSuite}.
 *
 * <p>The {@code name} field uniquely identifies the test within its suite and appears in the
 * terminal UI grid. The optional {@code skip} field, when non-blank, causes the engine to bypass
 * execution entirely and record a {@link TestResult#SKIPPED} outcome; the field value is stored as
 * the skip reason. The {@code skip} field supports Thymeleaf expressions (e.g. {@code
 * [[${suite.skip_flag}]]}) that are resolved during template processing before the engine runs.
 *
 * <p>All fields are deserialized from the YAML test-suite file via Jackson.
 */
public record TestCase(
        /** Name of the test case as declared in the suite YAML. */
        String name,

        /** Optional human-readable description of what this test verifies. */
        @Nullable String description,

        /**
         * Optional tag used to group and selectively run related tests. When a {@code --tag} filter is
         * active, only tests whose tag exactly matches the supplied value are executed. Supports
         * Thymeleaf expressions so tags can be driven by suite or CLI variables.
         */
        @Nullable String tag,

        /**
         * When non-blank, the test is skipped and this value is recorded as the skip reason. Supports
         * Thymeleaf expressions resolved before the engine runs.
         */
        @Nullable String skip,

        /** Per-test-case key/value pairs available for substitution in the request template. */
        Map<String, String> variables,

        /** HTTP request definition (method, URL, headers, optional body). */
        Request request,

        /** Ordered list of assertions to evaluate against the HTTP response. */
        List<Assertion> assertions) {}
