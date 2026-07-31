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
package io.github.snytkine.cmdrest.util;

/**
 * Thrown when a rest-client's {@code ssl} configuration cannot be turned into a usable {@link
 * javax.net.ssl.SSLContext} — for example an unreadable or corrupt certificate file, an unsupported
 * private-key format, or an incorrect key password.
 *
 * <p>The message is intended to be shown directly to the user, so it names the offending file or
 * property and, where helpful, how to fix it. This class is immutable apart from the state inherited
 * from {@link RuntimeException} and is therefore safe to construct and throw from any thread.
 */
public class SslConfigurationException extends RuntimeException {

    /**
     * Creates an exception with a user-facing message.
     *
     * @param message a clear description of what is wrong with the SSL configuration
     */
    public SslConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a user-facing message and an underlying cause.
     *
     * @param message a clear description of what is wrong with the SSL configuration
     * @param cause the underlying error (I/O or security exception)
     */
    public SslConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
