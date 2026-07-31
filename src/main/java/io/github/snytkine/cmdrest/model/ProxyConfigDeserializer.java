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
package io.github.snytkine.cmdrest.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/**
 * Jackson deserializer for the polymorphic {@code proxy} key of a rest-client, which YAML may
 * supply either as an object or as the boolean literal {@code false}.
 *
 * <p>Accepted forms:
 *
 * <ul>
 *   <li><b>Object</b> — {@code proxy: {url: ..., username: ..., password: ...}} → a configured
 *       {@link ProxyConfig}
 *   <li><b>Boolean {@code false}</b> — {@code proxy: false} → {@link ProxyConfig#DISABLED}
 *   <li><b>Textual {@code "false"}</b> — also mapped to {@link ProxyConfig#DISABLED}, so a
 *       templated value such as {@code proxy: "[[${env.USE_PROXY}]]"} that resolves to a quoted
 *       {@code false} behaves as the user intends
 *   <li><b>Absent</b> — the key is missing, Jackson does not invoke this deserializer and the field
 *       stays {@code null}, which means "an environment proxy may apply"
 * </ul>
 *
 * <p>Anything else — most importantly {@code proxy: true}, which users may reach for expecting it
 * to switch a proxy <em>on</em> — does not throw. It produces {@link ProxyConfig#invalid(String)}
 * carrying the offending literal, so {@code TestSuiteValidator} reports it as an ordinary
 * validation error together with every other problem in the suite. Throwing here would abort the
 * whole load and surface as a parse failure rather than an actionable message.
 *
 * <p>This class is stateless and thread-safe; Jackson reuses a single instance across concurrent
 * deserialization calls.
 */
public class ProxyConfigDeserializer extends StdDeserializer<ProxyConfig> {

    /** Constructs the deserializer, registering {@link ProxyConfig} as the handled type. */
    public ProxyConfigDeserializer() {
        super(ProxyConfig.class);
    }

    /**
     * Reads the {@code proxy} node in whichever of its accepted forms it appears.
     *
     * @param p the parser positioned at the proxy value
     * @param ctxt the deserialization context (unused)
     * @return a configured, disabled, or invalid-marked {@link ProxyConfig}; never {@code null}
     * @throws IOException if the node cannot be read
     */
    @Override
    public ProxyConfig deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.isBoolean()) {
            return node.booleanValue() ? ProxyConfig.invalid("true") : ProxyConfig.DISABLED;
        }
        if (node.isTextual()) {
            String text = node.asText();
            if ("false".equalsIgnoreCase(text.trim())) {
                return ProxyConfig.DISABLED;
            }
            return ProxyConfig.invalid(text);
        }
        if (node.isObject()) {
            return new ProxyConfig(text(node, "url"), text(node, "username"), text(node, "password"));
        }
        if (node.isNull()) {
            // 'proxy:' written with no value. Treated as absent (an environment proxy may still
            // apply), matching how every other optional key behaves when left empty. Jackson
            // normally short-circuits explicit nulls before reaching here; handled for safety.
            return null;
        }
        return ProxyConfig.invalid(node.toString());
    }

    /**
     * Reads an optional text field from the proxy object, returning {@code null} when the field is
     * absent or explicitly null.
     *
     * @param node the proxy object node
     * @param field the field name to read
     * @return the field's text value, or {@code null}
     */
    private static @org.jspecify.annotations.Nullable String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
