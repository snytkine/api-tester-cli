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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

/**
 * Spring Shell command that exports the bundled {@code test-suite-schema.json} to a
 * user-specified output directory.
 *
 * <p>The schema is loaded from the classpath at {@code schemas/test-suite-schema.json} (bundled
 * inside the JAR or GraalVM native binary). Users can copy it locally to enable IDE validation of
 * their test-suite YAML files without having to extract it from the artifact manually.
 *
 * <p>The output file is always named {@value #SCHEMA_FILENAME} and is written into the directory
 * supplied via {@code --out}. The directory is created automatically if it does not exist.
 *
 * <p>Thread-safety: this class holds no mutable instance state. It is safe to invoke {@link
 * #exportSchema} concurrently from multiple threads.
 */
@Component
public class ExportSchemaCommand {

    private static final Logger log = LoggerFactory.getLogger(ExportSchemaCommand.class);

    /** Filename of the schema resource on the classpath and the name used for the output file. */
    static final String SCHEMA_FILENAME = "test-suite-schema.json";

    private static final String SCHEMA_CLASSPATH_LOCATION = "schemas/" + SCHEMA_FILENAME;

    /**
     * Exports the bundled test-suite JSON schema into the directory at {@code outputDir}.
     *
     * <p>The output file is always written as {@value #SCHEMA_FILENAME} inside {@code outputDir}.
     * The directory is created automatically if it does not exist. If the destination file already
     * exists it is overwritten. On success a confirmation line is printed to the shell output. On
     * failure a descriptive error message is printed and the error is logged at {@code ERROR} level;
     * no exception is propagated to the shell framework.
     *
     * @param outputDir absolute or relative path to the directory where the schema file should be
     *     written
     * @param context Spring Shell command context used to write output back to the terminal
     */
    @Command(
            name = "export-schema",
            alias = {"es"},
            description = "Exports the bundled test-suite-schema.json to the specified directory."
                    + " Use --out to provide the destination directory path.")
    public void exportSchema(
            @Option(
                            longName = "out",
                            required = true,
                            description =
                                    "Absolute or relative path to the directory where the schema file should be written.")
                    String outputDir,
            CommandContext context) {

        Path directory = Path.of(outputDir);
        Path destination = directory.resolve(SCHEMA_FILENAME);
        ClassPathResource resource = new ClassPathResource(SCHEMA_CLASSPATH_LOCATION);
        try {
            Files.createDirectories(directory);
            Files.copy(resource.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            context.outputWriter().println("Schema written to: " + destination.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write schema to {}", destination, e);
            context.outputWriter().println("Error: failed to write schema to " + destination + ": " + e.getMessage());
        }
    }
}
