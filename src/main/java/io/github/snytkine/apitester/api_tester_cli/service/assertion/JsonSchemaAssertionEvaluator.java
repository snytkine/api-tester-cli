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
import com.jayway.jsonpath.JsonPath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.ObjectExpectedValue;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.JsonSchemaAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates a {@link JsonSchemaAssertion} by validating a portion of the HTTP response against a
 * JSON Schema document loaded from a file or supplied inline.
 *
 * <p>The schema version is detected automatically from the {@code $schema} keyword in the schema
 * document, supporting Draft 4, 6, 7, 2019-09, and 2020-12. When {@code $schema} is absent, Draft 7
 * is used as the default.
 *
 * <p>The {@code path} field on the assertion follows the same {@code response.*} convention as
 * {@link StringMatchAssertionEvaluator}:
 *
 * <ul>
 *   <li>{@code response.body.json} — validate the entire parsed JSON body
 *   <li>{@code response.body.json.<jsonpath>} — validate the value at the given JSONPath expression
 * </ul>
 *
 * <p>Each schema violation is recorded as an individual soft-assertion failure rather than stopping
 * at the first error, giving the caller a complete picture of all schema violations in one run.
 *
 * <p>This class is package-private and instantiated by {@link AssertionEvaluatorFactory}. It is not
 * a Spring bean; a new instance is created per test case. All state is immutable after
 * construction, so instances are safe to use from multiple threads if shared, though in practice
 * each test case owns its own instance.
 */
class JsonSchemaAssertionEvaluator implements AssertionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaAssertionEvaluator.class);

    private static final String RESPONSE_PREFIX = "response.";
    private static final String BODY_JSON = "body.json";
    private static final String BODY_JSON_DOT = "body.json.";

    private final JsonSchemaAssertion assertion;

    @Nullable private final Path suiteDir;

    private final ObjectMapper objectMapper;

    /**
     * Constructs the evaluator for the given assertion.
     *
     * @param assertion the json_schema assertion to evaluate
     * @param suiteDir directory of the test-suite file, used to resolve relative schema file paths;
     *     may be {@code null} when the suite was not loaded from disk
     * @param objectMapper the Jackson mapper used to parse the schema and convert response values to
     *     {@link JsonNode} for validation
     */
    JsonSchemaAssertionEvaluator(JsonSchemaAssertion assertion, @Nullable Path suiteDir, ObjectMapper objectMapper) {
        this.assertion = assertion;
        this.suiteDir = suiteDir;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates the response value at {@code assertion.path()} against the JSON Schema specified by
     * {@code assertion.expected()}, recording each schema violation as a separate failure.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     */
    @Override
    public void evaluate(ApiResponse response, FailureCollector collector) {
        if (response.body() == null || response.body().json() == null) {
            collector.fail("Response body is absent or not valid JSON for json_schema assertion");
            return;
        }

        JsonNode targetNode = extractTarget(response, collector);
        if (targetNode == null) {
            return;
        }

        String schemaContent;
        try {
            schemaContent = loadContent(assertion.expected());
        } catch (IOException e) {
            collector.fail(
                    "Failed to load schema '%s': %s", assertion.expected().content(), e.getMessage());
            return;
        }

        try {
            JsonNode schemaNode = objectMapper.readTree(schemaContent);
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(detectVersion(schemaNode));
            JsonSchema schema = factory.getSchema(schemaNode);
            Set<ValidationMessage> errors = schema.validate(targetNode);
            errors.forEach(error -> collector.fail("JSON schema violation: %s", error.getMessage()));
        } catch (Exception e) {
            collector.fail("JSON schema validation failed: %s", e.getMessage());
        }
    }

    /**
     * Extracts the response value to validate based on {@code assertion.path()}.
     *
     * <p>Supports {@code response.body.json} (the full parsed body) and {@code
     * response.body.json.<expr>} (a JSONPath sub-expression). Records a failure and returns {@code
     * null} for unsupported paths.
     *
     * @param response the captured HTTP response
     * @param collector the shared failure collector
     * @return the extracted {@link JsonNode}, or {@code null} if the path could not be resolved
     */
    @Nullable private JsonNode extractTarget(ApiResponse response, FailureCollector collector) {
        String path = assertion.path();
        if (!path.startsWith(RESPONSE_PREFIX)) {
            collector.fail("Unsupported path '%s': must start with 'response.'", path);
            return null;
        }
        String remaining = path.substring(RESPONSE_PREFIX.length());

        if (remaining.equals(BODY_JSON)) {
            return objectMapper.valueToTree(response.body().json());
        }

        if (remaining.startsWith(BODY_JSON_DOT)) {
            String jsonPathExpr = remaining.substring(BODY_JSON_DOT.length());
            if (response.body().text() == null) {
                collector.fail("Response body text is absent when evaluating path '%s'", path);
                return null;
            }
            try {
                Object value = JsonPath.read(response.body().text(), jsonPathExpr);
                return objectMapper.valueToTree(value);
            } catch (Exception e) {
                collector.fail("Failed to extract JSONPath '%s' from response body: %s", jsonPathExpr, e.getMessage());
                return null;
            }
        }

        collector.fail("Unsupported path '%s' for json_schema assertion", path);
        return null;
    }

    /**
     * Detects the JSON Schema version from the {@code $schema} keyword in the parsed schema document.
     *
     * <p>Recognises the standard draft URIs for Draft 4, 6, 7, 2019-09, and 2020-12. Returns {@link
     * SpecVersion.VersionFlag#V7} when the keyword is absent or unrecognised.
     *
     * @param schemaNode the parsed schema document
     * @return the matching {@link SpecVersion.VersionFlag}
     */
    private SpecVersion.VersionFlag detectVersion(JsonNode schemaNode) {
        JsonNode schemaUri = schemaNode.get("$schema");
        if (schemaUri != null) {
            String uri = schemaUri.asText();
            if (uri.contains("2020-12")) return SpecVersion.VersionFlag.V202012;
            if (uri.contains("2019-09")) return SpecVersion.VersionFlag.V201909;
            if (uri.contains("draft-07") || uri.contains("draft/07")) return SpecVersion.VersionFlag.V7;
            if (uri.contains("draft-06") || uri.contains("draft/06")) return SpecVersion.VersionFlag.V6;
            if (uri.contains("draft-04") || uri.contains("draft/04")) return SpecVersion.VersionFlag.V4;
        }
        return SpecVersion.VersionFlag.V7;
    }

    /**
     * Loads the schema content from an inline string or from a file relative to the suite directory.
     *
     * @param expected the content reference from the assertion
     * @return the raw schema JSON string
     * @throws IOException if the file cannot be read
     * @throws IllegalStateException if {@code type} is {@code file} but {@code suiteDir} is null
     */
    private String loadContent(ObjectExpectedValue expected) throws IOException {
        if ("file".equals(expected.type())) {
            if (suiteDir == null) {
                throw new IllegalStateException(
                        "Suite directory is required to resolve file reference: " + expected.content());
            }
            return Files.readString(suiteDir.resolve(expected.content()));
        }
        return expected.content();
    }
}
