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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.event.TestStatus;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.shell.jline.tui.component.ViewComponent;
import org.springframework.shell.jline.tui.component.ViewComponentBuilder;
import org.springframework.shell.jline.tui.component.view.event.EventLoop;

class TerminalUiControllerTest {

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
    TerminalUiController controller = new TerminalUiController(queue, builder);
    controller.start();

    queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
    queue.offer(new TestProgressEvent.TestStarted(0, "test-one"));
    queue.offer(
        new TestProgressEvent.TestCompleted(0, "test-one", TestStatus.PASS, 100L, null));
    queue.offer(new TestProgressEvent.TestStarted(1, "test-two"));
    queue.offer(
        new TestProgressEvent.TestCompleted(1, "test-two", TestStatus.FAIL, 200L, "failed"));
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
    TerminalUiController controller = new TerminalUiController(queue, builder);
    controller.start();

    queue.offer(new TestProgressEvent.SuiteStarted("suite", 2, Instant.now()));
    queue.offer(new TestProgressEvent.TestStarted(0, "test-a"));
    queue.offer(
        new TestProgressEvent.TestCompleted(0, "test-a", TestStatus.PASS, 50L, null));
    queue.offer(new TestProgressEvent.TestStarted(1, "test-b"));
    queue.offer(
        new TestProgressEvent.TestCompleted(1, "test-b", TestStatus.ERROR, 75L, "error"));
    queue.offer(new TestProgressEvent.SuiteCompleted(1, 1, 125L));

    controller.await();

    // 2 TestStarted + 2 TestCompleted = 4 redraw dispatches
    verify(eventLoop, times(4)).dispatch(any(Message.class));
  }

  @Test
  void controllerAbortsWhenFirstEventIsNotSuiteStarted() throws InterruptedException {
    ViewComponentBuilder builder = mock(ViewComponentBuilder.class);
    LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();

    TerminalUiController controller = new TerminalUiController(queue, builder);
    controller.start();

    // Offer a non-SuiteStarted event as the first event
    queue.offer(new TestProgressEvent.SuiteCompleted(0, 0, 0L));

    controller.await();

    // ViewComponentBuilder.build() must never have been called
    verify(builder, never()).build(any());
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
    TerminalUiController controller = new TerminalUiController(queue, builder);
    controller.start();

    queue.offer(new TestProgressEvent.SuiteStarted("empty-suite", 0, Instant.now()));
    queue.offer(new TestProgressEvent.SuiteCompleted(0, 0, 0L));

    controller.await();

    verify(vc).exit();
    verify(eventLoop, never()).dispatch(any(Message.class));
  }
}
