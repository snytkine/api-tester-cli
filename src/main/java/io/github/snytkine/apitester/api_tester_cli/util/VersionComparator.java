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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares {@code major.minor.patch} version strings to decide whether a newer release is
 * available than the one currently running.
 *
 * <p>Both versions must match {@code major.minor.patch} with an optional leading {@code v} and an
 * optional {@code -suffix} (e.g. {@code v1.2.3}, {@code 1.2.3-SNAPSHOT}, {@code 1.2.3-rc1}). A
 * suffix on the <em>running</em> version (e.g. {@code -SNAPSHOT}) marks it as a pre-release build
 * of that numeric version, so the same numeric {@code latestVersion} is still considered newer —
 * a running {@code 0.4.1-SNAPSHOT} is "older" than a published {@code 0.4.1} release. A malformed
 * or unparseable version on either side (including the literal string {@code "unknown"}, this
 * project's fallback {@code BuildProperties} version) is treated as "no upgrade available" rather
 * than throwing, since callers only use this for a best-effort, purely informational check.
 *
 * <p>This class is stateless and thread-safe; it holds no mutable state and every method is a pure
 * function of its arguments.
 */
public final class VersionComparator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-(.+))?$");

    private VersionComparator() {}

    /**
     * Determines whether {@code latestVersion} is newer than {@code runningVersion}.
     *
     * @param runningVersion the version currently running (e.g. from {@code
     *     BuildProperties#getVersion()}); may carry a pre-release suffix such as {@code -SNAPSHOT}
     * @param latestVersion the latest published version to compare against (e.g. a GitHub release
     *     {@code tag_name} with any leading {@code v} already stripped, or not — this method
     *     strips it too)
     * @return {@code Optional.of(latestVersion)} when {@code latestVersion} is strictly newer than
     *     {@code runningVersion}; {@code Optional.empty()} when it is the same or older, or when
     *     either version string cannot be parsed
     */
    public static Optional<String> newerVersion(String runningVersion, String latestVersion) {
        Matcher runningMatcher = VERSION_PATTERN.matcher(runningVersion.strip());
        Matcher latestMatcher = VERSION_PATTERN.matcher(latestVersion.strip());
        if (!runningMatcher.matches() || !latestMatcher.matches()) {
            return Optional.empty();
        }

        int[] running = numericComponents(runningMatcher);
        int[] latest = numericComponents(latestMatcher);
        int comparison = compareComponents(running, latest);
        boolean latestIsNumericallyGreater = comparison < 0;
        boolean sameNumericRelease = comparison == 0;
        boolean runningIsPreRelease = runningMatcher.group(4) != null;

        boolean upgradeAvailable = latestIsNumericallyGreater || (sameNumericRelease && runningIsPreRelease);
        return upgradeAvailable ? Optional.of(latestVersion) : Optional.empty();
    }

    /**
     * Extracts the numeric {@code major}, {@code minor}, {@code patch} components from a matched
     * {@link #VERSION_PATTERN} matcher.
     *
     * @param matcher a matcher that has already matched {@link #VERSION_PATTERN}
     * @return a 3-element array: {@code [major, minor, patch]}
     */
    private static int[] numericComponents(Matcher matcher) {
        return new int[] {
            Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))
        };
    }

    /**
     * Compares two {@code [major, minor, patch]} arrays component-by-component.
     *
     * @param a the first version's numeric components
     * @param b the second version's numeric components
     * @return a negative number if {@code a} is numerically less than {@code b}, positive if
     *     greater, {@code 0} if the numeric releases are identical
     */
    private static int compareComponents(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            int diff = Integer.compare(a[i], b[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }
}
