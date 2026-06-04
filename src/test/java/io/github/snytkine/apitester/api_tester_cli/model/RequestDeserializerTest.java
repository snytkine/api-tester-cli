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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link RequestDeserializer}, verifying that the correct {@link Request} subtype
 * is produced based on the {@code method} field value.
 */
class RequestDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void payloadMethodsDeserializeToPayloadRequest(String method) throws Exception {
        String json = "{\"method\":\"" + method + "\",\"url\":\"/api/resource\"}";

        Request request = mapper.readValue(json, Request.class);

        assertThat(request).isInstanceOf(PayloadRequest.class);
        assertThat(request.method()).isEqualTo(HttpMethod.fromValue(method));
        assertThat(request.url()).isEqualTo("/api/resource");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS", "TRACE"})
    void bodylessMethodsDeserializeToBodylessRequest(String method) throws Exception {
        String json = "{\"method\":\"" + method + "\",\"url\":\"/api/resource\"}";

        Request request = mapper.readValue(json, Request.class);

        assertThat(request).isInstanceOf(BodylessRequest.class);
        assertThat(request.method()).isEqualTo(HttpMethod.fromValue(method));
        assertThat(request.url()).isEqualTo("/api/resource");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void payloadRequestWithNullBodyIsAllowed(String method) throws Exception {
        String json = "{\"method\":\"" + method + "\",\"url\":\"/api/resource\"}";

        Request request = mapper.readValue(json, Request.class);

        assertThat(request).isInstanceOf(PayloadRequest.class);
        assertThat(((PayloadRequest) request).body()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void payloadRequestDeserializesInlineStringBody(String method) throws Exception {
        String json = "{\"method\":\"" + method + "\",\"url\":\"/api/resource\"," + "\"body\":\"hello world\"}";

        Request request = mapper.readValue(json, Request.class);

        assertThat(request).isInstanceOf(PayloadRequest.class);
        PayloadRequest pr = (PayloadRequest) request;
        assertThat(pr.body()).isNotNull();
        assertThat(pr.body().type()).isEqualTo(BodyType.STRING);
        assertThat(pr.body().content()).isEqualTo("hello world");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void payloadRequestDeserializesBodyWhenPresent(String method) throws Exception {
        String json = "{\"method\":\""
                + method
                + "\",\"url\":\"/api/resource\","
                + "\"body\":{\"type\":\"string\",\"content\":\"hello\"}}";

        Request request = mapper.readValue(json, Request.class);

        assertThat(request).isInstanceOf(PayloadRequest.class);
        PayloadRequest pr = (PayloadRequest) request;
        assertThat(pr.body()).isNotNull();
        assertThat(pr.body().type()).isEqualTo(BodyType.STRING);
        assertThat(pr.body().content()).isEqualTo("hello");
    }
}
