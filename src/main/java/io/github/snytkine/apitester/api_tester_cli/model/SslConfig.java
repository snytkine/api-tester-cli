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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Custom SSL/TLS settings for a single rest-client, declared under the {@code ssl} key of a {@code
 * rest-client} (or a {@code rest-clients} entry) in a test-suite YAML.
 *
 * <p>Three, mutually-related capabilities are supported:
 *
 * <ul>
 *   <li>{@code skip-certificate-validation} — disable certificate <em>and</em> hostname verification
 *       entirely, so requests to endpoints with self-signed or otherwise untrusted certificates
 *       succeed. When enabled, {@link #truststore()} and {@link #keystore()} are ignored.
 *   <li>{@code truststore} — trust a custom certificate (self-signed server cert or private CA) in
 *       addition to the JVM's default trust anchors.
 *   <li>{@code keystore} — present a client certificate and private key for mutual-TLS (mTLS)
 *       authentication.
 * </ul>
 *
 * <p>Instances carry only the raw YAML values; file paths are resolved and validated by {@code
 * TestSuiteValidator} and turned into an {@link javax.net.ssl.SSLContext} by {@code
 * SslContextFactory}. This record is immutable and therefore thread-safe.
 */
public record SslConfig(
        /**
         * When {@code true}, the rest-client is built with certificate and hostname verification
         * disabled and {@link #truststore()}/{@link #keystore()} are ignored. {@code null} is treated
         * as {@code false}.
         */
        @JsonProperty("skip-certificate-validation") @Nullable Boolean skipCertificateValidation,

        /** Optional custom trust material (a certificate to trust). {@code null} when absent. */
        @JsonProperty("truststore") @Nullable TruststoreConfig truststore,

        /** Optional client identity material for mTLS. {@code null} when absent. */
        @JsonProperty("keystore") @Nullable KeystoreConfig keystore) {

    /**
     * Returns whether certificate and hostname validation should be skipped, treating a {@code null}
     * {@link #skipCertificateValidation()} as {@code false}.
     *
     * @return {@code true} only when {@code skip-certificate-validation} is explicitly {@code true}
     */
    public boolean skip() {
        return Boolean.TRUE.equals(skipCertificateValidation);
    }

    /**
     * Returns whether this configuration requires any customization of the underlying SSL context.
     *
     * <p>It does when validation is skipped, or when a truststore or keystore is present. When it
     * returns {@code false}, the rest-client can use the JVM's default TLS behavior unchanged.
     *
     * @return {@code true} when a custom {@link javax.net.ssl.SSLContext} must be built
     */
    public boolean requiresCustomContext() {
        return skip() || truststore != null || keystore != null;
    }
}
