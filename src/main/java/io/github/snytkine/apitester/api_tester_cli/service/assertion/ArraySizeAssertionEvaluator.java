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
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeAssertion;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.opentest4j.AssertionFailedError;

/**
 * Evaluates an {@link ArraySizeAssertion} by resolving the value at {@code path} and asserting that
 * it is a JSON array with exactly {@code expected} elements using an AssertJ {@code hasSize}
 * assertion.
 *
 * <p>Fails when the path is missing, the value is not a {@link List}, or the size does not match.
 */
class ArraySizeAssertionEvaluator implements AssertionEvaluator {

    private final ArraySizeAssertion assertion;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the array_size assertion to evaluate
     */
    ArraySizeAssertionEvaluator(ArraySizeAssertion assertion) {
        this.assertion = assertion;
    }

    /**
     * Resolves the path, confirms the value is a list, and checks its size equals {@code expected}.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        switch (ResponseValueExtractor.extract(response, assertion.path())) {
            case Result.Found f -> {
                if (!(f.value() instanceof List<?> list)) {
                    collector.fail(
                            String.format(
                                    "Expected an array at path '%s' for array_size but was: %s (%s)",
                                    assertion.path(),
                                    f.value(),
                                    f.value() == null
                                            ? "null"
                                            : f.value().getClass().getSimpleName()),
                            null);
                    return;
                }
                try {
                    Assertions.assertThat(list).hasSize(assertion.expected());
                } catch (AssertionError e) {
                    collector.fail(new AssertionFailedError(
                            String.format(
                                    "Expected array at path '%s' to have size %d but was: %d",
                                    assertion.path(), assertion.expected(), list.size()),
                            String.valueOf(assertion.expected()),
                            "size " + list.size()));
                }
            }
            case Result.Missing m ->
                collector.fail(
                        String.format(
                                "Expected array at path '%s' for array_size but path does not exist", assertion.path()),
                        null);
            case Result.Error e -> collector.fail(e.message(), null);
        }
    }
}
