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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Jackson deserializer that coerces the {@code tag} YAML field into a {@code List<String>},
 * accepting both a plain string and an array of strings.
 *
 * <p>Accepted forms:
 *
 * <ul>
 *   <li><b>Single string</b> — {@code tag: "smoke"} → {@code List.of("smoke")}
 *   <li><b>Array</b> — {@code tag: ["smoke", "regression"]} → {@code List.of("smoke",
 *       "regression")}
 *   <li><b>Absent</b> — field not present → Jackson does not invoke this deserializer; the field
 *       remains {@code null}
 * </ul>
 *
 * <p>This class is stateless and thread-safe; Jackson reuses a single instance across concurrent
 * deserialization calls.
 */
public class TagsDeserializer extends StdDeserializer<List<String>> {

    /** Constructs the deserializer, registering {@code List<String>} as the handled type. */
    public TagsDeserializer() {
        super(List.class);
    }

    /**
     * Reads the {@code tag} node and returns it as an immutable list of strings.
     *
     * @param p the parser positioned at the tag value
     * @param ctxt the deserialization context (unused)
     * @return an immutable list containing one or more tag strings, or {@code null} if the node is
     *     neither a text nor an array node
     * @throws IOException if the JSON node cannot be read
     */
    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.isTextual()) {
            return List.of(node.asText());
        }
        if (node.isArray()) {
            List<String> tags = new ArrayList<>(node.size());
            for (JsonNode element : node) {
                tags.add(element.asText());
            }
            return Collections.unmodifiableList(tags);
        }
        return null;
    }
}
