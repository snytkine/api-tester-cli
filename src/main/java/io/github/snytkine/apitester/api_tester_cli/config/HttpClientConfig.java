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

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration that produces a {@link RestClient} bean.
 *
 * <p>If another component in the application context supplies a {@link ClientHttpRequestFactory}
 * bean, that factory is used. Otherwise a default {@link JdkClientHttpRequestFactory} backed by
 * Java's built-in {@link HttpClient} is created automatically.
 */
@Configuration
public class HttpClientConfig {

    /**
     * Creates a default {@link ClientHttpRequestFactory} backed by Java's built-in {@link HttpClient}
     * when no other {@link ClientHttpRequestFactory} bean is present in the application context.
     *
     * <p>The factory is configured with a 30-second connect timeout, HTTP/2 with HTTP/1.1 fallback,
     * and normal redirect following. This bean is skipped entirely when the application registers its
     * own {@link ClientHttpRequestFactory}.
     *
     * @return a {@link JdkClientHttpRequestFactory} wrapping a custom {@link HttpClient}
     */
    @Bean
    @ConditionalOnMissingBean(ClientHttpRequestFactory.class)
    public ClientHttpRequestFactory defaultClientHttpRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new JdkClientHttpRequestFactory(httpClient);
    }

    /**
     * Creates a {@link RestClient} bean using the provided {@link ClientHttpRequestFactory}.
     *
     * <p>The injected factory is either a custom bean registered elsewhere in the application context
     * or the default JDK-backed factory produced by {@link #defaultClientHttpRequestFactory()}.
     *
     * @param requestFactory the HTTP transport factory to use
     * @return a fully configured {@link RestClient} bean
     */
    @Bean
    public RestClient restClient(ClientHttpRequestFactory requestFactory) {
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
