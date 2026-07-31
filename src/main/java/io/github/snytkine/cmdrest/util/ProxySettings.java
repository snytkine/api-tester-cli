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

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The fully resolved proxy configuration for one rest-client: which proxy (if any) to use for
 * {@code http://} targets and which for {@code https://} targets, together with where the settings
 * came from.
 *
 * <p>Two endpoints are modelled rather than one because the {@code HTTP_PROXY} and {@code
 * HTTPS_PROXY} environment variables may name different servers, and either may be set without the
 * other. A proxy declared in YAML applies to both schemes, so both fields then reference the same
 * endpoint.
 *
 * <p>Note that {@code HTTPS_PROXY} selects the proxy used for <em>https targets</em>; the
 * connection to that proxy is still plaintext, and the endpoint's TLS is tunneled through it with
 * {@code CONNECT}. See {@link ProxyEndpoint}.
 *
 * <p>This record is immutable, and the {@link ProxySelector} and {@link ProxyAuthenticator} it
 * creates read only immutable state, so instances are safe to share across threads. In practice
 * each is confined to the {@code HttpClient} built from it.
 *
 * @param httpProxy proxy for {@code http://} targets, or {@code null} to connect directly
 * @param httpsProxy proxy for {@code https://} targets, or {@code null} to connect directly
 * @param source where these settings were resolved from, for logging and diagnostics
 */
public record ProxySettings(
        @Nullable ProxyEndpoint httpProxy, @Nullable ProxyEndpoint httpsProxy, ProxySettings.Source source) {

    /** Origin of a resolved proxy configuration. */
    public enum Source {
        /** Declared in the test-suite YAML under a rest-client's {@code proxy} key. */
        YAML("yaml"),
        /** Taken from the {@code HTTP_PROXY} / {@code HTTPS_PROXY} environment variables. */
        ENVIRONMENT("env");

        private final String label;

        Source(String label) {
            this.label = label;
        }

        /**
         * Returns the short label used in log messages.
         *
         * @return {@code "yaml"} or {@code "env"}
         */
        public String label() {
            return label;
        }
    }

    /**
     * Returns whether these settings route anything at all through a proxy.
     *
     * @return {@code true} when at least one scheme has a proxy endpoint
     */
    public boolean isEmpty() {
        return httpProxy == null && httpsProxy == null;
    }

    /**
     * Returns whether any configured endpoint requires authentication.
     *
     * @return {@code true} when at least one endpoint carries credentials
     */
    public boolean requiresAuthentication() {
        return (httpProxy != null && httpProxy.hasCredentials()) || (httpsProxy != null && httpsProxy.hasCredentials());
    }

    /**
     * Returns the endpoint that applies to the given target URI scheme.
     *
     * @param scheme the target URI's scheme; may be {@code null}
     * @return the matching endpoint, or {@code null} when that scheme should connect directly
     */
    public @Nullable ProxyEndpoint forScheme(@Nullable String scheme) {
        return "https".equalsIgnoreCase(scheme) ? httpsProxy : httpProxy;
    }

    /**
     * Returns the endpoint matching a proxy host/port pair, used to pick credentials when answering
     * a {@code 407} challenge.
     *
     * @param host the challenging proxy's host
     * @param port the challenging proxy's port
     * @return the matching endpoint, or {@code null} when neither endpoint matches
     */
    public @Nullable ProxyEndpoint forProxyAddress(@Nullable String host, int port) {
        if (httpProxy != null && httpProxy.matches(host, port)) {
            return httpProxy;
        }
        if (httpsProxy != null && httpsProxy.matches(host, port)) {
            return httpsProxy;
        }
        return null;
    }

    /**
     * Builds a {@link ProxySelector} that routes each request according to its target scheme.
     *
     * <p>Requests whose scheme has no configured endpoint are returned {@link Proxy#NO_PROXY} and
     * connect directly.
     *
     * @return a scheme-aware selector over these settings
     */
    public ProxySelector toProxySelector() {
        return new SchemeAwareProxySelector(this);
    }

    /**
     * Returns a log-safe, single-line description of these settings, naming each endpoint and the
     * source, and never including credentials.
     *
     * @return a description suitable for an INFO log line
     */
    public String describe() {
        if (isEmpty()) {
            return "no proxy";
        }
        if (httpProxy != null && httpProxy.equals(httpsProxy)) {
            return httpProxy + " [source=" + source.label() + "]";
        }
        StringBuilder sb = new StringBuilder();
        if (httpProxy != null) {
            sb.append("http -> ").append(httpProxy);
        }
        if (httpsProxy != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("https -> ").append(httpsProxy);
        }
        return sb.append(" [source=").append(source.label()).append(']').toString();
    }

    /**
     * A {@link ProxySelector} that chooses between the {@code http} and {@code https} endpoints of
     * enclosing {@link ProxySettings} based on the scheme of the URI being requested.
     *
     * <p>Immutable and therefore thread-safe; the JDK calls {@link #select(URI)} concurrently from
     * whichever threads issue requests.
     */
    private static final class SchemeAwareProxySelector extends ProxySelector {

        private final ProxySettings settings;

        /**
         * Creates a selector over the given resolved settings.
         *
         * @param settings the resolved proxy settings; must not be {@code null}
         */
        private SchemeAwareProxySelector(ProxySettings settings) {
            this.settings = settings;
        }

        /**
         * Returns the proxy to use for {@code uri}, or {@link Proxy#NO_PROXY} when its scheme has
         * no configured proxy.
         *
         * @param uri the target URI
         * @return a single-element list holding the chosen proxy
         */
        @Override
        public List<Proxy> select(URI uri) {
            ProxyEndpoint endpoint = settings.forScheme(uri == null ? null : uri.getScheme());
            return endpoint == null ? List.of(Proxy.NO_PROXY) : List.of(endpoint.toProxy());
        }

        /**
         * Called by the JDK when a connection to a selected proxy fails.
         *
         * <p>Intentionally a no-op: there is no alternative proxy to fall back to, and the failure
         * already propagates to the caller as an {@link java.io.IOException}, where {@code
         * ProxyErrorClassifier} turns it into a proxy-specific test failure.
         *
         * @param uri the URI that could not be reached
         * @param sa the address of the proxy that failed
         * @param ioe the failure
         */
        @Override
        public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {
            // No fallback proxy exists; the exception is classified further up the stack.
        }
    }
}
