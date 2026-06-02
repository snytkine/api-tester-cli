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

import java.util.Map;

/**
 * Immutable holder for all named variable namespaces available during test-suite loading and
 * execution.
 *
 * <p>Each namespace maps to a flat {@code String→String} variable map and corresponds to a
 * top-level Thymeleaf context variable of the same name:
 *
 * <ul>
 *   <li>{@code env} — environment variables merged from the {@code .env} file and the process
 *       environment; accessed in templates as {@code [[${env.MY_VAR}]]}
 *   <li>{@code cli} — variables supplied as {@code key=value} positional arguments on the command
 *       line; accessed as {@code [[${cli.my_var}]]}
 *   <li>{@code suite} — suite-level variables declared in the YAML {@code variables} block;
 *       initially an empty map and populated by {@code TestSuiteLoader} after template step 1;
 *       accessed in templates as {@code [[${suite.my_var}]]}
 *   <li>{@code test} — per-test-case variables; initially empty and merged per test case by
 *       {@code PureJavaTestEngine}; accessed as {@code [[${test.my_var}]]}
 * </ul>
 *
 * <p>All four maps are defensively copied and made unmodifiable at construction time.
 *
 * <p>This record is thread-safe; all fields are immutable.
 */
public record SuiteRunContext(
        Map<String, String> env, Map<String, String> cli, Map<String, String> suite, Map<String, String> test) {

    /**
     * Compact constructor that defensively copies all four maps.
     *
     * @param env environment variable map
     * @param cli CLI variable map
     * @param suite suite-level variable map (initially empty)
     * @param test test-case variable map (initially empty)
     */
    public SuiteRunContext {
        env = Map.copyOf(env);
        cli = Map.copyOf(cli);
        suite = Map.copyOf(suite);
        test = Map.copyOf(test);
    }

    /**
     * Creates a {@code SuiteRunContext} with the supplied {@code env} and {@code cli} maps and empty
     * {@code suite} and {@code test} maps. This is the primary factory used before template
     * processing begins.
     *
     * @param env environment variables (merged from {@code .env} file and process environment)
     * @param cli variables supplied on the command line as {@code key=value} pairs
     * @return a new, fully immutable {@code SuiteRunContext}
     */
    public static SuiteRunContext of(Map<String, String> env, Map<String, String> cli) {
        return new SuiteRunContext(env, cli, Map.of(), Map.of());
    }
}
