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
 * The primitive type a value captured into the {@code session} namespace may be coerced to.
 *
 * <p>A session capture ({@link io.github.snytkine.apitester.api_tester_cli.model.SavedSession})
 * extracts a value from an HTTP response and stores it under the {@code session} namespace. Only
 * primitive JSON values (string, number, boolean) may be captured; objects and arrays are rejected.
 * When a capture declares a {@code type}, the extracted primitive is converted to the requested
 * type and a conversion failure is reported as an error.
 *
 * <p>YAML values are matched case-insensitively (e.g. {@code string}, {@code STRING}) via {@link
 * #fromValue(String)}. This enum is an immutable, stateless constant and is inherently thread-safe.
 */
public enum SessionValueType {
    /** The value is stored as its textual representation. */
    STRING,

    /** The value is parsed as a whole number ({@link Long}). */
    INTEGER,

    /** The value is parsed as a floating-point number ({@link Double}). */
    DOUBLE,

    /** The value is parsed as a boolean ({@code true}/{@code false}). */
    BOOLEAN;

    /**
     * Deserializes a YAML scalar to a {@code SessionValueType}, accepting any letter case.
     *
     * @param value the raw YAML value (e.g. {@code "integer"}); must not be {@code null}
     * @return the matching enum constant
     * @throws IllegalArgumentException if {@code value} does not name a supported type
     */
    @JsonCreator
    public static SessionValueType fromValue(String value) {
        return SessionValueType.valueOf(value.trim().toUpperCase());
    }
}
