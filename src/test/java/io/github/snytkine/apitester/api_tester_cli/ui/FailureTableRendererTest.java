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
                List.of(new AssertionFailure("status_code equals 201", null, null)),
                false,
                80);

        assertThat(out).contains("POST /users returns 201");
    }

    @Test
    void singleFailureContainsAssertionLabel() {
        String out = render("my-test", List.of(new AssertionFailure("status_code equals 201", null, null)), false, 80);

        assertThat(out).contains("Assertion");
    }

    @Test
    void singleFailureContainsAssertionMessage() {
        String out = render("my-test", List.of(new AssertionFailure("status code was 400", null, null)), false, 80);

        assertThat(out).contains("status code was 400");
    }

    @Test
    void singleFailureContainsBorderCharacter() {
        String out = render("my-test", List.of(new AssertionFailure("status_code equals 201", null, null)), false, 80);

        assertThat(out).contains("┃");
    }

    @Test
    void nullExpectedAndActualSuppressesThoseRows() {
        String out = render("my-test", List.of(new AssertionFailure("check something", null, null)), false, 80);

        assertThat(out).doesNotContain("Expected");
        assertThat(out).doesNotContain("Actual");
        assertThat(out).contains("Assertion");
    }

    @Test
    void nonNullExpectedAndActualAppearsInOutput() {
        String out = render("my-test", List.of(new AssertionFailure("status equals 201", "201", "400")), false, 80);

        assertThat(out).contains("Expected");
        assertThat(out).contains("Actual");
        assertThat(out).contains("201");
        assertThat(out).contains("400");
    }

    @Test
    void mixedFailuresOnlyShowExpectedActualWhenNonNull() {
        List<AssertionFailure> failures = List.of(
                new AssertionFailure("status_code equals 201", "201", "400"),
                new AssertionFailure("json_path $.id is not null", null, null));

        String out = render("multi-test", failures, false, 80);

        assertThat(out).contains("status_code equals 201");
        assertThat(out).contains("json_path $.id is not null");
        assertThat(out).contains("201");
        assertThat(out).contains("400");
    }

    @Test
    void multipleFailuresAllAssertionMessagesPresent() {
        List<AssertionFailure> failures = List.of(
                new AssertionFailure("expected 200 but was 404", null, null),
                new AssertionFailure("missing header X-Request-Id", null, null));

        String out = render("multi-test", failures, false, 80);

        assertThat(out).contains("expected 200 but was 404");
        assertThat(out).contains("missing header X-Request-Id");
    }

    @Test
    void testNameRowAppearsBeforeAssertionRows() {
        String out = render("my-test-name", List.of(new AssertionFailure("check x", null, null)), false, 80);

        int testNamePos = out.indexOf("my-test-name");
        int assertionPos = out.indexOf("Assertion");
        assertThat(testNamePos).isLessThan(assertionPos);
    }

    // ---------------------------------------------------------------------------
    // Colors (useColors=true)
    // ---------------------------------------------------------------------------

    @Test
    void withColorsOutputContainsAnsiEscapeCodes() {
        String out = render("my-test", List.of(new AssertionFailure("some failure", null, null)), true, 80);

        assertThat(out).contains("\033[");
    }

    @Test
    void assertionRowsAreColoredBlue() {
        String out = render("my-test", List.of(new AssertionFailure("first failure", null, null)), true, 80);

        assertThat(out).contains(FailureTableRenderer.ASSERTION_COLOR);
    }

    @Test
    void multipleAssertionGroupsUseSameColor() {
        String out = render(
                "my-test",
                List.of(
                        new AssertionFailure("first failure", null, null),
                        new AssertionFailure("second failure", null, null)),
                true,
                80);

        assertThat(out).contains(FailureTableRenderer.ASSERTION_COLOR);
        assertThat(out).doesNotContain("\033[32m"); // no green — single consistent color
    }

    @Test
    void withoutColorsOutputContainsNoAnsiEscapeCodes() {
        String out = render("my-test", List.of(new AssertionFailure("some failure", null, null)), false, 80);

        assertThat(out).doesNotContain("\033[");
    }

    @Test
    void expectedAndActualRowsColoredWhenNonNull() {
        String out = render("my-test", List.of(new AssertionFailure("failed", "expect", "actual")), true, 80);

        assertThat(out).contains(FailureTableRenderer.ASSERTION_COLOR);
        assertThat(out).contains("Expected");
        assertThat(out).contains("Actual");
    }
}
