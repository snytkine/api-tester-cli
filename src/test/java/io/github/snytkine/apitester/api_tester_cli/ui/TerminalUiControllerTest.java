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

import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.event.TestStatus;
import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TerminalUiController}. All tests run the controller in a background thread
 * and capture its output via a {@link StringWriter}; no Spring Shell TUI infrastructure is
 * required.
 */
class TerminalUiControllerTest {

    // ---------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------

    private static TerminalUiController controller(LinkedBlockingQueue<TestProgressEvent> queue, StringWriter capture) {
        return new TerminalUiController(queue, false, 80, new PrintWriter(capture));
    }

    private static TerminalUiController colorController(
            LinkedBlockingQueue<TestProgressEvent> queue, StringWriter capture) {
        return new TerminalUiController(queue, true, 80, new PrintWriter(capture));
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    @Test
    void controllerTerminatesAfterSuiteCompleted() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = controller(queue, new StringWriter());
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "test-one"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "test-one", TestStatus.PASS, 100L, 1, List.of()));
        queue.offer(new TestProgressEvent.TestStarted("1", 1, "test-two"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "1",
                1,
                "test-two",
                TestStatus.FAIL,
                200L,
                1,
                List.of(new AssertionFailure("failed", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 1, 0L, 0L, 300L));

        // await() completes only when the controller thread exits — verifies clean termination
        ctrl.await();
    }

    @Test
    void controllerAbortsWhenFirstEventIsNotSuiteStarted() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteCompleted(0, 0, 0L, 0L, 0L));

        ctrl.await();

        // Nothing should have been written — the controller aborted before drawing
        assertThat(capture.toString()).isEmpty();
    }

    @Test
    void controllerHandlesZeroTestSuite() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("empty-suite", 0, Instant.now()));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 0, 0L, 0L, 0L));

        ctrl.await();

        // Grid headers must appear even for zero-test suites
        String out = capture.toString();
        assertThat(out).contains("Test Name");
        assertThat(out).contains("Status");
    }

    @Test
    void controllerWithColorsEnabledTerminatesCleanly() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = new TerminalUiController(queue, true, 80, new PrintWriter(new StringWriter()));
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "green-test"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "green-test", TestStatus.PASS, 42L, 2, List.of()));
        queue.offer(new TestProgressEvent.TestStarted("1", 1, "red-test"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "1",
                1,
                "red-test",
                TestStatus.FAIL,
                99L,
                2,
                List.of(new AssertionFailure("assertion failed", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 1, 0L, 0L, 141L));

        ctrl.await();
    }

    // ---------------------------------------------------------------------------
    // Grid output
    // ---------------------------------------------------------------------------

    @Test
    void gridHeadersArePresentInOutputAfterSuiteStarted() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("my-suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "t"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "t", TestStatus.PASS, 10L, 1, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 0, 0L, 0L, 10L));

        ctrl.await();

        String out = capture.toString();
        assertThat(out).contains("Test Name");
        assertThat(out).contains("Status");
        assertThat(out).contains("Response Time");
        assertThat(out).contains("Result");
    }

    @Test
    void bannerContainsSuiteNameInOutput() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("my-api-suite", 0, Instant.now()));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 0, 0L, 0L, 0L));

        ctrl.await();

        assertThat(capture.toString()).contains("Starting Test Suite my-api-suite");
    }

    @Test
    void testNameAppearsInOutputAfterTestStarted() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "verify-login"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "verify-login", TestStatus.PASS, 50L, 3, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 0, 0L, 0L, 50L));

        ctrl.await();

        assertThat(capture.toString()).contains("verify-login");
    }

    @Test
    void passResultAppearsInOutputAfterPassedTest() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "check-health"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "check-health", TestStatus.PASS, 30L, 4, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 0, 0L, 0L, 30L));

        ctrl.await();

        assertThat(capture.toString()).contains("4 passed");
    }

    @Test
    void failResultAppearsInOutputAfterFailedTest() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "check-status"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "0",
                0,
                "check-status",
                TestStatus.FAIL,
                20L,
                3,
                List.of(
                        new AssertionFailure("expected 200 but was 500", null, null, null),
                        new AssertionFailure("missing header", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 1, 0L, 0L, 20L));

        ctrl.await();

        assertThat(capture.toString()).contains("2 failed");
    }

    @Test
    void responseDurationAppearsInOutputAfterTestCompleted() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "timing-test"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "timing-test", TestStatus.PASS, 142L, 1, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 0, 0L, 0L, 142L));

        ctrl.await();

        assertThat(capture.toString()).contains("142ms");
    }

    // ---------------------------------------------------------------------------
    // Summary line
    // ---------------------------------------------------------------------------

    @Test
    void summaryLineContainsPassAndFailCounts() throws InterruptedException {
        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("my-suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "test-one"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "test-one", TestStatus.PASS, 100L, 1, List.of()));
        queue.offer(new TestProgressEvent.TestStarted("1", 1, "test-two"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "1",
                1,
                "test-two",
                TestStatus.FAIL,
                200L,
                1,
                List.of(new AssertionFailure("expected 200 but was 404", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 1, 0L, 0L, 300L));

        ctrl.await();

        String out = capture.toString();
        assertThat(out).contains(Glyphs.PASS + " 1 passed");
        assertThat(out).contains(Glyphs.FAIL + " 1 failed");
        assertThat(out).contains("(300ms)");
    }

    @Test
    void summaryLineReflectsAllPassWhenNoFailures() throws InterruptedException {
        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "alpha"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "alpha", TestStatus.PASS, 50L, 2, List.of()));
        queue.offer(new TestProgressEvent.TestStarted("1", 1, "beta"));
        queue.offer(new TestProgressEvent.TestCompleted("1", 1, "beta", TestStatus.PASS, 75L, 2, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(2, 0, 0L, 0L, 125L));

        ctrl.await();

        String out = capture.toString();
        assertThat(out).contains(Glyphs.PASS + " 2 passed");
        assertThat(out).contains(Glyphs.FAIL + " 0 failed");
        assertThat(out).contains("(125ms)");
    }

    @Test
    void summaryLineWithColorsContainsAnsiEscapeCodesForPassAndFail() throws InterruptedException {
        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = colorController(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "test-x"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "0",
                0,
                "test-x",
                TestStatus.FAIL,
                50L,
                1,
                List.of(new AssertionFailure("assertion failed", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 1, 0L, 0L, 50L));

        ctrl.await();

        String out = capture.toString();
        assertThat(out).contains("[32m"); // ANSI green for pass
        assertThat(out).contains("[33m"); // ANSI yellow (orange-like) for fail
    }

    // ---------------------------------------------------------------------------
    // Failure details
    // ---------------------------------------------------------------------------

    @Test
    void failureDetailsArePrintedAfterSummaryWhenTestsFail() throws InterruptedException {
        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "passing-test"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "passing-test", TestStatus.PASS, 40L, 1, List.of()));
        queue.offer(new TestProgressEvent.TestStarted("1", 1, "failing-test"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "1",
                1,
                "failing-test",
                TestStatus.FAIL,
                60L,
                1,
                List.of(new AssertionFailure("expected 200 but was 500", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 1, 0L, 0L, 100L));

        ctrl.await();

        String out = capture.toString();
        assertThat(out).contains("Failures:");
        assertThat(out).contains("failing-test");
        assertThat(out).contains("expected 200 but was 500");
    }

    @Test
    void noFailureSectionPrintedWhenAllTestsPass() throws InterruptedException {
        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "test-one"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "test-one", TestStatus.PASS, 40L, 1, List.of()));
        queue.offer(new TestProgressEvent.TestStarted("1", 1, "test-two"));
        queue.offer(new TestProgressEvent.TestCompleted("1", 1, "test-two", TestStatus.PASS, 60L, 1, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(2, 0, 0L, 0L, 100L));

        ctrl.await();

        assertThat(capture.toString()).doesNotContain("Failures:");
    }

    @Test
    void errorStatusIsIncludedInFailureDetails() throws InterruptedException {
        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "network-error-test"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "0",
                0,
                "network-error-test",
                TestStatus.ERROR,
                30L,
                0,
                List.of(new AssertionFailure("Connection refused", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 1, 0L, 0L, 30L));

        ctrl.await();

        String out = capture.toString();
        assertThat(out).contains("Failures:");
        assertThat(out).contains("network-error-test");
        assertThat(out).contains("Connection refused");
    }

    @Test
    void multipleFailuresAreAllListedInFailureDetails() throws InterruptedException {
        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 3, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "fail-a"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "0",
                0,
                "fail-a",
                TestStatus.FAIL,
                10L,
                1,
                List.of(new AssertionFailure("reason-a", null, null, null))));
        queue.offer(new TestProgressEvent.TestStarted("1", 1, "pass-b"));
        queue.offer(new TestProgressEvent.TestCompleted("1", 1, "pass-b", TestStatus.PASS, 20L, 1, List.of()));
        queue.offer(new TestProgressEvent.TestStarted("2", 2, "fail-c"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "2",
                2,
                "fail-c",
                TestStatus.ERROR,
                30L,
                0,
                List.of(new AssertionFailure("reason-c", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 2, 0L, 0L, 60L));

        ctrl.await();

        String out = capture.toString();
        assertThat(out).contains("fail-a");
        assertThat(out).contains("reason-a");
        assertThat(out).contains("fail-c");
        assertThat(out).contains("reason-c");
        // "pass-b" appears in the grid cell but must NOT appear in the Failures section
        int failuresStart = out.indexOf("Failures:");
        assertThat(failuresStart).isGreaterThan(0);
        assertThat(out.substring(failuresStart)).doesNotContain("pass-b");
    }

    @Test
    void allAssertionFailuresForOneTestArePrinted() throws InterruptedException {
        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "multi-assert-test"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "0",
                0,
                "multi-assert-test",
                TestStatus.FAIL,
                50L,
                3,
                List.of(
                        new AssertionFailure("expected 200 but was 404", null, null, null),
                        new AssertionFailure("body did not match", null, null, null),
                        new AssertionFailure("missing header X-Request-Id", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 1, 0L, 0L, 50L));

        ctrl.await();

        String out = capture.toString();
        assertThat(out).contains("multi-assert-test");
        assertThat(out).contains("expected 200 but was 404");
        assertThat(out).contains("body did not match");
        assertThat(out).contains("missing header X-Request-Id");
    }

    @Test
    void skipResultAppearsInOutputAfterSkippedTest() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "skip-me"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "skip-me", TestStatus.SKIP, 0L, 0, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 0, 1L, 0L, 0L));

        ctrl.await();

        assertThat(capture.toString()).contains("skipped");
    }

    @Test
    void errorResultAppearsInOutputAfterErrorTest() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "error-test"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "0",
                0,
                "error-test",
                TestStatus.ERROR,
                10L,
                0,
                List.of(new AssertionFailure("Connection refused", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 0, 0L, 1L, 10L));

        ctrl.await();

        assertThat(capture.toString()).contains("error");
    }

    @Test
    void summaryLineContainsAllFourCounters() throws InterruptedException {
        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 4, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "pass-test"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "pass-test", TestStatus.PASS, 10L, 1, List.of()));
        queue.offer(new TestProgressEvent.TestStarted("1", 1, "fail-test"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "1",
                1,
                "fail-test",
                TestStatus.FAIL,
                20L,
                1,
                List.of(new AssertionFailure("failed", null, null, null))));
        queue.offer(new TestProgressEvent.TestStarted("2", 2, "skip-test"));
        queue.offer(new TestProgressEvent.TestCompleted("2", 2, "skip-test", TestStatus.SKIP, 0L, 0, List.of()));
        queue.offer(new TestProgressEvent.TestStarted("3", 3, "error-test"));
        queue.offer(new TestProgressEvent.TestCompleted(
                "3",
                3,
                "error-test",
                TestStatus.ERROR,
                5L,
                0,
                List.of(new AssertionFailure("Connection refused", null, null, null))));
        queue.offer(new TestProgressEvent.SuiteCompleted(1L, 1L, 1L, 1L, 35L));

        ctrl.await();

        String out = capture.toString();
        assertThat(out).contains(Glyphs.PASS + " 1 passed");
        assertThat(out).contains(Glyphs.FAIL + " 1 failed");
        assertThat(out).contains(Glyphs.SKIP + " 1 skipped");
        assertThat(out).contains(Glyphs.ERROR + " 1 errors");
        assertThat(out).contains("(35ms)");
    }

    @Test
    void summaryLineWithColorsUsesBlueForSkipWhenCountIsNonZero() throws InterruptedException {
        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController ctrl = colorController(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "skip-me"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "skip-me", TestStatus.SKIP, 0L, 0, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(0L, 0L, 1L, 0L, 0L));

        ctrl.await();

        assertThat(capture.toString()).contains("[34m"); // ANSI blue for skip
    }

    // ---------------------------------------------------------------------------
    // Test name truncation
    // ---------------------------------------------------------------------------

    @Test
    void truncateNameReturnsOriginalWhenWithinLimit() {
        assertThat(TerminalUiController.truncateName("short", 80)).isEqualTo("short");
    }

    @Test
    void truncateNameReturnsOriginalWhenExactlyAtLimit() {
        assertThat(TerminalUiController.truncateName("abcde", 5)).isEqualTo("abcde");
    }

    @Test
    void truncateNameAppendsEllipsisWhenTooLong() {
        assertThat(TerminalUiController.truncateName("abcde", 4)).isEqualTo("abc…");
    }

    @Test
    void truncateNameReturnsEllipsisWhenMaxWidthIsOne() {
        assertThat(TerminalUiController.truncateName("abcde", 1)).isEqualTo("…");
    }

    @Test
    void truncateNameReturnsOriginalWhenMaxWidthIsZero() {
        assertThat(TerminalUiController.truncateName("abcde", 0)).isEqualTo("abcde");
    }

    @Test
    void truncateNameReturnsOriginalWhenMaxWidthIsNegative() {
        assertThat(TerminalUiController.truncateName("abcde", -1)).isEqualTo("abcde");
    }

    @Test
    void longTestNameIsTruncatedWithinNameColumnWidth() throws InterruptedException {
        // terminalWidth=80 → nameColWidth = max(10, 80 - 13 - 10 - 15 - 10) = max(10, 32) = 32
        String longName = "this-is-a-very-long-test-name-that-exceeds-the-limit";
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = new TerminalUiController(queue, false, 80, new PrintWriter(capture));
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, longName));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, longName, TestStatus.PASS, 10L, 1, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 0, 0L, 0L, 10L));

        ctrl.await();

        // nameColWidth = 32; truncated name must be at most 32 chars and end with "…"
        String expected = TerminalUiController.truncateName(longName, 32);
        assertThat(expected).endsWith("…");
        assertThat(expected).hasSizeLessThanOrEqualTo(32);
        assertThat(capture.toString()).contains(expected);
    }

    // ---------------------------------------------------------------------------
    // Column width calculation
    // ---------------------------------------------------------------------------

    @Test
    void nameColWidthUsesMinimumOfTenForNarrowTerminals() {
        TerminalUiController ctrl =
                new TerminalUiController(new LinkedBlockingQueue<>(), false, 30, new PrintWriter(new StringWriter()));
        // max(10, 30 - 13 - 10 - 15 - 10) = max(10, -18) = 10
        assertThat(ctrl.nameColWidth).isEqualTo(10);
    }

    @Test
    void nameColWidthIsComputedCorrectlyForEightyColTerminal() {
        TerminalUiController ctrl =
                new TerminalUiController(new LinkedBlockingQueue<>(), false, 80, new PrintWriter(new StringWriter()));
        assertThat(ctrl.nameColWidth).isEqualTo(32);
    }

    @Test
    void columnStartPositionsAreConsistentWithNameColWidth() {
        TerminalUiController ctrl =
                new TerminalUiController(new LinkedBlockingQueue<>(), false, 80, new PrintWriter(new StringWriter()));
        // statusColStart = nameColWidth + 6 = 32 + 6 = 38
        assertThat(ctrl.statusColStart).isEqualTo(38);
        // timeColStart = statusColStart + STATUS_COL_WIDTH + 3 = 38 + 10 + 3 = 51
        assertThat(ctrl.timeColStart).isEqualTo(51);
        // resultColStart = timeColStart + TIME_COL_WIDTH + 3 = 51 + 15 + 3 = 69
        assertThat(ctrl.resultColStart).isEqualTo(69);
    }

    // ---------------------------------------------------------------------------
    // Tag filter notice
    // ---------------------------------------------------------------------------

    @Test
    void tagNoticeAppearsWhenActiveTagFilterIsSet() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = new TerminalUiController(queue, false, 80, new PrintWriter(capture), "smoke");
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "test-one"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "test-one", TestStatus.PASS, 10L, 1, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 0, 0L, 0L, 10L));
        ctrl.await();

        assertThat(capture.toString()).contains("Filtering by tag: smoke");
    }

    @Test
    void tagNoticeAbsentWhenNoTagFilter() throws InterruptedException {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        StringWriter capture = new StringWriter();
        TerminalUiController ctrl = controller(queue, capture);
        ctrl.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted("0", 0, "test-one"));
        queue.offer(new TestProgressEvent.TestCompleted("0", 0, "test-one", TestStatus.PASS, 10L, 1, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 0, 0L, 0L, 10L));
        ctrl.await();

        assertThat(capture.toString()).doesNotContain("Filtering by tag");
    }
}
