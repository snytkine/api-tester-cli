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

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 *
 * <p>{@code templateContent} holds the raw YAML file content as read from disk before any
 * Thymeleaf processing. It is populated by {@code TestSuiteLoader} and is not part of the YAML
 * schema — Jackson skips it during deserialization ({@code @JsonIgnore}).
 */
public record TestSuite(
        String name,
        @Nullable String description,
        @Nullable @JsonProperty("rest_client") RestClientConfig restClientConfig,
        @Nullable Map<String, String> variables,
        List<TestCase> tests,
        @Nullable @JsonSerialize(using = ToStringSerializer.class) Path filePath,
        @JsonIgnore @Nullable String templateContent) {

    /**
     * Returns a new {@link TestSuite} that is identical to this one except that the {@code tests}
     * list is replaced by {@code filteredTests}. All other fields — name, description, REST-client
     * config, variables, file path, and template content — are carried over unchanged.
     *
     * <p>Intended for use in {@link
     * io.github.snytkine.apitester.api_tester_cli.commands.RunSuiteCommand} to narrow the test list
     * when a {@code --tag} filter is applied before execution begins.
     *
     * @param filteredTests the replacement test-case list; may be empty but must not be {@code null}
     * @return a new {@link TestSuite} with the supplied test list
     */
    public TestSuite withFilteredTests(List<TestCase> filteredTests) {
        return new TestSuite(name, description, restClientConfig, variables, filteredTests, filePath, templateContent);
    }
}
