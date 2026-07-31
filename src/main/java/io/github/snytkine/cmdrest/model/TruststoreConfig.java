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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Custom trust material for a rest-client, declared under {@code ssl.truststore} in a test-suite
 * YAML.
 *
 * <p>Adds a certificate (typically a self-signed server certificate or a private CA certificate) to
 * the trust anchors used when the client validates the server's TLS certificate. This makes it
 * possible to test HTTPS endpoints whose certificate chain does not chain up to a certificate
 * authority in the JVM's default trust store.
 *
 * <p>This record carries only the raw YAML value. The referenced file is resolved (absolute, or
 * relative to the suite file's directory) and validated for existence/readability by {@code
 * TestSuiteValidator}, and loaded into an {@link javax.net.ssl.SSLContext} by {@code
 * SslContextFactory}. Instances are immutable and therefore thread-safe.
 */
public record TruststoreConfig(
        /**
         * Path to a PEM-encoded certificate file (typically {@code .pem}). May be absolute or relative
         * to the test-suite file's directory. Required within a {@code truststore} block; {@code null}
         * only when the YAML omits it (rejected by validation).
         */
        @JsonProperty("certificate") @Nullable String certificate) {}
