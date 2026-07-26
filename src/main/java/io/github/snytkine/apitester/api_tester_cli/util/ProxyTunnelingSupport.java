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

/**
 * Enables {@code Basic} proxy authentication over {@code CONNECT} tunnels by clearing the JDK's
 * {@code jdk.http.auth.tunneling.disabledSchemes} system property.
 *
 * <p>Why this is necessary: for an {@code https://} endpoint reached through a proxy, the proxy
 * authenticates the {@code CONNECT} request that establishes the tunnel, not the request sent
 * inside it. The JDK ships with {@code Basic} listed in {@code
 * jdk.http.auth.tunneling.disabledSchemes}, which causes {@link java.net.http.HttpClient} to
 * silently decline to answer a {@code 407} challenge on that {@code CONNECT} — the request simply
 * fails, with no indication that credentials were withheld. Clearing the property restores the
 * behaviour a test tool needs. Plaintext {@code http://} targets are unaffected either way, because
 * they are proxied by forwarding rather than tunneling.
 *
 * <p><b>Ordering matters.</b> The JDK reads this property while statically initializing its HTTP
 * internals, so {@link #enableBasicAuthenticationOverConnect()} must run before any JDK HTTP class
 * is touched. It is called as the first statement of the application's {@code main} method, ahead
 * of the Spring context. Tests cannot rely on {@code main} having run, so the Surefire
 * configuration in {@code pom.xml} sets the same property for the test JVM.
 *
 * <p>An explicit value supplied on the command line is honoured: if the property is already set,
 * this class leaves it alone, so an operator can restore the JDK default or pick a different scheme
 * list with {@code -Djdk.http.auth.tunneling.disabledSchemes=Basic}.
 *
 * <p>This class is a stateless utility with only static methods and is thread-safe. In practice it
 * is called once, from a single thread, before the application starts.
 */
public final class ProxyTunnelingSupport {

    /** JDK system property listing authentication schemes disabled for {@code CONNECT} tunnels. */
    public static final String TUNNELING_DISABLED_SCHEMES = "jdk.http.auth.tunneling.disabledSchemes";

    /** Utility class; not instantiable. */
    private ProxyTunnelingSupport() {}

    /**
     * Clears {@link #TUNNELING_DISABLED_SCHEMES} unless it was already set explicitly.
     *
     * <p>Must be called before any {@link java.net.http.HttpClient} class is loaded; see the class
     * JavaDoc.
     */
    public static void enableBasicAuthenticationOverConnect() {
        if (System.getProperty(TUNNELING_DISABLED_SCHEMES) == null) {
            System.setProperty(TUNNELING_DISABLED_SCHEMES, "");
        }
    }
}
