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
 * Jackson deserializer that coerces the {@code expected} YAML field of a {@code json_match} or
 * {@code json_schema} assertion into an {@link ObjectExpectedValue}, accepting both a plain string
 * shorthand and the full {@code ContentReference} object form.
 *
 * <p>Accepted forms:
 *
 * <ul>
 *   <li><b>Plain string</b> — {@code expected: '{"id": 1}'} → {@code new
 *       ObjectExpectedValue("inline", "{\"id\": 1}", null)}. The string is treated as inline content
 *       with an empty {@code ignore} list.
 *   <li><b>Object</b> — {@code expected: {type: inline, content: '...', ignore: [...]}} → a
 *       field-by-field mapping of {@code type}, {@code content} and {@code ignore}.
 *   <li><b>Absent</b> — field not present → Jackson does not invoke this deserializer; the field
 *       remains {@code null}.
 * </ul>
 *
 * <p>This class is stateless and thread-safe; Jackson reuses a single instance across concurrent
 * deserialization calls.
 */
public class ObjectExpectedValueDeserializer extends StdDeserializer<ObjectExpectedValue> {

    /** Constructs the deserializer, registering {@link ObjectExpectedValue} as the handled type. */
    public ObjectExpectedValueDeserializer() {
        super(ObjectExpectedValue.class);
    }

    /**
     * Reads the {@code expected} node and returns it as an {@link ObjectExpectedValue}.
     *
     * @param p the parser positioned at the expected value
     * @param ctxt the deserialization context (unused)
     * @return an {@link ObjectExpectedValue}; never {@code null} for a textual or object node
     * @throws IOException if the JSON node cannot be read
     */
    @Override
    public ObjectExpectedValue deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.isTextual()) {
            return new ObjectExpectedValue("inline", node.asText(), null);
        }
        String type = node.hasNonNull("type") ? node.get("type").asText() : null;
        String content = node.hasNonNull("content") ? node.get("content").asText() : null;
        List<String> ignore = readIgnore(node.get("ignore"));
        return new ObjectExpectedValue(type, content, ignore);
    }

    /**
     * Reads the optional {@code ignore} node as an immutable list of strings.
     *
     * @param ignoreNode the {@code ignore} child node, or {@code null} if absent
     * @return an immutable list of field names, or {@code null} when the node is absent or not an array
     */
    private static List<String> readIgnore(JsonNode ignoreNode) {
        if (ignoreNode == null || !ignoreNode.isArray()) {
            return null;
        }
        List<String> ignore = new ArrayList<>(ignoreNode.size());
        for (JsonNode element : ignoreNode) {
            ignore.add(element.asText());
        }
        return Collections.unmodifiableList(ignore);
    }
}
