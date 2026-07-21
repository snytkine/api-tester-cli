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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

/**
 * Sealed interface for a single lifecycle hook declared in a test-suite YAML.
 *
 * <p>Jackson uses {@link JsonTypeInfo} and {@link JsonSubTypes} to deserialise the {@code type}
 * discriminator field into the correct concrete record — {@link ScriptHook} ({@code type: script})
 * or {@link WebHook} ({@code type: web}) — following the same pattern as the {@code
 * model.assertions.Assertion} hierarchy. Every permitted subtype must appear both in the {@code
 * permits} clause and in the {@code @JsonSubTypes} annotation, and all subtypes live in this same
 * package as required by the Java sealed-type rules.
 *
 * <p>All implementations are immutable records and therefore thread-safe. Instances hold only the
 * raw, template-resolved values parsed from YAML; phase- and index-dependent defaults (hook id,
 * timeout, async flag) are applied via the default methods below at execution time.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ScriptHook.class, name = "script"),
    @JsonSubTypes.Type(value = WebHook.class, name = "web"),
})
public sealed interface Hook permits ScriptHook, WebHook {

    /** Default per-hook timeout, in seconds, applied when the YAML omits {@code timeout-seconds}. */
    int DEFAULT_TIMEOUT_SECONDS = 10;

    /**
     * The explicit hook id from the YAML, or {@code null} when omitted (a default is derived at
     * execution time via {@link #effectiveId(HookPhase, int)}).
     *
     * @return the declared id, or {@code null}
     */
    @Nullable String id();

    /**
     * The explicit {@code async} flag from the YAML, or {@code null} when omitted (defaults to {@code
     * false} via {@link #isAsync()}).
     *
     * @return the declared async flag, or {@code null}
     */
    @Nullable Boolean async();

    /**
     * The explicit {@code timeout-seconds} from the YAML, or {@code null} when omitted (defaults to
     * {@link #DEFAULT_TIMEOUT_SECONDS} via {@link #effectiveTimeoutSeconds()}).
     *
     * @return the declared timeout in seconds, or {@code null}
     */
    @Nullable Integer timeoutSeconds();

    /**
     * Whether this hook runs asynchronously (does not block the next hook; failures never affect the
     * suite result).
     *
     * @return {@code true} when {@code async} was declared {@code true}, {@code false} otherwise
     */
    default boolean isAsync() {
        return Boolean.TRUE.equals(async());
    }

    /**
     * The effective timeout in seconds, applying {@link #DEFAULT_TIMEOUT_SECONDS} when the YAML
     * omitted the key.
     *
     * @return the timeout in seconds actually used for this hook
     */
    default int effectiveTimeoutSeconds() {
        Integer t = timeoutSeconds();
        return t != null ? t : DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * The effective hook id: the declared {@link #id()} when present and non-blank, otherwise a
     * derived default of {@code "<phase-key>-<index>"} (e.g. {@code "before-all-1"}).
     *
     * @param phase the lifecycle phase this hook belongs to
     * @param oneBasedIndex the hook's 1-based position within its phase list
     * @return a non-null, non-blank identifier for use in logs, events, and error messages
     */
    default String effectiveId(HookPhase phase, int oneBasedIndex) {
        String declared = id();
        return (declared != null && !declared.isBlank()) ? declared : phase.yamlKey() + "-" + oneBasedIndex;
    }
}
