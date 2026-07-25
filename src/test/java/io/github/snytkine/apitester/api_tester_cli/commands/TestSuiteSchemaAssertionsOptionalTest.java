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
package io.github.snytkine.apitester.api_tester_cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Validates suite YAML against the bundled {@code test-suite-schema.json} — the schema exported by
 * {@link ExportSchemaCommand} and wired into users' editors — to lock in that a test case's {@code
 * assertions} list is optional.
 *
 * <p>The schema is the only place where the "at least one assertion" rule was ever enforced (the
 * engine never required it), so a schema regression would silently reintroduce red squiggles in the
 * editor for suites the CLI runs happily.
 */
class TestSuiteSchemaAssertionsOptionalTest {

    private static final String REST_CLIENT = "rest-client:\n  base-url: \"http://api.test\"\n";

    private ObjectMapper yamlMapper;
    private JsonSchema schema;

    @BeforeEach
    void setUp() throws Exception {
        yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = getClass().getResourceAsStream("/schemas/test-suite-schema.json")) {
            ObjectNode schemaNode = (ObjectNode) new ObjectMapper().readTree(in);
            // The shipped schema carries a relative "$id" ("test-suite-schema.json") that editors
            // resolve against the file's own location. This validator rejects a non-absolute $id, so
            // it is dropped here; it affects only reference resolution, not the rules under test.
            schemaNode.remove("$id");
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schemaNode);
        }
    }

    private Set<ValidationMessage> validate(String yaml) throws Exception {
        return schema.validate(yamlMapper.readTree(yaml));
    }

    @Test
    void testCaseWithoutAssertionsKeyIsValid() throws Exception {
        Set<ValidationMessage> errors = validate("name: \"Suite\"\n"
                + REST_CLIENT
                + "tests:\n"
                + "- name: \"GET objects\"\n"
                + "  request:\n"
                + "    method: \"GET\"\n"
                + "    url: \"/objects\"\n");

        assertThat(errors).isEmpty();
    }

    @Test
    void testCaseWithAnEmptyAssertionsListIsValid() throws Exception {
        Set<ValidationMessage> errors = validate("name: \"Suite\"\n"
                + REST_CLIENT
                + "tests:\n"
                + "- name: \"GET objects\"\n"
                + "  request:\n"
                + "    method: \"GET\"\n"
                + "    url: \"/objects\"\n"
                + "  assertions: []\n");

        assertThat(errors).isEmpty();
    }

    @Test
    void testCaseWithAssertionsIsStillValid() throws Exception {
        Set<ValidationMessage> errors = validate("name: \"Suite\"\n"
                + REST_CLIENT
                + "tests:\n"
                + "- name: \"GET objects\"\n"
                + "  request:\n"
                + "    method: \"GET\"\n"
                + "    url: \"/objects\"\n"
                + "  assertions:\n"
                + "  - type: \"status_code\"\n"
                + "    expected: 200\n");

        assertThat(errors).isEmpty();
    }

    @Test
    void testCaseWithoutNameOrRequestIsStillInvalid() throws Exception {
        Set<ValidationMessage> errors =
                validate("name: \"Suite\"\n" + REST_CLIENT + "tests:\n" + "- skip: \"later\"\n");

        assertThat(errors).isNotEmpty();
    }
}
