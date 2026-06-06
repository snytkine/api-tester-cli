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
package io.github.snytkine.apitester.api_tester_cli.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Enumeration of supported HTTP authentication schemes.
 *
 * <p>This enum is immutable and thread-safe.
 */
public enum AuthType {
    BASIC;

    /**
     * Deserialises a string value to the corresponding {@link AuthType} constant. The string is
     * converted to uppercase before matching, so {@code "basic"}, {@code "BASIC"}, and {@code
     * "Basic"} all map to {@link #BASIC}.
     *
     * @param value the authentication type as a string
     * @return the corresponding {@link AuthType}
     * @throws IllegalArgumentException if the value does not match any constant
     */
    @JsonCreator
    public static AuthType fromValue(String value) {
        return AuthType.valueOf(value.toUpperCase());
    }
}
