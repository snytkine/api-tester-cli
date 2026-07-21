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

import io.github.snytkine.apitester.api_tester_cli.service.VersionChecker;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Spring {@link ApplicationRunner} that kicks off {@link VersionChecker#checkForUpdate()} on a
 * background daemon thread as early as possible during startup.
 *
 * <p>The thread is daemon so it never delays JVM shutdown; because this CLI is short-lived, the
 * check is explicitly best-effort — if the process exits before the background thread completes,
 * no upgrade message is shown for that run. This runner itself returns immediately after starting
 * the thread, so it never delays the shell prompt or the first command.
 *
 * <p>Ordered just after {@link LoggingActivatorRunner} ({@link Ordered#HIGHEST_PRECEDENCE}) so the
 * background thread gets the maximum possible head start before a single non-interactive command
 * finishes and the process exits — this is a nice-to-have for a best-effort feature, not a
 * correctness requirement, since the check never blocks regardless of ordering.
 *
 * <p>Thread-safety: this class holds no mutable state; {@link #run(ApplicationArguments)} is
 * idempotent and safe to invoke more than once (each invocation starts an independent thread).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class VersionCheckStartupRunner implements ApplicationRunner {

    private final VersionChecker versionChecker;
    private final VersionCheckProperties properties;

    /**
     * Constructs the runner with its collaborators.
     *
     * @param versionChecker performs the actual background check
     * @param properties used to check {@link VersionCheckProperties#enabled()} before spawning a
     *     thread at all
     */
    public VersionCheckStartupRunner(VersionChecker versionChecker, VersionCheckProperties properties) {
        this.versionChecker = versionChecker;
        this.properties = properties;
    }

    /**
     * Starts the background version-check thread when enabled; does nothing otherwise.
     *
     * @param args application arguments (not used)
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        Thread thread = new Thread(versionChecker::checkForUpdate, "version-check");
        thread.setDaemon(true);
        thread.start();
    }
}
