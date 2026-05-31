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
package io.github.snytkine.apitester.api_tester_cli.model.assertions;

/**
 * Assertion that passes only when the value at {@code path} is the boolean literal {@code true}.
 *
 * <p>All other values — including the string {@code "true"}, the integer {@code 1}, and {@code
 * null} — are treated as a failed assertion.
 */
public record AssertTrueAssertion(String path) implements Assertion {}
