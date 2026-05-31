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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Represents a loaded test suite.
 *
 * <p>{@code filePath} is not present in the YAML definition; it is set by {@code TestSuiteLoader}
 * after loading so that downstream components can resolve relative file references (request bodies,
 * schema files, expected response files) against the directory containing the suite file.
 *
 * <p>{@code restClientConfig} maps to the optional {@code rest_client} key in the YAML and carries
 * suite-level HTTP client settings such as a base URL and connect timeout.
 */
public record TestSuite(
        String name,
        @Nullable String description,
        @Nullable @JsonProperty("rest_client") RestClientConfig restClientConfig,
        @Nullable Map<String, String> variables,
        List<TestCase> tests,
        @Nullable @JsonSerialize(using = ToStringSerializer.class) Path filePath) {}
