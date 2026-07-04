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
package io.github.snytkine.apitester.api_tester_cli.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.snytkine.apitester.api_tester_cli.config.VersionCheckProperties;
import io.github.snytkine.apitester.api_tester_cli.util.VersionComparator;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Checks the GitHub Releases API for a newer published version than the one currently running,
 * and records it in a {@link LatestVersionHolder} when found.
 *
 * <p>{@link #checkForUpdate()} is designed to be invoked on a background daemon thread (see {@code
 * config.VersionCheckStartupRunner}) so it never blocks application startup or a suite run. It is
 * entirely best-effort: every failure mode (network error, non-2xx response, timeout, malformed
 * JSON, unparseable version) is caught, logged at {@code debug}/{@code trace}, and swallowed —
 * this method never throws and never writes to stdout/stderr, so it can never pollute CLI output
 * or crash the caller.
 *
 * <p>Thread-safety: this class is a stateless Spring singleton (its only fields are immutable
 * collaborators injected at construction); {@link #checkForUpdate()} holds no state across calls
 * and is safe to invoke concurrently, though in practice it is invoked exactly once per process.
 */
@Service
public class VersionChecker {

    private static final Logger log = LoggerFactory.getLogger(VersionChecker.class);

    /** Version reported by the fallback {@code BuildProperties} bean when build-info is absent. */
    private static final String UNKNOWN_VERSION = "unknown";

    private final RestClient restClient;
    private final VersionCheckProperties properties;
    private final BuildProperties buildProperties;
    private final LatestVersionHolder latestVersionHolder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs the checker with its collaborators.
     *
     * @param restClient the shared {@link RestClient} bean (see {@code config.HttpClientConfig})
     *     used to issue the GitHub Releases API request
     * @param properties binds the {@code apitester.version-check.*} configuration keys
     * @param buildProperties supplies the currently running application version
     * @param latestVersionHolder populated when a newer version is found
     */
    public VersionChecker(
            RestClient restClient,
            VersionCheckProperties properties,
            BuildProperties buildProperties,
            LatestVersionHolder latestVersionHolder) {
        this.restClient = restClient;
        this.properties = properties;
        this.buildProperties = buildProperties;
        this.latestVersionHolder = latestVersionHolder;
    }

    /**
     * Performs the version check: skips entirely when disabled or when the running version is
     * {@value #UNKNOWN_VERSION}, otherwise fetches the latest GitHub release (retrying on
     * failure), compares it against the running version, and populates {@link
     * #latestVersionHolder} when a newer version is available.
     *
     * <p>Never throws; all failures are logged at {@code debug}/{@code trace} and swallowed.
     */
    public void checkForUpdate() {
        if (!properties.enabled()) {
            log.debug("Version check disabled via apitester.version-check.enabled=false");
            return;
        }

        String runningVersion = buildProperties.getVersion();
        if (UNKNOWN_VERSION.equals(runningVersion)) {
            log.debug("Running version is \"unknown\" (no build-info.properties present); skipping version check");
            return;
        }

        fetchLatestTagName().ifPresent(tagName -> {
            String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            VersionComparator.newerVersion(runningVersion, latestVersion)
                    .ifPresentOrElse(
                            latestVersionHolder::set,
                            () -> log.debug(
                                    "Running version {} is up to date (latest published: {})",
                                    runningVersion,
                                    latestVersion));
        });
    }

    /**
     * Fetches and parses the {@code tag_name} field from the configured GitHub releases endpoint,
     * retrying up to {@link VersionCheckProperties#maxRetries()} times in total with {@link
     * VersionCheckProperties#retryIntervalSeconds()} between attempts.
     *
     * @return the raw {@code tag_name} value (leading {@code v} not yet stripped), or {@link
     *     Optional#empty()} if every attempt failed or the sleep between retries was interrupted
     */
    private Optional<String> fetchLatestTagName() {
        int attempts = Math.max(1, properties.maxRetries());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String body = fetchWithTimeout();
                JsonNode root = objectMapper.readTree(body);
                String tagName = root.path("tag_name").asText(null);
                if (tagName == null || tagName.isBlank()) {
                    log.debug("Version-check response had no tag_name field");
                    return Optional.empty();
                }
                return Optional.of(tagName);
            } catch (Exception e) {
                log.debug("Version check attempt {}/{} failed: {}", attempt, attempts, e.toString());
                if (attempt < attempts && sleepInterrupted(properties.retryIntervalSeconds())) {
                    break;
                }
            }
        }
        log.debug("Version check gave up after {} attempt(s)", attempts);
        return Optional.empty();
    }

    /**
     * Issues the GitHub releases GET request via {@link #restClient}, bounded by {@link
     * VersionCheckProperties#timeoutSeconds()} using a dedicated single-thread executor so the
     * timeout is enforced regardless of how the shared {@link RestClient}'s transport is
     * configured.
     *
     * @return the raw response body
     * @throws Exception if the request fails, times out, or is interrupted
     */
    private String fetchWithTimeout() throws Exception {
        try (ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "version-check-http");
            thread.setDaemon(true);
            return thread;
        })) {
            Future<String> future = executor.submit(
                    () -> restClient.get().uri(properties.url()).retrieve().body(String.class));
            return future.get(properties.timeoutSeconds(), TimeUnit.SECONDS);
        }
    }

    /**
     * Sleeps for {@code seconds}, restoring the interrupt flag and returning {@code true} if
     * interrupted while sleeping.
     *
     * @param seconds duration to sleep, in seconds
     * @return {@code true} if the sleep was interrupted (the retry loop should stop), {@code
     *     false} if it completed normally
     */
    private static boolean sleepInterrupted(int seconds) {
        try {
            Thread.sleep(Duration.ofSeconds(seconds));
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }
}
