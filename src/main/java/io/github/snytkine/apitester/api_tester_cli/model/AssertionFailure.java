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

import org.jspecify.annotations.Nullable;

/**
 * Represents a single assertion failure within a test case, decomposed into the three pieces of
 * information needed to render a structured failure table (one row per piece): a human-readable
 * description of the assertion, the expected value/condition, and the actual observed value.
 *
 * <p>For a normal assertion mismatch all three fields are populated, e.g. {@code description =
 * "status_code equals 201"}, {@code expected = "201"}, {@code actual = "400"}. For non-assertion
 * errors such as an HTTP network failure or an unsupported path, {@code description} carries the
 * error text and both {@code expected} and {@code actual} are {@code null} so that the renderer can
 * omit the Expected/Actual rows.
 *
 * @param description human-readable description of the assertion that was evaluated, or the error
 *     text for non-assertion failures
 * @param expected the value or condition the assertion expected, or {@code null} when not applicable
 * @param actual the value actually observed in the response, or {@code null} when not applicable
 */
public record AssertionFailure(String description, @Nullable String expected, @Nullable String actual) {}
