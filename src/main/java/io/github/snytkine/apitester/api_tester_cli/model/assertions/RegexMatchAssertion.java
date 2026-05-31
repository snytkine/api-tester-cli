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
 * Assertion that passes when the string value at {@code path} matches the regular expression in
 * {@code expected}.
 *
 * <p>The match is a full-string match (equivalent to anchoring the pattern with {@code ^...$}).
 * Non-string values, {@code null}, and missing paths are treated as failures. An invalid regex
 * pattern records a failure at evaluation time rather than at parse time.
 */
public record RegexMatchAssertion(String path, String expected) implements Assertion {}
