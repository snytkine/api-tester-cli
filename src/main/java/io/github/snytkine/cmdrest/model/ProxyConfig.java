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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.jspecify.annotations.Nullable;

/**
 * HTTP proxy settings for a single rest-client, declared under the {@code proxy} key of a {@code
 * rest-client} (or a {@code rest-clients} entry) in a test-suite YAML.
 *
 * <p>The {@code proxy} key is polymorphic and this record therefore represents three distinct
 * states, which callers must not conflate:
 *
 * <ul>
 *   <li><b>Absent</b> — the {@code proxy} key is missing entirely, so the {@code RestClientConfig}
 *       holds {@code null}. An {@code HTTP_PROXY} / {@code HTTPS_PROXY} environment proxy applies
 *       to this client automatically.
 *   <li><b>Disabled</b> — {@code proxy: false}, represented by {@link #DISABLED}. The client never
 *       uses a proxy, regardless of any environment variable. This is an absolute opt-out.
 *   <li><b>Configured</b> — an object with a {@code url} and optional credentials.
 * </ul>
 *
 * <p>A fourth, transient state exists only between parsing and validation: when the YAML supplies a
 * scalar that is neither {@code false} nor an object (most importantly {@code proxy: true}), {@link
 * ProxyConfigDeserializer} records the offending literal in {@link #invalidValue()} rather than
 * throwing, so {@code TestSuiteValidator} can report it alongside every other suite error instead
 * of aborting the load with a stack trace.
 *
 * <p>There is deliberately no {@code skip-certificate-validation} key here. {@link
 * java.net.http.HttpClient} never negotiates TLS with the proxy itself — it opens a plaintext
 * connection and {@code CONNECT}-tunnels the endpoint's TLS through it — so there is no proxy
 * certificate to validate. Certificate validation for the endpoint is governed by {@link SslConfig}
 * and is unaffected by proxying.
 *
 * <p>This record is immutable and therefore thread-safe.
 */
@JsonDeserialize(using = ProxyConfigDeserializer.class)
public record ProxyConfig(
        /**
         * Proxy URL in the form {@code http://host[:port]}. The scheme, when present, must be {@code
         * http}; port defaults to {@value #DEFAULT_PROXY_PORT}. {@code null} when this config is
         * {@link #DISABLED} or carries an {@link #invalidValue()}.
         */
        @JsonProperty("url") @Nullable String url,

        /** Optional proxy username. {@code null} when the proxy needs no authentication. */
        @JsonProperty("username") @Nullable String username,

        /** Optional proxy password. Only permitted when {@link #username()} is also present. */
        @JsonProperty("password") @Nullable String password,

        /**
         * {@code true} when the YAML declared {@code proxy: false}, meaning this rest-client must
         * never use a proxy even when {@code HTTP_PROXY} / {@code HTTPS_PROXY} are set.
         */
        boolean disabled,

        /**
         * The offending literal when the YAML supplied an unusable scalar for {@code proxy} (e.g.
         * {@code true}), otherwise {@code null}. Carried through to validation so the error can be
         * reported with the value the user actually wrote.
         */
        @Nullable String invalidValue) {

    /** Port assumed when a proxy URL omits one. */
    public static final int DEFAULT_PROXY_PORT = 80;

    /** Singleton representing {@code proxy: false} — proxying disabled for this rest-client. */
    public static final ProxyConfig DISABLED = new ProxyConfig(null, null, null, true, null);

    /**
     * Convenience constructor for a configured proxy, delegating to the canonical constructor with
     * {@code disabled = false} and no {@code invalidValue}.
     *
     * @param url proxy URL in the form {@code http://host[:port]}
     * @param username optional proxy username
     * @param password optional proxy password
     */
    public ProxyConfig(@Nullable String url, @Nullable String username, @Nullable String password) {
        this(url, username, password, false, null);
    }

    /**
     * Creates a config marking an unusable {@code proxy} scalar for later reporting by the
     * validator.
     *
     * @param literal the offending YAML literal, rendered as text
     * @return a config whose {@link #invalidValue()} is {@code literal}
     */
    public static ProxyConfig invalid(String literal) {
        return new ProxyConfig(null, null, null, false, literal);
    }

    /**
     * Returns whether this config is the {@code proxy: false} opt-out.
     *
     * @return {@code true} when proxying is explicitly disabled for the owning rest-client
     */
    public boolean isDisabled() {
        return disabled;
    }

    /**
     * Returns whether proxy credentials were supplied.
     *
     * <p>A blank username counts as absent, so a suite that templates {@code username:
     * [[${env.PROXY_USER}]]} against an unset variable behaves as an unauthenticated proxy rather
     * than sending empty credentials.
     *
     * @return {@code true} when a non-blank {@link #username()} is present
     */
    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }
}
