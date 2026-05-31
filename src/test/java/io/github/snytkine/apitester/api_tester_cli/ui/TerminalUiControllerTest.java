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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.event.TestStatus;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.shell.jline.tui.component.ViewComponent;
import org.springframework.shell.jline.tui.component.ViewComponentBuilder;
import org.springframework.shell.jline.tui.component.view.event.EventLoop;

class TerminalUiControllerTest {

    // ---------------------------------------------------------------------------
    // Lifecycle tests
    // ---------------------------------------------------------------------------

    @Test
    void controllerExitsCleanlyAfterSuiteCompleted() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        EventLoop eventLoop = mock(EventLoop.class);

        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(eventLoop);

        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller =
                new TerminalUiController(queue, builder, false, 80, new PrintWriter(new StringWriter()));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "test-one"));
        queue.offer(new TestProgressEvent.TestCompleted(0, "test-one", TestStatus.PASS, 100L, List.of()));
        queue.offer(new TestProgressEvent.TestStarted(1, "test-two"));
        queue.offer(new TestProgressEvent.TestCompleted(1, "test-two", TestStatus.FAIL, 200L, List.of("failed")));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 1, 300L));

        controller.await();

        verify(vc).exit();
    }

    @Test
    void controllerDispatchesRedrawForEachTestEvent() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        EventLoop eventLoop = mock(EventLoop.class);

        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(eventLoop);

        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller =
                new TerminalUiController(queue, builder, false, 80, new PrintWriter(new StringWriter()));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "test-a"));
        queue.offer(new TestProgressEvent.TestCompleted(0, "test-a", TestStatus.PASS, 50L, List.of()));
        queue.offer(new TestProgressEvent.TestStarted(1, "test-b"));
        queue.offer(new TestProgressEvent.TestCompleted(1, "test-b", TestStatus.ERROR, 75L, List.of("error")));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 1, 125L));

        controller.await();

        // 2 TestStarted + 2 TestCompleted = 4 redraw dispatches
        verify(eventLoop, times(4)).dispatch(any(Message.class));
    }

    @Test
    void controllerAbortsWhenFirstEventIsNotSuiteStarted() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();

        TerminalUiController controller =
                new TerminalUiController(queue, builder, false, 80, new PrintWriter(new StringWriter()));
        controller.start();

        // Offer a non-SuiteStarted event as the first event
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 0, 0L));

        controller.await();

        // ViewComponentBuilder.build() must never have been called
        verify(builder, never()).build(any());
    }

    @Test
    void controllerWithColorsEnabledExitsCleanlyAfterSuiteCompleted() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        EventLoop eventLoop = mock(EventLoop.class);

        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(eventLoop);

        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller =
                new TerminalUiController(queue, builder, true, 80, new PrintWriter(new StringWriter()));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "green-test"));
        queue.offer(new TestProgressEvent.TestCompleted(0, "green-test", TestStatus.PASS, 42L, List.of()));
        queue.offer(new TestProgressEvent.TestStarted(1, "red-test"));
        queue.offer(
                new TestProgressEvent.TestCompleted(1, "red-test", TestStatus.FAIL, 99L, List.of("assertion failed")));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 1, 141L));

        controller.await();

        verify(vc).exit();
    }

    @Test
    void controllerHandlesZeroTestSuite() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        EventLoop eventLoop = mock(EventLoop.class);

        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(eventLoop);

        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller =
                new TerminalUiController(queue, builder, false, 80, new PrintWriter(new StringWriter()));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("empty-suite", 0, Instant.now()));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 0, 0L));

        controller.await();

        verify(vc).exit();
        verify(eventLoop, never()).dispatch(any(Message.class));
    }

    // ---------------------------------------------------------------------------
    // Phase 7: summary line
    // ---------------------------------------------------------------------------

    @Test
    void summaryLineContainsPassAndFailCounts() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(mock(EventLoop.class));

        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller = new TerminalUiController(queue, builder, false, 80, new PrintWriter(capture));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("my-suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "test-one"));
        queue.offer(new TestProgressEvent.TestCompleted(0, "test-one", TestStatus.PASS, 100L, List.of()));
        queue.offer(new TestProgressEvent.TestStarted(1, "test-two"));
        queue.offer(new TestProgressEvent.TestCompleted(
                1, "test-two", TestStatus.FAIL, 200L, List.of("expected 200 but was 404")));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 1, 300L));

        controller.await();

        String out = capture.toString();
        assertThat(out).contains(Glyphs.PASS + " 1 passed");
        assertThat(out).contains(Glyphs.FAIL + " 1 failed");
        assertThat(out).contains("(300ms)");
    }

    @Test
    void summaryLineReflectsAllPassWhenNoFailures() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(mock(EventLoop.class));

        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller = new TerminalUiController(queue, builder, false, 80, new PrintWriter(capture));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "alpha"));
        queue.offer(new TestProgressEvent.TestCompleted(0, "alpha", TestStatus.PASS, 50L, List.of()));
        queue.offer(new TestProgressEvent.TestStarted(1, "beta"));
        queue.offer(new TestProgressEvent.TestCompleted(1, "beta", TestStatus.PASS, 75L, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(2, 0, 125L));

        controller.await();

        String out = capture.toString();
        assertThat(out).contains(Glyphs.PASS + " 2 passed");
        assertThat(out).contains(Glyphs.FAIL + " 0 failed");
        assertThat(out).contains("(125ms)");
    }

    @Test
    void summaryLineWithColorsContainsAnsiEscapeCodesForPassAndFail() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(mock(EventLoop.class));

        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller = new TerminalUiController(queue, builder, true, 80, new PrintWriter(capture));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "test-x"));
        queue.offer(
                new TestProgressEvent.TestCompleted(0, "test-x", TestStatus.FAIL, 50L, List.of("assertion failed")));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 1, 50L));

        controller.await();

        String out = capture.toString();
        // ANSI green for pass (32m) and red for fail (31m) when colours are enabled
        assertThat(out).contains("[32m");
        assertThat(out).contains("[31m");
    }

    // ---------------------------------------------------------------------------
    // Phase 7: failure details
    // ---------------------------------------------------------------------------

    @Test
    void failureDetailsArePrintedAfterSummaryWhenTestsFail() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(mock(EventLoop.class));

        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller = new TerminalUiController(queue, builder, false, 80, new PrintWriter(capture));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "passing-test"));
        queue.offer(new TestProgressEvent.TestCompleted(0, "passing-test", TestStatus.PASS, 40L, List.of()));
        queue.offer(new TestProgressEvent.TestStarted(1, "failing-test"));
        queue.offer(new TestProgressEvent.TestCompleted(
                1, "failing-test", TestStatus.FAIL, 60L, List.of("expected 200 but was 500")));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 1, 100L));

        controller.await();

        String out = capture.toString();
        assertThat(out).contains("Failures:");
        assertThat(out).contains("failing-test");
        assertThat(out).contains("expected 200 but was 500");
    }

    @Test
    void noFailureSectionPrintedWhenAllTestsPass() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(mock(EventLoop.class));

        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller = new TerminalUiController(queue, builder, false, 80, new PrintWriter(capture));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "test-one"));
        queue.offer(new TestProgressEvent.TestCompleted(0, "test-one", TestStatus.PASS, 40L, List.of()));
        queue.offer(new TestProgressEvent.TestStarted(1, "test-two"));
        queue.offer(new TestProgressEvent.TestCompleted(1, "test-two", TestStatus.PASS, 60L, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(2, 0, 100L));

        controller.await();

        assertThat(capture.toString()).doesNotContain("Failures:");
    }

    @Test
    void errorStatusIsIncludedInFailureDetails() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(mock(EventLoop.class));

        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller = new TerminalUiController(queue, builder, false, 80, new PrintWriter(capture));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "network-error-test"));
        queue.offer(new TestProgressEvent.TestCompleted(
                0, "network-error-test", TestStatus.ERROR, 30L, List.of("Connection refused")));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 1, 30L));

        controller.await();

        String out = capture.toString();
        assertThat(out).contains("Failures:");
        assertThat(out).contains("network-error-test");
        assertThat(out).contains("Connection refused");
    }

    @Test
    void multipleFailuresAreAllListedInFailureDetails() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(mock(EventLoop.class));

        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller = new TerminalUiController(queue, builder, false, 80, new PrintWriter(capture));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 3, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "fail-a"));
        queue.offer(new TestProgressEvent.TestCompleted(0, "fail-a", TestStatus.FAIL, 10L, List.of("reason-a")));
        queue.offer(new TestProgressEvent.TestStarted(1, "pass-b"));
        queue.offer(new TestProgressEvent.TestCompleted(1, "pass-b", TestStatus.PASS, 20L, List.of()));
        queue.offer(new TestProgressEvent.TestStarted(2, "fail-c"));
        queue.offer(new TestProgressEvent.TestCompleted(2, "fail-c", TestStatus.ERROR, 30L, List.of("reason-c")));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 2, 60L));

        controller.await();

        String out = capture.toString();
        assertThat(out).contains("fail-a");
        assertThat(out).contains("reason-a");
        assertThat(out).contains("fail-c");
        assertThat(out).contains("reason-c");
        // The passing test name should not appear in the failure block
        assertThat(out.indexOf("pass-b")).isEqualTo(-1);
    }

    @Test
    void allAssertionFailuresForOneTestArePrinted() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(mock(EventLoop.class));

        StringWriter capture = new StringWriter();
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller = new TerminalUiController(queue, builder, false, 80, new PrintWriter(capture));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, "multi-assert-test"));
        queue.offer(new TestProgressEvent.TestCompleted(
                0,
                "multi-assert-test",
                TestStatus.FAIL,
                50L,
                List.of("expected 200 but was 404", "body did not match", "missing header X-Request-Id")));
        queue.offer(new TestProgressEvent.SuiteCompleted(0, 1, 50L));

        controller.await();

        String out = capture.toString();
        assertThat(out).contains("multi-assert-test");
        assertThat(out).contains("expected 200 but was 404");
        assertThat(out).contains("body did not match");
        assertThat(out).contains("missing header X-Request-Id");
    }

    // ---------------------------------------------------------------------------
    // Phase 7: test name truncation
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
    void longTestNameIsTruncatedWithinConfiguredWidth() throws InterruptedException {
        ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
        ViewComponent vc = mock(ViewComponent.class);
        ViewComponent.ViewComponentRun run = mock(ViewComponent.ViewComponentRun.class);
        when(builder.build(any())).thenReturn(vc);
        when(vc.runAsync()).thenReturn(run);
        when(vc.getEventLoop()).thenReturn(mock(EventLoop.class));

        // terminalWidth=30 → maxNameWidth = max(10, 30 - 14) = 16
        String longName = "this-is-a-very-long-test-name-that-exceeds-the-limit";
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiController controller =
                new TerminalUiController(queue, builder, false, 30, new PrintWriter(new StringWriter()));
        controller.start();

        queue.offer(new TestProgressEvent.SuiteStarted("suite", 1, Instant.now()));
        queue.offer(new TestProgressEvent.TestStarted(0, longName));
        queue.offer(new TestProgressEvent.TestCompleted(0, longName, TestStatus.PASS, 10L, List.of()));
        queue.offer(new TestProgressEvent.SuiteCompleted(1, 0, 10L));

        controller.await();

        // The truncated name must be at most maxNameWidth chars and end with "…"
        String expected = TerminalUiController.truncateName(longName, 16);
        assertThat(expected).endsWith("…");
        assertThat(expected).hasSizeLessThanOrEqualTo(16);
    }
}
