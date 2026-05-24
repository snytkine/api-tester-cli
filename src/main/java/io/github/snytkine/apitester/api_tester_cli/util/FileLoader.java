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

  public static String loadFile(String basePath, String file) throws IOException {
    Path resolved = Path.of(basePath).getParent().resolve(file);
    if (!Files.exists(resolved)) {
      throw new FileNotFoundException("File not found: " + resolved);
    }
    return Files.readString(resolved);
  }

  public static String parseFile(
      String file, Map<String, String> suiteVariables, Map<String, String> variables) {
    Context context = new Context();
    context.setVariable("suite", Map.of("variables", suiteVariables));
    context.setVariable("variables", variables);
    return TEMPLATE_ENGINE.process(file, context);
  }
}
