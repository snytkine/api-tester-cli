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
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Jackson deserializer that selects the correct {@link Request} subtype based on the value of the
 * {@code method} field in the JSON/YAML input.
 *
 * <p>Methods that conventionally carry a request body ({@code POST}, {@code PUT}, {@code PATCH},
 * {@code DELETE}) are deserialized as {@link PayloadRequest}. All other methods ({@code GET},
 * {@code HEAD}, {@code OPTIONS}, {@code TRACE}) are deserialized as {@link BodylessRequest}.
 *
 * <p>The concrete records are constructed directly rather than delegating back to Jackson (which
 * would recurse through the {@link Request} interface annotation). Fields are read from the {@link
 * JsonNode} individually; only {@link RequestBody} is delegated to Jackson because it is
 * independent of the {@link Request} hierarchy and carries no inherited deserializer.
 *
 * <p>This class is stateless and thread-safe; Jackson reuses a single instance across concurrent
 * deserialization calls.
 */
public class RequestDeserializer extends StdDeserializer<Request> {

    private static final Set<HttpMethod> PAYLOAD_METHODS =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    /** Constructs the deserializer, registering {@link Request} as the handled type. */
    public RequestDeserializer() {
        super(Request.class);
    }

    /**
     * Reads the {@code method} field from the JSON node and delegates to the appropriate concrete
     * record type.
     *
     * @param p the parser positioned at the start of the request object
     * @param ctxt the deserialization context
     * @return a {@link PayloadRequest} for body-bearing methods, or a {@link BodylessRequest}
     *     otherwise
     * @throws IOException if the JSON cannot be read or the {@code method} value is unrecognised
     */
    @Override
    public Request deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectCodec codec = p.getCodec();
        JsonNode node = codec.readTree(p);

        String methodStr = node.path("method").asText();
        HttpMethod method = HttpMethod.fromValue(methodStr);
        String url = node.path("url").asText();
        Map<String, String> headers = readHeaders(node);
        RequestAuth auth = readAuth(codec, node);
        String restClient = readRestClient(node);

        if (PAYLOAD_METHODS.contains(method)) {
            return new PayloadRequest(method, url, headers, readBody(codec, node), auth, restClient);
        }
        return new BodylessRequest(method, url, headers, auth, restClient);
    }

    /**
     * Reads the optional {@code headers} object as a {@code Map<String, String>}.
     *
     * @param node the root request node
     * @return a map of header names to values, or {@code null} when the key is absent
     */
    private static Map<String, String> readHeaders(JsonNode node) {
        JsonNode headersNode = node.get("headers");
        if (headersNode == null || !headersNode.isObject()) {
            return null;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headersNode
                .fields()
                .forEachRemaining(e -> headers.put(e.getKey(), e.getValue().asText()));
        return headers;
    }

    /**
     * Reads the optional {@code body} field as a {@link RequestBody}.
     *
     * <p>Two forms are accepted:
     *
     * <ul>
     *   <li><b>Plain string</b> — the string value becomes the body content with an implicit type
     *       of {@link BodyType#STRING}. Example: {@code body: '{"name": "Alice"}'}
     *   <li><b>Object form</b> — an object with {@code type} and {@code content} fields,
     *       deserialized via Jackson's standard {@link RequestBody} deserializer. Example: {@code
     *       body: {type: string, content: '{"name": "Alice"}'}}
     * </ul>
     *
     * @param codec the codec used to read the sub-tree
     * @param node the root request node
     * @return a {@link RequestBody} when the {@code body} key is present, otherwise {@code null}
     * @throws IOException if the body node cannot be deserialized
     */
    private static RequestBody readBody(ObjectCodec codec, JsonNode node) throws IOException {
        JsonNode bodyNode = node.get("body");
        if (bodyNode == null) {
            return null;
        }
        if (bodyNode.isTextual()) {
            return new RequestBody(BodyType.STRING, bodyNode.asText());
        }
        return codec.treeToValue(bodyNode, RequestBody.class);
    }

    /**
     * Reads the optional {@code auth} field as a {@link RequestAuth}.
     *
     * @param codec the codec used to read the sub-tree
     * @param node the root request node
     * @return a {@link RequestAuth} when the {@code auth} key is present, otherwise {@code null}
     * @throws IOException if the auth node cannot be deserialized
     */
    private static RequestAuth readAuth(ObjectCodec codec, JsonNode node) throws IOException {
        JsonNode authNode = node.get("auth");
        if (authNode == null) {
            return null;
        }
        return codec.treeToValue(authNode, RequestAuth.class);
    }

    /**
     * Reads the optional {@code rest-client} field, the id of the REST client this request should
     * use.
     *
     * @param node the root request node
     * @return the selected REST client id, or {@code null} when the key is absent
     */
    private static String readRestClient(JsonNode node) {
        JsonNode restClientNode = node.get("rest-client");
        if (restClientNode == null || restClientNode.isNull()) {
            return null;
        }
        return restClientNode.asText();
    }
}
