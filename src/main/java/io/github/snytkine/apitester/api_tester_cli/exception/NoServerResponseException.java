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

/**
 * Thrown by the test engine when a test case's request produced no HTTP response at all — the
 * connection was refused, the host was unknown, the TLS handshake failed, or the connection timed
 * out.
 *
 * <p>This is the failure path of the implicit {@link
 * io.github.snytkine.apitester.api_tester_cli.model.assertions.BaseServerResponseAssertion}: since
 * nothing came back, none of the test's declared assertions could be evaluated, so the test is
 * recorded as {@link io.github.snytkine.apitester.api_tester_cli.model.TestResult#FAILED} with zero
 * passed assertions and this single {@link AssertionFailure}. It is distinct from {@link
 * AssertionFailuresException}, which reports assertions that ran and did not pass.
 *
 * <p>Thread-safety: the carried failure is an immutable record, so instances are effectively
 * immutable and safe to read from any thread.
 */
public final class NoServerResponseException extends RuntimeException {

    private final transient AssertionFailure failure;

    /**
     * Constructs the exception with the structured {@code base_server_response} failure to report
     * and the transport error that caused it.
     *
     * @param failure the structured failure describing the expected timeout and the observed
     *     transport error
     * @param cause the underlying transport exception, retained for logging
     */
    public NoServerResponseException(AssertionFailure failure, Throwable cause) {
        super(failure.error() != null ? failure.error() : failure.description(), cause);
        this.failure = failure;
    }

    /**
     * Returns the structured {@code base_server_response} failure for this test case.
     *
     * @return the single {@link AssertionFailure} to record for the failed test
     */
    public AssertionFailure failure() {
        return failure;
    }
}
