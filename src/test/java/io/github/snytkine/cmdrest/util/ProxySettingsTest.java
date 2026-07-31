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
package io.github.snytkine.cmdrest.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ProxySettings}, its scheme-aware {@link ProxySelector}, {@link ProxyEndpoint},
 * and {@link ProxyAuthenticator}.
 *
 * <p>The two behaviours worth guarding closely are that the selector routes by target scheme, and
 * that the authenticator refuses to answer anything but a proxy challenge — the latter is what
 * keeps a proxy password from ever being offered to the API under test.
 */
class ProxySettingsTest {

    private static final ProxyEndpoint HTTP = new ProxyEndpoint("plain.example.com", 3128, null, null);
    private static final ProxyEndpoint HTTPS = new ProxyEndpoint("secure.example.com", 3129, "user", "pass");

    @Test
    void selectorRoutesByTargetScheme() {
        ProxySelector selector = new ProxySettings(HTTP, HTTPS, ProxySettings.Source.ENVIRONMENT).toProxySelector();

        assertThat(hostPort(selector, "http://api.example.com/x")).isEqualTo("plain.example.com:3128");
        assertThat(hostPort(selector, "https://api.example.com/x")).isEqualTo("secure.example.com:3129");
    }

    /**
     * Returns the {@code host:port} the selector chose for a target URL.
     *
     * @param selector the selector under test
     * @param target the target URL
     * @return the selected proxy's host and port
     */
    private static String hostPort(ProxySelector selector, String target) {
        List<Proxy> selected = selector.select(URI.create(target));
        assertThat(selected).hasSize(1);
        java.net.InetSocketAddress address =
                (java.net.InetSocketAddress) selected.get(0).address();
        return address.getHostString() + ":" + address.getPort();
    }

    @Test
    void selectorReturnsNoProxyForASchemeWithoutOne() {
        ProxySelector selector = new ProxySettings(null, HTTPS, ProxySettings.Source.ENVIRONMENT).toProxySelector();

        assertThat(selector.select(URI.create("http://api.example.com/x"))).containsExactly(Proxy.NO_PROXY);
    }

    /**
     * The proxy address is left unresolved so DNS happens at connect time; resolving during
     * selection would turn a name-resolution problem into a silently unusable address.
     */
    @Test
    void proxyAddressIsUnresolved() {
        Proxy proxy = HTTP.toProxy();

        assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP);
        assertThat(((java.net.InetSocketAddress) proxy.address()).isUnresolved())
                .isTrue();
    }

    @Test
    void connectFailedDoesNotThrow() {
        ProxySelector selector = new ProxySettings(HTTP, HTTP, ProxySettings.Source.YAML).toProxySelector();

        selector.connectFailed(
                URI.create("http://api.example.com/x"),
                new java.net.InetSocketAddress("plain.example.com", 3128),
                new java.io.IOException("boom"));
    }

    @Test
    void endpointMatchesHostCaseInsensitivelyAndPortExactly() {
        assertThat(HTTP.matches("PLAIN.EXAMPLE.COM", 3128)).isTrue();
        assertThat(HTTP.matches("plain.example.com", 9999)).isFalse();
        assertThat(HTTP.matches(null, 3128)).isFalse();
    }

    /** Endpoints land in log lines and exception messages, so the password must not be in them. */
    @Test
    void endpointToStringNeverContainsThePassword() {
        assertThat(HTTPS.toString()).doesNotContain("pass").contains("secure.example.com:3129", "authenticated");
        assertThat(HTTP.toString()).isEqualTo("plain.example.com:3128");
    }

    @Test
    void describeIsCredentialFreeAndNamesTheSource() {
        String described = new ProxySettings(HTTPS, HTTPS, ProxySettings.Source.YAML).describe();

        assertThat(described)
                .doesNotContain("pass")
                .contains("secure.example.com:3129")
                .contains("source=yaml");
    }

    @Test
    void describeListsBothEndpointsWhenTheyDiffer() {
        String described = new ProxySettings(HTTP, HTTPS, ProxySettings.Source.ENVIRONMENT).describe();

        assertThat(described).contains("http -> plain.example.com:3128").contains("https -> secure.example.com:3129");
    }

    @Test
    void describeOfEmptySettingsSaysNoProxy() {
        assertThat(new ProxySettings(null, null, ProxySettings.Source.YAML).describe())
                .isEqualTo("no proxy");
    }

    @Test
    void emptyAndAuthenticationFlagsReflectTheEndpoints() {
        assertThat(new ProxySettings(null, null, ProxySettings.Source.YAML).isEmpty())
                .isTrue();
        assertThat(new ProxySettings(HTTP, null, ProxySettings.Source.YAML).isEmpty())
                .isFalse();
        assertThat(new ProxySettings(HTTP, null, ProxySettings.Source.YAML).requiresAuthentication())
                .isFalse();
        assertThat(new ProxySettings(HTTP, HTTPS, ProxySettings.Source.YAML).requiresAuthentication())
                .isTrue();
    }

    @Test
    void forProxyAddressPicksTheMatchingEndpoint() {
        ProxySettings settings = new ProxySettings(HTTP, HTTPS, ProxySettings.Source.ENVIRONMENT);

        assertThat(settings.forProxyAddress("secure.example.com", 3129)).isEqualTo(HTTPS);
        assertThat(settings.forProxyAddress("plain.example.com", 3128)).isEqualTo(HTTP);
        assertThat(settings.forProxyAddress("other.example.com", 1)).isNull();
    }

    /**
     * The central guarantee of {@link ProxyAuthenticator}: a server challenge is never answered, so
     * proxy credentials cannot leak to the API under test.
     */
    @Test
    void authenticatorAnswersProxyChallengesOnly() throws Exception {
        ProxySettings settings = new ProxySettings(HTTPS, HTTPS, ProxySettings.Source.YAML);
        ProxyAuthenticator authenticator = new ProxyAuthenticator(settings);

        PasswordAuthentication proxyAnswer =
                request(authenticator, "secure.example.com", 3129, Authenticator.RequestorType.PROXY);
        assertThat(proxyAnswer).isNotNull();
        assertThat(proxyAnswer.getUserName()).isEqualTo("user");
        assertThat(new String(proxyAnswer.getPassword())).isEqualTo("pass");

        PasswordAuthentication serverAnswer =
                request(authenticator, "secure.example.com", 3129, Authenticator.RequestorType.SERVER);
        assertThat(serverAnswer).isNull();
    }

    @Test
    void authenticatorDeclinesUnknownProxiesAndCredentiallessOnes() throws Exception {
        ProxyAuthenticator authenticator =
                new ProxyAuthenticator(new ProxySettings(HTTP, HTTPS, ProxySettings.Source.ENVIRONMENT));

        assertThat(request(authenticator, "elsewhere.example.com", 8080, Authenticator.RequestorType.PROXY))
                .isNull();
        assertThat(request(authenticator, "plain.example.com", 3128, Authenticator.RequestorType.PROXY))
                .isNull();
    }

    /**
     * Invokes the authenticator the way the JDK does, via the package-visible request hook.
     *
     * @param authenticator the authenticator under test
     * @param host the challenging host
     * @param port the challenging port
     * @param type whether the challenge is from a proxy or a server
     * @return the credentials returned, or {@code null}
     * @throws Exception if the request cannot be issued
     */
    private static PasswordAuthentication request(
            Authenticator authenticator, String host, int port, Authenticator.RequestorType type) throws Exception {
        return Authenticator.requestPasswordAuthentication(
                authenticator, host, null, port, "http", "realm", "basic", new java.net.URL("http://" + host), type);
    }
}
