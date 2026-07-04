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
import java.util.LinkedHashMap;
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
 * <p>A suite declares its HTTP client(s) using exactly one of two YAML keys: {@code restClient}
 * maps to the singular {@code rest-client} key (shorthand for a single default client), and {@code
 * restClients} maps to the plural {@code rest-clients} list (multiple named clients, each with an
 * {@code id}). These are stored as parsed so that {@link
 * io.github.snytkine.apitester.api_tester_cli.service.TestSuiteValidator} can enforce that exactly
 * one is present. Use {@link #restClientsById()} / {@link #defaultRestClient()} to obtain the
 * normalized id-to-config view consumed by the engine.
 *
 * <p>{@code templateContent} holds the raw YAML file content as read from disk before any
 * Thymeleaf processing. It is populated by {@code TestSuiteLoader} and is not part of the YAML
 * schema — Jackson skips it during deserialization ({@code @JsonIgnore}).
 */
public record TestSuite(
        String name,
        @Nullable String description,
        @Nullable @JsonProperty("rest-client") RestClientConfig restClient,
        @Nullable @JsonProperty("rest-clients") List<RestClientConfig> restClients,
        @Nullable Map<String, String> variables,
        List<TestCase> tests,
        @Nullable @JsonSerialize(using = ToStringSerializer.class) Path filePath,
        @JsonIgnore @Nullable String templateContent) {

    /** The id under which the default REST client is registered. */
    public static final String DEFAULT_REST_CLIENT_ID = "default";

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
        return new TestSuite(
                name, description, restClient, restClients, variables, filteredTests, filePath, templateContent);
    }

    /**
     * Normalizes this suite's REST client declaration into an id-to-config map.
     *
     * <p>The singular {@code rest-client} form yields a single entry keyed by {@link
     * #DEFAULT_REST_CLIENT_ID}. For the plural {@code rest-clients} form, a single entry whose {@code
     * id} is {@code null} is also keyed by {@link #DEFAULT_REST_CLIENT_ID}; otherwise each entry is
     * keyed by its own {@code id}. Insertion order is preserved.
     *
     * <p>This method assumes the suite has already passed {@link
     * io.github.snytkine.apitester.api_tester_cli.service.TestSuiteValidator#validateRestClients}, so
     * that exactly one form is present and ids are unique. If neither form is present an empty map is
     * returned.
     *
     * @return an ordered, possibly-empty map of client id to {@link RestClientConfig}
     */
    public Map<String, RestClientConfig> restClientsById() {
        Map<String, RestClientConfig> byId = new LinkedHashMap<>();
        if (restClient != null) {
            byId.put(DEFAULT_REST_CLIENT_ID, restClient);
        } else if (restClients != null) {
            if (restClients.size() == 1 && restClients.get(0).id() == null) {
                byId.put(DEFAULT_REST_CLIENT_ID, restClients.get(0));
            } else {
                for (RestClientConfig config : restClients) {
                    byId.put(config.id(), config);
                }
            }
        }
        return byId;
    }

    /**
     * Returns the default REST client — the one used by requests that do not select a client by id.
     *
     * @return the {@link RestClientConfig} registered under {@link #DEFAULT_REST_CLIENT_ID}, or
     *     {@code null} when no default client is defined
     */
    public @Nullable RestClientConfig defaultRestClient() {
        return restClientsById().get(DEFAULT_REST_CLIENT_ID);
    }
}
