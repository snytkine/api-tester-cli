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
package io.github.snytkine.apitester.api_tester_cli.commands;

import org.springframework.boot.info.BuildProperties;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

/**
 * Spring Shell command that displays the application version.
 *
 * <p>The version string is sourced from {@link BuildProperties}, which is populated at build time
 * by the {@code spring-boot-maven-plugin}'s {@code build-info} goal. It reads the version directly
 * from {@code pom.xml} and embeds it in {@code META-INF/build-info.properties} inside the JAR or
 * GraalVM native binary — no reflection, no classpath scanning at runtime.
 *
 * <p>Thread-safety: this class holds no mutable instance state and is safe for concurrent use.
 */
@Component
public class VersionCommand {

    private final BuildProperties buildProperties;

    /**
     * Constructs the command with its required build properties.
     *
     * @param buildProperties Spring Boot build properties bean providing the application version
     */
    public VersionCommand(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    /**
     * Returns the application version string.
     *
     * <p>Example output: {@code Api Tester CLI version 0.1.1}
     *
     * @return human-readable version string
     */
    @Command(name = "version", description = "Displays the application version.")
    public String version() {
        return "Api Tester CLI version " + buildProperties.getVersion();
    }
}
