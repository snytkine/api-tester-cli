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
import org.springframework.stereotype.Component;

/**
 * Spring component for loading variables from a {@code .env} file.
 *
 * <p>This class provides an instance method that reads a {@code .env} file from a given directory
 * and merges it with the process's system environment variables. System environment variables take
 * precedence over same-named entries in the {@code .env} file.
 *
 * <p>This class is thread-safe; it holds no mutable state.
 */
@Component
public class DotEnvLoader {

    /**
     * Constructs a {@code DotEnvLoader}. Spring uses this constructor to create the managed singleton;
     * tests may also instantiate directly.
     */
    public DotEnvLoader() {}

    /**
     * Loads variables from a file literally named {@code .env} in {@code directory} and merges them
     * with the process's system environment variables.
     *
     * <p>This is a convenience overload of {@link #loadDotEnv(Path, String)} that always uses the
     * conventional {@code .env} filename.
     *
     * <p>This method is thread-safe; each invocation creates its own {@link Dotenv} instance with no
     * shared mutable state.
     *
     * @param directory the directory in which to look for a {@code .env} file
     * @return an immutable map containing the merged variables
     */
    public Map<String, String> loadDotEnv(Path directory) {
        return loadDotEnv(directory, ".env");
    }

    /**
     * Loads variables from a file named {@code filename} in {@code directory} and merges them with
     * the process's system environment variables.
     *
     * <p>The env file is loaded first; system environment variables are then overlaid on top, so a
     * system variable with the same name as an env-file entry always wins. If no such file exists,
     * only system environment variables are returned — the loader itself never treats a missing file
     * as an error (via {@code ignoreIfMissing()}); enforcing that an explicitly requested file must
     * exist is a caller-side (CLI boundary) concern.
     *
     * <p>This method is thread-safe; each invocation creates its own {@link Dotenv} instance with no
     * shared mutable state.
     *
     * @param directory the directory in which to look for the env file
     * @param filename the name of the env file to load (e.g. {@code .env} or {@code staging.env})
     * @return an immutable map containing the merged variables
     */
    public Map<String, String> loadDotEnv(Path directory, String filename) {
        Dotenv dotenv = Dotenv.configure()
                .directory(directory.toString())
                .filename(filename)
                .ignoreIfMissing()
                .load();
        return dotenv.entries().stream()
                .collect(Collectors.toUnmodifiableMap(DotenvEntry::getKey, DotenvEntry::getValue));
    }
}
