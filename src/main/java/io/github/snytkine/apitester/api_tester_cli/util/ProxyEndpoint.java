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

import java.net.InetSocketAddress;
import java.net.Proxy;
import org.jspecify.annotations.Nullable;

/**
 * A single resolved proxy server: the host and port to connect through, plus the optional
 * credentials to authenticate with.
 *
 * <p>This is the runtime counterpart of {@link
 * io.github.snytkine.apitester.api_tester_cli.model.ProxyConfig}: the YAML/environment forms have
 * been parsed, the port defaulted, and credentials extracted from either explicit fields or the
 * URL's userinfo. It always describes a plaintext HTTP proxy, because {@link
 * java.net.http.HttpClient} supports no other kind.
 *
 * <p>{@link #toString()} is overridden to redact the password, so an endpoint can be interpolated
 * into a log line or exception message without leaking credentials. Use {@link #password()} only
 * where the value is actually needed to authenticate.
 *
 * <p>This record is immutable and therefore thread-safe.
 *
 * @param host proxy hostname or literal IP address; never blank
 * @param port proxy port
 * @param username proxy username, or {@code null} when the proxy needs no authentication
 * @param password proxy password, or {@code null}
 */
public record ProxyEndpoint(String host, int port, @Nullable String username, @Nullable String password) {

    /**
     * Returns whether this endpoint carries credentials to authenticate with.
     *
     * @return {@code true} when a non-blank username is present
     */
    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    /**
     * Returns whether this endpoint refers to the given proxy host and port.
     *
     * <p>Used by {@link ProxyAuthenticator} to decide which credentials answer a {@code 407}
     * challenge when the {@code http} and {@code https} proxies differ. Host comparison is
     * case-insensitive, as hostnames are.
     *
     * @param otherHost the host being challenged
     * @param otherPort the port being challenged
     * @return {@code true} when both match this endpoint
     */
    public boolean matches(@Nullable String otherHost, int otherPort) {
        return otherHost != null && host.equalsIgnoreCase(otherHost) && port == otherPort;
    }

    /**
     * Returns this endpoint as a {@link Proxy} for a {@link java.net.ProxySelector}.
     *
     * <p>The address is deliberately left <em>unresolved</em> ({@link
     * InetSocketAddress#createUnresolved}) so that DNS lookup happens at connect time rather than
     * during selection. That keeps name-resolution failures inside the request, where they surface
     * as a classifiable connection error, instead of silently producing an unusable address.
     *
     * @return an HTTP-type {@link Proxy} pointing at this endpoint
     */
    public Proxy toProxy() {
        return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port));
    }

    /**
     * Returns {@code host:port}, with an authentication marker but never the credentials
     * themselves.
     *
     * @return a log-safe description of this endpoint
     */
    @Override
    public String toString() {
        return host + ":" + port + (hasCredentials() ? " (authenticated)" : "");
    }
}
