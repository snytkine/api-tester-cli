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
package io.github.snytkine.apitester.api_tester_cli.service.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.Assertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusCodeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StringMatchAssertion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ResponseResolverTest {

    @Mock
    private RestClient.ResponseSpec rawSpec;

    @Mock
    private RestClient.ResponseSpec handledSpec;

    private ResponseResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ResponseResolver();
        when(rawSpec.onStatus(any(), any())).thenReturn(handledSpec);
    }

    @Test
    void statusOnlyResolutionWhenAllAssertionsAreStatusCode() {
        when(handledSpec.toBodilessEntity()).thenReturn(new ResponseEntity<>(HttpStatus.OK));

        List<Assertion> assertions = List.of(new StatusCodeAssertion(200));
        ApiResponse result = resolver.resolve(rawSpec, assertions);

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).isNull();
    }

    @Test
    void fullResolutionWhenNonStatusCodeAssertionPresent() {
        when(handledSpec.toEntity(String.class)).thenReturn(ResponseEntity.ok("{\"key\":\"value\"}"));

        List<Assertion> assertions = List.of(
                new StatusCodeAssertion(200),
                new StringMatchAssertion("response.headers.content-type", "application/json", null));
        ApiResponse result = resolver.resolve(rawSpec, assertions);

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).isNotNull();
        assertThat(result.body().text()).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void headersAreLowercasedInStatusOnlyMode() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("X-Request-Id", "abc123");
        when(handledSpec.toBodilessEntity()).thenReturn(new ResponseEntity<>(null, headers, HttpStatus.OK));

        ApiResponse result = resolver.resolve(rawSpec, List.of(new StatusCodeAssertion(200)));

        assertThat(result.headers()).containsKey("content-type");
        assertThat(result.headers()).containsKey("x-request-id");
        assertThat(result.headers()).doesNotContainKey("Content-Type");
        assertThat(result.headers()).doesNotContainKey("X-Request-Id");
    }

    @Test
    void headersAreLowercasedInFullMode() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        when(handledSpec.toEntity(String.class)).thenReturn(new ResponseEntity<>("{}", headers, HttpStatus.OK));

        List<Assertion> assertions =
                List.of(new StringMatchAssertion("response.headers.content-type", "application/json", null));
        ApiResponse result = resolver.resolve(rawSpec, assertions);

        assertThat(result.headers()).containsEntry("content-type", "application/json");
    }

    @Test
    void jsonBodyIsParsedIntoObject() {
        when(handledSpec.toEntity(String.class)).thenReturn(ResponseEntity.ok("{\"name\":\"Alice\"}"));

        List<Assertion> assertions = List.of(new StringMatchAssertion("response.body.text", "any", null));
        ApiResponse result = resolver.resolve(rawSpec, assertions);

        assertThat(result.body()).isNotNull();
        assertThat(result.body().json()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonMap = (Map<String, Object>) result.body().json();
        assertThat(jsonMap).containsEntry("name", "Alice");
    }

    @Test
    void nonJsonBodyLeavesJsonNull() {
        when(handledSpec.toEntity(String.class)).thenReturn(ResponseEntity.ok("plain text response"));

        List<Assertion> assertions = List.of(new StringMatchAssertion("response.body.text", "any", null));
        ApiResponse result = resolver.resolve(rawSpec, assertions);

        assertThat(result.body()).isNotNull();
        assertThat(result.body().text()).isEqualTo("plain text response");
        assertThat(result.body().json()).isNull();
    }

    @Test
    void errorStatusCodesAreCapturedWithoutThrowing() {
        HttpHeaders headers = new HttpHeaders();
        when(handledSpec.toBodilessEntity()).thenReturn(new ResponseEntity<>(null, headers, HttpStatus.NOT_FOUND));

        ApiResponse result = resolver.resolve(rawSpec, List.of(new StatusCodeAssertion(404)));

        assertThat(result.statusCode()).isEqualTo(404);
    }

    @Test
    void nullBodyTextLeavesJsonNull() {
        ResponseEntity<String> noContentEntity = new ResponseEntity<>(null, new HttpHeaders(), HttpStatus.NO_CONTENT);
        when(handledSpec.toEntity(String.class)).thenReturn(noContentEntity);

        List<Assertion> assertions = List.of(new StringMatchAssertion("response.body.text", "any", null));
        ApiResponse result = resolver.resolve(rawSpec, assertions);

        assertThat(result.body()).isNotNull();
        assertThat(result.body().text()).isNull();
        assertThat(result.body().json()).isNull();
    }
}
