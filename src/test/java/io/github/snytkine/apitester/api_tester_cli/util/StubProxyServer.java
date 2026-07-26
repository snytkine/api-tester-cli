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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal in-process HTTP proxy used to prove that requests really do traverse a proxy.
 *
 * <p>The proxy code path lives inside {@link java.net.http.HttpClient}, below the {@code
 * ClientHttpRequestFactory} seam that the other engine tests stub out, so it cannot be observed
 * with a stub factory. This server is the smallest thing that can observe it, and it speaks both
 * shapes of proxying:
 *
 * <ul>
 *   <li><b>Forwarding</b> — for {@code http://} targets the client sends an absolute-form request
 *       line ({@code GET http://host:port/path HTTP/1.1}), which is rewritten to origin form and
 *       relayed to the real server.
 *   <li><b>Tunneling</b> — for {@code https://} targets the client sends {@code CONNECT host:port},
 *       and after a {@code 200 Connection Established} the bytes are blindly piped in both
 *       directions so the client's TLS handshake reaches the origin untouched.
 * </ul>
 *
 * <p>Optionally the proxy demands {@code Proxy-Authorization}, answering an unauthenticated request
 * with {@code 407} and a {@code Proxy-Authenticate} challenge. That is what exercises the JDK's
 * tunneled-Basic-auth behaviour, which is disabled by default and re-enabled by {@link
 * ProxyTunnelingSupport}.
 *
 * <p>Everything the test asserts on ({@link #requestTargets()}, {@link #observedProxyAuthorization()},
 * {@link #challengeCount()}) is recorded in thread-safe collections, because each connection is
 * handled on its own thread.
 */
public final class StubProxyServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final Thread acceptThread;
    private final String expectedCredential;
    private final String challengeScheme;

    private final List<String> requestTargets = new CopyOnWriteArrayList<>();
    private final List<String> observedProxyAuthorization = new CopyOnWriteArrayList<>();
    private final AtomicInteger challengeCount = new AtomicInteger();
    private volatile boolean running = true;

    /**
     * Starts a proxy on an ephemeral loopback port.
     *
     * @param username required proxy username, or {@code null} to accept unauthenticated requests
     * @param password required proxy password; ignored when {@code username} is {@code null}
     * @param challengeScheme the scheme named in the {@code Proxy-Authenticate} header, normally
     *     {@code Basic}; set to something else (e.g. {@code NTLM}) to simulate a proxy demanding an
     *     unsupported scheme
     * @throws IOException if the listening socket cannot be bound
     */
    public StubProxyServer(String username, String password, String challengeScheme) throws IOException {
        this.expectedCredential = username == null
                ? null
                : Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.challengeScheme = challengeScheme;
        this.serverSocket = new ServerSocket();
        this.serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));
        this.acceptThread = new Thread(this::acceptLoop, "stub-proxy");
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    /**
     * Starts a proxy that requires no authentication.
     *
     * @return a running proxy
     * @throws IOException if the listening socket cannot be bound
     */
    public static StubProxyServer open() throws IOException {
        return new StubProxyServer(null, null, "Basic");
    }

    /**
     * Starts a proxy that requires the given Basic credentials.
     *
     * @param username required proxy username
     * @param password required proxy password
     * @return a running proxy
     * @throws IOException if the listening socket cannot be bound
     */
    public static StubProxyServer requiringAuth(String username, String password) throws IOException {
        return new StubProxyServer(username, password, "Basic");
    }

    /**
     * Returns the port this proxy listens on.
     *
     * @return the ephemeral local port
     */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /**
     * Returns the proxy URL clients should be pointed at.
     *
     * @return a {@code http://127.0.0.1:port} URL
     */
    public String url() {
        return "http://127.0.0.1:" + port();
    }

    /**
     * Returns the request targets this proxy has seen: absolute URLs for forwarded requests and
     * {@code host:port} authorities for {@code CONNECT} tunnels.
     *
     * @return an immutable snapshot, in arrival order
     */
    public List<String> requestTargets() {
        return List.copyOf(requestTargets);
    }

    /**
     * Returns every {@code Proxy-Authorization} header value this proxy has received.
     *
     * @return an immutable snapshot, in arrival order
     */
    public List<String> observedProxyAuthorization() {
        return List.copyOf(observedProxyAuthorization);
    }

    /**
     * Returns how many {@code 407} challenges this proxy has issued.
     *
     * @return the challenge count
     */
    public int challengeCount() {
        return challengeCount.get();
    }

    /** Accepts connections until closed, handling each on its own daemon thread. */
    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                Thread worker = new Thread(() -> handle(client), "stub-proxy-conn");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                if (running) {
                    // Transient accept failure; keep serving.
                    continue;
                }
                return;
            }
        }
    }

    /**
     * Handles one client connection: reads the request head, enforces authentication, then either
     * tunnels or forwards.
     *
     * @param client the accepted socket
     */
    private void handle(Socket client) {
        try (Socket socket = client) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }
            List<String> headers = new ArrayList<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                headers.add(line);
            }

            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String target = parts.length > 1 ? parts[1] : "";

            String proxyAuth = headerValue(headers, "proxy-authorization");
            if (proxyAuth != null) {
                observedProxyAuthorization.add(proxyAuth);
            }
            if (!authorized(proxyAuth)) {
                challengeCount.incrementAndGet();
                out.write(("HTTP/1.1 407 Proxy Authentication Required\r\n"
                                + "Proxy-Authenticate: " + challengeScheme + " realm=\"stub\"\r\n"
                                + "Content-Length: 0\r\n"
                                + "Connection: close\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.flush();
                return;
            }

            requestTargets.add(target);
            if ("CONNECT".equalsIgnoreCase(method)) {
                tunnel(socket, in, out, target);
            } else {
                forward(socket, in, out, requestLine, headers);
            }
        } catch (IOException ignored) {
            // Client disconnected mid-exchange; nothing useful to assert on here.
        }
    }

    /**
     * Establishes a blind byte tunnel to {@code target} after acknowledging the {@code CONNECT}.
     *
     * @param client the client socket, kept open for the lifetime of the tunnel
     * @param in the client input stream, positioned just past the request head
     * @param out the client output stream
     * @param target the {@code host:port} authority to connect to
     * @throws IOException if the origin cannot be reached
     */
    private void tunnel(Socket client, InputStream in, OutputStream out, String target) throws IOException {
        int colon = target.lastIndexOf(':');
        String host = colon < 0 ? target : target.substring(0, colon);
        int port = colon < 0 ? 443 : Integer.parseInt(target.substring(colon + 1));

        try (Socket origin = new Socket(host, port)) {
            out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            Thread upstream = pump(in, origin.getOutputStream());
            copy(origin.getInputStream(), out);
            upstream.interrupt();
        }
    }

    /**
     * Relays an absolute-form request to its origin server and copies the response back.
     *
     * <p>The request line is rewritten to origin form, proxy-specific headers are dropped, and
     * {@code Connection: close} is forced so the origin terminates the response and the copy loop
     * ends deterministically.
     *
     * @param client the client socket
     * @param in the client input stream, positioned at the request body (if any)
     * @param out the client output stream
     * @param requestLine the absolute-form request line
     * @param headers the request headers as received
     * @throws IOException if the origin cannot be reached
     */
    private void forward(Socket client, InputStream in, OutputStream out, String requestLine, List<String> headers)
            throws IOException {
        String[] parts = requestLine.split(" ");
        String method = parts[0];
        String absoluteUrl = parts[1];
        java.net.URI uri = java.net.URI.create(absoluteUrl);
        int port = uri.getPort() == -1 ? 80 : uri.getPort();
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            path = path + "?" + uri.getRawQuery();
        }

        try (Socket origin = new Socket(uri.getHost(), port)) {
            StringBuilder head = new StringBuilder();
            head.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
            for (String header : headers) {
                String lower = header.toLowerCase(Locale.ROOT);
                if (lower.startsWith("proxy-") || lower.startsWith("connection:")) {
                    continue;
                }
                head.append(header).append("\r\n");
            }
            head.append("Connection: close\r\n\r\n");

            OutputStream originOut = origin.getOutputStream();
            originOut.write(head.toString().getBytes(StandardCharsets.UTF_8));
            originOut.flush();

            Thread body = pump(in, originOut);
            copy(origin.getInputStream(), out);
            body.interrupt();
        }
    }

    /**
     * Returns whether a request carrying {@code proxyAuth} may proceed.
     *
     * @param proxyAuth the received {@code Proxy-Authorization} value, or {@code null}
     * @return {@code true} when no authentication is required or the credential matches
     */
    private boolean authorized(String proxyAuth) {
        if (expectedCredential == null) {
            return true;
        }
        return proxyAuth != null
                && proxyAuth.regionMatches(true, 0, "Basic ", 0, 6)
                && proxyAuth.substring(6).trim().equals(expectedCredential);
    }

    /**
     * Finds a header value by case-insensitive name.
     *
     * @param headers the raw header lines
     * @param name the lower-case header name to find
     * @return the trimmed value, or {@code null} when absent
     */
    private static String headerValue(List<String> headers, String name) {
        for (String header : headers) {
            int colon = header.indexOf(':');
            if (colon > 0
                    && header.substring(0, colon)
                            .trim()
                            .toLowerCase(Locale.ROOT)
                            .equals(name)) {
                return header.substring(colon + 1).trim();
            }
        }
        return null;
    }

    /**
     * Reads a single CRLF-terminated line without buffering beyond it, so the remainder of the
     * stream stays intact for tunneling or body relay.
     *
     * @param in the stream to read from
     * @return the line without its terminator, or {@code null} at end of stream
     * @throws IOException if the stream cannot be read
     */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buffer.write(b);
            }
        }
        if (b == -1 && buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * Copies one stream into another on a daemon thread.
     *
     * @param from the source stream
     * @param to the destination stream
     * @return the started thread
     */
    private static Thread pump(InputStream from, OutputStream to) {
        Thread thread = new Thread(
                () -> {
                    try {
                        copy(from, to);
                    } catch (IOException ignored) {
                        // Peer closed; the other direction's copy ends the exchange.
                    }
                },
                "stub-proxy-pump");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Copies bytes until end of stream, flushing as it goes.
     *
     * @param from the source stream
     * @param to the destination stream
     * @throws IOException if either stream fails
     */
    private static void copy(InputStream from, OutputStream to) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = from.read(buffer)) != -1) {
            to.write(buffer, 0, read);
            to.flush();
        }
    }

    /** Stops accepting connections and closes the listening socket. */
    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
        acceptThread.interrupt();
    }
}
