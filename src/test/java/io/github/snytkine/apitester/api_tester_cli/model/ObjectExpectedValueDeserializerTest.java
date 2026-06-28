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
import io.github.snytkine.apitester.api_tester_cli.model.assertions.JsonMatchAssertion;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ObjectExpectedValueDeserializer}. */
class ObjectExpectedValueDeserializerTest {

    private static final ObjectMapper YAML_MAPPER =
            new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void plainStringIsNormalisedToInlineContentWithNullIgnore() throws Exception {
        String yaml =
                """
        type: "json_match"
        path: "response.body.json"
        expected: '{"id": 1, "name": "Alice"}'
        """;

        JsonMatchAssertion assertion = YAML_MAPPER.readValue(yaml, JsonMatchAssertion.class);

        ObjectExpectedValue expected = assertion.expected();
        assertThat(expected.type()).isEqualTo("inline");
        assertThat(expected.content()).isEqualTo("{\"id\": 1, \"name\": \"Alice\"}");
        assertThat(expected.ignore()).isNull();
    }

    @Test
    void objectFormMapsAllFields() throws Exception {
        String yaml =
                """
        type: "json_match"
        path: "response.body.json"
        expected:
          type: "inline"
          content: '{"id": 1}'
          ignore:
          - "createdAt"
          - "updatedAt"
        """;

        JsonMatchAssertion assertion = YAML_MAPPER.readValue(yaml, JsonMatchAssertion.class);

        ObjectExpectedValue expected = assertion.expected();
        assertThat(expected.type()).isEqualTo("inline");
        assertThat(expected.content()).isEqualTo("{\"id\": 1}");
        assertThat(expected.ignore()).containsExactly("createdAt", "updatedAt");
    }

    @Test
    void objectFormWithoutIgnoreYieldsNullIgnore() throws Exception {
        String yaml =
                """
        type: "json_match"
        path: "response.body.json"
        expected:
          type: "file"
          content: "expected.json"
        """;

        JsonMatchAssertion assertion = YAML_MAPPER.readValue(yaml, JsonMatchAssertion.class);

        ObjectExpectedValue expected = assertion.expected();
        assertThat(expected.type()).isEqualTo("file");
        assertThat(expected.content()).isEqualTo("expected.json");
        assertThat(expected.ignore()).isNull();
    }

    @Test
    void plainStringIsDeserialisedDirectlyAsObjectExpectedValue() throws Exception {
        ObjectExpectedValue expected = YAML_MAPPER.readValue("'hello'", ObjectExpectedValue.class);

        assertThat(expected.type()).isEqualTo("inline");
        assertThat(expected.content()).isEqualTo("hello");
        assertThat(expected.ignore()).isNull();
    }
}
