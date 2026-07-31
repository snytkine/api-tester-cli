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

import io.github.snytkine.cmdrest.model.ProxyConfig;
import io.github.snytkine.cmdrest.model.RestClientConfig;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the effective proxy for a single rest-client from its YAML {@code proxy} declaration and
 * the {@code HTTP_PROXY} / {@code HTTPS_PROXY} / {@code PROXY_USE_ENV} environment variables.
 *
 * <h2>Precedence</h2>
 *
 * An environment proxy applies <em>by default</em> to any rest-client that does not declare a
 * {@code proxy} key — no opt-in flag is required. {@code PROXY_USE_ENV} exists solely to decide who
 * wins when a client declares a proxy <em>object</em> and the environment also supplies one.
 *
 * <table border="1">
 *   <caption>Resolution matrix</caption>
 *   <tr><th>YAML {@code proxy}</th><th>{@code PROXY_USE_ENV}</th><th>{@code HTTP(S)_PROXY}</th><th>Result</th></tr>
 *   <tr><td>absent</td><td>any</td><td>set</td><td>environment proxy</td></tr>
 *   <tr><td>absent</td><td>any</td><td>unset</td><td>no proxy</td></tr>
 *   <tr><td>{@code false}</td><td>any</td><td>any</td><td>no proxy — absolute opt-out</td></tr>
 *   <tr><td>object</td><td>not {@code true}</td><td>any</td><td>the YAML object</td></tr>
 *   <tr><td>object</td><td>{@code true}</td><td>set</td><td>environment proxy; YAML discarded entirely</td></tr>
 *   <tr><td>object</td><td>{@code true}</td><td>unset</td><td>the YAML object (nothing to override with)</td></tr>
 * </table>
 *
 * <p>The {@code PROXY_USE_ENV} override is all-or-nothing: when the environment wins, the YAML
 * block is discarded <em>including its credentials</em>, which then come solely from the
 * environment URL's userinfo. Credentials are never mixed between the two sources.
 *
 * <p>{@code proxy: false} is absolute and is checked first, so a malformed environment value can
 * never fail a rest-client that has opted out. Environment values are otherwise parsed only when
 * they would actually be used, so a typo surfaces for exactly the clients it affects.
 *
 * <h2>URL form</h2>
 *
 * Accepted: {@code http://host}, {@code http://host:port}, {@code http://user:pass@host:port}, and
 * the same without a scheme ({@code host:port}), which the conventional shell variables often use.
 * A scheme other than {@code http} is rejected — see {@link #parseProxyUrl}. The port defaults to
 * {@value io.github.snytkine.cmdrest.model.ProxyConfig#DEFAULT_PROXY_PORT}.
 *
 * <p>This class is a stateless utility: only static methods, no fields, and every object it creates
 * lives on the caller's stack, so it is inherently thread-safe.
 */
public final class ProxyResolver {

    /** Environment variable naming the proxy for {@code http://} targets. */
    public static final String HTTP_PROXY_ENV = "HTTP_PROXY";

    /** Environment variable naming the proxy for {@code https://} targets. */
    public static final String HTTPS_PROXY_ENV = "HTTPS_PROXY";

    /** Environment variable that lets the environment override a YAML {@code proxy} object. */
    public static final String PROXY_USE_ENV = "PROXY_USE_ENV";

    /** Utility class; not instantiable. */
    private ProxyResolver() {}

    /**
     * Resolves the effective proxy settings for one rest-client.
     *
     * @param config the rest-client configuration; may be {@code null}, treated as declaring no
     *     proxy
     * @param env the merged environment (process environment overlaid on the suite's {@code .env});
     *     may be {@code null}, treated as empty
     * @return the resolved settings, or {@code null} when this client must connect directly
     * @throws ProxyConfigurationException if a proxy URL that would actually be used is unusable
     */
    public static @Nullable ProxySettings resolve(
            @Nullable RestClientConfig config, @Nullable Map<String, String> env) {
        Map<String, String> environment = env == null ? Map.of() : env;
        ProxyConfig yaml = config == null ? null : config.proxy();

        // Absolute opt-out, checked before anything else so that neither a malformed environment
        // value nor PROXY_USE_ENV can resurrect a proxy the suite explicitly turned off.
        if (yaml != null && yaml.isDisabled()) {
            return null;
        }

        if (yaml != null && yaml.invalidValue() != null) {
            throw new ProxyConfigurationException("proxy must be either a configuration object or the value 'false'"
                    + " to disable proxying, but was '" + yaml.invalidValue() + "'"
                    + (isTrueLiteral(yaml.invalidValue())
                            ? ". To route this client through a proxy, supply a 'url' (and optional"
                                    + " credentials), or set the HTTP_PROXY / HTTPS_PROXY environment variable"
                            : ""));
        }

        if (yaml == null) {
            // No YAML declaration: an environment proxy applies automatically.
            return fromEnvironment(environment);
        }

        if (useEnvironmentOverride(environment)) {
            ProxySettings fromEnv = fromEnvironment(environment);
            if (fromEnv != null) {
                // All-or-nothing: the YAML block, credentials included, is discarded.
                return fromEnv;
            }
        }
        return fromYaml(yaml);
    }

    /**
     * Returns whether {@code PROXY_USE_ENV} requests that the environment override a YAML proxy
     * object.
     *
     * @param env the merged environment
     * @return {@code true} when the variable is set to {@code true}, ignoring case and surrounding
     *     whitespace
     */
    public static boolean useEnvironmentOverride(Map<String, String> env) {
        String value = env.get(PROXY_USE_ENV);
        return value != null && "true".equalsIgnoreCase(value.trim());
    }

    /**
     * Builds settings from a rest-client's YAML {@code proxy} object. The single declared proxy
     * applies to both {@code http://} and {@code https://} targets.
     *
     * @param yaml the configured proxy block; must be neither disabled nor invalid
     * @return the resolved settings
     * @throws ProxyConfigurationException if the URL or credential combination is unusable
     */
    private static ProxySettings fromYaml(ProxyConfig yaml) {
        String url = yaml.url();
        if (url == null || url.isBlank()) {
            throw new ProxyConfigurationException("proxy.url is required");
        }
        if (yaml.password() != null && !yaml.hasCredentials()) {
            throw new ProxyConfigurationException("proxy.password is only allowed when proxy.username is also set");
        }
        ProxyEndpoint endpoint = parseProxyUrl(url, "proxy.url");
        // Explicit username/password fields take precedence over any userinfo in the URL itself.
        if (yaml.hasCredentials()) {
            endpoint = new ProxyEndpoint(endpoint.host(), endpoint.port(), yaml.username(), yaml.password());
        }
        return new ProxySettings(endpoint, endpoint, ProxySettings.Source.YAML);
    }

    /**
     * Builds settings from {@code HTTP_PROXY} / {@code HTTPS_PROXY}, accepting the conventional
     * lowercase spellings as aliases (uppercase wins when both are present).
     *
     * @param env the merged environment
     * @return the resolved settings, or {@code null} when neither variable is set
     * @throws ProxyConfigurationException if a set variable cannot be parsed
     */
    public static @Nullable ProxySettings fromEnvironment(Map<String, String> env) {
        String httpValue = firstNonBlank(env, HTTP_PROXY_ENV, "http_proxy");
        String httpsValue = firstNonBlank(env, HTTPS_PROXY_ENV, "https_proxy");
        if (httpValue == null && httpsValue == null) {
            return null;
        }
        ProxyEndpoint http = httpValue == null ? null : parseProxyUrl(httpValue, HTTP_PROXY_ENV);
        ProxyEndpoint https = httpsValue == null ? null : parseProxyUrl(httpsValue, HTTPS_PROXY_ENV);
        return new ProxySettings(http, https, ProxySettings.Source.ENVIRONMENT);
    }

    /**
     * Returns the first non-blank value among the given keys, or {@code null} when none is set.
     *
     * @param env the merged environment
     * @param keys the keys to try, in priority order
     * @return the first non-blank value, or {@code null}
     */
    private static @Nullable String firstNonBlank(Map<String, String> env, String... keys) {
        for (String key : keys) {
            String value = env.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * Parses a proxy URL into an endpoint.
     *
     * <p>A missing scheme is assumed to be {@code http}, since the shell convention for {@code
     * HTTP_PROXY} permits a bare {@code host:port}. An explicit scheme must be {@code http}: {@link
     * java.net.http.HttpClient} cannot negotiate TLS with a proxy, so an {@code https://} proxy URL
     * is rejected with an explanation rather than silently downgraded.
     *
     * @param value the raw URL, from YAML or the environment
     * @param label the setting's name, used in error messages (e.g. {@code proxy.url})
     * @return the parsed endpoint, with credentials taken from any userinfo present
     * @throws ProxyConfigurationException if the value is not a usable {@code http} proxy URL
     */
    public static ProxyEndpoint parseProxyUrl(String value, String label) {
        String candidate = value.trim();
        if (!candidate.contains("://")) {
            candidate = "http://" + candidate;
        }
        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            throw new ProxyConfigurationException(
                    label + " is not a valid URL: '" + redact(value) + "' (" + e.getReason() + ")", e);
        }

        String scheme = uri.getScheme();
        if (scheme != null && !"http".equalsIgnoreCase(scheme)) {
            if ("https".equalsIgnoreCase(scheme)) {
                throw new ProxyConfigurationException(label + " must use the 'http' scheme, but was '" + redact(value)
                        + "'. The underlying JDK HTTP client cannot establish a TLS connection to a proxy"
                        + " server: it connects to the proxy in plaintext and tunnels the endpoint's own TLS"
                        + " through it with CONNECT. Use 'http://" + (uri.getHost() == null ? "host" : uri.getHost())
                        + "' here — this does not weaken TLS to the API endpoint, which is unaffected by"
                        + " proxying and is configured by the rest-client's 'ssl' block.");
            }
            throw new ProxyConfigurationException(
                    label + " must use the 'http' scheme, but was '" + redact(value) + "'");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ProxyConfigurationException(label + " does not specify a proxy host: '" + redact(value)
                    + "'. Expected a value like" + " 'http://proxy.example.com:8080'");
        }

        int port = uri.getPort() == -1 ? ProxyConfig.DEFAULT_PROXY_PORT : uri.getPort();
        String username = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int separator = userInfo.indexOf(':');
            username = separator < 0 ? userInfo : userInfo.substring(0, separator);
            password = separator < 0 ? null : userInfo.substring(separator + 1);
        }
        return new ProxyEndpoint(host, port, username, password);
    }

    /**
     * Removes any userinfo from a URL so it can appear in an error message without leaking
     * credentials.
     *
     * <p>Operates on the raw string rather than a parsed {@link URI}, because the value is reported
     * precisely in the cases where parsing failed.
     *
     * @param value the raw configured value
     * @return the value with any {@code user:pass@} portion replaced by {@code ***@}
     */
    static String redact(String value) {
        int at = value.lastIndexOf('@');
        if (at < 0) {
            return value;
        }
        int schemeEnd = value.indexOf("://");
        int start = schemeEnd < 0 ? 0 : schemeEnd + 3;
        if (at <= start) {
            return value;
        }
        return value.substring(0, start) + "***@" + value.substring(at + 1);
    }

    /**
     * Returns whether an invalid {@code proxy} literal was the boolean {@code true}, which warrants
     * extra guidance in the error message.
     *
     * @param literal the offending literal
     * @return {@code true} when the literal is {@code true}, ignoring case
     */
    private static boolean isTrueLiteral(String literal) {
        return "true".equalsIgnoreCase(literal.trim());
    }
}
