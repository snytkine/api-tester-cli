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
package io.github.snytkine.cmdrest.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.cmdrest.model.AssertionFailure;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link NoServerResponseException}. */
class NoServerResponseExceptionTest {

    @Test
    void carriesFailureCauseAndErrorMessage() {
        AssertionFailure failure = new AssertionFailure(
                "base_server_response",
                "service must respond within default timeout of 30 seconds",
                "no response received: Connection refused",
                "The service did not return a response: Connection refused");
        IOException cause = new IOException("Connection refused");

        NoServerResponseException e = new NoServerResponseException(failure, cause);

        assertThat(e).isInstanceOf(RuntimeException.class);
        assertThat(e.failure()).isSameAs(failure);
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getMessage()).isEqualTo("The service did not return a response: Connection refused");
    }

    @Test
    void fallsBackToDescriptionWhenFailureCarriesNoError() {
        AssertionFailure failure = new AssertionFailure("base_server_response", null, null, null);

        NoServerResponseException e = new NoServerResponseException(failure, new IOException("boom"));

        assertThat(e.getMessage()).isEqualTo("base_server_response");
    }
}
