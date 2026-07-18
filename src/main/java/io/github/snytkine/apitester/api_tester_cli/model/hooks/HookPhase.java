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
package io.github.snytkine.apitester.api_tester_cli.model.hooks;

/**
 * The seven lifecycle phases at which suite-execution hooks can fire.
 *
 * <p>Each constant carries its kebab-case YAML key (the property name under the top-level {@code
 * hooks} block) so that error messages, default hook ids, and progress events can present the same
 * name the user wrote in the suite file.
 *
 * <p>This enum is immutable and thread-safe.
 */
public enum HookPhase {

    /** Fired once when pre-execution validation fails; nothing else runs afterward. */
    SUITE_VALIDATION_FAILED("suite-validation-failed"),

    /** Fired once before the first test case. A blocking failure here aborts the run. */
    BEFORE_ALL("before-all"),

    /** Fired before each test case's HTTP request. A blocking failure marks that test an error. */
    BEFORE_EACH("before-each"),

    /** Fired after each test case's assertions complete. Failures are warnings only. */
    AFTER_EACH("after-each"),

    /** Fired once after the last test case completes. Failures are warnings only. */
    AFTER_ALL("after-all"),

    /** Fired once before the HTML report is written (only when {@code --report} is passed). */
    BEFORE_REPORT("before-report"),

    /** Fired once after the HTML report has been written (only when {@code --report} is passed). */
    AFTER_REPORT("after-report");

    private final String yamlKey;

    /**
     * @param yamlKey the kebab-case property name for this phase under the {@code hooks} YAML block
     */
    HookPhase(String yamlKey) {
        this.yamlKey = yamlKey;
    }

    /**
     * Returns the kebab-case YAML key for this phase (e.g. {@code "before-all"}).
     *
     * @return the property name this phase is declared under in the suite YAML
     */
    public String yamlKey() {
        return yamlKey;
    }
}
