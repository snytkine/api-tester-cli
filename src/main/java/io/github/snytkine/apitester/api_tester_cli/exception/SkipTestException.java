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

/**
 * Thrown by the test engine when a {@link
 * io.github.snytkine.apitester.api_tester_cli.model.TestCase} declares a non-blank {@code skip}
 * field.
 *
 * <p>This exception is an internal control-flow signal used exclusively within {@link
 * io.github.snytkine.apitester.api_tester_cli.service.PureJavaTestEngine}: it is thrown from
 * {@code executeSingleTest} and caught in {@code runConfigurationSuite} to record a {@link
 * io.github.snytkine.apitester.api_tester_cli.model.TestResult#SKIPPED} outcome without sending
 * any HTTP request or evaluating any assertions.
 *
 * <p>The exception message holds the verbatim value of the test case's {@code skip} field, which
 * is stored as the {@code skipReason} in the resulting {@link
 * io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult}.
 *
 * <p>Thread-safety: immutable after construction; safe to read from any thread.
 */
public class SkipTestException extends RuntimeException {

    /**
     * Constructs a {@code SkipTestException} with the given skip reason.
     *
     * @param reason the non-blank value of the test case's {@code skip} field; used as the exception
     *     message and stored as the skip reason in the test result
     */
    public SkipTestException(String reason) {
        super(reason);
    }
}
