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
package io.github.snytkine.apitester.api_tester_cli.exception;

/**
 * Unchecked exception thrown when a blocking (synchronous) {@code before-all} lifecycle hook fails
 * (non-zero exit, non-2xx HTTP status, or timeout).
 *
 * <p>A {@code before-all} failure is fatal: no test cases run. {@code PureJavaTestEngine} throws
 * this before firing {@code SuiteStarted}, and {@code RunSuiteCommand} catches it to exit with the
 * dedicated {@code EXIT_HOOK_FAILURE} code in non-interactive mode (or to suppress the run summary
 * in interactive mode). Failures in any other phase are non-fatal warnings and never raise this.
 *
 * <p>This exception carries only a summary message, never resolved script parameters or web
 * payloads, consistent with the feature's secret-handling rules.
 */
public class HookFailedException extends RuntimeException {

    /**
     * Constructs the exception with a human-readable message describing the fatal before-all failure.
     *
     * @param message the failure description (e.g. {@code "Before All hook 'seed-db' returned
     *     non-zero status"})
     */
    public HookFailedException(String message) {
        super(message);
    }
}
