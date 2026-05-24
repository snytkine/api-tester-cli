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
package io.github.snytkine.apitester.api_tester_cli.thymeleaf;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A {@link Map} wrapper that throws {@link NoSuchElementException} when a requested key is absent,
 * rather than returning {@code null}.
 *
 * <p>Standard Java maps return {@code null} for missing keys, which Thymeleaf silently converts to
 * an empty string. This prevents {@link PassThroughExpressionEvaluator} from ever seeing a failure
 * for unresolved template variables. By throwing instead of returning {@code null}, this map causes
 * OGNL to propagate an exception, which {@link PassThroughExpressionEvaluator} catches and converts
 * back to the original expression string (e.g. {@code ${cli.missingKey}}).
 */
public class StrictVariableMap extends HashMap<String, String> {

  public StrictVariableMap(Map<String, String> source) {
    super(source);
  }

  @Override
  public String get(Object key) {
    if (!containsKey(key)) {
      throw new NoSuchElementException("Template variable not found: " + key);
    }
    return super.get(key);
  }
}
