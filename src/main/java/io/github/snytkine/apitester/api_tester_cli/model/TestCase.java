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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.Assertion;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A single HTTP test case within a {@link TestSuite}.
 *
 * <p>The {@code name} field uniquely identifies the test within its suite and appears in the
 * terminal UI grid. The optional {@code tags} field (YAML key: {@code tag}) accepts a plain string
 * or a list of strings; a plain string is automatically coerced to a single-element list during
 * deserialization. The {@code --tag} CLI filter runs only tests whose tag list contains the
 * supplied value. The optional {@code skip} field, when non-blank, causes the engine to bypass
 * execution entirely and record a {@link TestResult#SKIPPED} outcome; the field value is stored as
 * the skip reason. The {@code skip} field supports Thymeleaf expressions (e.g. {@code
 * [[${suite.skip_flag}]]}) that are resolved during template processing before the engine runs.
 *
 * <p>The optional {@code saved-session} field declares a list of {@link SavedSession} captures that
 * extract primitive values from this test's response into the suite-wide {@code session} namespace
 * for use by later tests. The optional {@code depends-on} field lists other test names that must run
 * (in the listed order, transitively) immediately before this test so their captured values are
 * available to it; a depended-on test is executed at most once per suite run and its result is reused
 * by every test that depends on it. The optional {@code transient} field, when {@code true}, makes the
 * test run <em>only</em> as another test's dependency and never as a standalone test.
 *
 * <p>All fields are deserialized from the YAML test-suite file via Jackson.
 */
public record TestCase(
        /** Name of the test case as declared in the suite YAML. */
        String name,

        /** Optional human-readable description of what this test verifies. */
        @Nullable String description,

        /**
         * Optional list of tags used to group and selectively run related tests. The YAML key is
         * {@code tag} and accepts either a plain string (coerced to a single-element list) or a YAML
         * sequence. When a {@code --tag} filter is active, only tests whose tag list contains the
         * supplied value are executed. Supports Thymeleaf expressions so tag values can be driven by
         * suite or CLI variables.
         *
         * <p>Examples:
         *
         * <pre>
         * tag: "smoke"                        # deserialized as List.of("smoke")
         * tag: ["smoke", "regression"]        # deserialized as List.of("smoke", "regression")
         * </pre>
         */
        @JsonProperty("tag") @JsonDeserialize(using = TagsDeserializer.class) @Nullable List<String> tags,

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
        List<Assertion> assertions,

        /**
         * Optional list of response-value captures (YAML key {@code saved-session}). Each entry
         * extracts a primitive value from this test's response into the suite-wide {@code session}
         * namespace under its {@code name}. May be {@code null} or empty when the test captures
         * nothing.
         */
        @JsonProperty("saved-session") @Nullable List<SavedSession> savedSession,

        /**
         * Optional list of other test names this test depends on (YAML key {@code depends-on}). The
         * listed dependencies run — in order, resolved transitively — immediately before this test so
         * their {@code session} captures are available to it. A depended-on test runs at most once per
         * suite run; its result (and captured session values) is reused by every dependent. May be
         * {@code null} or empty.
         */
        @JsonProperty("depends-on") @Nullable List<String> dependsOn,

        /**
         * When {@code true} (YAML key {@code transient}), this test runs <em>only</em> as another
         * test's dependency and is never executed as a standalone test. Defaults to {@code false}.
         */
        @JsonProperty("transient") boolean transientCase) {

    /**
     * Backward-compatible convenience constructor for callers (and tests) that predate the {@code
     * saved-session}, {@code depends-on}, and {@code transient} fields. Delegates to the canonical
     * constructor with {@code null} captures, {@code null} dependencies, and {@code
     * transientCase=false}.
     *
     * @param name the test case name
     * @param description optional human-readable description
     * @param tags optional list of tags
     * @param skip optional skip reason (non-blank means skipped)
     * @param variables per-test-case template variables
     * @param request the HTTP request definition
     * @param assertions the assertions to evaluate
     */
    public TestCase(
            String name,
            @Nullable String description,
            @Nullable List<String> tags,
            @Nullable String skip,
            Map<String, String> variables,
            Request request,
            List<Assertion> assertions) {
        this(name, description, tags, skip, variables, request, assertions, null, null, false);
    }
}
