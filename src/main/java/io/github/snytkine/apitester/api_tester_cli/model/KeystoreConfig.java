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
 * Client identity material for mutual-TLS (mTLS), declared under {@code ssl.keystore} in a
 * test-suite YAML.
 *
 * <p>When a server requires a client certificate, this block supplies the certificate the client
 * presents during the TLS handshake, together with its private key and an optional passphrase that
 * decrypts the key file.
 *
 * <p>This record carries only the raw YAML values. Referenced files are resolved (absolute, or
 * relative to the suite file's directory) and validated for existence/readability by {@code
 * TestSuiteValidator}, and loaded into an {@link javax.net.ssl.SSLContext} by {@code
 * SslContextFactory}. Instances are immutable and therefore thread-safe.
 */
public record KeystoreConfig(
        /**
         * Path to a PEM-encoded client certificate file (typically {@code .pem}). May be absolute or
         * relative to the test-suite file's directory. Required within a {@code keystore} block;
         * {@code null} only when the YAML omits it (rejected by validation).
         */
        @JsonProperty("certificate") @Nullable String certificate,

        /**
         * Optional path to a PEM-encoded PKCS#8 private-key file (typically {@code .key}) matching the
         * client certificate. May be absolute or relative to the test-suite file's directory. Required
         * for the client to actually authenticate itself via mTLS; when {@code null} the certificate is
         * loaded as a trusted-certificate entry only.
         */
        @JsonProperty("private-key") @Nullable String privateKey,

        /**
         * Optional passphrase that decrypts an encrypted private-key file. Only permitted when {@link
         * #privateKey()} is present and non-blank (enforced by validation). Values commonly use a
         * template placeholder such as {@code [[${env.KEYSTORE_PASSWORD}]]} so the secret is not stored
         * in the suite file. {@code null} when the YAML omits it.
         */
        @JsonProperty("password") @Nullable String password) {}
