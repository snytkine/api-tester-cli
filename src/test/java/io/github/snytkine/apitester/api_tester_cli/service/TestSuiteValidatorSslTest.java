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

import io.github.snytkine.apitester.api_tester_cli.model.BodylessRequest;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.KeystoreConfig;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SslConfig;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.TruststoreConfig;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TestSuiteValidator#validateSsl(TestSuite)}, verifying the fail-fast rules for
 * a rest-client's custom {@code ssl} block: required/existing/readable certificate and key files, the
 * password-requires-private-key rule, skip-validation short-circuiting, and deeper parse/password
 * errors surfaced by building the SSL context.
 */
class TestSuiteValidatorSslTest {

    private static final String ENCRYPTED_KEY_PASSWORD = "changeit";

    private final TestSuiteValidator validator = new TestSuiteValidator();

    /**
     * Returns the absolute path string of an SSL fixture file.
     *
     * @param name the fixture file name
     * @return the absolute path string
     * @throws URISyntaxException if the resource URL is malformed
     */
    private static String fixture(String name) throws URISyntaxException {
        return Path.of(TestSuiteValidatorSslTest.class.getResource("/ssl").toURI())
                .resolve(name)
                .toString();
    }

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

    private static TestSuite suiteWithSsl(SslConfig ssl) {
        RestClientConfig client = new RestClientConfig(null, "https://api.example.com", 30000, null, null, ssl);
        return new TestSuite("suite", null, client, null, null, List.of(tc()), null, null);
    }

    @Test
    void noErrorsWhenNoSslConfigured() {
        assertThat(validator.validateSsl(suiteWithSsl(null))).isEmpty();
    }

    @Test
    void noErrorsForSkipValidation() {
        assertThat(validator.validateSsl(suiteWithSsl(new SslConfig(true, null, null))))
                .isEmpty();
    }

    @Test
    void skipValidationIgnoresMissingTruststoreFile() {
        SslConfig ssl = new SslConfig(true, new TruststoreConfig("/no/such/ca.pem"), null);
        assertThat(validator.validateSsl(suiteWithSsl(ssl))).isEmpty();
    }

    @Test
    void noErrorsForValidTruststore() throws Exception {
        SslConfig ssl = new SslConfig(null, new TruststoreConfig(fixture("ca.pem")), null);
        assertThat(validator.validateSsl(suiteWithSsl(ssl))).isEmpty();
    }

    @Test
    void reportsMissingTruststoreFile() {
        SslConfig ssl = new SslConfig(null, new TruststoreConfig("/no/such/ca.pem"), null);
        List<String> errors = validator.validateSsl(suiteWithSsl(ssl));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("ssl.truststore.certificate").contains("does not exist");
    }

    @Test
    void reportsMissingTruststoreCertificateProperty() {
        SslConfig ssl = new SslConfig(null, new TruststoreConfig(null), null);
        List<String> errors = validator.validateSsl(suiteWithSsl(ssl));
        assertThat(errors).anyMatch(e -> e.contains("ssl.truststore.certificate") && e.contains("is required"));
    }

    @Test
    void noErrorsForValidKeystoreWithUnencryptedKey() throws Exception {
        SslConfig ssl =
                new SslConfig(null, null, new KeystoreConfig(fixture("client.pem"), fixture("client.key"), null));
        assertThat(validator.validateSsl(suiteWithSsl(ssl))).isEmpty();
    }

    @Test
    void noErrorsForValidKeystoreWithEncryptedKey() throws Exception {
        SslConfig ssl = new SslConfig(
                null,
                null,
                new KeystoreConfig(fixture("client.pem"), fixture("client-encrypted.key"), ENCRYPTED_KEY_PASSWORD));
        assertThat(validator.validateSsl(suiteWithSsl(ssl))).isEmpty();
    }

    @Test
    void reportsPasswordWithoutPrivateKey() throws Exception {
        SslConfig ssl = new SslConfig(null, null, new KeystoreConfig(fixture("client.pem"), null, "secret"));
        List<String> errors = validator.validateSsl(suiteWithSsl(ssl));
        assertThat(errors).anyMatch(e -> e.contains("ssl.keystore.password") && e.contains("private-key"));
    }

    @Test
    void reportsMissingKeystoreCertificateFile() throws Exception {
        SslConfig ssl =
                new SslConfig(null, null, new KeystoreConfig("/no/such/client.pem", fixture("client.key"), null));
        List<String> errors = validator.validateSsl(suiteWithSsl(ssl));
        assertThat(errors).anyMatch(e -> e.contains("ssl.keystore.certificate") && e.contains("does not exist"));
    }

    @Test
    void reportsWrongEncryptedKeyPassword() throws Exception {
        SslConfig ssl = new SslConfig(
                null, null, new KeystoreConfig(fixture("client.pem"), fixture("client-encrypted.key"), "wrong"));
        List<String> errors = validator.validateSsl(suiteWithSsl(ssl));
        assertThat(errors).anyMatch(e -> e.contains("password"));
    }

    @Test
    void reportsUnsupportedPkcs1Key() throws Exception {
        SslConfig ssl =
                new SslConfig(null, null, new KeystoreConfig(fixture("client.pem"), fixture("client-pkcs1.key"), null));
        List<String> errors = validator.validateSsl(suiteWithSsl(ssl));
        assertThat(errors).anyMatch(e -> e.contains("PKCS#8"));
    }
}
