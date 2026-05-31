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
package io.github.snytkine.apitester.api_tester_cli.service.assertion;

import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.RegexMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates a {@link RegexMatchAssertion} by resolving the value at {@code path} and asserting that
 * it is a string matching the regular expression in {@code expected}.
 *
 * <p>The match uses {@link Pattern#matches}, which requires the entire string to be covered by the
 * pattern (equivalent to anchoring with {@code ^...$}). Partial matching can be expressed by the
 * caller using {@code .*} wildcards.
 *
 * <p>Failure cases:
 *
 * <ul>
 *   <li>The {@code expected} pattern is not a valid regular expression.
 *   <li>The resolved value is not a {@link String} (including {@code null}).
 *   <li>The path does not exist in the response.
 *   <li>The pattern does not match the resolved string value.
 * </ul>
 */
class RegexMatchAssertionEvaluator implements AssertionEvaluator {

    private final RegexMatchAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the regex_match assertion to evaluate
     */
    RegexMatchAssertionEvaluator(RegexMatchAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Compiles the regex pattern, resolves the path, and records a failure if the value is not a
     * string or does not match the pattern.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(assertion.expected());
        } catch (PatternSyntaxException e) {
            collector.fail(
                    "Invalid regex pattern '%s' in regex_match assertion at path '%s': %s",
                    assertion.expected(), assertion.path(), e.getMessage());
            return;
        }

        switch (ResponseValueExtractor.extract(response, assertion.path())) {
            case Result.Found f -> {
                if (!(f.value() instanceof String actual)) {
                    collector.fail(
                            "Expected a string value at path '%s' for regex match but was: %s (%s)",
                            assertion.path(),
                            f.value(),
                            f.value() == null ? "null" : f.value().getClass().getSimpleName());
                    return;
                }
                if (!pattern.matcher(actual).matches()) {
                    collector.fail(
                            "Expected value at path '%s' to match pattern '%s' but was: %s",
                            assertion.path(), assertion.expected(), actual);
                }
            }
            case Result.Missing m ->
                collector.fail(
                        "Expected string at path '%s' to match pattern '%s' but path does not exist",
                        assertion.path(), assertion.expected());
            case Result.Error e -> collector.fail(e.message());
        }
    }
}
