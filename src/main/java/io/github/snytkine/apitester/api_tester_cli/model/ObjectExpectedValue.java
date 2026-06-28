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
package io.github.snytkine.apitester.api_tester_cli.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

/**
 * Holds a content-reference expected value with an optional list of JSON paths to ignore during
 * comparison.
 *
 * <p>Deserialization accepts two YAML forms via {@link ObjectExpectedValueDeserializer}: the full
 * object form ({@code {type, content, ignore}}) and a plain-string shorthand which is normalised to
 * {@code type: inline} with the string as {@code content} and an empty {@code ignore} list.
 *
 * @param type the content-reference type, e.g. {@code inline} or {@code file}
 * @param content the inline content or the file path, depending on {@code type}
 * @param ignore optional top-level field names to exclude from the comparison; may be {@code null}
 */
@JsonDeserialize(using = ObjectExpectedValueDeserializer.class)
public record ObjectExpectedValue(String type, String content, List<String> ignore) {}
