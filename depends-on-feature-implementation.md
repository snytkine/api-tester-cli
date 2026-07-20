# Issue #56 — Implementation Progress (`session` capture + `depends-on`)

Tracks implementation of the refined GitHub issue #56. Source of truth for the design:
the issue body on GitHub and `depends-on-feature.md`.

**Confirmed decisions (2026-07-20):**
- Concurrency/parallel interaction is **deferred** — execution is sequential today; the mutable
  `session` map lives on the suite-run call stack (no per-request state on the singleton engine).
- Each execution is reported as its **own labeled result row** (e.g. `"CreateRecord (dependency of GetRecord)"`).
- `depends-on` resolves **transitively**; **cycles are a suite-validation error** before execution.

**Overall status:** Phases 1 & 2 complete and green (full suite: 919 tests pass, JaCoCo passes,
spotless clean). Phase 3 and docs/schema remain.

---

## ✅ Phase 1 — Model + deserialization + validation (DONE)

Files added:
- `model/SessionValueType.java` — enum `STRING | INTEGER | DOUBLE | BOOLEAN`, `@JsonCreator fromValue`
  (case-insensitive).
- `model/SavedSession.java` — record `(String name, String path, @Nullable SessionValueType type,
  @JsonProperty("default") @Nullable String defaultValue, boolean required)`.

Files changed:
- `model/TestCase.java` — added record components:
  - `@JsonProperty("saved-session") @Nullable List<SavedSession> savedSession`
  - `@JsonProperty("depends-on") @Nullable List<String> dependsOn`
  - `@JsonProperty("transient") boolean transientCase`  ← named `transientCase` because `transient`
    is a Java keyword and can't be a record component name.
  - Added a 7-arg **backward-compatible convenience constructor** (old callers/tests still compile).
- `service/TestSuiteValidator.java` — new `validateDependencies(TestSuite)`:
  - Rule 1: every `depends-on` name must reference a defined test →
    `"Test '<name>' depends-on unknown test: '<ref>'"`.
  - Rule 2: `depends-on` graph must be acyclic (transitive DFS `findCycle`) →
    `"Circular depends-on dependency detected: a -> b -> a"`. Skipped if unknown refs exist.
- `commands/RunSuiteCommand.java` — `collectValidationErrors` now calls `validateDependencies`.

Tests added:
- `model/TestCaseSessionDeserializationTest.java` — YAML → new fields; enum case-insensitivity.
- Added dependency cases to `service/TestSuiteValidatorTest.java` (`tcDep` helper; unknown ref,
  self-cycle, transitive cycle, diamond-not-a-cycle, unknown-before-cycle).

---

## ✅ Phase 2 — `session` namespace + `saved-session` capture (DONE)

Key insight: with sequential execution + a persistent `session` map, **capture-and-reuse already
works for any test ordered later in the file — no `depends-on` required.** `depends-on` (Phase 3) only
adds explicit ordering/re-run.

Files added:
- `exception/SessionCaptureException.java` — raised on required-but-missing, non-primitive
  extraction, or failed type conversion.
- `service/SessionCapturer.java` — static, stateless. `capture(testName, captures, response, sessionVars)`:
  - Resolves each path via `ResponseValueExtractor.extract(...)`.
  - `Found(null)` / `Missing` / `Error` → "absent": use `default`, else fail if `required`, else skip.
  - Non-primitive (`Map`/`List`) → `"Extracted session parameter '<n>' in test '<t>' at path '<p>' is not a primitive type"`.
  - Type coercion: INTEGER rejects fractional/non-numeric; DOUBLE parses number/string; BOOLEAN
    accepts boolean or `true`/`false` string. Failure →
    `"Session parameter '<n>' in test '<t>' at path '<p>' cannot be converted to <type>: value '<v>'"`.
  - Required miss → `"Failed to extract session parameter '<n>' from response at path '<p>'"`.
  - Stores canonical string form under `name` (last-write-wins); logs each capture at DEBUG.

Files changed:
- `service/assertion/ResponseValueExtractor.java` — class + `extract` made **public** for reuse
  (its nested `Result` records are implicitly public as interface members).
- `service/assertion/ResponseResolver.java` — added `resolve(spec, assertions, boolean forceFull)`
  overload; original 2-arg delegates with `false`. Needed so a capturing test whose only assertion
  is `status_code` still reads/parses the body.
- `service/PureJavaTestEngine.java`:
  - Added suite-wide mutable `Map<String,String> sessionVars = new LinkedHashMap<>()`; exposed in
    `configMap` under key `"session"`.
  - `executeSingleTest` now takes `sessionVars`; re-parse condition changed to
    `templateContent != null && (!testVariables.isEmpty() || !sessionVars.isEmpty())` so
    `[[${session.*}]]` resolves even without test-level vars.
  - After `responseCapture.accept(...)` and **before** assertions:
    `SessionCapturer.capture(config.name(), config.savedSession(), apiResponse, sessionVars)`.
  - `resolve(...)` called with `hasCaptures = config.savedSession() != null && !isEmpty`.
  - New `catch (SessionCaptureException e)` → records the test as `TestResult.FAILED` (message in the
    `AssertionFailure.description` field, matching the existing convention).
- `service/TestSuiteLoader.java` — added empty `"session"` namespace to **both** template passes
  (Step 1 & Step 2) so `[[${session.x}]]` resolves to empty string at load time and is filled in per
  test by the engine.

Tests added:
- `service/SessionCapturerTest.java` — string/int/double/boolean, header, statusCode, non-primitive
  object & array, required-miss, default, default-over-required, optional-skip, bad conversion,
  fractional→int, last-write-wins, null list.
- `service/PureJavaTestEngineSessionTest.java` + `resources/test-suite-stub-session.yml` — end-to-end:
  capture id in test 1, substitute `[[${session.itemId}]]` into test 2's URL; required-miss fails test.

**Gotcha for next session:** real JSONPath body syntax is `response.body.json.$.<expr>` (leading `$`),
e.g. `response.body.json.$.id`. The issue's example `response.body.json.inventory.price` omits the `$`.

---

## ⬜ Phase 3 — `depends-on` execution (NOT STARTED)

This is the invasive part — rewrites `PureJavaTestEngine`'s main loop in `runConfigurationSuite`.

To implement:
1. **Skip transient tests in the top-level iteration** — a test with `transientCase == true` never runs
   standalone; it runs only when another test names it in `depends-on`.
2. **Run dependencies before a test**, in listed order, resolved **transitively** (A→B→C runs C, then
   B, then A). Suggested recursion: `runWithDeps(test, label)` that first runs each dependency via
   `runWithDeps(dep, "<dep> (dependency of <test>)")`, then runs `test` itself.
3. **Re-run per dependent** — a dependency is executed **once per dependent** (no de-dup/caching); a
   non-transient dep named by two others runs 3× total (1 standalone + 2). This falls out naturally
   from the recursion calling the dep each time.
4. **Failure propagation** — if any dependency ends FAILED/ERROR, mark the dependent FAILED without
   sending its request: `"Parent test '<id>' failed with error <parent_error>"`. Stop the chain.
   Return an outcome record `(TestResult, @Nullable String errorMessage)` from the recursion.
5. **Separate labeled result rows** — each execution (standalone, or each dependency re-run) is its own
   `TestCaseResult` labeled with the triggering context.

Interactions to be careful with (this is why Phase 3 was checkpointed):
- before-each / after-each **hooks** currently fire once per top-level test using loop index `i` —
  decide whether they fire for each dependency re-run (recommended: yes, each execution is a full run).
- **Progress events** `TestStarted`/`TestCompleted` carry `uniqueId`/index used by the terminal UI
  grid — the labeled-row model needs unique ids per execution; confirm against the UI grid renderer.
- Cycle safety already guaranteed by `validateDependencies` (runs pre-execution).

Tests to add: transient-only-runs-as-dep; dep runs once per dependent (re-run count); transitive chain
order; parent-failure propagation message; labeled rows in results.

---

## ⬜ Docs + JSON schema (NOT STARTED)

- Update **both** schemas: `.vscode/test-suite-schema.json` and
  `src/main/resources/schemas/test-suite-schema.json` — add `saved-session`, `depends-on`,
  `transient` to the test object.
- Update user docs (see memory `project_documentation_structure`: docs live in
  `cmdrest-web/src/docs`, README stays in repo root; the top-level `docs/` folder is obsolete):
  the new `session` namespace, `saved-session` fields, `depends-on`, `transient`, the primitive-only
  limitation (no objects/arrays), the `response.body.json.$.` path syntax, and the re-run-per-dependent
  semantics.

---

## Build / verify commands
```bash
./mvnw spotless:apply
./mvnw clean test                              # full suite + jacoco (use clean to avoid stale AOT)
./mvnw test -Dtest='SessionCapturerTest,TestSuiteValidatorTest' -Djacoco.skip=true   # fast subset
```
