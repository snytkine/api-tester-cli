# Terminal UI TODO

1. **Suite start banner** — When a test suite begins, display a header at the top of the
   terminal window before the per-test rows:
   

   ```
   ┌──────────────────────────────────────────────────────────────┐
   │              Starting Test Suite <name>                      │
   └──────────────────────────────────────────────────────────────┘
   ```

   where `<name>` comes from `TestSuite.name()`. Requirements:

   - Rendered inside a `BoxView` with a visible border.
   - The box width should be approximately 80% of the current terminal width (derive from
     `terminalWidth` at construction time).
   - The text inside should be horizontally centered within the box if the JLine/Spring Shell
     API makes centering straightforward; otherwise left-aligned with a leading space is
     acceptable.
   - The text should be rendered in yellow.
   - The banner is a static row; it does not animate or update after it is first drawn.

2. ~~**4-column test grid**~~ ✅ **DONE** — Replace the current single-column row list with a proper 4-column
   `GridView` that has visible borders. Columns:

   | Test Name | Status | Response Time | Result |
   |-----------|--------|---------------|--------|

   **Row count**: one row per test case plus one header row. The header row shows the column
   titles above the data rows.

   **Borders**: grid borders must be visible between all rows and columns.

   **Test identity tracking**: before building the grid, map `TestSuite.tests()` to a new list
   of `IndexedTestCase` records (or similar wrapper) that pairs each `TestCase` with a generated
   `uniqueId` (e.g. a `UUID` or sequential integer assigned at mapping time). Maintain an internal
   `Map<String, Integer>` from `uniqueId` to grid row index. `TestProgressEvent` must carry the
   `uniqueId` so the render loop can look up the correct row without relying on list position
   alone — this keeps the design compatible with future parallel execution.

   **On `TestStarted`**: populate the row with:
   - *Test Name*: the test case name
   - *Status*: Braille spinner (animated, same frames as current implementation)
   - *Response Time*: blank
   - *Result*: blank

   **On `TestCompleted` (PASS)**:
   - *Status*: green `✔` glyph (same as `Glyphs.PASS`)
   - *Response Time*: `<N>ms`
   - *Result*: number of passed assertions (e.g. `3 passed`)

   **On `TestCompleted` (FAIL or ERROR)**:
   - *Status*: red `✘` glyph (same as `Glyphs.FAIL`)
   - *Response Time*: `<N>ms`
   - *Result*: number of failed assertions (e.g. `2 failed`)

   > **Important — in-place cell updates**: cell values must be updated in-place as results
   > arrive; the entire terminal screen must not be redrawn on each event. When a cell's content
   > changes (e.g. spinner → PASS glyph, blank → `142ms`), the cursor must be repositioned to
   > the exact screen coordinates of that cell before writing the new value, then returned to its
   > original position. This requires tracking the absolute terminal row and column of every cell
   > at the time the grid is first rendered, and using ANSI cursor-positioning escape sequences
   > (e.g. `\033[<row>;<col>H`) or the equivalent JLine API to seek to those coordinates for
   > each targeted write. No full-screen refresh should occur during the run loop.

3. **Failed assertion detail block** — After the suite grid (post-TUI, same section as the
   current failure output), if any tests failed print a detailed breakdown:

   ```
   Failed Tests

   <test name>
     <per-assertion grid — one per failed assertion>

   <next failed test name>
     ...
   ```

   For each failed assertion render a 2-row × 2-column bordered grid:

   ```
   | Expected | <expected value> |
   | Was      | <actual value>   |
   ```

   - Column 1, row 1: label `"Expected"`
   - Column 1, row 2: label `"Was"`
   - Column 2, row 1: the `AssertionFailure.expected()` value (rendered via `toString()`; show
     `"—"` when `null`)
   - Column 2, row 2: the `AssertionFailure.actual()` value (rendered via `toString()`; show
     `"—"` when `null`)

   Assertions that only have a `message` and no `expected`/`actual` (e.g. free-form
   `fail("…")` calls or network errors) should instead print the message as a single indented
   line rather than a grid.

   **No cursor-position tracking needed**: this block is printed after the TUI exits and the
   terminal is fully restored, so content is appended sequentially — there is no need to
   calculate or store cell coordinates or use cursor-positioning escape sequences. A simpler
   rendering approach is therefore appropriate here.

   **Rendering library**: investigate using `spring-shell-table` (`org.springframework.shell.table`
   package) to render the per-assertion grids. Before adopting it, verify during implementation
   that it supports:
   - text colour inside cells
   - border colour
   - cell/row background colour

   If `spring-shell-table` does not support colours adequately, fall back to a plain ANSI-formatted
   ASCII table rendered manually. Check during implementation whether the dependency is already
   transitively available via `spring-shell-starter` before adding an explicit POM entry.

   **Data flow note**: `TestCompleted.failureMessages()` currently carries only plain strings.
   To render expected/actual values this event must be changed to carry
   `List<AssertionFailure>` instead, so the structured `expected` and `actual` fields are
   available to the render loop. `AssertionFailure` is already defined in
   `model/AssertionFailure.java` and already populated by `PureJavaTestEngine.extractFailures()`.
   Note: `TestCompleted` now also carries `uniqueId` and `assertionCount` (added for Item 2).
