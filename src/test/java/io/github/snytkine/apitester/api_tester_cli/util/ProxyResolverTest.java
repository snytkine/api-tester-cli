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
package io.github.snytkine.apitester.api_tester_cli.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.snytkine.apitester.api_tester_cli.model.ProxyConfig;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProxyResolver}, covering every row of the precedence matrix in its class
 * JavaDoc, the accepted and rejected URL forms, and credential handling.
 *
 * <p>The precedence cases are the ones most likely to be broken by a well-meaning refactor, because
 * three inputs interact: the YAML {@code proxy} key (absent / {@code false} / object), {@code
 * PROXY_USE_ENV}, and whether {@code HTTP_PROXY} / {@code HTTPS_PROXY} are set.
 */
class ProxyResolverTest {

    /** Builds a rest-client config carrying the given proxy declaration. */
    private static RestClientConfig client(ProxyConfig proxy) {
        return new RestClientConfig("c", "https://api.example.com", 1000, null, null, null, true, proxy);
    }

    @Nested
    class PrecedenceMatrix {

        @Test
        void absentProxyWithEnvironmentSetUsesEnvironment() {
            ProxySettings settings = ProxyResolver.resolve(client(null), Map.of("HTTP_PROXY", "http://envproxy:3128"));

            assertThat(settings).isNotNull();
            assertThat(settings.source()).isEqualTo(ProxySettings.Source.ENVIRONMENT);
            assertThat(settings.httpProxy().host()).isEqualTo("envproxy");
            assertThat(settings.httpProxy().port()).isEqualTo(3128);
        }

        /**
         * The environment proxy applies without {@code PROXY_USE_ENV} — that flag only arbitrates
         * against a YAML object, and requiring it here would make the common case need opt-in.
         */
        @Test
        void absentProxyDoesNotRequireProxyUseEnvToPickUpEnvironment() {
            ProxySettings settings = ProxyResolver.resolve(
                    client(null), Map.of("HTTP_PROXY", "http://envproxy:3128", "PROXY_USE_ENV", "false"));

            assertThat(settings).isNotNull();
            assertThat(settings.source()).isEqualTo(ProxySettings.Source.ENVIRONMENT);
        }

        @Test
        void absentProxyWithNoEnvironmentResolvesToNoProxy() {
            assertThat(ProxyResolver.resolve(client(null), Map.of())).isNull();
        }

        @Test
        void disabledProxyIgnoresEnvironment() {
            ProxySettings settings =
                    ProxyResolver.resolve(client(ProxyConfig.DISABLED), Map.of("HTTP_PROXY", "http://envproxy:3128"));

            assertThat(settings).isNull();
        }

        /** The opt-out is absolute: {@code PROXY_USE_ENV} must not resurrect it. */
        @Test
        void disabledProxyIgnoresEnvironmentEvenWhenProxyUseEnvIsTrue() {
            ProxySettings settings = ProxyResolver.resolve(
                    client(ProxyConfig.DISABLED),
                    Map.of("HTTP_PROXY", "http://envproxy:3128", "PROXY_USE_ENV", "true"));

            assertThat(settings).isNull();
        }

        @Test
        void yamlObjectWinsOverEnvironmentByDefault() {
            ProxySettings settings = ProxyResolver.resolve(
                    client(new ProxyConfig("http://yamlproxy:8080", null, null)),
                    Map.of("HTTP_PROXY", "http://envproxy:3128"));

            assertThat(settings).isNotNull();
            assertThat(settings.source()).isEqualTo(ProxySettings.Source.YAML);
            assertThat(settings.httpProxy().host()).isEqualTo("yamlproxy");
        }

        @Test
        void yamlObjectAppliesWhenEnvironmentIsUnset() {
            ProxySettings settings =
                    ProxyResolver.resolve(client(new ProxyConfig("http://yamlproxy:8080", null, null)), Map.of());

            assertThat(settings).isNotNull();
            assertThat(settings.source()).isEqualTo(ProxySettings.Source.YAML);
        }

        @Test
        void proxyUseEnvTrueLetsEnvironmentReplaceYamlObject() {
            ProxySettings settings = ProxyResolver.resolve(
                    client(new ProxyConfig("http://yamlproxy:8080", null, null)),
                    Map.of("HTTP_PROXY", "http://envproxy:3128", "PROXY_USE_ENV", "true"));

            assertThat(settings).isNotNull();
            assertThat(settings.source()).isEqualTo(ProxySettings.Source.ENVIRONMENT);
            assertThat(settings.httpProxy().host()).isEqualTo("envproxy");
        }

        /** With nothing in the environment to override with, the YAML object still stands. */
        @Test
        void proxyUseEnvTrueWithNoEnvironmentProxyFallsBackToYaml() {
            ProxySettings settings = ProxyResolver.resolve(
                    client(new ProxyConfig("http://yamlproxy:8080", null, null)), Map.of("PROXY_USE_ENV", "true"));

            assertThat(settings).isNotNull();
            assertThat(settings.source()).isEqualTo(ProxySettings.Source.YAML);
        }

        /**
         * The override is all-or-nothing: YAML credentials must not survive alongside an
         * environment URL, or a suite could silently send one proxy's password to another.
         */
        @Test
        void proxyUseEnvTrueDiscardsYamlCredentialsEntirely() {
            ProxySettings settings = ProxyResolver.resolve(
                    client(new ProxyConfig("http://yamlproxy:8080", "yamluser", "yamlpass")),
                    Map.of("HTTP_PROXY", "http://envproxy:3128", "PROXY_USE_ENV", "true"));

            assertThat(settings).isNotNull();
            assertThat(settings.httpProxy().username()).isNull();
            assertThat(settings.httpProxy().password()).isNull();
            assertThat(settings.requiresAuthentication()).isFalse();
        }
    }

    @Nested
    class EnvironmentVariables {

        @Test
        void httpAndHttpsProxiesMayDifferAndAreSelectedByTargetScheme() {
            ProxySettings settings = ProxyResolver.resolve(
                    client(null), Map.of("HTTP_PROXY", "http://plain:3128", "HTTPS_PROXY", "http://secure:3129"));

            assertThat(settings).isNotNull();
            assertThat(settings.forScheme("http").host()).isEqualTo("plain");
            assertThat(settings.forScheme("https").host()).isEqualTo("secure");
            assertThat(settings.forScheme("HTTPS").host()).isEqualTo("secure");
        }

        @Test
        void onlyHttpsProxySetLeavesHttpTargetsDirect() {
            ProxySettings settings = ProxyResolver.resolve(client(null), Map.of("HTTPS_PROXY", "http://secure:3129"));

            assertThat(settings).isNotNull();
            assertThat(settings.forScheme("https")).isNotNull();
            assertThat(settings.forScheme("http")).isNull();
        }

        @Test
        void lowercaseVariantsAreAccepted() {
            ProxySettings settings = ProxyResolver.resolve(client(null), Map.of("http_proxy", "http://lower:3128"));

            assertThat(settings).isNotNull();
            assertThat(settings.httpProxy().host()).isEqualTo("lower");
        }

        @Test
        void uppercaseWinsWhenBothCasesAreSet() {
            ProxySettings settings = ProxyResolver.resolve(
                    client(null), Map.of("HTTP_PROXY", "http://upper:1", "http_proxy", "http://lower:2"));

            assertThat(settings).isNotNull();
            assertThat(settings.httpProxy().host()).isEqualTo("upper");
        }

        @Test
        void credentialsAreReadFromEnvironmentUrlUserinfo() {
            ProxySettings settings =
                    ProxyResolver.resolve(client(null), Map.of("HTTP_PROXY", "http://user:secret@envproxy:3128"));

            assertThat(settings).isNotNull();
            assertThat(settings.httpProxy().username()).isEqualTo("user");
            assertThat(settings.httpProxy().password()).isEqualTo("secret");
            assertThat(settings.requiresAuthentication()).isTrue();
        }

        @Test
        void blankEnvironmentValueIsTreatedAsUnset() {
            assertThat(ProxyResolver.resolve(client(null), Map.of("HTTP_PROXY", "   ")))
                    .isNull();
        }

        @Test
        void proxyUseEnvIsCaseInsensitiveAndTrimmed() {
            assertThat(ProxyResolver.useEnvironmentOverride(Map.of("PROXY_USE_ENV", " TRUE ")))
                    .isTrue();
            assertThat(ProxyResolver.useEnvironmentOverride(Map.of("PROXY_USE_ENV", "yes")))
                    .isFalse();
            assertThat(ProxyResolver.useEnvironmentOverride(Map.of())).isFalse();
        }
    }

    @Nested
    class UrlParsing {

        @Test
        void portDefaultsToEighty() {
            ProxyEndpoint endpoint = ProxyResolver.parseProxyUrl("http://proxy.example.com", "proxy.url");

            assertThat(endpoint.host()).isEqualTo("proxy.example.com");
            assertThat(endpoint.port()).isEqualTo(80);
        }

        /** The shell convention permits a bare {@code host:port}; it is read as an http proxy. */
        @Test
        void schemeMayBeOmitted() {
            ProxyEndpoint endpoint = ProxyResolver.parseProxyUrl("proxy.example.com:8080", "HTTP_PROXY");

            assertThat(endpoint.host()).isEqualTo("proxy.example.com");
            assertThat(endpoint.port()).isEqualTo(8080);
        }

        @Test
        void usernameWithoutPasswordIsAccepted() {
            ProxyEndpoint endpoint = ProxyResolver.parseProxyUrl("http://user@proxy:8080", "proxy.url");

            assertThat(endpoint.username()).isEqualTo("user");
            assertThat(endpoint.password()).isNull();
        }

        @Test
        void httpsSchemeIsRejectedWithAnExplanationOfTheJdkLimitation() {
            assertThatThrownBy(() -> ProxyResolver.parseProxyUrl("https://proxy.example.com:8443", "proxy.url"))
                    .isInstanceOf(ProxyConfigurationException.class)
                    .hasMessageContaining("must use the 'http' scheme")
                    .hasMessageContaining("CONNECT")
                    .hasMessageContaining("ssl");
        }

        @Test
        void otherSchemesAreRejected() {
            assertThatThrownBy(() -> ProxyResolver.parseProxyUrl("socks5://proxy:1080", "proxy.url"))
                    .isInstanceOf(ProxyConfigurationException.class)
                    .hasMessageContaining("must use the 'http' scheme");
        }

        @Test
        void missingHostIsRejected() {
            assertThatThrownBy(() -> ProxyResolver.parseProxyUrl("http:///some/path", "proxy.url"))
                    .isInstanceOf(ProxyConfigurationException.class)
                    .hasMessageContaining("does not specify a proxy host");
        }

        @Test
        void unparseableValueIsRejected() {
            assertThatThrownBy(() -> ProxyResolver.parseProxyUrl("http://proxy:not-a-port", "proxy.url"))
                    .isInstanceOf(ProxyConfigurationException.class)
                    .hasMessageContaining("proxy.url");
        }

        /** Error messages are user-facing; a password in the URL must not reach them. */
        @Test
        void credentialsAreRedactedFromErrorMessages() {
            assertThatThrownBy(() -> ProxyResolver.parseProxyUrl("https://user:sup3rsecret@proxy:8443", "proxy.url"))
                    .isInstanceOf(ProxyConfigurationException.class)
                    .hasMessageNotContaining("sup3rsecret")
                    .hasMessageContaining("***@");
        }

        @Test
        void redactLeavesCredentiallessValuesUnchanged() {
            assertThat(ProxyResolver.redact("http://proxy:8080")).isEqualTo("http://proxy:8080");
        }
    }

    @Nested
    class YamlConfiguration {

        @Test
        void explicitCredentialFieldsWinOverUrlUserinfo() {
            ProxySettings settings = ProxyResolver.resolve(
                    client(new ProxyConfig("http://urluser:urlpass@proxy:8080", "fielduser", "fieldpass")), Map.of());

            assertThat(settings).isNotNull();
            assertThat(settings.httpProxy().username()).isEqualTo("fielduser");
            assertThat(settings.httpProxy().password()).isEqualTo("fieldpass");
        }

        @Test
        void oneYamlProxyAppliesToBothSchemes() {
            ProxySettings settings =
                    ProxyResolver.resolve(client(new ProxyConfig("http://proxy:8080", null, null)), Map.of());

            assertThat(settings).isNotNull();
            assertThat(settings.forScheme("http")).isEqualTo(settings.forScheme("https"));
        }

        @Test
        void missingUrlIsRejected() {
            assertThatThrownBy(() -> ProxyResolver.resolve(client(new ProxyConfig(null, null, null)), Map.of()))
                    .isInstanceOf(ProxyConfigurationException.class)
                    .hasMessageContaining("proxy.url is required");
        }

        @Test
        void passwordWithoutUsernameIsRejected() {
            assertThatThrownBy(() ->
                            ProxyResolver.resolve(client(new ProxyConfig("http://proxy:8080", null, "pw")), Map.of()))
                    .isInstanceOf(ProxyConfigurationException.class)
                    .hasMessageContaining("only allowed when proxy.username is also set");
        }

        /** A blank templated username means "unset", not "authenticate as the empty user". */
        @Test
        void blankUsernameIsTreatedAsNoCredentials() {
            ProxySettings settings =
                    ProxyResolver.resolve(client(new ProxyConfig("http://proxy:8080", "  ", null)), Map.of());

            assertThat(settings).isNotNull();
            assertThat(settings.requiresAuthentication()).isFalse();
        }

        @Test
        void proxyTrueIsRejectedWithGuidance() {
            assertThatThrownBy(() -> ProxyResolver.resolve(client(ProxyConfig.invalid("true")), Map.of()))
                    .isInstanceOf(ProxyConfigurationException.class)
                    .hasMessageContaining("either a configuration object or the value 'false'")
                    .hasMessageContaining("HTTP_PROXY");
        }

        @Test
        void otherInvalidScalarsAreRejected() {
            assertThatThrownBy(() -> ProxyResolver.resolve(client(ProxyConfig.invalid("maybe")), Map.of()))
                    .isInstanceOf(ProxyConfigurationException.class)
                    .hasMessageContaining("'maybe'");
        }
    }

    @Nested
    class MalformedEnvironment {

        @Test
        void malformedEnvironmentValueFailsAClientThatWouldUseIt() {
            assertThatThrownBy(() -> ProxyResolver.resolve(client(null), Map.of("HTTP_PROXY", "https://proxy:8443")))
                    .isInstanceOf(ProxyConfigurationException.class)
                    .hasMessageContaining("HTTP_PROXY");
        }

        /**
         * A client that opted out never consults the environment, so a typo there must not fail it.
         * This is what makes {@code proxy: false} usable as an escape hatch in a broken environment.
         */
        @Test
        void malformedEnvironmentValueDoesNotFailAnOptedOutClient() {
            assertThatCode(() -> ProxyResolver.resolve(
                            client(ProxyConfig.DISABLED), Map.of("HTTP_PROXY", "https://proxy:8443")))
                    .doesNotThrowAnyException();
        }

        /** Nor does it fail a client whose own proxy object takes precedence anyway. */
        @Test
        void malformedEnvironmentValueDoesNotFailAClientWithItsOwnProxy() {
            assertThatCode(() -> ProxyResolver.resolve(
                            client(new ProxyConfig("http://proxy:8080", null, null)),
                            Map.of("HTTP_PROXY", "https://bad:8443")))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void nullConfigAndNullEnvironmentResolveToNoProxy() {
        assertThat(ProxyResolver.resolve(null, null)).isNull();
    }
}
