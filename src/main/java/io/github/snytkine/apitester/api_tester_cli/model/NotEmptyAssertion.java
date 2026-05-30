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
 * Assertion that passes when the value at the given {@code path} is present, not {@code null}, and
 * — if the value is a string — not an empty string.
 *
 * <p>The {@code path} follows the same {@code response.*} convention as {@link
 * StringMatchAssertion}. Non-string non-null values (numbers, booleans, objects, arrays) always
 * pass the emptiness check; only string values are additionally tested for blankness.
 */
public record NotEmptyAssertion(String path) implements Assertion {}
