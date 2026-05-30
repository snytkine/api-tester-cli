# Terminal UI Implementation Plan

## Goal

Replace (or augment) the current JSON-only output of `RunSuiteCommand` with an
interactive terminal UI that renders one row per test case, each row showing a
spinner while the test is running and a coloured status glyph
(`✓` green for pass, `✗` red for fail) when the test completes. The UI must
coexist with the planned parallel-execution model (see
`memory/project_parallel_execution.md`) and must degrade gracefully when stdout
is not a TTY.

## Constraints

- **GraalVM native image** must still build and run. Any Spring Shell view
  component, JLine widget, or library introduced here must be verified in the
  native build and accompanied by reflection/proxy/resource hints if required.
- **Thread safety** — singleton beans hold no per-suite mutable state. The
  shared state for an in-flight run lives on the call stack of the runner and
  is communicated to the UI through a bounded, thread-safe queue.
- **Backwards compatibility** — current JSON output remains the default when
  the UI cannot or should not render (non-TTY, `--no-ui`, `NO_COLOR`, etc.).

## High-level architecture

```
┌────────────────────┐   onProgress(event)   ┌────────────────────────┐
│ TestRunner         │ ────────────────────▶ │ TestProgressListener   │
│ (1..N worker       │                       │ (interface)            │
│  threads)          │                       └──────────┬─────────────┘
└────────────────────┘                                  │
                                                        ▼
                                            ┌────────────────────────┐
                                            │ NoOpProgressListener   │  ← non-TTY/--no-ui
                                            │ (drops events)         │
                                            └────────────────────────┘
                                                        OR
                                            ┌────────────────────────┐
                                            │ TerminalUiListener     │
                                            │   queue.offer(event)   │  ← non-blocking
                                            └──────────┬─────────────┘
                                                       │ BlockingQueue
                                                       ▼
                                            ┌────────────────────────┐
                                            │ TerminalUiController   │
                                            │ (single render thread) │
                                            │ drains queue → updates │
                                            │ GridView / ProgressView│
                                            └────────────────────────┘
```

The producer side (test workers) is decoupled from the consumer side (the
single thread that owns the JLine terminal). Producers never block on UI
rendering — they call `queue.offer(event)` and move on. The UI render loop
drains the queue between frames.

## Component breakdown

### 1. `event/` package — listener interface and event types

```
event/
  TestProgressEvent.java       sealed interface
    SuiteStarted               suite name, total test count, started timestamp
    TestStarted                test index, test name
    TestCompleted              test index, status (PASS/FAIL/ERROR),
                               duration ms, optional failure summary
    SuiteCompleted             pass/fail counts, total duration
  TestProgressListener.java    functional interface, single onProgress method
  NoOpProgressListener.java    silent fallback for non-UI runs
```

`TestProgressEvent` is a sealed interface so the controller can use exhaustive
`switch` (same pattern already used for `Assertion` and
`ResponseValueExtractor.Result`).

### 2. `ui/` package — terminal rendering

```
ui/
  TerminalUiController.java      builds and updates the view; owns the render thread
  TerminalUiListener.java        implements TestProgressListener; only enqueues events
  Glyphs.java                    unicode constants (✓ ✗ ⠋⠙⠹… spinner frames)
  TtyDetector.java               isTty(), supportsColor(), shouldUseUi(CliFlags)
```

`TerminalUiController` is the only class that touches the JLine terminal. It
owns the `BlockingQueue<TestProgressEvent>` and a single worker thread that:

1. Builds the `GridView` once on `SuiteStarted` (N rows, 3 columns:
   status cell, name cell, optional duration cell).
2. Polls the queue with a short timeout (e.g. 100 ms) so the spinner
   animation can advance even when no events arrive.
3. Updates the relevant row cell on each event and re-renders.
4. Shuts down cleanly on `SuiteCompleted`, restoring the terminal state.

### 3. TTY / capability detection

`TtyDetector.shouldUseUi(...)` returns `true` only when **all** of:

- `System.console() != null` (stdout is attached to a TTY).
- The `NO_COLOR` environment variable is absent
  (per <https://no-color.org>).
- The user did not pass `--no-ui` (new flag on `RunSuiteCommand`).
- The terminal width is at least some minimum (e.g. 40 cols) — below that
  the UI is unreadable; fall back to JSON.

A separate `--ui` flag can force the UI on for debugging when detection is
wrong.

### 4. Wiring into the runner

`RunSuiteCommand` is responsible for:

1. Choosing the listener implementation based on `TtyDetector.shouldUseUi(...)`.
2. Passing the listener into the test runner so it can fire events from
   `executeSingleTest` (start + complete) and at suite boundaries.
3. After the suite completes:
   - **UI mode** — the controller has already rendered the final state;
     `RunSuiteCommand` prints a one-line summary below the grid (and
     optionally writes JSON to a file if `--output` is passed).
   - **No-UI mode** — current behaviour: print JSON to stdout.

The runner itself is unchanged in shape; it gains a single
`TestProgressListener` parameter that defaults to `NoOpProgressListener`.

## Threading model

- **Producers** — one thread per test in the parallel model, or a single
  thread today. Each test thread fires:
  - `TestStarted` immediately before invoking the request.
  - `TestCompleted` after assertions evaluate.
- **Queue** — `LinkedBlockingQueue<TestProgressEvent>` with no capacity cap
  (events are tiny records; backpressure is not a concern at realistic test
  suite sizes). All producers call `queue.offer(event)` — never `put` —
  so an unexpected stall in the UI thread cannot block a test.
- **Consumer** — single render thread owned by `TerminalUiController`. Loop:

  ```
  while (!suiteComplete) {
      event = queue.poll(SPINNER_FRAME_INTERVAL, MILLISECONDS);
      if (event != null) apply(event);
      advanceSpinnerFrame();
      render();
  }
  ```

  Polling with a timeout lets the spinner animate independently of event
  arrival. `apply()` mutates the controller's own `Map<Integer, RowState>`
  — no shared state with the runner.

## Spring Shell view components

Planned components from `org.springframework.shell.component.view`:

- `GridView` — top-level layout for the row-per-test display.
- `BoxView` / `StringView` — per-cell content.
- `ProgressView` — optional summary bar at the bottom (X of N complete).

Spinner animation is hand-rolled with a frame counter and the Braille spinner
glyph sequence (`⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏`), since Spring Shell does not ship a
spinner widget at time of writing.

## GraalVM verification — required before merge

Spring Shell's view components are newer than the rest of the project's
dependencies and have not been exercised by this codebase. Before relying on
them:

1. Stand up a minimal `GridView` + `BoxView` example as a smoke test command.
2. Run `./mvnw -Pnative native:compile` and exercise the smoke command.
3. If reflection / resource errors appear, add hints via
   `RuntimeHints` registrar or `@RegisterReflectionForBinding` and document
   them in `native-build-support.md` alongside the existing notes.
4. JLine native-image hints: verify that the JLine version pulled in by
   Spring Shell 4.0.2 publishes its own GraalVM metadata. If not, file
   issues / add hints.

If `GridView` proves infeasible in native, fall back to a simpler line-based
renderer (overwrite the same N lines with ANSI cursor movement) — this is
also a candidate first pass to limit blast radius.

## Implementation phases

Each phase is independently shippable and leaves the project in a working
state. Recommended order:

1. **Event plumbing.** Add `TestProgressEvent`, `TestProgressListener`,
   `NoOpProgressListener`. Wire `TestProgressListener` parameter through the
   runner. Default to `NoOpProgressListener`. No UI yet. JSON output
   unchanged. Tests verify events are fired in the expected order.
2. **TTY detection + CLI flags.** Add `TtyDetector`, `--no-ui`, `--ui`
   flags. Branch in `RunSuiteCommand` between JSON and (still nonexistent)
   UI mode.
3. **Spring Shell TUI smoke test.** Verify `GridView` renders and updates
   correctly under both JVM run and GraalVM native image. Document any
   required AOT hints.
4. **Static UI.** `TerminalUiController` that builds the grid on
   `SuiteStarted`, sets each row to "pending", and replaces with final
   status on `TestCompleted`. No spinner animation yet.
5. **Spinner animation.** Add the render-loop timer, Braille spinner
   frames, and per-row state transitions.
6. **Colours and glyphs.** Green ✓, red ✗, yellow spinner; respect
   `NO_COLOR`.
7. **Polish.** Terminal resize handling, final summary line, optional
   `ProgressView` summary bar, failure detail rendering (collapsed by
   default, expandable via key press? — open question).

## CLI surface changes

New flags on `run-suite` (names tentative):

- `--no-ui` — force JSON output even when stdout is a TTY.
- `--ui` — force the UI even when stdout looks like a non-TTY (useful for
  capturing demos via `script` or similar).
- `--output <file>` *(if not already present)* — write the JSON report to a
  file. In UI mode this is the only way to obtain the structured report,
  since stdout is consumed by the UI.

## Open questions

- **Failure detail rendering.** When a test fails, do we (a) show the first
  failure inline under the row, (b) collapse and require a keypress, or
  (c) print the full failure report after the suite completes? Pick one
  before phase 7.
- **Window resize.** JLine fires `WINCH` events — does the Spring Shell
  view rebuild automatically or do we need to listen and rebuild ourselves?
- **Test name truncation.** Names longer than the available cell width
  must truncate gracefully (ellipsis from the middle?). Define before
  phase 4.
- **Parallel execution row ordering.** When tests run out of order, do
  rows appear in declaration order (with later rows showing "pending"
  until reached) or in start order? Declaration order is more readable
  but requires reserving N rows up front, which is fine since
  `SuiteStarted` carries the total count.
- **CI environments.** Some CI systems present a TTY but render escape
  codes literally. Consider checking the `CI` environment variable and
  defaulting to JSON when set, with `--ui` to override.

## Files added (estimated)

```
src/main/java/.../event/TestProgressEvent.java
src/main/java/.../event/TestProgressListener.java
src/main/java/.../event/NoOpProgressListener.java
src/main/java/.../ui/TerminalUiController.java
src/main/java/.../ui/TerminalUiListener.java
src/main/java/.../ui/TtyDetector.java
src/main/java/.../ui/Glyphs.java
src/test/java/.../event/...Test.java          (per event type / listener)
src/test/java/.../ui/TtyDetectorTest.java
src/test/java/.../ui/TerminalUiControllerTest.java
```

## Files modified (estimated)

- `RunSuiteCommand` — add flags, instantiate the appropriate listener,
  hand it to the runner.
- The test runner class — accept and invoke `TestProgressListener`.
- `pom.xml` — possibly extra Spring Shell modules if view components are
  packaged separately.
- `native-build-support.md` — document any new GraalVM hints.
- `MEMORY.md` / project memory — note the new UI feature once shipped.
