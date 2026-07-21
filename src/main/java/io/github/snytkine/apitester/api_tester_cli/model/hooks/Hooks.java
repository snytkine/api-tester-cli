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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The top-level {@code hooks} block of a test-suite YAML: one optional, ordered list of {@link Hook}
 * entries per lifecycle {@link HookPhase}.
 *
 * <p>Any phase key omitted from the YAML deserialises to {@code null}; callers should use {@link
 * #forPhase(HookPhase)}, which returns an empty (never-null) list for absent phases.
 *
 * <p>This record is immutable; the lists it holds are whatever Jackson produced during
 * deserialisation and are not defensively copied, so callers must treat them as read-only. Under
 * that contract the type is thread-safe.
 */
public record Hooks(
        @JsonProperty("suite-validation-failed") @Nullable List<Hook> suiteValidationFailed,
        @JsonProperty("before-all") @Nullable List<Hook> beforeAll,
        @JsonProperty("before-each") @Nullable List<Hook> beforeEach,
        @JsonProperty("after-each") @Nullable List<Hook> afterEach,
        @JsonProperty("after-all") @Nullable List<Hook> afterAll,
        @JsonProperty("before-report") @Nullable List<Hook> beforeReport,
        @JsonProperty("after-report") @Nullable List<Hook> afterReport) {

    /**
     * Returns the hooks declared for {@code phase}, or an empty list when the phase key was absent.
     *
     * @param phase the lifecycle phase whose hook list is requested
     * @return a non-null, possibly-empty, read-only list of hooks for that phase
     */
    public List<Hook> forPhase(HookPhase phase) {
        List<Hook> list =
                switch (phase) {
                    case SUITE_VALIDATION_FAILED -> suiteValidationFailed;
                    case BEFORE_ALL -> beforeAll;
                    case BEFORE_EACH -> beforeEach;
                    case AFTER_EACH -> afterEach;
                    case AFTER_ALL -> afterAll;
                    case BEFORE_REPORT -> beforeReport;
                    case AFTER_REPORT -> afterReport;
                };
        return list != null ? list : List.of();
    }

    /**
     * Returns every declared hook across all phases, in phase-declaration order.
     *
     * @return a non-null, possibly-empty list of all hooks in this block
     */
    public List<Hook> allHooks() {
        List<Hook> all = new ArrayList<>();
        for (HookPhase phase : HookPhase.values()) {
            all.addAll(forPhase(phase));
        }
        return all;
    }

    /**
     * Whether this block declares at least one {@link ScriptHook} in any phase. Used to decide
     * whether the {@code --allow-scripts} opt-in gate must be satisfied.
     *
     * @return {@code true} when any phase contains a script hook
     */
    public boolean hasAnyScriptHook() {
        return allHooks().stream().anyMatch(ScriptHook.class::isInstance);
    }

    /**
     * Whether this block declares no hooks at all in any phase.
     *
     * @return {@code true} when every phase list is null or empty
     */
    public boolean isEmpty() {
        return allHooks().isEmpty();
    }
}
