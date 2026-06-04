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
package io.github.snytkine.apitester.api_tester_cli.exception;

import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import java.util.List;

/**
 * Thrown by the test engine when one or more assertions in a test case fail.
 *
 * <p>This exception is an internal control-flow signal used exclusively within {@link
 * io.github.snytkine.apitester.api_tester_cli.service.PureJavaTestEngine}: it is thrown from {@code
 * executeSingleTest} after every assertion has been evaluated and caught in {@code
 * runConfigurationSuite} to record a {@link
 * io.github.snytkine.apitester.api_tester_cli.model.TestResult#FAILED} outcome. Unlike a raw
 * AssertJ {@code MultipleFailuresError}, it carries fully-structured {@link AssertionFailure}
 * records (assertion description, expected value, actual value) ready for tabular display.
 *
 * <p>Thread-safety: the failure list passed at construction is wrapped in an unmodifiable copy, so
 * instances are effectively immutable and safe to read from any thread.
 */
public final class AssertionFailuresException extends RuntimeException {

    private final transient List<AssertionFailure> failures;

    /**
     * Constructs an {@code AssertionFailuresException} carrying the given structured failures.
     *
     * @param failures the non-empty list of assertion failures collected for the test case; defensively
     *     copied into an unmodifiable list
     */
    public AssertionFailuresException(List<AssertionFailure> failures) {
        super(failures.size() + " assertion(s) failed");
        this.failures = List.copyOf(failures);
    }

    /**
     * Returns the structured assertion failures collected for the test case.
     *
     * @return an unmodifiable list of {@link AssertionFailure} records
     */
    public List<AssertionFailure> failures() {
        return failures;
    }
}
