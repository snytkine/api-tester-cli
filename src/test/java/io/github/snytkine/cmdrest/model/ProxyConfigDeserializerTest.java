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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

/**
 * Tests that the polymorphic {@code proxy} key deserializes into the three states the rest of the
 * feature depends on: absent, disabled, and configured.
 *
 * <p>Absent versus disabled is the distinction worth guarding. Both look like "no proxy" at a
 * glance, but only {@code null} lets an environment proxy apply — collapsing them would silently
 * break either the opt-out or the environment default.
 */
class ProxyConfigDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    /**
     * Parses a rest-client YAML snippet.
     *
     * @param yaml the snippet
     * @return the parsed config
     * @throws Exception if parsing fails
     */
    private RestClientConfig parse(String yaml) throws Exception {
        return mapper.readValue(yaml, RestClientConfig.class);
    }

    @Test
    void objectFormIsParsedIntoAConfiguredProxy() throws Exception {
        RestClientConfig config = parse(
                """
                base-url: "https://api.example.com"
                proxy:
                  url: "http://proxy.example.com:8080"
                  username: "user"
                  password: "pass"
                """);

        ProxyConfig proxy = config.proxy();
        assertThat(proxy).isNotNull();
        assertThat(proxy.isDisabled()).isFalse();
        assertThat(proxy.invalidValue()).isNull();
        assertThat(proxy.url()).isEqualTo("http://proxy.example.com:8080");
        assertThat(proxy.username()).isEqualTo("user");
        assertThat(proxy.password()).isEqualTo("pass");
        assertThat(proxy.hasCredentials()).isTrue();
    }

    @Test
    void urlOnlyObjectHasNoCredentials() throws Exception {
        RestClientConfig config = parse(
                """
                proxy:
                  url: "http://proxy.example.com:8080"
                """);

        assertThat(config.proxy()).isNotNull();
        assertThat(config.proxy().hasCredentials()).isFalse();
    }

    @Test
    void booleanFalseIsParsedAsTheDisabledSentinel() throws Exception {
        RestClientConfig config = parse("proxy: false\n");

        assertThat(config.proxy()).isNotNull();
        assertThat(config.proxy().isDisabled()).isTrue();
        assertThat(config.proxy()).isEqualTo(ProxyConfig.DISABLED);
    }

    /**
     * A quoted {@code "false"} reaches Jackson as text when it arrives from a template expression
     * such as {@code proxy: "[[${env.USE_PROXY}]]"}, and must behave the same as the literal.
     */
    @Test
    void textualFalseIsAlsoParsedAsDisabled() throws Exception {
        assertThat(parse("proxy: \"false\"\n").proxy().isDisabled()).isTrue();
        assertThat(parse("proxy: \"False\"\n").proxy().isDisabled()).isTrue();
    }

    @Test
    void absentProxyKeyLeavesTheFieldNull() throws Exception {
        RestClientConfig config = parse("base-url: \"https://api.example.com\"\n");

        assertThat(config.proxy()).isNull();
    }

    /**
     * {@code proxy: true} is recorded rather than thrown so the validator can report it with every
     * other suite error; throwing here would abort the load as a parse failure.
     */
    @Test
    void booleanTrueIsRecordedAsInvalidRatherThanThrowing() throws Exception {
        RestClientConfig config = parse("proxy: true\n");

        assertThat(config.proxy()).isNotNull();
        assertThat(config.proxy().isDisabled()).isFalse();
        assertThat(config.proxy().invalidValue()).isEqualTo("true");
    }

    @Test
    void arbitraryScalarIsRecordedAsInvalid() throws Exception {
        RestClientConfig config = parse("proxy: \"http://proxy:8080\"\n");

        assertThat(config.proxy()).isNotNull();
        assertThat(config.proxy().invalidValue()).isEqualTo("http://proxy:8080");
    }

    @Test
    void numericScalarIsRecordedAsInvalid() throws Exception {
        RestClientConfig config = parse("proxy: 8080\n");

        assertThat(config.proxy()).isNotNull();
        assertThat(config.proxy().invalidValue()).isEqualTo("8080");
    }

    @Test
    void disabledSentinelReportsNoCredentials() {
        assertThat(ProxyConfig.DISABLED.hasCredentials()).isFalse();
        assertThat(ProxyConfig.DISABLED.url()).isNull();
    }
}
