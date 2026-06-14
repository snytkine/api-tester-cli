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

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import io.github.snytkine.apitester.api_tester_cli.model.ExecutedRequestInfo;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.ReportOptions;
import io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.info.BuildProperties;

/** Unit tests for {@link HtmlReportGenerator}. */
class HtmlReportGeneratorTest {

    @TempDir
    Path tempDir;

    private static BuildProperties buildProperties() {
        Properties props = new Properties();
        props.setProperty("version", "1.0.0-TEST");
        return new BuildProperties(props);
    }

    private final HtmlReportGenerator generator = new HtmlReportGenerator(buildProperties());

    @Test
    void generateWritesSelfContainedHtmlFile() throws Exception {
        TestRunResult result = buildTestRunResult();
        TestSuite suite = buildTestSuite();
        Path outputPath = tempDir.resolve("report.html");

        generator.generate(result, suite, outputPath, ReportOptions.defaults());

        assertThat(outputPath).exists();
        String html = Files.readString(outputPath);
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("<style>");
    }

    @Test
    void defaultOptionsEmbedScriptTag() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, ReportOptions.defaults());
        String html = Files.readString(outputPath);
        assertThat(html).contains("<script");
    }

    @Test
    void noJsOptionOmitsScriptTag() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, new ReportOptions(false, true));
        String html = Files.readString(outputPath);
        assertThat(html).doesNotContain("<script");
    }

    @Test
    void generateIncludesSuiteName() throws Exception {
        TestRunResult result = buildTestRunResult();
        TestSuite suite = buildTestSuite();
        Path outputPath = tempDir.resolve("report.html");

        generator.generate(result, suite, outputPath, ReportOptions.defaults());

        String html = Files.readString(outputPath);
        assertThat(html).contains("Pet Store API");
    }

    @Test
    void generateIncludesSuiteDescription() throws Exception {
        TestRunResult result = buildTestRunResult();
        TestSuite suite = buildTestSuite();
        Path outputPath = tempDir.resolve("report.html");

        generator.generate(result, suite, outputPath, ReportOptions.defaults());

        String html = Files.readString(outputPath);
        assertThat(html).contains("Integration tests for the pet store");
    }

    @Test
    void generateIncludesCounts() throws Exception {
        TestRunResult result = buildTestRunResult();
        TestSuite suite = buildTestSuite();
        Path outputPath = tempDir.resolve("report.html");

        generator.generate(result, suite, outputPath, ReportOptions.defaults());

        String html = Files.readString(outputPath);
        // passed=1, failed=1, skipped=1, error=0, total=3
        assertThat(html).contains(">1<").contains(">0<").contains(">3<");
    }

    @Test
    void generateIncludesAllTestNames() throws Exception {
        TestRunResult result = buildTestRunResult();
        TestSuite suite = buildTestSuite();
        Path outputPath = tempDir.resolve("report.html");

        generator.generate(result, suite, outputPath, ReportOptions.defaults());

        String html = Files.readString(outputPath);
        assertThat(html).contains("Get all pets").contains("Create pet").contains("Skip this test");
    }

    @Test
    void generateIncludesStatusWords() throws Exception {
        TestRunResult result = buildTestRunResult();
        TestSuite suite = buildTestSuite();
        Path outputPath = tempDir.resolve("report.html");

        generator.generate(result, suite, outputPath, ReportOptions.defaults());

        String html = Files.readString(outputPath);
        assertThat(html).contains("PASSED").contains("FAILED").contains("SKIPPED");
    }

    @Test
    void generateIncludesDetailsAndSummaryElements() throws Exception {
        TestRunResult result = buildTestRunResult();
        TestSuite suite = buildTestSuite();
        Path outputPath = tempDir.resolve("report.html");

        generator.generate(result, suite, outputPath, ReportOptions.defaults());

        String html = Files.readString(outputPath);
        assertThat(html).contains("<details").contains("<summary");
    }

    @Test
    void generateIncludesSkipReason() throws Exception {
        TestRunResult result = buildTestRunResult();
        TestSuite suite = buildTestSuite();
        Path outputPath = tempDir.resolve("report.html");

        generator.generate(result, suite, outputPath, ReportOptions.defaults());

        String html = Files.readString(outputPath);
        assertThat(html).contains("not needed in this env");
    }

    @Test
    void generateIncludesFailureDescriptionExpectedAndActual() throws Exception {
        TestRunResult result = buildTestRunResult();
        TestSuite suite = buildTestSuite();
        Path outputPath = tempDir.resolve("report.html");

        generator.generate(result, suite, outputPath, ReportOptions.defaults());

        String html = Files.readString(outputPath);
        assertThat(html).contains("status_code equals 201");
        assertThat(html).contains("201");
        assertThat(html).contains("400");
    }

    @Test
    void defaultOptionsStoreCompactJsonInPreBlock() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, ReportOptions.defaults());
        String html = Files.readString(outputPath);
        // With jsEnabled=true JSON is compact — no newlines inside the pre block
        assertThat(html).contains("[{&quot;id&quot;:1}]");
    }

    @Test
    void noJsOptionPrettyPrintsJsonInPreBlock() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, new ReportOptions(false, true));
        String html = Files.readString(outputPath);
        // With jsEnabled=false JSON is pretty-printed — &quot;id&quot; appears indented
        assertThat(html).contains("&quot;id&quot;");
        // Pretty-printed JSON has newlines inside the <pre> block
        assertThat(html).containsPattern(java.util.regex.Pattern.compile("(?s)<pre[^>]*>.*\\n.*</pre>"));
    }

    @Test
    void defaultOptionsStoreCompactRequestBodyJson() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, ReportOptions.defaults());
        String html = Files.readString(outputPath);
        // The "Create pet" test has request body {"name":"Fido"} — compact with jsEnabled=true
        assertThat(html).contains("{&quot;name&quot;:&quot;Fido&quot;}");
    }

    @Test
    void noJsOptionPrettyPrintsRequestBodyJson() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, new ReportOptions(false, true));
        String html = Files.readString(outputPath);
        // With jsEnabled=false request body JSON is pretty-printed server-side
        assertThat(html).contains("&quot;name&quot;");
        assertThat(html).containsPattern(java.util.regex.Pattern.compile("(?s)<pre[^>]*>.*\\n.*</pre>"));
    }

    @Test
    void generateCreatesParentDirectoryWhenMissing() throws Exception {
        TestRunResult result = buildTestRunResult();
        TestSuite suite = buildTestSuite();
        Path outputPath = tempDir.resolve("subdir/nested/report.html");

        generator.generate(result, suite, outputPath, ReportOptions.defaults());

        assertThat(outputPath).exists();
    }

    @Test
    void minifiedOutputHasNoLeadingWhitespaceOnLines() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, ReportOptions.defaults());
        // Strip <pre> blocks first — their content legitimately has leading whitespace.
        // Only check the structural HTML outside <pre>.
        String htmlOutsidePre = Files.readString(outputPath).replaceAll("(?s)<pre[^>]*>.*?</pre>", "<pre></pre>");
        assertThat(htmlOutsidePre.lines())
                .noneMatch(line -> !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t'));
    }

    @Test
    void minifiedOutputHasNoInterTagWhitespace() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, ReportOptions.defaults());
        String htmlOutsidePre = Files.readString(outputPath).replaceAll("(?s)<pre[^>]*>.*?</pre>", "<pre></pre>");
        assertThat(htmlOutsidePre).doesNotContainPattern(java.util.regex.Pattern.compile(">\\s+<"));
    }

    @Test
    void noMinifyOptionProducesUnminifiedOutput() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, new ReportOptions(true, false));
        String html = Files.readString(outputPath);
        // Unminified output retains the leading indentation on at least some lines
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html.lines().anyMatch(l -> l.startsWith("  "))).isTrue();
    }

    @Test
    void generateIncludesPoweredByFooterWithVersion() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, ReportOptions.defaults());
        String html = Files.readString(outputPath);
        assertThat(html).contains("Powered by Api Tester CLI version");
        assertThat(html).contains("1.0.0-TEST");
    }

    @Test
    void minifiedOutputPreservesPreBlockWhitespace() throws Exception {
        Path outputPath = tempDir.resolve("report.html");
        // Use no-JS so JSON is pretty-printed server-side and has embedded newlines in the <pre>
        generator.generate(buildTestRunResult(), buildTestSuite(), outputPath, new ReportOptions(false, true));
        String html = Files.readString(outputPath);
        assertThat(html).containsPattern(java.util.regex.Pattern.compile("(?s)<pre[^>]*>.*\\n.*</pre>"));
    }

    private static TestSuite buildTestSuite() {
        return new TestSuite("Pet Store API", "Integration tests for the pet store", null, null, List.of(), null, null);
    }

    private static TestRunResult buildTestRunResult() {
        TestCaseResult passed = new TestCaseResult(
                "Get all pets",
                TestResult.PASSED,
                3,
                List.of(),
                null,
                new ExecutedRequestInfo(HttpMethod.GET, "/api/pets", Map.of("Accept", "application/json"), null),
                new ApiResponse(
                        200,
                        Map.of("Content-Type", "application/json"),
                        new ApiResponse.Body("[{\"id\":1}]", List.of(Map.of("id", 1))),
                        45L));

        TestCaseResult failed = new TestCaseResult(
                "Create pet",
                TestResult.FAILED,
                1,
                List.of(new AssertionFailure("status_code equals 201", "201", "400", "Expected 201 but was 400")),
                null,
                new ExecutedRequestInfo(HttpMethod.POST, "/api/pets", null, "{\"name\":\"Fido\"}"),
                new ApiResponse(
                        400,
                        Map.of("Content-Type", "application/json"),
                        new ApiResponse.Body("{\"error\":\"bad request\"}", Map.of("error", "bad request")),
                        120L));

        TestCaseResult skipped = new TestCaseResult(
                "Skip this test", TestResult.SKIPPED, 0, List.of(), "not needed in this env", null, null);

        return new TestRunResult(1L, 1L, 1L, 0L, List.of(passed, failed, skipped), Map.of());
    }
}
