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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a {@link TestCase} deserializes the {@code saved-session}, {@code depends-on}, and
 * {@code transient} YAML keys (and the {@code default} key nested under a capture) into the correct
 * record components, matching the YAML dialect used by the real suite loader.
 */
class TestCaseSessionDeserializationTest {

    private final ObjectMapper mapper =
            new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void deserializesSavedSessionDependsOnAndTransient() throws Exception {
        String yaml =
                """
                name: create-record
                transient: true
                depends-on:
                  - seed
                  - warmup
                request:
                  method: POST
                  url: /records
                saved-session:
                  - name: recordId
                    path: response.body.json.$.id
                    type: integer
                    required: true
                  - name: etag
                    path: response.headers.etag
                    default: "none"
                """;

        TestCase tc = mapper.readValue(yaml, TestCase.class);

        assertThat(tc.name()).isEqualTo("create-record");
        assertThat(tc.transientCase()).isTrue();
        assertThat(tc.dependsOn()).containsExactly("seed", "warmup");
        assertThat(tc.savedSession()).hasSize(2);

        SavedSession first = tc.savedSession().get(0);
        assertThat(first.name()).isEqualTo("recordId");
        assertThat(first.path()).isEqualTo("response.body.json.$.id");
        assertThat(first.type()).isEqualTo(SessionValueType.INTEGER);
        assertThat(first.required()).isTrue();
        assertThat(first.defaultValue()).isNull();

        SavedSession second = tc.savedSession().get(1);
        assertThat(second.name()).isEqualTo("etag");
        assertThat(second.type()).isNull();
        assertThat(second.required()).isFalse();
        assertThat(second.defaultValue()).isEqualTo("none");
    }

    @Test
    void defaultsWhenNewKeysAbsent() throws Exception {
        String yaml =
                """
                name: plain
                request:
                  method: GET
                  url: /health
                """;

        TestCase tc = mapper.readValue(yaml, TestCase.class);

        assertThat(tc.transientCase()).isFalse();
        assertThat(tc.dependsOn()).isNull();
        assertThat(tc.savedSession()).isNull();
    }

    @Test
    void sessionValueTypeIsCaseInsensitive() {
        assertThat(SessionValueType.fromValue("Boolean")).isEqualTo(SessionValueType.BOOLEAN);
        assertThat(SessionValueType.fromValue("DOUBLE")).isEqualTo(SessionValueType.DOUBLE);
        assertThat(SessionValueType.fromValue("string")).isEqualTo(SessionValueType.STRING);
    }
}
