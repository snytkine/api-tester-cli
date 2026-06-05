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

/** Unit tests for {@link TagsDeserializer} via full YAML deserialization of {@link TestCase}. */
class TagsDeserializerTest {

    private static final ObjectMapper YAML_MAPPER =
            new ObjectMapper(new YAMLFactory()).configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void singleStringTagIsCoercedToList() throws Exception {
        String yaml =
                """
        name: "test one"
        tag: "smoke"
        request:
          method: "GET"
          url: "http://example.com"
        assertions:
        - type: "status_code"
          expected: 200
        """;

        TestCase tc = YAML_MAPPER.readValue(yaml, TestCase.class);

        assertThat(tc.tags()).containsExactly("smoke");
    }

    @Test
    void listTagIsDeserializedAsIs() throws Exception {
        String yaml =
                """
        name: "test two"
        tag:
        - "smoke"
        - "regression"
        request:
          method: "GET"
          url: "http://example.com"
        assertions:
        - type: "status_code"
          expected: 200
        """;

        TestCase tc = YAML_MAPPER.readValue(yaml, TestCase.class);

        assertThat(tc.tags()).containsExactly("smoke", "regression");
    }

    @Test
    void absentTagDeserializesToNull() throws Exception {
        String yaml =
                """
        name: "test three"
        request:
          method: "GET"
          url: "http://example.com"
        assertions:
        - type: "status_code"
          expected: 200
        """;

        TestCase tc = YAML_MAPPER.readValue(yaml, TestCase.class);

        assertThat(tc.tags()).isNull();
    }
}
