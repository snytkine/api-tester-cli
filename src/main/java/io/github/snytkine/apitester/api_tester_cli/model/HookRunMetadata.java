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

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Immutable run-level metadata that lifecycle hooks need but that is only known at the command
 * layer, carried into the engine on the {@link SuiteRunContext} so that hooks can be dispatched
 * without widening the {@code TestEngine} signature.
 *
 * <p>Every value here is command-scoped context (not a variable namespace): the interactive-mode
 * flag, the {@code --report} directory and the fully-resolved report file path, the active {@code
 * --tag} / {@code --test} filter values, and the resolved {@code .env} file path. Per-test data
 * (URL, method, status, headers, body) is supplied separately by the engine at hook-dispatch time.
 *
 * <p>This record is immutable and thread-safe.
 *
 * @param interactive {@code true} when the run is using the interactive terminal UI
 * @param reportDir the {@code --report} directory argument, or {@code null} when not requested
 * @param reportPath the fully-resolved report file path (computed up front so {@code after-all} and
 *     report-phase hooks can receive it), or {@code null} when no report is requested
 * @param tagFilter the active {@code --tag} value, or {@code null}
 * @param testNameFilter the active {@code --test} value, or {@code null}
 * @param envFilePath the resolved {@code .env} file path when it exists on disk, or {@code null}
 */
public record HookRunMetadata(
        boolean interactive,
        @Nullable String reportDir,
        @Nullable Path reportPath,
        @Nullable String tagFilter,
        @Nullable String testNameFilter,
        @Nullable Path envFilePath) {

    /**
     * Returns a metadata instance with no report, no filters, no env file, and non-interactive mode.
     * Used as the default when a {@link SuiteRunContext} carries no explicit hook metadata (e.g. in
     * unit tests that call the engine directly).
     *
     * @return an all-empty, non-interactive metadata instance
     */
    public static HookRunMetadata empty() {
        return new HookRunMetadata(false, null, null, null, null, null);
    }
}
