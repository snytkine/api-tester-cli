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
package io.github.snytkine.apitester.api_tester_cli.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link HttpClientConfig}.
 *
 * <p>Tests instantiate {@link HttpClientConfig} directly (no Spring context) and invoke each
 * {@code @Bean} factory method to verify the returned objects are correctly configured and
 * non-null.
 */
class HttpClientConfigTest {

    private HttpClientConfig config;

    @BeforeEach
    void setUp() {
        config = new HttpClientConfig();
    }

    @Test
    void defaultClientHttpRequestFactory_returnsNonNull() {
        ClientHttpRequestFactory factory = config.defaultClientHttpRequestFactory();
        assertThat(factory).isNotNull();
    }

    @Test
    void defaultClientHttpRequestFactory_returnsJdkClientHttpRequestFactory() {
        ClientHttpRequestFactory factory = config.defaultClientHttpRequestFactory();
        assertThat(factory).isInstanceOf(JdkClientHttpRequestFactory.class);
    }

    @Test
    void restClient_withDefaultFactory_returnsNonNull() {
        ClientHttpRequestFactory factory = config.defaultClientHttpRequestFactory();
        RestClient client = config.restClient(factory);
        assertThat(client).isNotNull();
    }

    @Test
    void restClient_withCustomFactory_returnsNonNull() {
        ClientHttpRequestFactory customFactory = new JdkClientHttpRequestFactory();
        RestClient client = config.restClient(customFactory);
        assertThat(client).isNotNull();
    }

    @Test
    void defaultClientHttpRequestFactory_eachCallReturnsNewInstance() {
        ClientHttpRequestFactory first = config.defaultClientHttpRequestFactory();
        ClientHttpRequestFactory second = config.defaultClientHttpRequestFactory();
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void restClient_eachCallReturnsNewInstance() {
        ClientHttpRequestFactory factory = config.defaultClientHttpRequestFactory();
        RestClient first = config.restClient(factory);
        RestClient second = config.restClient(factory);
        assertThat(first).isNotSameAs(second);
    }
}
