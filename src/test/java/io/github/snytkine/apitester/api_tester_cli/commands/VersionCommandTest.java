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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

/** Unit tests for {@link VersionCommand}. */
class VersionCommandTest {

    private static BuildProperties buildProperties(String version) {
        Properties props = new Properties();
        props.setProperty("version", version);
        return new BuildProperties(props);
    }

    @Test
    void versionIncludesAppNameAndVersion() {
        VersionCommand command = new VersionCommand(buildProperties("1.2.3"));
        assertThat(command.version()).isEqualTo("Api Tester CLI version 1.2.3");
    }

    @Test
    void versionReflectsSnapshotSuffix() {
        VersionCommand command = new VersionCommand(buildProperties("0.1.1-SNAPSHOT"));
        assertThat(command.version()).isEqualTo("Api Tester CLI version 0.1.1-SNAPSHOT");
    }
}
