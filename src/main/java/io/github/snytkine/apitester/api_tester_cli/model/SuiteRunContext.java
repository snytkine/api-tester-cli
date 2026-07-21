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
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
 * <p>In addition to the four variable namespaces, every instance carries a {@link #getRunID()
 * runID} — a unique identifier generated at construction time that uniquely identifies a single
 * test-suite execution. It is intended for execution tracking, log correlation, and per-run state
 * management.
 *
 * <p>All four maps are defensively copied and made unmodifiable at construction time.
 *
 * <p>This class is deliberately implemented as an immutable final class rather than a record so
 * that the {@code runID} can be generated internally by the constructor and can never be supplied
 * (and therefore never duplicated) by a caller. All fields are {@code final} and either immutable
 * or defensively copied unmodifiable views, so instances are fully thread-safe and may be shared
 * freely across threads without external synchronization.
 */
public final class SuiteRunContext {

    /** Environment-variable namespace; unmodifiable. */
    private final Map<String, String> env;

    /** CLI-variable namespace; unmodifiable. */
    private final Map<String, String> cli;

    /** Suite-level variable namespace; unmodifiable. */
    private final Map<String, String> suite;

    /** Test-case variable namespace; unmodifiable. */
    private final Map<String, String> test;

    /**
     * Unique identifier for this test-suite execution.
     *
     * <p>Generated exactly once in the constructor via {@link UUID#randomUUID()} and never
     * reassigned. Because it is a {@code final} field assigned only inside the constructor it is
     * inherently thread-safe, and because it is generated internally rather than accepted as a
     * parameter, every {@code SuiteRunContext} instance is guaranteed to have a distinct value.
     */
    private final String runID;

    /**
     * Optional run-level metadata needed to dispatch lifecycle hooks (interactive flag, report
     * dir/path, filters, env-file path). {@code null} when the run declares no hooks or the context
     * has not yet been enriched by the command layer. See {@link #withHookRunMetadata}.
     */
    @Nullable private final HookRunMetadata hookRunMetadata;

    /**
     * Constructs a {@code SuiteRunContext}, defensively copying all four maps into unmodifiable
     * views and generating a fresh, unique {@link #getRunID() runID}. No hook metadata is attached;
     * use {@link #withHookRunMetadata(HookRunMetadata)} to attach it while preserving the runID.
     *
     * @param env environment variable map
     * @param cli CLI variable map
     * @param suite suite-level variable map (initially empty)
     * @param test test-case variable map (initially empty)
     */
    public SuiteRunContext(
            Map<String, String> env, Map<String, String> cli, Map<String, String> suite, Map<String, String> test) {
        this.env = Map.copyOf(env);
        this.cli = Map.copyOf(cli);
        this.suite = Map.copyOf(suite);
        this.test = Map.copyOf(test);
        this.runID = UUID.randomUUID().toString();
        this.hookRunMetadata = null;
    }

    /**
     * Private copy constructor that preserves an existing {@code runID} rather than generating a new
     * one, used by {@link #withHookRunMetadata(HookRunMetadata)} so that attaching metadata never
     * changes the run's identity.
     *
     * @param env environment variable map (already unmodifiable)
     * @param cli CLI variable map (already unmodifiable)
     * @param suite suite-level variable map (already unmodifiable)
     * @param test test-case variable map (already unmodifiable)
     * @param runID the run identifier to preserve
     * @param hookRunMetadata the hook run metadata to attach, or {@code null}
     */
    private SuiteRunContext(
            Map<String, String> env,
            Map<String, String> cli,
            Map<String, String> suite,
            Map<String, String> test,
            String runID,
            @Nullable HookRunMetadata hookRunMetadata) {
        this.env = env;
        this.cli = cli;
        this.suite = suite;
        this.test = test;
        this.runID = runID;
        this.hookRunMetadata = hookRunMetadata;
    }

    /**
     * Returns a copy of this context with the supplied hook run metadata attached, preserving the
     * {@link #getRunID() runID} and all four variable namespaces unchanged.
     *
     * @param metadata the run-level metadata lifecycle hooks require
     * @return a new {@code SuiteRunContext} identical to this one but carrying {@code metadata}
     */
    public SuiteRunContext withHookRunMetadata(HookRunMetadata metadata) {
        return new SuiteRunContext(env, cli, suite, test, runID, metadata);
    }

    /**
     * Returns the attached hook run metadata, or {@link HookRunMetadata#empty()} when none was
     * attached (so callers never have to null-check).
     *
     * @return the hook run metadata; never {@code null}
     */
    public HookRunMetadata hookRunMetadata() {
        return hookRunMetadata != null ? hookRunMetadata : HookRunMetadata.empty();
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

    /**
     * Returns the environment-variable namespace.
     *
     * @return an unmodifiable {@code String→String} map of environment variables
     */
    public Map<String, String> env() {
        return env;
    }

    /**
     * Returns the CLI-variable namespace.
     *
     * @return an unmodifiable {@code String→String} map of CLI variables
     */
    public Map<String, String> cli() {
        return cli;
    }

    /**
     * Returns the suite-level variable namespace.
     *
     * @return an unmodifiable {@code String→String} map of suite-level variables
     */
    public Map<String, String> suite() {
        return suite;
    }

    /**
     * Returns the test-case variable namespace.
     *
     * @return an unmodifiable {@code String→String} map of test-case variables
     */
    public Map<String, String> test() {
        return test;
    }

    /**
     * Returns the unique identifier for this test-suite execution.
     *
     * <p>The value is generated once at construction time and never changes for the lifetime of the
     * instance. Two distinct {@code SuiteRunContext} instances are guaranteed to return different
     * values.
     *
     * @return the immutable, per-instance run identifier
     */
    public String getRunID() {
        return runID;
    }

    /**
     * Returns a diagnostic string containing the {@code runID} and the sizes of each variable
     * namespace. Variable values are intentionally omitted because they may contain secrets.
     *
     * @return a concise, secret-free description of this context
     */
    @Override
    public String toString() {
        return "SuiteRunContext[runID=" + runID + ", env=" + env.size() + ", cli=" + cli.size() + ", suite="
                + suite.size() + ", test=" + test.size() + "]";
    }
}
