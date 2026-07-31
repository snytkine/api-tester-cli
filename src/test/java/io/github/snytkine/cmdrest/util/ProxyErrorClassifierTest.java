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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/**
 * Tests for {@link ProxyErrorClassifier}.
 *
 * <p>The classifier's job is to stop a proxy problem from being reported as an endpoint problem, so
 * the assertions here check that the proxy's host and port appear in the message and that
 * unrecognised failures are left alone rather than guessed at.
 */
class ProxyErrorClassifierTest {

    private static final ProxyEndpoint PLAIN = new ProxyEndpoint("proxy.example.com", 8080, null, null);
    private static final ProxyEndpoint AUTHENTICATED = new ProxyEndpoint("proxy.example.com", 8080, "user", "pass");

    private static ProxySettings settings(ProxyEndpoint endpoint) {
        return new ProxySettings(endpoint, endpoint, ProxySettings.Source.YAML);
    }

    /**
     * A refused connection carries no message at all on some JDKs, so the exception type name has
     * to be enough — this is the case that first exposed the gap.
     */
    @Test
    void connectExceptionWithoutAMessageIsStillRecognised() {
        Throwable failure = new ResourceAccessException("I/O error", new ConnectException());

        String message = ProxyErrorClassifier.classify(failure, settings(PLAIN), "http");

        assertThat(message).contains("could not connect to the proxy at proxy.example.com:8080");
    }

    @Test
    void connectionRefusedNamesTheProxyAndNotTheEndpoint() {
        Throwable failure = new ResourceAccessException("I/O error", new ConnectException("Connection refused"));

        String message = ProxyErrorClassifier.classify(failure, settings(PLAIN), "https");

        assertThat(message)
                .contains("could not connect to the proxy at proxy.example.com:8080")
                .contains("endpoint itself was never contacted");
    }

    /**
     * The layers above paste the endpoint URL into their messages, so the chain text routinely
     * carries a random ephemeral port. A port that merely contains the digits {@code 407} must not
     * be read as a {@code 407} status — that turned a refused connection into "the proxy requires
     * authentication" and made CI fail roughly one run in two hundred.
     */
    @Test
    void anEphemeralPortContaining407IsNotAProxyAuthChallenge() {
        Throwable failure = new ResourceAccessException(
                "I/O error on GET request for \"http://127.0.0.1:34071/api\"", new ConnectException());

        String message = ProxyErrorClassifier.classify(failure, settings(PLAIN), "http");

        assertThat(message).contains("could not connect to the proxy at proxy.example.com:8080");
    }

    /** The same guard, for a 407 buried in the middle of a longer number rather than at its start. */
    @Test
    void a407InsideALongerNumberIsNotAProxyAuthChallenge() {
        Throwable failure = new ResourceAccessException(
                "I/O error on GET request for \"http://127.0.0.1:14079/api\"",
                new ConnectException("Connection refused"));

        assertThat(ProxyErrorClassifier.classify(failure, settings(PLAIN), "http"))
                .contains("could not connect to the proxy");
    }

    /** A genuine 407 status line is still recognised, which is what the narrower match must preserve. */
    @Test
    void a407StatusLineIsStillRecognisedAsAnAuthChallenge() {
        Throwable failure = new ResourceAccessException(
                "I/O error",
                new IOException("Unable to tunnel through proxy. Proxy returns \"HTTP/1.1 407 Proxy"
                        + " Authentication Required\""));

        assertThat(ProxyErrorClassifier.classify(failure, settings(PLAIN), "https"))
                .contains("requires authentication but no proxy credentials are configured");
    }

    @Test
    void unknownHostIsRecognised() {
        Throwable failure = new ResourceAccessException("I/O error", new UnknownHostException("proxy.example.com"));

        assertThat(ProxyErrorClassifier.classify(failure, settings(PLAIN), "http"))
                .contains("could not connect to the proxy");
    }

    @Test
    void tunnelRefusalIsRecognised() {
        Throwable failure = new ResourceAccessException("I/O error", new IOException("Unable to tunnel through proxy"));

        assertThat(ProxyErrorClassifier.classify(failure, settings(PLAIN), "https"))
                .contains("refused to open a tunnel");
    }

    @Test
    void unrecognisedFailureFallsThroughToGenericHandling() {
        Throwable failure = new ResourceAccessException("I/O error", new IOException("something else entirely"));

        assertThat(ProxyErrorClassifier.classify(failure, settings(PLAIN), "http"))
                .isNull();
    }

    @Test
    void unproxiedClientIsNeverClassified() {
        Throwable failure = new ResourceAccessException("I/O error", new ConnectException("Connection refused"));

        assertThat(ProxyErrorClassifier.classify(failure, null, "http")).isNull();
    }

    /** A scheme with no configured proxy connects directly, so its failures are not the proxy's. */
    @Test
    void schemeWithoutAProxyIsNotClassified() {
        ProxySettings httpsOnly = new ProxySettings(null, PLAIN, ProxySettings.Source.ENVIRONMENT);
        Throwable failure = new ResourceAccessException("I/O error", new ConnectException("Connection refused"));

        assertThat(ProxyErrorClassifier.classify(failure, httpsOnly, "http")).isNull();
    }

    @Test
    void missingCredentialsAreExplainedOn407Response() {
        String message = ProxyErrorClassifier.classifyProxyAuthResponse(settings(PLAIN), "https", "Basic realm=\"x\"");

        assertThat(message)
                .contains("requires authentication but no proxy credentials are configured")
                .contains("proxy.example.com:8080");
    }

    @Test
    void rejectedCredentialsAreDistinguishedFromMissingOnes() {
        String message =
                ProxyErrorClassifier.classifyProxyAuthResponse(settings(AUTHENTICATED), "https", "Basic realm=\"x\"");

        assertThat(message).contains("rejected the configured proxy credentials");
    }

    @Test
    void nonBasicChallengeNamesTheUnsupportedScheme() {
        String message = ProxyErrorClassifier.classifyProxyAuthResponse(settings(AUTHENTICATED), "https", "NTLM");

        assertThat(message).contains("NTLM").contains("not supported").contains("Only 'Basic'");
    }

    @Test
    void negotiateChallengeIsRecognised() {
        assertThat(ProxyErrorClassifier.classifyProxyAuthResponse(settings(AUTHENTICATED), "https", "Negotiate"))
                .contains("Negotiate");
    }

    @Test
    void authResponseWithoutProxySettingsIsNotClassified() {
        assertThat(ProxyErrorClassifier.classifyProxyAuthResponse(null, "https", "Basic"))
                .isNull();
    }

    /** No {@code Proxy-Authenticate} header still yields the missing/rejected credential message. */
    @Test
    void missingChallengeHeaderStillProducesAMessage() {
        assertThat(ProxyErrorClassifier.classifyProxyAuthResponse(settings(PLAIN), "http", null))
                .contains("requires authentication");
    }

    @Test
    void nullFailureIsNotClassified() {
        assertThat(ProxyErrorClassifier.classify(null, settings(PLAIN), "http")).isNull();
    }
}
