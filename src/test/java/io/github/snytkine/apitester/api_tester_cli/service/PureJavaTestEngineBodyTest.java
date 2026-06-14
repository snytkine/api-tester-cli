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
package io.github.snytkine.apitester.api_tester_cli.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.snytkine.apitester.api_tester_cli.model.BodyType;
import io.github.snytkine.apitester.api_tester_cli.model.RequestBody;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link PureJavaTestEngine#loadBodyContent}.
 *
 * <p>This is a package-private static helper, tested here in the same package to avoid exposing it
 * in the public API.
 */
class PureJavaTestEngineBodyTest {

    // --- loadBodyContent: STRING type ---

    @Test
    void stringTypeReturnsContentAsIs() throws IOException {
        RequestBody body = new RequestBody(BodyType.STRING, "hello world");

        String result = PureJavaTestEngine.loadBodyContent(body, null, Map.of());

        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void stringTypeDoesNotProcessThymeleafExpressions() throws IOException {
        RequestBody body = new RequestBody(BodyType.STRING, "[[${test.name}]]");

        String result = PureJavaTestEngine.loadBodyContent(body, null, Map.of("test", Map.of("name", "Alice")));

        assertThat(result).isEqualTo("[[${test.name}]]");
    }

    // --- loadBodyContent: FILE type ---

    @Test
    void fileTypeLoadsFileAndProcessesThymeleafTemplate(@TempDir Path tempDir) throws IOException {
        Path bodyFile = tempDir.resolve("body.json");
        Files.writeString(bodyFile, "{\"name\":\"[[${test.username}]]\"}");
        RequestBody body = new RequestBody(BodyType.FILE, "body.json");

        String result = PureJavaTestEngine.loadBodyContent(body, tempDir, Map.of("test", Map.of("username", "alice")));

        assertThat(result).isEqualTo("{\"name\":\"alice\"}");
    }

    @Test
    void fileTypeResolvesSuiteVariablesInTemplate(@TempDir Path tempDir) throws IOException {
        Path bodyFile = tempDir.resolve("body.json");
        Files.writeString(bodyFile, "{\"id\":\"[[${suite.request_id}]]\"}");
        RequestBody body = new RequestBody(BodyType.FILE, "body.json");

        String result =
                PureJavaTestEngine.loadBodyContent(body, tempDir, Map.of("suite", Map.of("request_id", "abc-123")));

        assertThat(result).isEqualTo("{\"id\":\"abc-123\"}");
    }

    @Test
    void fileTypeResolvesExistingTestFixture() throws Exception {
        Path suiteDir =
                Path.of(getClass().getResource("/request-body-1.json").toURI()).getParent();
        RequestBody body = new RequestBody(BodyType.FILE, "request-body-1.json");

        String result = PureJavaTestEngine.loadBodyContent(
                body, suiteDir, Map.of("suite", Map.of("request_id", "req-99"), "test", Map.of("username", "jsmith")));

        assertThat(result).contains("\"username\": \"jsmith\"");
        assertThat(result).contains("\"requestID\": \"req-99\"");
        assertThat(result).contains("\"firstName\": \"John\"");
    }

    @Test
    void fileTypeThrowsIllegalStateWhenSuiteDirIsNull() {
        RequestBody body = new RequestBody(BodyType.FILE, "body.json");

        assertThatThrownBy(() -> PureJavaTestEngine.loadBodyContent(body, null, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("body.json");
    }

    @Test
    void fileTypeThrowsFileNotFoundWhenFileDoesNotExist(@TempDir Path tempDir) {
        RequestBody body = new RequestBody(BodyType.FILE, "nonexistent.json");

        assertThatThrownBy(() -> PureJavaTestEngine.loadBodyContent(body, tempDir, Map.of()))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("nonexistent.json");
    }

    // --- loadBodyContent: unsupported types ---

    @ParameterizedTest
    @EnumSource(
            value = BodyType.class,
            names = {"JSON", "XML", "FORM_DATA"})
    void unsupportedTypeThrowsUnsupportedOperationException(BodyType type) {
        RequestBody body = new RequestBody(type, "content");

        assertThatThrownBy(() -> PureJavaTestEngine.loadBodyContent(body, null, Map.of()))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining(type.name());
    }
}
