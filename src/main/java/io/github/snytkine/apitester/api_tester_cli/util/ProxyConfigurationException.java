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
package io.github.snytkine.apitester.api_tester_cli.util;

/**
 * Thrown when a proxy configuration cannot be turned into usable connection settings — a malformed
 * proxy URL, an unsupported scheme, or credentials declared in an invalid combination.
 *
 * <p>Mirrors the role of {@link SslConfigurationException}: {@code TestSuiteValidator} catches it
 * during pre-run validation and converts the message into an ordinary validation error, so a bad
 * proxy setting is reported alongside every other suite problem rather than aborting the run with a
 * stack trace.
 *
 * <p>Messages must never contain proxy credentials; {@link ProxyResolver} redacts userinfo before
 * including a URL in a message.
 *
 * <p>This class is immutable and thread-safe.
 */
public class ProxyConfigurationException extends RuntimeException {

    /**
     * Creates an exception with a human-readable, credential-free description of the problem.
     *
     * @param message the detail message
     */
    public ProxyConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and an underlying cause.
     *
     * @param message the detail message
     * @param cause the underlying failure
     */
    public ProxyConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
