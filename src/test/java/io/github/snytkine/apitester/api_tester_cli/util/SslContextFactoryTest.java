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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.snytkine.apitester.api_tester_cli.model.KeystoreConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SslConfig;
import io.github.snytkine.apitester.api_tester_cli.model.TruststoreConfig;
import java.net.URISyntaxException;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509ExtendedTrustManager;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SslContextFactory}, exercising skip-validation, custom truststore and
 * client-keystore (mTLS) contexts as well as path resolution and the error paths for encrypted keys,
 * incorrect passwords and unsupported (PKCS#1) key formats.
 *
 * <p>Fixtures under {@code src/test/resources/ssl} are generated with OpenSSL: {@code ca.pem} is a
 * self-signed certificate, {@code client.pem} a certificate signed by it, {@code client.key} an
 * unencrypted PKCS#8 key, {@code client-encrypted.key} the same key encrypted with password {@code
 * changeit}, and {@code client-pkcs1.key} the legacy PKCS#1 form.
 */
class SslContextFactoryTest {

    private static final String ENCRYPTED_KEY_PASSWORD = "changeit";

    /**
     * Returns the directory that contains the SSL fixtures.
     *
     * @return the fixtures directory
     * @throws URISyntaxException if the resource URL is malformed
     */
    private static Path sslDir() throws URISyntaxException {
        return Path.of(SslContextFactoryTest.class.getResource("/ssl").toURI());
    }

    /**
     * Returns the absolute path of a fixture file as a string.
     *
     * @param name the fixture file name
     * @return the absolute path string
     * @throws URISyntaxException if the resource URL is malformed
     */
    private static String fixture(String name) throws URISyntaxException {
        return sslDir().resolve(name).toString();
    }

    @Test
    void returnsNullWhenConfigIsNull() {
        assertThat(SslContextFactory.create(null, null)).isNull();
    }

    @Test
    void returnsNullWhenNoCustomizationRequested() {
        assertThat(SslContextFactory.create(new SslConfig(false, null, null), null))
                .isNull();
    }

    @Test
    void skipValidationBuildsContext() {
        SSLContext context = SslContextFactory.create(new SslConfig(true, null, null), null);
        assertThat(context).isNotNull();
    }

    @Test
    void skipValidationIgnoresTruststoreAndKeystore() {
        // Even with non-existent truststore/keystore paths, skip short-circuits and succeeds.
        SslConfig ssl = new SslConfig(
                true,
                new TruststoreConfig("/no/such/ca.pem"),
                new KeystoreConfig("/no/such/client.pem", "/no/such/client.key", null));
        assertThat(SslContextFactory.create(ssl, null)).isNotNull();
    }

    @Test
    void customTruststoreBuildsContext() throws Exception {
        SslConfig ssl = new SslConfig(null, new TruststoreConfig(fixture("ca.pem")), null);
        assertThat(SslContextFactory.create(ssl, null)).isNotNull();
    }

    @Test
    void truststoreResolvesRelativePathAgainstSuiteDir() throws Exception {
        SslConfig ssl = new SslConfig(null, new TruststoreConfig("ca.pem"), null);
        assertThat(SslContextFactory.create(ssl, sslDir())).isNotNull();
    }

    @Test
    void keystoreWithUnencryptedKeyBuildsContext() throws Exception {
        SslConfig ssl =
                new SslConfig(null, null, new KeystoreConfig(fixture("client.pem"), fixture("client.key"), null));
        assertThat(SslContextFactory.create(ssl, null)).isNotNull();
    }

    @Test
    void keystoreWithEncryptedKeyAndCorrectPasswordBuildsContext() throws Exception {
        SslConfig ssl = new SslConfig(
                null,
                null,
                new KeystoreConfig(fixture("client.pem"), fixture("client-encrypted.key"), ENCRYPTED_KEY_PASSWORD));
        assertThat(SslContextFactory.create(ssl, null)).isNotNull();
    }

    @Test
    void keystoreWithCertificateOnlyBuildsContext() throws Exception {
        SslConfig ssl = new SslConfig(null, null, new KeystoreConfig(fixture("client.pem"), null, null));
        assertThat(SslContextFactory.create(ssl, null)).isNotNull();
    }

    @Test
    void combinedTruststoreAndKeystoreBuildsContext() throws Exception {
        SslConfig ssl = new SslConfig(
                null,
                new TruststoreConfig(fixture("ca.pem")),
                new KeystoreConfig(fixture("client.pem"), fixture("client.key"), null));
        assertThat(SslContextFactory.create(ssl, null)).isNotNull();
    }

    @Test
    void encryptedKeyWithWrongPasswordThrows() throws Exception {
        SslConfig ssl = new SslConfig(
                null,
                null,
                new KeystoreConfig(fixture("client.pem"), fixture("client-encrypted.key"), "wrong-password"));
        assertThatThrownBy(() -> SslContextFactory.create(ssl, null))
                .isInstanceOf(SslConfigurationException.class)
                .hasMessageContaining("password");
    }

    @Test
    void encryptedKeyWithoutPasswordThrows() throws Exception {
        SslConfig ssl = new SslConfig(
                null, null, new KeystoreConfig(fixture("client.pem"), fixture("client-encrypted.key"), null));
        assertThatThrownBy(() -> SslContextFactory.create(ssl, null))
                .isInstanceOf(SslConfigurationException.class)
                .hasMessageContaining("encrypted");
    }

    @Test
    void legacyPkcs1KeyThrowsWithActionableMessage() throws Exception {
        SslConfig ssl =
                new SslConfig(null, null, new KeystoreConfig(fixture("client.pem"), fixture("client-pkcs1.key"), null));
        assertThatThrownBy(() -> SslContextFactory.create(ssl, null))
                .isInstanceOf(SslConfigurationException.class)
                .hasMessageContaining("PKCS#8");
    }

    @Test
    void missingCertificateFileThrows() {
        SslConfig ssl = new SslConfig(null, new TruststoreConfig("/no/such/ca.pem"), null);
        assertThatThrownBy(() -> SslContextFactory.create(ssl, null)).isInstanceOf(SslConfigurationException.class);
    }

    @Test
    void blankTruststoreCertificateThrows() {
        SslConfig ssl = new SslConfig(null, new TruststoreConfig("  "), null);
        assertThatThrownBy(() -> SslContextFactory.create(ssl, null))
                .isInstanceOf(SslConfigurationException.class)
                .hasMessageContaining("ssl.truststore.certificate is required");
    }

    @Test
    void missingKeystoreCertificatePropertyThrows() {
        SslConfig ssl = new SslConfig(null, null, new KeystoreConfig(null, null, null));
        assertThatThrownBy(() -> SslContextFactory.create(ssl, null))
                .isInstanceOf(SslConfigurationException.class)
                .hasMessageContaining("ssl.keystore.certificate is required");
    }

    @Test
    void nonCertificateTruststoreFileThrows() throws Exception {
        // A private-key file contains no CERTIFICATE block, so no certificates are found.
        SslConfig ssl = new SslConfig(null, new TruststoreConfig(fixture("client.key")), null);
        assertThatThrownBy(() -> SslContextFactory.create(ssl, null)).isInstanceOf(SslConfigurationException.class);
    }

    @Test
    void privateKeyFileWithNonKeyPemTypeThrows() throws Exception {
        // ca.pem is a CERTIFICATE PEM block, which is not a valid private-key type.
        SslConfig ssl = new SslConfig(null, null, new KeystoreConfig(fixture("client.pem"), fixture("ca.pem"), null));
        assertThatThrownBy(() -> SslContextFactory.create(ssl, null))
                .isInstanceOf(SslConfigurationException.class)
                .hasMessageContaining("Unsupported private key PEM type");
    }

    @Test
    void privateKeyFileWithoutPemBlockThrows() throws Exception {
        SslConfig ssl =
                new SslConfig(null, null, new KeystoreConfig(fixture("client.pem"), fixture("not-a-pem.txt"), null));
        assertThatThrownBy(() -> SslContextFactory.create(ssl, null))
                .isInstanceOf(SslConfigurationException.class)
                .hasMessageContaining("does not contain a PEM block");
    }

    @Test
    void trustAllManagerAcceptsEverythingAndVerifiesNoHostname() throws Exception {
        X509ExtendedTrustManager tm = SslContextFactory.trustAllManager();
        // None of these no-op checks throw, and there are no accepted issuers.
        tm.checkClientTrusted(null, "RSA");
        tm.checkServerTrusted(null, "RSA");
        tm.checkClientTrusted(null, "RSA", (java.net.Socket) null);
        tm.checkServerTrusted(null, "RSA", (java.net.Socket) null);
        tm.checkClientTrusted(null, "RSA", (javax.net.ssl.SSLEngine) null);
        tm.checkServerTrusted(null, "RSA", (javax.net.ssl.SSLEngine) null);
        assertThat(tm.getAcceptedIssuers()).isEmpty();
    }

    @Test
    void resolveReturnsAbsolutePathUnchanged() {
        Path absolute = Path.of("/etc/ssl/ca.pem");
        assertThat(SslContextFactory.resolve(Path.of("/some/suite/dir"), absolute.toString()))
                .isEqualTo(absolute);
    }

    @Test
    void resolveJoinsRelativePathToSuiteDir() {
        Path resolved = SslContextFactory.resolve(Path.of("/some/suite/dir"), "certs/ca.pem");
        assertThat(resolved).isEqualTo(Path.of("/some/suite/dir/certs/ca.pem"));
    }

    @Test
    void resolveUsesRelativePathWhenSuiteDirNull() {
        assertThat(SslContextFactory.resolve(null, "certs/ca.pem")).isEqualTo(Path.of("certs/ca.pem"));
    }
}
