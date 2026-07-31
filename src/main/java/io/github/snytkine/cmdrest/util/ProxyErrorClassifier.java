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

import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Recognises transport failures that are actually proxy failures and rewrites them into messages
 * that name the proxy.
 *
 * <p>Without this, every proxy problem reaches the user as an ordinary connection error against the
 * <em>endpoint</em> URL — "connection refused" for an API that is perfectly healthy, when the real
 * cause is an unreachable proxy or a rejected credential. That is the single most confusing failure
 * mode of running behind a proxy, so the cases below are singled out and reported with the proxy's
 * host and port.
 *
 * <p>Classification is best-effort and based on the exception chain's messages, because the JDK
 * surfaces most proxy conditions as plain {@link java.io.IOException}s with no dedicated type.
 * Anything unrecognised returns {@code null} and falls through to the caller's generic handling —
 * this class never guesses.
 *
 * <p>This class is a stateless utility with only static methods and is thread-safe.
 */
public final class ProxyErrorClassifier {

    /** Utility class; not instantiable. */
    private ProxyErrorClassifier() {}

    /**
     * Returns a proxy-specific explanation for {@code failure}, or {@code null} when the failure
     * does not appear to be proxy-related.
     *
     * @param failure the transport exception thrown while sending the request
     * @param settings the proxy settings in force for the rest-client that failed, or {@code null}
     *     when the client is not proxied — in which case no classification is attempted
     * @param targetScheme the scheme of the request URL, used to name the proxy actually in use
     * @return a human-readable, credential-free message, or {@code null} to fall back to the
     *     generic transport message
     */
    public static @Nullable String classify(
            @Nullable Throwable failure, @Nullable ProxySettings settings, @Nullable String targetScheme) {
        if (failure == null || settings == null) {
            return null;
        }
        ProxyEndpoint endpoint = settings.forScheme(targetScheme);
        if (endpoint == null) {
            return null;
        }

        String text = chainText(failure);
        String proxy = endpoint.host() + ":" + endpoint.port();

        if (indicatesProxyAuthChallenge(text)) {
            return authenticationFailure(endpoint, proxy, nonBasicScheme(text));
        }

        if (text.contains("unable to tunnel") || text.contains("tunnel failed") || text.contains("cannot tunnel")) {
            return "the proxy at " + proxy + " refused to open a tunnel to the requested host."
                    + " The proxy may not permit access to this destination";
        }

        if (isConnectionFailure(text)) {
            return "could not connect to the proxy at " + proxy + " (" + firstMessage(failure) + ")."
                    + " The endpoint itself was never contacted — verify the proxy host, port, and that the proxy"
                    + " is reachable from this machine";
        }

        return null;
    }

    /**
     * Returns a proxy-specific explanation for a {@code 407} <em>response</em>.
     *
     * <p>A rejected proxy credential does not always arrive as an exception. When the proxy answers
     * the request (or the {@code CONNECT} that fronts it) with {@code 407}, the JDK hands that
     * response back to the caller like any other, so without this the test would fail on a
     * status-code assertion reading simply "expected 200 but was 407" — with nothing to indicate
     * that a proxy, rather than the service, produced it.
     *
     * @param settings the proxy settings in force, or {@code null} when the client is not proxied
     * @param targetScheme the scheme of the request URL
     * @param proxyAuthenticate the response's {@code Proxy-Authenticate} header, if any, used to
     *     name a challenge scheme the JDK cannot satisfy
     * @return a human-readable, credential-free message, or {@code null} when the response is not a
     *     proxy authentication failure this classifier can explain
     */
    public static @Nullable String classifyProxyAuthResponse(
            @Nullable ProxySettings settings, @Nullable String targetScheme, @Nullable String proxyAuthenticate) {
        if (settings == null) {
            return null;
        }
        ProxyEndpoint endpoint = settings.forScheme(targetScheme);
        if (endpoint == null) {
            return null;
        }
        String scheme = proxyAuthenticate == null ? null : nonBasicScheme(proxyAuthenticate.toLowerCase(Locale.ROOT));
        return authenticationFailure(endpoint, endpoint.host() + ":" + endpoint.port(), scheme);
    }

    /**
     * Builds the message for a proxy authentication failure, distinguishing the three cases a user
     * needs to tell apart: an unsupported scheme, missing credentials, and rejected credentials.
     *
     * @param endpoint the proxy that challenged the request
     * @param proxy the {@code host:port} label for messages
     * @param nonBasicScheme the challenged scheme when it is one the JDK cannot satisfy, else
     *     {@code null}
     * @return the failure message
     */
    private static String authenticationFailure(ProxyEndpoint endpoint, String proxy, @Nullable String nonBasicScheme) {
        if (nonBasicScheme != null) {
            return "the proxy at " + proxy + " requires '" + nonBasicScheme + "' authentication, which is not"
                    + " supported. Only 'Basic' proxy authentication is available, because the underlying JDK"
                    + " HTTP client implements no other scheme";
        }
        if (!endpoint.hasCredentials()) {
            return "the proxy at " + proxy + " requires authentication but no proxy credentials are configured."
                    + " Set 'username' and 'password' on the rest-client's proxy block, or include them in the"
                    + " HTTP_PROXY / HTTPS_PROXY URL";
        }
        return "the proxy at " + proxy + " rejected the configured proxy credentials (407 Proxy Authentication"
                + " Required)";
    }

    /**
     * Returns whether the failure text carries a {@code 407} status line, as opposed to merely
     * containing those three digits somewhere.
     *
     * <p>The haystack is not a status line — it is the whole exception chain, and the layers above
     * this one paste URLs into their messages ({@code I/O error on GET request for
     * "http://127.0.0.1:34071/api"}). A plain {@code contains("407")} therefore matched any host,
     * port, byte count or path that happened to contain those digits, and reported a healthy
     * endpoint as a proxy demanding authentication. Ephemeral ports made that intermittent: roughly
     * one CI run in two hundred drew a port such as {@code 34071} or {@code 40712} and failed.
     *
     * <p>So {@code 407} counts only as a standalone token — not adjoined to another digit, and not
     * preceded by the {@code :} or {@code .} of an address — which is how it appears in the JDK's
     * {@code Proxy returns "HTTP/1.1 407 Proxy Authentication Required"}. The reason phrase alone is
     * also accepted, since any proxy that sends it means it.
     *
     * @param text the lower-cased concatenated exception chain text
     * @return {@code true} when the text reports a 407 proxy authentication challenge
     */
    private static boolean indicatesProxyAuthChallenge(String text) {
        return text.contains("proxy authentication required")
                || STANDALONE_407.matcher(text).find();
    }

    /**
     * Matches {@code 407} as a standalone token: not part of a longer number, and not the port or
     * final octet of an address.
     */
    private static final Pattern STANDALONE_407 = Pattern.compile("(?<![\\d.:])407(?![\\d.])");

    /**
     * Returns whether the failure text looks like a failure to reach the proxy host at all, as
     * opposed to a protocol-level rejection by a proxy that did answer.
     *
     * @param text the lower-cased concatenated exception chain text
     * @return {@code true} when the text indicates a connect, DNS or timeout failure
     */
    private static boolean isConnectionFailure(String text) {
        // Exception type names are part of the haystack, which matters: a refused connection
        // carries no message at all on some JDKs, leaving the type name as the only evidence.
        return text.contains("connectexception")
                || text.contains("unknownhostexception")
                || text.contains("connecttimeoutexception")
                || text.contains("sockettimeoutexception")
                || text.contains("connection refused")
                || text.contains("connect timed out")
                || text.contains("connection timed out")
                || text.contains("network is unreachable")
                || text.contains("no route to host")
                || text.contains("nodename nor servname")
                || text.contains("name or service not known");
    }

    /**
     * Extracts the authentication scheme from a {@code 407} challenge when it is one the JDK cannot
     * satisfy, so the user is told why their credentials were never sent.
     *
     * @param text the lower-cased concatenated exception chain text
     * @return the offending scheme name in its conventional casing, or {@code null} when the
     *     challenge is Basic or the scheme could not be determined
     */
    private static @Nullable String nonBasicScheme(String text) {
        if (text.contains("ntlm")) {
            return "NTLM";
        }
        if (text.contains("negotiate")) {
            return "Negotiate";
        }
        if (text.contains("kerberos")) {
            return "Kerberos";
        }
        if (text.contains("digest")) {
            return "Digest";
        }
        return null;
    }

    /**
     * Concatenates the messages and type names of the whole exception chain, lower-cased, for
     * keyword matching.
     *
     * @param failure the outermost exception
     * @return a lower-cased haystack covering every exception in the chain
     */
    private static String chainText(Throwable failure) {
        StringBuilder sb = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            sb.append(current.getClass().getSimpleName()).append(' ');
            if (current.getMessage() != null) {
                sb.append(current.getMessage()).append(' ');
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the deepest non-blank message in the chain, or the deepest exception's simple type
     * name when the chain carries no message at all.
     *
     * @param failure the outermost exception
     * @return a short description of the underlying cause
     */
    private static String firstMessage(Throwable failure) {
        String message = null;
        Throwable deepest = failure;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            deepest = current;
            if (current.getCause() == current) {
                break;
            }
        }
        return message != null ? message : deepest.getClass().getSimpleName();
    }
}
