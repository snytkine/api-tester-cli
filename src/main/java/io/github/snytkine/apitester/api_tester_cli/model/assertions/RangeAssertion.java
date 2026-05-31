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
 * Assertion that passes when the numeric value at {@code path} is between {@code min} and {@code
 * max} inclusive.
 *
 * <p>If the value at the path is a string it will be parsed as a {@code double} before comparison.
 * The assertion fails if the value is neither a number nor a parseable string.
 */
public record RangeAssertion(String path, double min, double max) implements Assertion {}
