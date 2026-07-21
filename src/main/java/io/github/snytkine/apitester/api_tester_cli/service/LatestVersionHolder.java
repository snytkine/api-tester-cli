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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Thread-safe holder for the latest known GitHub release version, when it is newer than the
 * version currently running.
 *
 * <p>Written once by the background {@code VersionChecker} thread after it determines that a
 * newer version is available; read by {@code HtmlReportGenerator} and {@code RunSuiteCommand} to
 * decide whether to surface an upgrade message. Because the check is best-effort and runs on a
 * daemon thread, {@link #get()} may return empty for the entire lifetime of a short-lived CLI
 * invocation if the background check has not completed yet — this is expected, not an error.
 *
 * <p>Thread-safety: this class is a stateless-except-for-one-field Spring singleton; the sole
 * mutable field is an {@link AtomicReference}, so concurrent writes from the checker thread and
 * concurrent reads from the report/UI thread require no additional synchronisation.
 */
@Component
public class LatestVersionHolder {

    private final AtomicReference<String> latestVersion = new AtomicReference<>();

    /**
     * Constructs an empty holder. Spring uses this constructor to create the managed singleton;
     * tests may also instantiate directly.
     */
    public LatestVersionHolder() {}

    /**
     * Records {@code version} as the latest known newer-than-running release.
     *
     * @param version the latest published version string (e.g. {@code "0.5.0"}); must not be
     *     {@code null}
     */
    public void set(String version) {
        latestVersion.set(version);
    }

    /**
     * Returns the latest known newer-than-running version, if the background check has completed
     * and found one.
     *
     * @return the latest version, or {@link Optional#empty()} when no upgrade is known — either
     *     because the check has not completed yet, or because the running version is already
     *     current
     */
    public Optional<String> get() {
        return Optional.ofNullable(latestVersion.get());
    }
}
