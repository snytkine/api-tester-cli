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
package io.github.snytkine.apitester.api_tester_cli.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code apitester.version-check.*} keys from {@code application.properties}.
 *
 * <p>Controls the background check (performed by {@code service.VersionChecker}) that queries the
 * GitHub Releases API for a newer published version than the one currently running, and the
 * message shown when an upgrade is available.
 *
 * <p>This is a Spring-managed immutable configuration-properties record; it is thread-safe by
 * construction (all fields are final and set once at binding time).
 *
 * @param enabled when {@code false}, the background version check is skipped entirely and no
 *     network call is made
 * @param url the GitHub Releases API endpoint to query for the latest published release
 * @param upgradePageUrl the human-facing page linked from the upgrade message
 * @param timeoutSeconds per-request timeout, in seconds, for the version-check HTTP call
 * @param maxRetries number of additional attempts made when the request fails, before giving up
 *     silently
 * @param retryIntervalSeconds seconds to sleep between retry attempts
 * @param upgradeMessage template for the upgrade-available message; supports a {@code
 *     {latestVersion}} placeholder, replaced with the actual latest version at render time
 */
@ConfigurationProperties("apitester.version-check")
public record VersionCheckProperties(
        boolean enabled,
        String url,
        String upgradePageUrl,
        int timeoutSeconds,
        int maxRetries,
        int retryIntervalSeconds,
        String upgradeMessage) {

    /** Placeholder in {@link #upgradeMessage()} replaced with the actual latest version. */
    public static final String LATEST_VERSION_PLACEHOLDER = "{latestVersion}";

    /**
     * Resolves {@link #upgradeMessage()} by substituting {@link #LATEST_VERSION_PLACEHOLDER} with
     * {@code latestVersion}.
     *
     * <p>Both the HTML report and the terminal UI call this method so the two surfaces can never
     * disagree on the resolved text.
     *
     * @param latestVersion the latest published version to substitute into the message
     * @return the resolved, human-readable upgrade message
     */
    public String resolveUpgradeMessage(String latestVersion) {
        return upgradeMessage.replace(LATEST_VERSION_PLACEHOLDER, latestVersion);
    }
}
