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
package io.github.snytkine.apitester.api_tester_cli.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.snytkine.apitester.api_tester_cli.exception.SessionCaptureException;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.SavedSession;
import io.github.snytkine.apitester.api_tester_cli.model.SessionValueType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SessionCapturer}. */
class SessionCapturerTest {

    private final Map<String, String> session = new LinkedHashMap<>();

    private static ApiResponse json(String body) {
        return new ApiResponse(200, Map.of("etag", "W/\"abc\""), new ApiResponse.Body(body, null));
    }

    private static SavedSession cap(String name, String path, SessionValueType type, String def, boolean required) {
        return new SavedSession(name, path, type, def, required);
    }

    @Test
    void capturesStringWithoutType() {
        SessionCapturer.capture(
                "t",
                List.of(cap("name", "response.body.json.$.name", null, null, false)),
                json("{\"name\":\"Alice\"}"),
                session);

        assertThat(session).containsEntry("name", "Alice");
    }

    @Test
    void capturesIntegerWithTypeCoercion() {
        SessionCapturer.capture(
                "t",
                List.of(cap("id", "response.body.json.$.id", SessionValueType.INTEGER, null, false)),
                json("{\"id\":42}"),
                session);

        assertThat(session).containsEntry("id", "42");
    }

    @Test
    void capturesHeaderValue() {
        SessionCapturer.capture(
                "t", List.of(cap("etag", "response.headers.etag", null, null, false)), json("{}"), session);

        assertThat(session).containsEntry("etag", "W/\"abc\"");
    }

    @Test
    void capturesStatusCode() {
        SessionCapturer.capture(
                "t",
                List.of(cap("code", "response.statusCode", SessionValueType.INTEGER, null, false)),
                json("{}"),
                session);

        assertThat(session).containsEntry("code", "200");
    }

    @Test
    void capturesBooleanAndDouble() {
        SessionCapturer.capture(
                "t",
                List.of(
                        cap("active", "response.body.json.$.active", SessionValueType.BOOLEAN, null, false),
                        cap("price", "response.body.json.$.price", SessionValueType.DOUBLE, null, false)),
                json("{\"active\":true,\"price\":9.5}"),
                session);

        assertThat(session).containsEntry("active", "true").containsEntry("price", "9.5");
    }

    @Test
    void rejectsJsonObjectAsNonPrimitive() {
        assertThatThrownBy(() -> SessionCapturer.capture(
                        "createTest",
                        List.of(cap("obj", "response.body.json.$.obj", null, null, false)),
                        json("{\"obj\":{\"a\":1}}"),
                        session))
                .isInstanceOf(SessionCaptureException.class)
                .hasMessageContaining("is not a primitive type")
                .hasMessageContaining("createTest");
    }

    @Test
    void rejectsJsonArrayAsNonPrimitive() {
        assertThatThrownBy(() -> SessionCapturer.capture(
                        "t",
                        List.of(cap("arr", "response.body.json.$.arr", null, null, false)),
                        json("{\"arr\":[1,2,3]}"),
                        session))
                .isInstanceOf(SessionCaptureException.class)
                .hasMessageContaining("is not a primitive type");
    }

    @Test
    void requiredMissingValueFails() {
        assertThatThrownBy(() -> SessionCapturer.capture(
                        "t",
                        List.of(cap("missing", "response.body.json.$.nope", null, null, true)),
                        json("{\"name\":\"Alice\"}"),
                        session))
                .isInstanceOf(SessionCaptureException.class)
                .hasMessageContaining("Failed to extract session parameter 'missing'");
    }

    @Test
    void missingValueUsesDefault() {
        SessionCapturer.capture(
                "t", List.of(cap("token", "response.body.json.$.token", null, "fallback", false)), json("{}"), session);

        assertThat(session).containsEntry("token", "fallback");
    }

    @Test
    void defaultTakesPrecedenceOverRequired() {
        SessionCapturer.capture(
                "t", List.of(cap("token", "response.body.json.$.token", null, "fallback", true)), json("{}"), session);

        assertThat(session).containsEntry("token", "fallback");
    }

    @Test
    void optionalMissingValueIsSkipped() {
        SessionCapturer.capture(
                "t", List.of(cap("token", "response.body.json.$.token", null, null, false)), json("{}"), session);

        assertThat(session).doesNotContainKey("token");
    }

    @Test
    void failedTypeConversionFails() {
        assertThatThrownBy(() -> SessionCapturer.capture(
                        "t",
                        List.of(cap("id", "response.body.json.$.id", SessionValueType.INTEGER, null, false)),
                        json("{\"id\":\"not-a-number\"}"),
                        session))
                .isInstanceOf(SessionCaptureException.class)
                .hasMessageContaining("cannot be converted to integer");
    }

    @Test
    void fractionalNumberCannotBecomeInteger() {
        assertThatThrownBy(() -> SessionCapturer.capture(
                        "t",
                        List.of(cap("id", "response.body.json.$.id", SessionValueType.INTEGER, null, false)),
                        json("{\"id\":4.7}"),
                        session))
                .isInstanceOf(SessionCaptureException.class)
                .hasMessageContaining("cannot be converted to integer");
    }

    @Test
    void lastWriteWinsOnDuplicateName() {
        SessionCapturer.capture(
                "t1",
                List.of(cap("v", "response.body.json.$.v", null, null, false)),
                json("{\"v\":\"first\"}"),
                session);
        SessionCapturer.capture(
                "t2",
                List.of(cap("v", "response.body.json.$.v", null, null, false)),
                json("{\"v\":\"second\"}"),
                session);

        assertThat(session).containsEntry("v", "second");
    }

    @Test
    void nullCapturesListIsNoOp() {
        SessionCapturer.capture("t", null, json("{}"), session);

        assertThat(session).isEmpty();
    }
}
