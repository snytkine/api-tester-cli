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
 * Represents a single assertion failure within a test case.
 *
 * <p>When an assertion fails via AssertJ's {@link org.assertj.core.api.SoftAssertions}, each
 * individual failure is captured as an {@code AssertionFailure}. The {@code expected} and {@code
 * actual} fields are populated when the underlying failure is an {@link
 * org.opentest4j.AssertionFailedError} with defined values (e.g. from {@code isEqualTo()}). They
 * are {@code null} for free-form failures produced by {@code soft.fail("message")}, or for
 * non-assertion errors such as HTTP network failures.
 */
public record AssertionFailure(
        /** Human-readable description of what failed. */
        String message,

        /** The value the assertion expected, or {@code null} if not applicable. */
        @Nullable Object expected,

        /** The value the assertion actually observed, or {@code null} if not applicable. */
        @Nullable Object actual) {}
