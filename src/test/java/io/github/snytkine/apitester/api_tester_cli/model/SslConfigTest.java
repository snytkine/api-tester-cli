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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SslConfig#skip()} and {@link SslConfig#requiresCustomContext()}, covering
 * how a {@code null} {@code skip-certificate-validation} flag and absent truststore/keystore blocks
 * are interpreted.
 */
class SslConfigTest {

    @Test
    void skipTreatsNullAsFalse() {
        assertThat(new SslConfig(null, null, null).skip()).isFalse();
    }

    @Test
    void skipTrueWhenExplicitlyTrue() {
        assertThat(new SslConfig(true, null, null).skip()).isTrue();
    }

    @Test
    void skipFalseWhenExplicitlyFalse() {
        assertThat(new SslConfig(false, null, null).skip()).isFalse();
    }

    @Test
    void requiresCustomContextFalseWhenEverythingAbsent() {
        assertThat(new SslConfig(null, null, null).requiresCustomContext()).isFalse();
        assertThat(new SslConfig(false, null, null).requiresCustomContext()).isFalse();
    }

    @Test
    void requiresCustomContextTrueWhenSkipEnabled() {
        assertThat(new SslConfig(true, null, null).requiresCustomContext()).isTrue();
    }

    @Test
    void requiresCustomContextTrueWhenTruststorePresent() {
        SslConfig ssl = new SslConfig(null, new TruststoreConfig("ca.pem"), null);
        assertThat(ssl.requiresCustomContext()).isTrue();
    }

    @Test
    void requiresCustomContextTrueWhenKeystorePresent() {
        SslConfig ssl = new SslConfig(null, null, new KeystoreConfig("client.pem", "client.key", null));
        assertThat(ssl.requiresCustomContext()).isTrue();
    }
}
