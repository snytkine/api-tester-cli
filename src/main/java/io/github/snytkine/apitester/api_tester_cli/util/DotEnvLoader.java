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

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for loading variables from a {@code .env} file.
 *
 * <p>This class provides a single static method that reads a {@code .env} file from a given
 * directory and merges it with the process's system environment variables. System environment
 * variables take precedence over same-named entries in the {@code .env} file.
 *
 * <p>This class is thread-safe; it holds no mutable state.
 */
public class DotEnvLoader {

    private DotEnvLoader() {}

    /**
     * Loads variables from a {@code .env} file in {@code directory} and merges them with the
     * process's system environment variables.
     *
     * <p>The {@code .env} file is loaded first; system environment variables are then overlaid on
     * top, so a system variable with the same name as a {@code .env} entry always wins. If no
     * {@code .env} file exists, only system environment variables are returned.
     *
     * <p>This method is thread-safe; each invocation creates its own {@link Dotenv} instance with no
     * shared mutable state.
     *
     * @param directory the directory in which to look for a {@code .env} file
     * @return an immutable map containing the merged variables
     */
    public static Map<String, String> loadDotEnv(Path directory) {
        Dotenv dotenv = Dotenv.configure()
                .directory(directory.toString())
                .ignoreIfMissing()
                .load();
        return dotenv.entries().stream()
                .collect(Collectors.toUnmodifiableMap(DotenvEntry::getKey, DotenvEntry::getValue));
    }
}
