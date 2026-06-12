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
 * Immutable options controlling HTML report rendering behaviour.
 *
 * <p>Both flags default to {@code true} (JS enabled, minification enabled). They can be overridden
 * via environment variables — either OS-level or through the suite's {@code .env} file:
 *
 * <ul>
 *   <li>{@code REPORT_NO_JS=true} — disables the inline JavaScript JSON formatter; JSON bodies are
 *       pretty-printed server-side instead. Use when the report must render without JavaScript.
 *   <li>{@code REPORT_NO_MINIFY=true} — disables HTML minification; the report is written as raw
 *       Thymeleaf output. Useful for debugging the report template.
 * </ul>
 *
 * <p>Thread-safety: this record is immutable and safe for concurrent use.
 *
 * @param jsEnabled when {@code true}: JSON bodies are stored as compact JSON and an inline
 *     JavaScript formatter renders them in the browser; when {@code false}: JSON bodies are
 *     pretty-printed server-side and no {@code <script>} tag is emitted
 * @param minifyEnabled when {@code true}: the rendered HTML is post-processed to remove
 *     insignificant whitespace; when {@code false}: the raw Thymeleaf output is written as-is
 */
public record ReportOptions(boolean jsEnabled, boolean minifyEnabled) {

    /**
     * Returns the default options: JS enabled and minification enabled.
     *
     * @return default {@link ReportOptions} instance
     */
    public static ReportOptions defaults() {
        return new ReportOptions(true, true);
    }

    /**
     * Derives options from the merged environment map (OS environment variables + {@code .env} file).
     *
     * <p>The map is expected to be the result of {@code DotEnvLoader.loadDotEnv()}, which merges the
     * suite directory's {@code .env} file with the OS environment (OS takes precedence).
     *
     * @param envVars merged environment variable map
     * @return {@link ReportOptions} with flags derived from {@code REPORT_NO_JS} and {@code
     *     REPORT_NO_MINIFY}
     */
    public static ReportOptions fromEnv(Map<String, String> envVars) {
        boolean noJs = "true".equalsIgnoreCase(envVars.getOrDefault("REPORT_NO_JS", "false"));
        boolean noMinify = "true".equalsIgnoreCase(envVars.getOrDefault("REPORT_NO_MINIFY", "false"));
        return new ReportOptions(!noJs, !noMinify);
    }
}
