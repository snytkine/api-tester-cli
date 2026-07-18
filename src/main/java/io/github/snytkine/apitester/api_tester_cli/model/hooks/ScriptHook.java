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
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A lifecycle hook that executes a local script via {@code ProcessBuilder} ({@code type: script}).
 *
 * <p>The {@code path} is absolute, or relative to the directory containing the suite YAML. Optional
 * {@code parameters} are user-defined {@code key=value} arguments appended, in declaration order,
 * after the standard system arguments the runner supplies. All values may contain Thymeleaf
 * expressions, resolved by {@code TestSuiteLoader} before deserialisation.
 *
 * <p>This record is immutable and thread-safe.
 *
 * @param id explicit hook id, or {@code null} to derive a default
 * @param async explicit async flag, or {@code null} (defaults to {@code false})
 * @param timeoutSeconds explicit per-hook timeout, or {@code null} (defaults to {@link
 *     Hook#DEFAULT_TIMEOUT_SECONDS})
 * @param path the executable's path; absolute or relative to the suite directory
 * @param parameters optional user-defined {@code key=value} arguments; may be {@code null}
 */
public record ScriptHook(
        @JsonProperty("id") @Nullable String id,
        @JsonProperty("async") @Nullable Boolean async,
        @JsonProperty("timeout-seconds") @Nullable Integer timeoutSeconds,
        @JsonProperty("path") String path,
        @JsonProperty("parameters") @Nullable Map<String, String> parameters)
        implements Hook {}
