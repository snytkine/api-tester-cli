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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link FileLoader}.
 *
 * <p>Covers both {@code loadFile} overloads (success and not-found paths), and {@code parseFile}
 * with Thymeleaf variable substitution.
 */
class FileLoaderTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // loadFile(Path directory, String file)
    // -------------------------------------------------------------------------

    @Test
    void loadFile_byDirectory_returnsFileContents() throws IOException {
        Path file = tempDir.resolve("data.txt");
        Files.writeString(file, "hello world");
        assertThat(FileLoader.loadFile(tempDir, "data.txt")).isEqualTo("hello world");
    }

    @Test
    void loadFile_byDirectory_whenFileAbsent_throwsFileNotFoundException() {
        assertThatThrownBy(() -> FileLoader.loadFile(tempDir, "missing.txt"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("missing.txt");
    }

    @Test
    void loadFile_byDirectory_handlesSubdirectoryRelativePath() throws IOException {
        Path sub = tempDir.resolve("sub");
        Files.createDirectory(sub);
        Files.writeString(sub.resolve("nested.json"), "{\"key\":\"value\"}");
        assertThat(FileLoader.loadFile(tempDir, "sub/nested.json")).isEqualTo("{\"key\":\"value\"}");
    }

    // -------------------------------------------------------------------------
    // loadFile(String basePath, String file)
    // -------------------------------------------------------------------------

    @Test
    void loadFile_byBasePath_usesParentDirectoryOfBasePath() throws IOException {
        Path file = tempDir.resolve("schema.json");
        Files.writeString(file, "{}");
        // basePath points to a "suite file" inside tempDir; parent is tempDir
        String syntheticSuitePath = tempDir.resolve("suite.yml").toString();
        assertThat(FileLoader.loadFile(syntheticSuitePath, "schema.json")).isEqualTo("{}");
    }

    @Test
    void loadFile_byBasePath_whenFileAbsent_throwsFileNotFoundException() {
        String syntheticSuitePath = tempDir.resolve("suite.yml").toString();
        assertThatThrownBy(() -> FileLoader.loadFile(syntheticSuitePath, "no-such-file.json"))
                .isInstanceOf(FileNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // parseFile
    // -------------------------------------------------------------------------

    @Test
    void parseFile_substitutesVariablesFromContext() {
        String template = "Hello [[${greeting.name}]]!";
        Map<String, Map<String, String>> ctx = Map.of("greeting", Map.of("name", "World"));
        assertThat(FileLoader.parseFile(template, ctx)).isEqualTo("Hello World!");
    }

    @Test
    void parseFile_withEmptyContext_returnsTemplateUnchanged() {
        String template = "no variables here";
        assertThat(FileLoader.parseFile(template, Map.of())).isEqualTo("no variables here");
    }

    @Test
    void parseFile_withMultipleNamespaces_resolvesAll() {
        String template = "[[${a.x}]] and [[${b.y}]]";
        Map<String, Map<String, String>> ctx = Map.of("a", Map.of("x", "foo"), "b", Map.of("y", "bar"));
        assertThat(FileLoader.parseFile(template, ctx)).isEqualTo("foo and bar");
    }
}
