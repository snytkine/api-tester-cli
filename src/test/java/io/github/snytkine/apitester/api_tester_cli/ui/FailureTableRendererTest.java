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
package io.github.snytkine.apitester.api_tester_cli.ui;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link FailureTableRenderer}. */
class FailureTableRendererTest {

    private static String render(String testName, List<AssertionFailure> failures, boolean useColors, int width) {
        StringWriter sw = new StringWriter();
        new FailureTableRenderer().render(testName, failures, useColors, width, new PrintWriter(sw));
        return sw.toString();
    }

    // ---------------------------------------------------------------------------
    // Structure (useColors=false)
    // ---------------------------------------------------------------------------

    @Test
    void singleFailureContainsTestName() {
        String out = render(
                "POST /users returns 201",
                List.of(new AssertionFailure("status_code equals 201", null, null, null)),
                false,
                80);

        assertThat(out).contains("POST /users returns 201");
    }

    @Test
    void singleFailureContainsAssertionLabel() {
        String out =
                render("my-test", List.of(new AssertionFailure("status_code equals 201", null, null, null)), false, 80);

        assertThat(out).contains("Assertion");
    }

    @Test
    void singleFailureContainsAssertionMessage() {
        String out =
                render("my-test", List.of(new AssertionFailure("status code was 400", null, null, null)), false, 80);

        assertThat(out).contains("status code was 400");
    }

    @Test
    void singleFailureContainsBorderCharacter() {
        String out =
                render("my-test", List.of(new AssertionFailure("status_code equals 201", null, null, null)), false, 80);

        assertThat(out).contains("┃");
    }

    @Test
    void nullExpectedAndActualSuppressesThoseRows() {
        String out = render("my-test", List.of(new AssertionFailure("check something", null, null, null)), false, 80);

        assertThat(out).doesNotContain("Expected");
        assertThat(out).doesNotContain("Actual");
        assertThat(out).contains("Assertion");
    }

    @Test
    void nonNullExpectedAndActualAppearsInOutput() {
        String out =
                render("my-test", List.of(new AssertionFailure("status equals 201", "201", "400", null)), false, 80);

        assertThat(out).contains("Expected");
        assertThat(out).contains("Actual");
        assertThat(out).contains("201");
        assertThat(out).contains("400");
    }

    @Test
    void mixedFailuresOnlyShowExpectedActualWhenNonNull() {
        List<AssertionFailure> failures = List.of(
                new AssertionFailure("status_code equals 201", "201", "400", null),
                new AssertionFailure("json_path $.id is not null", null, null, null));

        String out = render("multi-test", failures, false, 80);

        assertThat(out).contains("status_code equals 201");
        assertThat(out).contains("json_path $.id is not null");
        assertThat(out).contains("201");
        assertThat(out).contains("400");
    }

    @Test
    void multipleFailuresAllAssertionMessagesPresent() {
        List<AssertionFailure> failures = List.of(
                new AssertionFailure("expected 200 but was 404", null, null, null),
                new AssertionFailure("missing header X-Request-Id", null, null, null));

        String out = render("multi-test", failures, false, 80);

        assertThat(out).contains("expected 200 but was 404");
        assertThat(out).contains("missing header X-Request-Id");
    }

    @Test
    void testNameRowAppearsBeforeAssertionRows() {
        String out = render("my-test-name", List.of(new AssertionFailure("check x", null, null, null)), false, 80);

        int testNamePos = out.indexOf("my-test-name");
        int assertionPos = out.indexOf("Assertion");
        assertThat(testNamePos).isLessThan(assertionPos);
    }

    // ---------------------------------------------------------------------------
    // Colors (useColors=true)
    // ---------------------------------------------------------------------------

    @Test
    void withColorsOutputContainsAnsiEscapeCodes() {
        String out = render("my-test", List.of(new AssertionFailure("some failure", null, null, null)), true, 80);

        assertThat(out).contains("\033[");
    }

    @Test
    void testNameRowIsInvertedWhenColorsEnabled() {
        String out = render("my-test", List.of(new AssertionFailure("first failure", null, null, null)), true, 80);

        String testNameLine = java.util.Arrays.stream(out.split("\n"))
                .filter(l -> l.contains("Test Name"))
                .findFirst()
                .orElse("");
        // Inverse-video wraps the inner content only; the outer ┃ borders must remain outside the
        // escape sequence to avoid rendering artefacts at the border characters.
        assertThat(testNameLine).contains(FailureTableRenderer.INVERSE_VIDEO);
        assertThat(testNameLine).startsWith("┃");
        assertThat(testNameLine).doesNotStartWith(FailureTableRenderer.INVERSE_VIDEO);
        assertThat(testNameLine).endsWith("┃");
    }

    @Test
    void assertionRowsHaveAssertionColorWhenColorsEnabled() {
        String out = render(
                "my-test",
                List.of(
                        new AssertionFailure("first failure", null, null, null),
                        new AssertionFailure("second failure", null, null, null)),
                true,
                80);

        java.util.Arrays.stream(out.split("\n"))
                .filter(l -> l.contains("┃ Assertion"))
                .forEach(line -> assertThat(line).contains(FailureTableRenderer.ASSERTION_COLOR));
    }

    @Test
    void expectedRowHasExpectedColorWhenColorsEnabled() {
        String out =
                render("my-test", List.of(new AssertionFailure("failed", "exp-value", "act-value", null)), true, 80);

        // After colorization the Expected line is: ┃<EXPECTED_COLOR> Expected ...<RESET>┃
        // Filter on the label text which is inside the escape sequence.
        java.util.Arrays.stream(out.split("\n"))
                .filter(l -> l.contains("Expected"))
                .forEach(line -> assertThat(line).contains(FailureTableRenderer.EXPECTED_COLOR));
    }

    @Test
    void actualRowHasNoColorWhenColorsEnabled() {
        String out =
                render("my-test", List.of(new AssertionFailure("failed", "exp-value", "act-value", null)), true, 80);

        java.util.Arrays.stream(out.split("\n"))
                .filter(l -> l.contains("Actual"))
                .forEach(line -> assertThat(line).doesNotContain("\033["));
    }

    @Test
    void withoutColorsOutputContainsNoAnsiEscapeCodes() {
        String out = render("my-test", List.of(new AssertionFailure("some failure", null, null, null)), false, 80);

        assertThat(out).doesNotContain("\033[");
    }

    @Test
    void expectedAndActualRowsAppearWhenNonNull() {
        String out = render("my-test", List.of(new AssertionFailure("failed", "expect", "actual", null)), true, 80);

        assertThat(out).contains("Expected");
        assertThat(out).contains("Actual");
    }

    // ---------------------------------------------------------------------------
    // Error row (issue 24)
    // ---------------------------------------------------------------------------

    @Test
    void errorRowAppearsAfterActualWhenBothPresent() {
        String out = render(
                "my-test",
                List.of(new AssertionFailure(
                        "status_code equals 201", "201", "400", "Expected status code 201 but was 400")),
                false,
                80);

        assertThat(out).contains("Expected");
        assertThat(out).contains("Actual");
        assertThat(out).contains("Error");
        assertThat(out).contains("Expected status code 201 but was 400");
        int actualPos = out.indexOf("Actual");
        int errorPos = out.indexOf("Error");
        assertThat(actualPos).isLessThan(errorPos);
    }

    @Test
    void errorOnlyRowWhenActualIsNull() {
        String out = render(
                "my-test",
                List.of(new AssertionFailure("json_schema response.body", null, null, "Schema validation failed: …")),
                false,
                80);

        assertThat(out).contains("Error");
        assertThat(out).contains("Schema validation failed");
        assertThat(out).doesNotContain("Expected");
        assertThat(out).doesNotContain("Actual");
    }

    @Test
    void noErrorRowWhenErrorFieldIsNull() {
        String out = render("my-test", List.of(new AssertionFailure("check something", null, null, null)), false, 80);

        assertThat(out).doesNotContain("Error");
    }

    @Test
    void errorRowHasErrorColorWhenColorsEnabled() {
        String out = render(
                "my-test",
                List.of(new AssertionFailure("json_schema response.body", null, null, "Schema validation failed")),
                true,
                80);

        // After colorization the line is: ┃<ERROR_COLOR> ✗ Error ...<RESET>┃
        // so "┃ ✗" is split by the escape; filter on the ballot-X character instead.
        String errorLine = java.util.Arrays.stream(out.split("\n"))
                .filter(l -> l.contains("✗"))
                .findFirst()
                .orElse("");
        assertThat(errorLine).isNotEmpty();
        assertThat(errorLine).contains(FailureTableRenderer.ERROR_COLOR);
    }

    @Test
    void errorLabelContainsUnicodeIndicator() {
        assertThat(FailureTableRenderer.ERROR_LABEL).contains("✗");
    }

    @Test
    void multiLineErrorAllContinuationLinesHaveErrorColor() {
        // Render at a narrow width so the error text is forced to wrap
        String longError = "Schema validation failed: property 'id' is required but missing in the response body";
        String out = render(
                "my-test", List.of(new AssertionFailure("json_schema response.body", null, null, longError)), true, 60);

        long coloredLines = java.util.Arrays.stream(out.split("\n"))
                .filter(l -> l.contains(FailureTableRenderer.ERROR_COLOR))
                .count();
        // There must be at least two lines carrying the error colour (label + ≥1 continuation)
        assertThat(coloredLines).isGreaterThanOrEqualTo(2);
        // Every content line that carries the error text fragment must be red
        java.util.Arrays.stream(out.split("\n"))
                .filter(l -> l.contains("Schema") || l.contains("missing"))
                .forEach(line -> assertThat(line).contains(FailureTableRenderer.ERROR_COLOR));
    }

    // ---------------------------------------------------------------------------
    // renderParentFailure (depends-on parent-failure)
    // ---------------------------------------------------------------------------

    private static String renderParentFailure(String testName, String message, boolean useColors, int width) {
        StringWriter sw = new StringWriter();
        new FailureTableRenderer().renderParentFailure(testName, message, useColors, width, new PrintWriter(sw));
        return sw.toString();
    }

    @Test
    void parentFailureShowsTestNameAndMessage() {
        String out = renderParentFailure(
                "get-widget", "This test depends on a failed parent test \"create-widget\".", false, 80);

        assertThat(out).contains("get-widget");
        assertThat(out).contains("This test depends on a failed parent test \"create-widget\".");
    }

    @Test
    void parentFailureShowsErrorRowButNoAssertionExpectedActualRows() {
        String out = renderParentFailure(
                "get-widget", "This test depends on a failed parent test \"create-widget\".", false, 80);

        assertThat(out).contains(FailureTableRenderer.ERROR_LABEL);
        assertThat(out).doesNotContain("Assertion");
        assertThat(out).doesNotContain("Expected");
        assertThat(out).doesNotContain("Actual");
    }

    @Test
    void parentFailureErrorRowIsRedWhenColorsEnabled() {
        String out = renderParentFailure(
                "get-widget", "This test depends on a failed parent test \"create-widget\".", true, 80);

        String errorLine = java.util.Arrays.stream(out.split("\n"))
                .filter(l -> l.contains("✗"))
                .findFirst()
                .orElse("");
        assertThat(errorLine).isNotEmpty();
        assertThat(errorLine).contains(FailureTableRenderer.ERROR_COLOR);
    }

    // ---------------------------------------------------------------------------
    // renderError (test result ERROR — e.g. HTTP I/O failure such as connection refused)
    // ---------------------------------------------------------------------------

    private static String renderError(String testName, List<AssertionFailure> failures, boolean useColors, int width) {
        StringWriter sw = new StringWriter();
        new FailureTableRenderer().renderError(testName, failures, useColors, width, new PrintWriter(sw));
        return sw.toString();
    }

    @Test
    void errorShowsTestNameAndErrorTextButNoAssertionRow() {
        String out = renderError(
                "create-pet",
                List.of(new AssertionFailure(
                        "I/O error on POST request for \"http://localhost:9999/pets\": Connection refused",
                        null,
                        null,
                        null)),
                false,
                80);

        assertThat(out).contains("create-pet");
        assertThat(out).contains(FailureTableRenderer.ERROR_LABEL);
        assertThat(out).contains("Connection refused");
        // The error text must never be rendered as a (green) Assertion row.
        assertThat(out).doesNotContain("Assertion");
        assertThat(out).doesNotContain("Expected");
        assertThat(out).doesNotContain("Actual");
    }

    @Test
    void errorRowIsRedWhenColorsEnabled() {
        String out = renderError(
                "create-pet", List.of(new AssertionFailure("Connection refused", null, null, null)), true, 80);

        String errorLine = java.util.Arrays.stream(out.split("\n"))
                .filter(l -> l.contains("✗"))
                .findFirst()
                .orElse("");
        assertThat(errorLine).isNotEmpty();
        assertThat(errorLine).contains(FailureTableRenderer.ERROR_COLOR);
        // The assertion (green) colour must not be applied anywhere in an error table.
        assertThat(out).doesNotContain(FailureTableRenderer.ASSERTION_COLOR);
    }

    @Test
    void errorWithNullDescriptionRendersGracefully() {
        String out = renderError("create-pet", List.of(new AssertionFailure(null, null, null, null)), false, 80);

        assertThat(out).contains("create-pet");
        assertThat(out).contains(FailureTableRenderer.ERROR_LABEL);
        assertThat(out).doesNotContain("Assertion");
    }
}
