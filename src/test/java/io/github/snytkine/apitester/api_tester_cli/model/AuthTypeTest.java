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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link AuthType}, verifying that {@code fromValue} correctly deserialises string
 * values to enum constants (case-insensitive).
 */
class AuthTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {"basic", "BASIC", "Basic"})
    void fromValueDeserialisesCaseInsensitively(String value) {
        AuthType result = AuthType.fromValue(value);

        assertThat(result).isEqualTo(AuthType.BASIC);
    }

    @Test
    void fromValueThrowsOnUnknownValue() {
        assertThatThrownBy(() -> AuthType.fromValue("oauth2")).isInstanceOf(IllegalArgumentException.class);
    }
}
