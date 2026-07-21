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
package io.github.snytkine.apitester.api_tester_cli.service.hooks;

import org.jspecify.annotations.Nullable;

/**
 * The outcome of executing a single lifecycle hook — script or web.
 *
 * <p>For a script hook, {@code exitCodeOrStatus} is the process exit code; for a web hook it is the
 * HTTP status code. When the hook could not be launched or no response was obtained (missing
 * executable, connection error, timeout) it is {@code -1}. {@code errorMessage} carries the captured
 * stderr summary (script) or exception message (web) for logging and warning display; it is never
 * surfaced in progress events, which by design carry no free-form text that might contain secrets.
 *
 * <p>This record is immutable and thread-safe.
 *
 * @param success whether the hook completed successfully (script exit {@code 0} / web status {@code
 *     200} or {@code 201})
 * @param exitCodeOrStatus the process exit code or HTTP status, or {@code -1} when not applicable
 * @param durationMs wall-clock duration of the hook in milliseconds
 * @param timedOut whether the hook was terminated for exceeding its timeout
 * @param errorMessage a short human-readable failure reason, or {@code null} on success
 */
public record HookExecutionResult(
        boolean success, int exitCodeOrStatus, long durationMs, boolean timedOut, @Nullable String errorMessage) {

    /** Sentinel used for {@code exitCodeOrStatus} when no exit code / HTTP status is available. */
    public static final int NO_STATUS = -1;

    /**
     * Creates a successful result.
     *
     * @param exitCodeOrStatus the exit code ({@code 0}) or HTTP status ({@code 200}/{@code 201})
     * @param durationMs wall-clock duration in milliseconds
     * @return a successful {@link HookExecutionResult}
     */
    public static HookExecutionResult success(int exitCodeOrStatus, long durationMs) {
        return new HookExecutionResult(true, exitCodeOrStatus, durationMs, false, null);
    }

    /**
     * Creates a failed (non-timeout) result.
     *
     * @param exitCodeOrStatus the exit code or HTTP status, or {@link #NO_STATUS}
     * @param durationMs wall-clock duration in milliseconds
     * @param errorMessage a short failure reason
     * @return a failed {@link HookExecutionResult}
     */
    public static HookExecutionResult failure(int exitCodeOrStatus, long durationMs, String errorMessage) {
        return new HookExecutionResult(false, exitCodeOrStatus, durationMs, false, errorMessage);
    }

    /**
     * Creates a timed-out result.
     *
     * @param durationMs wall-clock duration in milliseconds (approximately the timeout)
     * @param errorMessage a short failure reason naming the timeout
     * @return a timed-out {@link HookExecutionResult}
     */
    public static HookExecutionResult timedOut(long durationMs, String errorMessage) {
        return new HookExecutionResult(false, NO_STATUS, durationMs, true, errorMessage);
    }
}
