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
package io.github.snytkine.apitester.api_tester_cli.service.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.ObjectExpectedValue;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.JsonMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import io.github.snytkine.apitester.api_tester_cli.util.FileLoader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates a {@link JsonMatchAssertion} by comparing the response's JSON body to an expected JSON
 * document, with optional top-level field exclusions.
 *
 * <p>The expected JSON is loaded from an inline string or from a file path relative to the suite
 * directory. Fields named in {@link ObjectExpectedValue#ignore()} are removed from both the actual
 * and expected JSON trees before comparison, allowing volatile values (timestamps, generated IDs)
 * to be ignored.
 *
 * <p>Current limitation: only top-level field names are supported in the ignore list. Nested
 * JSONPath expressions are not yet evaluated.
 */
class JsonMatchAssertionEvaluator implements AssertionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(JsonMatchAssertionEvaluator.class);

    private final JsonMatchAssertion assertion;

    @Nullable private final Path suiteDir;

    private final ObjectMapper objectMapper;
    private final Map<String, String> suiteVariables;
    private final Map<String, String> testVariables;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the json_match assertion to evaluate
     * @param suiteDir directory of the test-suite file, used to resolve relative expected-file paths;
     *     may be {@code null} when the suite was not loaded from disk
     * @param objectMapper the Jackson mapper used to parse and compare JSON trees
     * @param suiteVariables resolved suite-level variables forwarded to the Thymeleaf template
     *     processor when the expected file contains template expressions
     * @param testVariables test-case-level variables forwarded to the Thymeleaf template processor
     */
    JsonMatchAssertionEvaluator(
            JsonMatchAssertion assertion,
            @Nullable Path suiteDir,
            ObjectMapper objectMapper,
            Map<String, String> suiteVariables,
            Map<String, String> testVariables) {
        this.assertion = assertion;
        this.suiteDir = suiteDir;
        this.objectMapper = objectMapper;
        this.suiteVariables = suiteVariables;
        this.testVariables = testVariables;
    }

    /**
     * Compares the response JSON body to the expected JSON document, recording any mismatch in {@code
     * collector}.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        if (response.body() == null || response.body().json() == null) {
            collector.fail("Response body is absent or not valid JSON for json_match assertion");
            return;
        }

        String expectedJson;
        try {
            expectedJson = loadContent(assertion.expected());
        } catch (IOException e) {
            collector.fail(
                    "Failed to load expected JSON content '%s': %s",
                    assertion.expected().content(), e.getMessage());
            return;
        }

        try {
            JsonNode actualNode = objectMapper.valueToTree(response.body().json());
            JsonNode expectedNode = objectMapper.readTree(expectedJson);

            removeIgnoredFields(actualNode, assertion.expected().ignore());
            removeIgnoredFields(expectedNode, assertion.expected().ignore());

            String actualSerialized = objectMapper.writeValueAsString(actualNode);
            String expectedSerialized = objectMapper.writeValueAsString(expectedNode);

            collector
                    .assertThat(actualSerialized)
                    .as("JSON body match (ignoring: %s)", assertion.expected().ignore())
                    .isEqualTo(expectedSerialized);
        } catch (Exception e) {
            collector.fail("Failed to compare JSON bodies: %s", e.getMessage());
        }
    }

    /**
     * Loads the expected JSON content from an inline string or from a file relative to the suite
     * directory, then processes the result as a Thymeleaf TEXT-mode template.
     *
     * <p>Template expressions in the expected JSON (e.g. {@code [[${suite.variables.userId}]]}) are
     * resolved against the suite- and test-level variables supplied at construction time. Content
     * without any template expressions is returned unchanged.
     *
     * @param expected the content reference from the assertion
     * @return the template-processed expected JSON string
     * @throws IOException if the file cannot be read
     * @throws IllegalStateException if {@code type} is {@code file} but {@code suiteDir} is null
     */
    private String loadContent(ObjectExpectedValue expected) throws IOException {
        String raw;
        if ("file".equals(expected.type())) {
            if (suiteDir == null) {
                throw new IllegalStateException(
                        "Suite directory is required to resolve file reference: " + expected.content());
            }
            raw = FileLoader.loadFile(suiteDir, expected.content());
        } else {
            raw = expected.content();
        }
        return FileLoader.parseFile(raw, suiteVariables, testVariables);
    }

    /**
     * Removes the named fields from a {@link JsonNode} in place.
     *
     * <p>For an {@link ObjectNode} the fields are removed from the top level. For an {@link
     * ArrayNode} the fields are removed from each object element, allowing ignore lists to work on
     * array responses whose elements share common volatile fields.
     *
     * @param node the JSON node to modify
     * @param fields the field names to remove; no-op when {@code null} or empty
     */
    private void removeIgnoredFields(JsonNode node, @Nullable List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        if (node instanceof ObjectNode objectNode) {
            fields.forEach(objectNode::remove);
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(element -> {
                if (element instanceof ObjectNode objectElement) {
                    fields.forEach(objectElement::remove);
                }
            });
        }
    }
}
