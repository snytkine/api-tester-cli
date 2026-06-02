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

/**
 * Four-way terminal status of a single {@link TestCase} execution.
 *
 * <p>{@link #PASSED} — all assertions evaluated and passed. {@link #FAILED} — one or more
 * assertions did not pass (soft-assertion failures collected by {@link
 * io.github.snytkine.apitester.api_tester_cli.util.FailureCollector}). {@link #SKIPPED} — the test
 * case declared a non-blank {@code skip} field; execution was bypassed entirely and no HTTP request
 * was sent. {@link #ERROR} — an unexpected exception was thrown before or during assertion
 * evaluation (e.g. network error, JSON parse error).
 */
public enum TestResult {
    PASSED,
    FAILED,
    SKIPPED,
    ERROR
}
