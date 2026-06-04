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
 * Represents a single assertion failure within a test case, decomposed into the four pieces of
 * information needed to render a structured failure table: a human-readable description of the
 * assertion type and path, the expected value/condition, the actual observed value, and the
 * evaluator's failure message.
 *
 * <p>For a normal assertion mismatch all four fields are populated, e.g. {@code description =
 * "status_code equals 201"}, {@code expected = "201"}, {@code actual = "400"}, {@code error =
 * "Expected status code 201 but was 400"}. For non-assertion errors such as an HTTP network
 * failure, {@code description} carries the error text and {@code expected}, {@code actual}, and
 * {@code error} are {@code null}.
 *
 * @param description human-readable description of the assertion type and target path (e.g.
 *     {@code "status_code equals 201"}, {@code "not_null response.body.json.id"}), or the error
 *     text for non-assertion failures
 * @param expected the value or condition the assertion expected, or {@code null} when not applicable
 * @param actual the value actually observed in the response, or {@code null} when not applicable
 * @param error the evaluator's failure message explaining why the assertion failed, or {@code null}
 *     for non-assertion errors where the message is already in {@code description}
 */
public record AssertionFailure(
        String description, @Nullable String expected, @Nullable String actual, @Nullable String error) {}
