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

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import org.jspecify.annotations.Nullable;

/**
 * Supplies proxy credentials to {@link java.net.http.HttpClient} in response to a {@code 407 Proxy
 * Authentication Required} challenge.
 *
 * <p><b>Only proxy challenges are answered.</b> {@link #getPasswordAuthentication()} returns {@code
 * null} for {@link RequestorType#SERVER}, so a proxy password is never offered to the endpoint
 * under test. Endpoint authentication travels a completely separate path — an {@code Authorization}
 * header applied by the rest-client builder — which lets a suite authenticate to a proxy with one
 * credential and to the API with a different one, the two never crossing.
 *
 * <p>When the {@code http} and {@code https} proxies are different servers, credentials are matched
 * by the challenging proxy's host and port, so each proxy receives only its own.
 *
 * <p>For {@code https://} endpoints the challenge arrives on the {@code CONNECT} request that opens
 * the tunnel. The JDK refuses to answer such a challenge with {@code Basic} unless {@code
 * jdk.http.auth.tunneling.disabledSchemes} has been cleared — see {@link ProxyTunnelingSupport},
 * which must run before any JDK HTTP class is initialized.
 *
 * <p>This class holds only an immutable {@link ProxySettings} reference and is therefore
 * thread-safe; the JDK may invoke it concurrently from multiple request threads.
 */
public final class ProxyAuthenticator extends Authenticator {

    private final ProxySettings settings;

    /**
     * Creates an authenticator serving credentials from the given resolved proxy settings.
     *
     * @param settings the resolved proxy settings; must not be {@code null}
     */
    public ProxyAuthenticator(ProxySettings settings) {
        this.settings = settings;
    }

    /**
     * Returns the credentials for the challenging proxy, or {@code null} when the challenge is not
     * from a proxy, the challenging proxy is not one of ours, or it has no configured credentials.
     *
     * @return the proxy credentials to send, or {@code null} to leave the challenge unanswered
     */
    @Override
    protected @Nullable PasswordAuthentication getPasswordAuthentication() {
        if (getRequestorType() != RequestorType.PROXY) {
            // An endpoint (server) challenge. Proxy credentials must never be offered here.
            return null;
        }
        ProxyEndpoint endpoint = settings.forProxyAddress(getRequestingHost(), getRequestingPort());
        if (endpoint == null || !endpoint.hasCredentials()) {
            return null;
        }
        String password = endpoint.password();
        return new PasswordAuthentication(endpoint.username(), password == null ? new char[0] : password.toCharArray());
    }
}
