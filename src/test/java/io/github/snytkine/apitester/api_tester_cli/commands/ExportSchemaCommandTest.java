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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.ParsedInput;

class ExportSchemaCommandTest {

    @TempDir
    Path tempDir;

    private ExportSchemaCommand command;
    private StringWriter output;
    private CommandContext ctx;

    @BeforeEach
    void setUp() {
        command = new ExportSchemaCommand();
        output = new StringWriter();
        ctx = new CommandContext(
                new ParsedInput("export-schema", List.of(), List.of(), List.of()),
                new CommandRegistry(),
                new PrintWriter(output),
                null);
    }

    @Test
    void writesSchemaFileNamedTestSuiteSchemaToOutputDirectory() {
        command.exportSchema(tempDir.toString(), ctx);

        assertThat(tempDir.resolve(ExportSchemaCommand.SCHEMA_FILENAME))
                .exists()
                .isNotEmptyFile();
    }

    @Test
    void writtenFileContainsValidJson() throws IOException {
        command.exportSchema(tempDir.toString(), ctx);

        assertThat(Files.readString(tempDir.resolve(ExportSchemaCommand.SCHEMA_FILENAME))
                        .trim())
                .startsWith("{");
    }

    @Test
    void printsSuccessMessageWithAbsolutePath() {
        command.exportSchema(tempDir.toString(), ctx);

        Path expected = tempDir.resolve(ExportSchemaCommand.SCHEMA_FILENAME).toAbsolutePath();
        assertThat(output.toString()).contains("Schema written to:").contains(expected.toString());
    }

    @Test
    void createsOutputDirectoryIfAbsent() {
        Path subDir = tempDir.resolve("nested/subdir");

        command.exportSchema(subDir.toString(), ctx);

        assertThat(subDir.resolve(ExportSchemaCommand.SCHEMA_FILENAME)).exists().isNotEmptyFile();
    }

    @Test
    void overwritesExistingSchemaFileInDirectory() throws IOException {
        Path existing = tempDir.resolve(ExportSchemaCommand.SCHEMA_FILENAME);
        Files.writeString(existing, "old content");

        command.exportSchema(tempDir.toString(), ctx);

        assertThat(Files.readString(existing)).doesNotContain("old content");
        assertThat(Files.readString(existing).trim()).startsWith("{");
    }

    @Test
    void printsErrorMessageWhenDirectoryCannotBeCreated() throws IOException {
        // Place a regular file where the directory path is expected so createDirectories fails.
        Path blocker = tempDir.resolve("not-a-dir");
        Files.writeString(blocker, "I am a file");
        Path impossibleDir = blocker.resolve("subdir");

        command.exportSchema(impossibleDir.toString(), ctx);

        assertThat(output.toString()).contains("Error:");
    }
}
