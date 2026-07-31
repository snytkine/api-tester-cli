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
package io.github.snytkine.cmdrest.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.cmdrest.model.BodylessRequest;
import io.github.snytkine.cmdrest.model.HttpMethod;
import io.github.snytkine.cmdrest.model.ProxyConfig;
import io.github.snytkine.cmdrest.model.RestClientConfig;
import io.github.snytkine.cmdrest.model.TestCase;
import io.github.snytkine.cmdrest.model.TestSuite;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TestSuiteValidator#validateProxy(TestSuite, Map)}.
 *
 * <p>Validation exists so that a bad proxy setting is reported before the run alongside every other
 * suite problem, rather than surfacing as a mid-run stack trace. These tests check both that real
 * problems are reported with the offending rest-client named, and — just as importantly — that a
 * client which opted out of proxying is never failed by an environment it does not consult.
 */
class TestSuiteValidatorProxyTest {

    private final TestSuiteValidator validator = new TestSuiteValidator();

    private static TestCase tc() {
        return new TestCase(
                "test",
                null,
                null,
                null,
                Map.of(),
                new BodylessRequest(HttpMethod.GET, "/", null, null, null),
                List.of());
    }

    private static TestSuite suiteWithProxy(ProxyConfig proxy) {
        RestClientConfig client =
                new RestClientConfig(null, "https://api.example.com", 30000, null, null, null, true, proxy);
        return new TestSuite("suite", null, client, null, null, List.of(tc()), null, null);
    }

    private static TestSuite suiteWithClients(RestClientConfig... clients) {
        return new TestSuite("suite", null, null, List.of(clients), null, List.of(tc()), null, null);
    }

    @Test
    void noErrorsWhenNoProxyConfigured() {
        assertThat(validator.validateProxy(suiteWithProxy(null), Map.of())).isEmpty();
    }

    @Test
    void noErrorsForAValidProxyObject() {
        assertThat(validator.validateProxy(
                        suiteWithProxy(new ProxyConfig("http://proxy.example.com:8080", "u", "p")), Map.of()))
                .isEmpty();
    }

    @Test
    void noErrorsForTheDisabledForm() {
        assertThat(validator.validateProxy(suiteWithProxy(ProxyConfig.DISABLED), Map.of()))
                .isEmpty();
    }

    @Test
    void proxyTrueIsReportedWithTheClientName() {
        List<String> errors = validator.validateProxy(suiteWithProxy(ProxyConfig.invalid("true")), Map.of());

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .contains("rest-client 'default'")
                .contains("either a configuration object or the value 'false'");
    }

    @Test
    void missingUrlIsReported() {
        List<String> errors = validator.validateProxy(suiteWithProxy(new ProxyConfig(null, null, null)), Map.of());

        assertThat(errors).singleElement().asString().contains("proxy.url is required");
    }

    @Test
    void httpsProxyUrlIsReportedWithAnExplanation() {
        List<String> errors =
                validator.validateProxy(suiteWithProxy(new ProxyConfig("https://proxy:8443", null, null)), Map.of());

        assertThat(errors)
                .singleElement()
                .asString()
                .contains("must use the 'http' scheme")
                .contains("CONNECT");
    }

    @Test
    void passwordWithoutUsernameIsReported() {
        List<String> errors =
                validator.validateProxy(suiteWithProxy(new ProxyConfig("http://proxy:8080", null, "secret")), Map.of());

        assertThat(errors)
                .singleElement()
                .asString()
                .contains("only allowed when proxy.username is also set")
                .doesNotContain("secret");
    }

    @Test
    void malformedEnvironmentProxyIsReportedForAClientThatWouldUseIt() {
        List<String> errors = validator.validateProxy(suiteWithProxy(null), Map.of("HTTP_PROXY", "https://proxy:8443"));

        assertThat(errors).singleElement().asString().contains("HTTP_PROXY");
    }

    /**
     * The opt-out is absolute, so a broken environment must not fail a client that has declared
     * {@code proxy: false} — otherwise the escape hatch would be unusable exactly when it is needed.
     */
    @Test
    void malformedEnvironmentProxyDoesNotFailAnOptedOutClient() {
        List<String> errors = validator.validateProxy(
                suiteWithProxy(ProxyConfig.DISABLED), Map.of("HTTP_PROXY", "https://proxy:8443"));

        assertThat(errors).isEmpty();
    }

    @Test
    void errorsAreReportedPerRestClientId() {
        List<String> errors = validator.validateProxy(
                suiteWithClients(
                        new RestClientConfig("good", "https://a.example.com", 1000, null, null, null, true, null),
                        new RestClientConfig(
                                "bad",
                                "https://b.example.com",
                                1000,
                                null,
                                null,
                                null,
                                true,
                                new ProxyConfig("https://proxy:8443", null, null))),
                Map.of());

        assertThat(errors).singleElement().asString().contains("rest-client 'bad'");
    }

    @Test
    void nullEnvironmentIsTreatedAsEmpty() {
        assertThat(validator.validateProxy(suiteWithProxy(null), null)).isEmpty();
    }
}
