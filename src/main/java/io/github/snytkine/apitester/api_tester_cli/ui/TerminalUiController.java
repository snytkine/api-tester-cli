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

import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.jline.tui.component.ViewComponent;
import org.springframework.shell.jline.tui.component.ViewComponentBuilder;
import org.springframework.shell.jline.tui.component.message.ShellMessageBuilder;
import org.springframework.shell.jline.tui.component.view.control.BoxView;
import org.springframework.shell.jline.tui.component.view.control.GridView;
import org.springframework.shell.jline.tui.component.view.screen.Screen;

/**
 * Drives the Spring Shell {@link GridView}-based terminal UI for a single suite run, with Braille
 * spinner animation for in-progress tests.
 *
 * <p>Lifecycle for one suite run:
 *
 * <ol>
 *   <li>{@link #start()} — starts a background controller thread that blocks on the event queue.
 *   <li>The thread receives {@link TestProgressEvent.SuiteStarted} and builds a {@link GridView}
 *       with one row per test, pre-populated with "pending" text.
 *   <li>On {@link TestProgressEvent.TestStarted}: the row transitions to "running" state. The
 *       render loop then advances a per-row Braille spinner frame on every tick ({@value
 *       #POLL_TIMEOUT_MS} ms) and dispatches a redraw, producing smooth animation.
 *   <li>On {@link TestProgressEvent.TestCompleted}: the row is updated with the final status glyph
 *       and duration, and spinner animation for that row stops.
 *   <li>{@link TestProgressEvent.SuiteCompleted} signals the loop to exit, the view component is
 *       stopped, and the controller thread terminates.
 *   <li>{@link #await()} — blocks the calling thread until the controller thread finishes so the
 *       terminal is fully restored before the caller continues.
 * </ol>
 *
 * <p>Spinner animation runs on the controller thread. Every {@link #POLL_TIMEOUT_MS} ms (or sooner
 * if an event arrives), all rows whose state is "running" have their spinner frame advanced and
 * their row text rewritten. A JLine redraw is dispatched only when at least one row is running or
 * a {@link TestProgressEvent.TestCompleted} event has just updated a row — idle ticks with no
 * running rows produce no redraw.
 *
 * <p>This class is <em>not</em> a Spring singleton. One instance is created per suite run by
 * {@link io.github.snytkine.apitester.api_tester_cli.commands.RunSuiteCommand}. All mutable view
 * state is confined to the controller thread. The only cross-thread communication uses an {@link
 * AtomicReferenceArray} for row text (written by the controller thread, read by the JLine render
 * thread).
 *
 * <p>Thread-safety: thread-safe by construction. The controller thread is the sole writer of view
 * state; the JLine render thread is a pure reader. The {@link LinkedBlockingQueue} provides safe
 * producer-consumer handoff for incoming events.
 */
public final class TerminalUiController {

  private static final Logger log = LoggerFactory.getLogger(TerminalUiController.class);

  /** Milliseconds to wait for the next event before looping (enables Phase 5 spinner animation). */
  static final long POLL_TIMEOUT_MS = 100L;

  /** Seconds to wait for the initial {@link TestProgressEvent.SuiteStarted} event. */
  static final long SUITE_STARTED_TIMEOUT_SECONDS = 30L;

  private final LinkedBlockingQueue<TestProgressEvent> queue;
  private final ViewComponentBuilder viewComponentBuilder;

  private Thread controllerThread;

  /**
   * Constructs a controller for one suite run.
   *
   * @param queue the shared event queue populated by a {@link TerminalUiListener}
   * @param viewComponentBuilder Spring-managed factory for creating a {@link ViewComponent}
   */
  public TerminalUiController(
      LinkedBlockingQueue<TestProgressEvent> queue, ViewComponentBuilder viewComponentBuilder) {
    this.queue = queue;
    this.viewComponentBuilder = viewComponentBuilder;
  }

  /**
   * Starts the background controller thread.
   *
   * <p>Must be called once before any events are offered to the shared queue. The thread is
   * configured as a daemon thread so it does not prevent JVM shutdown if the main thread exits
   * unexpectedly.
   */
  public void start() {
    controllerThread = new Thread(this::runLoop, "tui-controller");
    controllerThread.setDaemon(true);
    controllerThread.start();
  }

  /**
   * Blocks the calling thread until the controller thread has finished and the terminal has been
   * fully restored.
   *
   * @throws InterruptedException if the calling thread is interrupted while waiting
   */
  public void await() throws InterruptedException {
    if (controllerThread != null) {
      controllerThread.join();
    }
  }

  /**
   * Main controller thread loop. Waits for {@link TestProgressEvent.SuiteStarted}, builds the
   * view, then runs the event-and-spinner loop until {@link TestProgressEvent.SuiteCompleted}.
   *
   * <p>Per-row animation state ({@code testNames}, {@code isRunning}, {@code spinnerFrames}) is
   * entirely local to this thread — no external synchronisation is required.
   */
  private void runLoop() {
    try {
      TestProgressEvent firstEvent =
          queue.poll(SUITE_STARTED_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!(firstEvent instanceof TestProgressEvent.SuiteStarted suiteStarted)) {
        log.warn(
            "TUI controller received unexpected first event or timed out; aborting UI render");
        return;
      }

      int rowCount = suiteStarted.totalTestCount();
      AtomicReferenceArray<String> rows = new AtomicReferenceArray<>(rowCount);
      String[] testNames = new String[rowCount];
      boolean[] isRunning = new boolean[rowCount];
      int[] spinnerFrames = new int[rowCount];

      for (int i = 0; i < rowCount; i++) {
        rows.set(i, "  " + Glyphs.PENDING + " (pending)");
      }

      GridView grid = buildGrid(rows, rowCount);
      ViewComponent vc = viewComponentBuilder.build(grid);
      ViewComponent.ViewComponentRun vcRun = vc.runAsync();

      try {
        boolean done = false;
        while (!done) {
          TestProgressEvent event = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
          if (event != null) {
            done = applyEvent(event, rows, testNames, isRunning);
          }

          // Advance the Braille spinner for every row that is currently running.
          boolean anyRunning = false;
          for (int i = 0; i < rowCount; i++) {
            if (isRunning[i]) {
              anyRunning = true;
              int frame = spinnerFrames[i];
              rows.set(
                  i,
                  "  "
                      + Glyphs.SPINNER_FRAMES[frame]
                      + " "
                      + testNames[i]
                      + " (running...)");
              spinnerFrames[i] = (frame + 1) % Glyphs.SPINNER_FRAMES.length;
            }
          }

          // Dispatch a redraw only when the display actually changed: either a spinner frame
          // ticked on at least one running row, or a TestCompleted event just wrote a final
          // status line. SuiteStarted and SuiteCompleted produce no visible change.
          if (anyRunning || event instanceof TestProgressEvent.TestCompleted) {
            vc.getEventLoop().dispatch(ShellMessageBuilder.ofRedraw());
          }
        }
      } finally {
        vc.exit();
      }
      vcRun.await();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.debug("TUI controller thread interrupted");
    } catch (Exception e) {
      log.error("TUI controller failed unexpectedly", e);
    }
  }

  /**
   * Applies a single event, updating the per-row state arrays used by the spinner loop.
   *
   * <p>{@link TestProgressEvent.TestStarted} records the test name and marks the row as running;
   * the spinner loop then writes the first animated frame on the very next tick. {@link
   * TestProgressEvent.TestCompleted} clears the running flag and writes the final status text
   * directly to {@code rows}; the spinner loop will no longer touch that row. Redraw dispatching
   * is handled by the caller after this method returns.
   *
   * @param event the event to apply
   * @param rows shared row-text array; updated directly on {@link TestProgressEvent.TestCompleted}
   * @param testNames per-row test name; written on {@link TestProgressEvent.TestStarted}
   * @param isRunning per-row running flag; set on {@link TestProgressEvent.TestStarted}, cleared
   *     on {@link TestProgressEvent.TestCompleted}
   * @return {@code true} when the suite is complete and the render loop should exit
   */
  private boolean applyEvent(
      TestProgressEvent event,
      AtomicReferenceArray<String> rows,
      String[] testNames,
      boolean[] isRunning) {
    return switch (event) {
      case TestProgressEvent.SuiteStarted ignored ->
          // Duplicate SuiteStarted; should never happen — ignore.
          false;
      case TestProgressEvent.TestStarted e -> {
        testNames[e.testIndex()] = e.testName();
        isRunning[e.testIndex()] = true;
        yield false;
      }
      case TestProgressEvent.TestCompleted e -> {
        isRunning[e.testIndex()] = false;
        String glyph =
            switch (e.status()) {
              case PASS -> Glyphs.PASS;
              case FAIL, ERROR -> Glyphs.FAIL;
            };
        rows.set(
            e.testIndex(), "  " + glyph + " " + e.testName() + " (" + e.durationMs() + "ms)");
        yield false;
      }
      case TestProgressEvent.SuiteCompleted ignored -> true;
    };
  }

  /**
   * Builds a {@link GridView} with one {@link BoxView} per row. Each box's draw function reads
   * its content from the shared {@code rows} array at render time, which allows the controller
   * thread to update content without synchronising with the render thread.
   *
   * @param rows shared row-content array; updated by the controller thread, read by the render
   *     thread
   * @param rowCount number of rows to create
   * @return a fully configured {@link GridView} ready to be passed to {@link
   *     ViewComponentBuilder#build}
   */
  private GridView buildGrid(AtomicReferenceArray<String> rows, int rowCount) {
    GridView grid = new GridView();
    int[] rowSizes = new int[rowCount];
    for (int i = 0; i < rowCount; i++) {
      rowSizes[i] = 1;
    }
    grid.setRowSize(rowSizes);
    grid.setColumnSize(0);

    for (int i = 0; i < rowCount; i++) {
      int idx = i;
      BoxView box = new BoxView();
      box.setDrawFunction(
          (screen, rect) -> {
            Screen.Writer writer = screen.writerBuilder().build();
            writer.text(rows.get(idx), rect.x(), rect.y());
            return rect;
          });
      grid.addItem(box, i, 0, 1, 1, 0, 0);
    }
    return grid;
  }
}
