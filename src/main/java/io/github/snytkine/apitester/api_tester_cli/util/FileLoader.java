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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

public class FileLoader {

    private static final TemplateEngine TEMPLATE_ENGINE;

    static {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.TEXT);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        TEMPLATE_ENGINE = engine;
    }

    private FileLoader() {}

    /**
     * Reads a file relative to the given base file path.
     *
     * <p>The directory containing {@code basePath} is used as the base for resolving {@code file}.
     * Use this overload when you have the suite file path (not the directory).
     *
     * @param basePath path to the suite file; its parent directory is used for resolution
     * @param file file name or relative path to resolve against the suite directory
     * @return the file contents as a string
     * @throws FileNotFoundException if the resolved path does not exist
     * @throws IOException if the file cannot be read
     */
    public static String loadFile(String basePath, String file) throws IOException {
        return loadFile(Path.of(basePath).getParent(), file);
    }

    /**
     * Reads a file relative to the given directory.
     *
     * @param directory the base directory used to resolve {@code file}
     * @param file file name or relative path to resolve against {@code directory}
     * @return the file contents as a string
     * @throws FileNotFoundException if the resolved path does not exist
     * @throws IOException if the file cannot be read
     */
    public static String loadFile(Path directory, String file) throws IOException {
        Path resolved = directory.resolve(file);
        if (!Files.exists(resolved)) {
            throw new FileNotFoundException("File not found: " + resolved);
        }
        return Files.readString(resolved);
    }

    /**
     * Processes a file contents string as a Thymeleaf TEXT-mode template.
     *
     * <p>Each entry in {@code configMap} is set as a top-level context variable, so a map keyed by
     * {@code "suite"}, {@code "test"}, {@code "cli"}, and {@code "env"} is accessible in templates as
     * {@code [[${suite.myKey}]]}, {@code [[${test.myKey}]]}, etc.
     *
     * @param file the raw template string
     * @param configMap all variable namespaces to expose in the template context
     * @return the processed template result
     */
    public static String parseFile(String file, Map<String, Map<String, String>> configMap) {
        Context context = new Context();
        configMap.forEach(context::setVariable);
        return TEMPLATE_ENGINE.process(file, context);
    }
}
