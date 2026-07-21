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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotEnvLoaderTest {

    @TempDir
    Path tempDir;

    private final DotEnvLoader loader = new DotEnvLoader();

    @Test
    void loadDotEnvIncludesDotEnvFileEntries() throws IOException {
        Files.writeString(tempDir.resolve(".env"), "API_KEY=secret\nBASE_URL=https://example.com\n");

        Map<String, String> result = loader.loadDotEnv(tempDir);

        assertThat(result).containsEntry("API_KEY", "secret").containsEntry("BASE_URL", "https://example.com");
    }

    @Test
    void loadDotEnvIncludesSystemEnvironmentVariables() {
        Map<String, String> result = loader.loadDotEnv(tempDir);

        assertThat(result).containsKey("PATH");
    }

    @Test
    void loadDotEnvSystemEnvTakesPrecedenceOverDotEnvFile() throws IOException {
        // PATH is always set in system env; write a conflicting value in .env
        Files.writeString(tempDir.resolve(".env"), "PATH=/fake/path\n");

        Map<String, String> result = loader.loadDotEnv(tempDir);

        assertThat(result.get("PATH")).isEqualTo(System.getenv("PATH"));
    }

    @Test
    void loadDotEnvWithMissingFileReturnsSystemEnvironmentVariables() {
        Map<String, String> result = loader.loadDotEnv(tempDir);

        assertThat(result).containsAllEntriesOf(System.getenv());
    }

    @Test
    void loadDotEnvWithExplicitFilenameLoadsNonDotEnvFile() throws IOException {
        Files.writeString(tempDir.resolve("staging.env"), "STAGE=staging\nBASE_URL=https://staging.example.com\n");

        Map<String, String> result = loader.loadDotEnv(tempDir, "staging.env");

        assertThat(result).containsEntry("STAGE", "staging").containsEntry("BASE_URL", "https://staging.example.com");
    }

    @Test
    void loadDotEnvWithExplicitFilenameIgnoresDotEnvFile() throws IOException {
        // A plain .env file in the same directory must not leak in when an explicit filename is used.
        Files.writeString(tempDir.resolve(".env"), "FROM_DOT_ENV=yes\n");
        Files.writeString(tempDir.resolve("custom.env"), "FROM_CUSTOM=yes\n");

        Map<String, String> result = loader.loadDotEnv(tempDir, "custom.env");

        assertThat(result).containsEntry("FROM_CUSTOM", "yes").doesNotContainKey("FROM_DOT_ENV");
    }

    @Test
    void loadDotEnvWithMissingExplicitFilenameReturnsSystemEnvironmentVariables() {
        Map<String, String> result = loader.loadDotEnv(tempDir, "nonexistent.env");

        assertThat(result).containsKey("PATH");
    }
}
