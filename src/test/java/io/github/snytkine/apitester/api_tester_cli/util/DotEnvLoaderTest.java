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

    @Test
    void loadDotEnvIncludesDotEnvFileEntries() throws IOException {
        Files.writeString(tempDir.resolve(".env"), "API_KEY=secret\nBASE_URL=https://example.com\n");

        Map<String, String> result = DotEnvLoader.loadDotEnv(tempDir);

        assertThat(result).containsEntry("API_KEY", "secret").containsEntry("BASE_URL", "https://example.com");
    }

    @Test
    void loadDotEnvIncludesSystemEnvironmentVariables() {
        Map<String, String> result = DotEnvLoader.loadDotEnv(tempDir);

        assertThat(result).containsKey("PATH");
    }

    @Test
    void loadDotEnvSystemEnvTakesPrecedenceOverDotEnvFile() throws IOException {
        // PATH is always set in system env; write a conflicting value in .env
        Files.writeString(tempDir.resolve(".env"), "PATH=/fake/path\n");

        Map<String, String> result = DotEnvLoader.loadDotEnv(tempDir);

        assertThat(result.get("PATH")).isEqualTo(System.getenv("PATH"));
    }

    @Test
    void loadDotEnvWithMissingFileReturnsSystemEnvironmentVariables() {
        Map<String, String> result = DotEnvLoader.loadDotEnv(tempDir);

        assertThat(result).containsAllEntriesOf(System.getenv());
    }
}
