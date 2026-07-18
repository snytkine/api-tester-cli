# Issue #65 — Lifecycle hooks: implementation progress

Living record of implementation state so work can resume across sessions.
Spec: GitHub issue #65 (also mirrored in `hooks-feature.md`).

## Status legend
- [ ] not started
- [~] in progress
- [x] done + compiles + tests green at last checkpoint

## Key design decisions (may deviate from the issue's literal plan)
1. **No separate `HookType` enum.** Hooks use the sealed-interface + Jackson
   `@JsonSubTypes` discriminator pattern (`type: script|web`), mirroring the existing
   `model.assertions.Assertion` hierarchy rather than the `AuthType`/`BodyType` enum
   pattern. `Hook` sealed interface permits `ScriptHook`, `WebHook`.
2. **Hook run-metadata carried on `SuiteRunContext`.** Rather than change the
   `TestEngine.runConfigurationSuite(suite, context, listener)` 3-arg signature (60+
   test call sites), the extra run metadata hooks need (interactive flag, report
   dir/path, tag/test filters, env-file path, suite dir) rides on `SuiteRunContext`
   via a new optional `HookRunMetadata`. The engine reads `testSuite.hooks()` +
   this metadata; the interface is unchanged.
3. **Report path computed up front** in `RunSuiteCommand` (before the engine runs) so
   `report_path` is available to `after-all` hooks (fired inside the engine).
4. **Phase ownership:**
   - Engine (`PureJavaTestEngine`): `before-all`, `before-each`, `after-each`,
     `after-all`. `after-all` events fired before `SuiteCompleted` so the TUI can
     buffer + render them after the summary.
   - Command (`RunSuiteCommand`): `suite-validation-failed`, `before-report`,
     `after-report`, plus the `--allow-scripts` gate.
5. **Async hooks** submitted to a per-run executor; awaited (bounded by each hook's
   timeout) at end of the owning scope (engine end for test-loop phases; command for
   report phases). Failures are warnings only. This satisfies "CLI waits for async
   hooks before exit" without true detached lifetime.
6. **TUI:** `before-all` rendered before the grid; `after-all` buffered and rendered
   after the summary; `before-each`/`after-each` NOT rendered as separate grid lines
   (would corrupt cursor math) — their failures surface as test Error / async warnings.

## Phases
- [x] 1. Progress file (this file)
- [x] 2. Model: `HookPhase`, `Hook`, `ScriptHook`, `WebHook`, `Hooks`; `TestSuite.hooks`;
      `TestSuiteLoader` threading; `withFilteredTests`; Jackson subtype reg + reflect-config
      (main compiles)
- [x] 3. `HookRunMetadata` + `SuiteRunContext` optional metadata field (runID-preserving wither)
- [x] 4. Events: `HookPhaseStarted`, `HookCompleted`, `HookPhaseCompleted` + exhaustive
      switch handling + before-all/after-all rendering in `TerminalUiController` (main compiles)
- [x] 5. Executors: `HookExecutionResult`, `ScriptHookExecutor`, `WebHookExecutor`, `HookFailedException`
- [x] 6. `HookRunner` + `AsyncHookHandles`
- [x] 7. Engine orchestration (before-all/before-each/after-each/after-all)
- [x] 8. `RunSuiteCommand`: `--allow-scripts` gate, `EXIT_HOOK_FAILURE=3`, report + validation hooks, metadata wiring
- [x] 9. `TestSuiteValidator.validateHooks`
- [ ] 10. JSON schema (`.vscode/test-suite-schema.json` + `src/main/resources/schemas/…`)
- [ ] 11. TUI rendering polish (async warnings / still-running note) — basic boxes done in phase 4
- [ ] 12. Docs: stand-alone hooks page in cmdrest-web/src/docs + intro link + README mention
- [~] 13. Tests for every new/changed unit; `spotless:apply`; full `./mvnw test` green

## CHECKPOINT (state as of last session activity)
- All 802 pre-existing tests PASS. Main + tests compile clean.
- `PureJavaTestEngine` now has TWO public constructors; the 4-arg (with HookRunner) is
  `@Autowired` so Spring picks it. The 3-arg convenience builds a default HookRunner.
- Remaining blocker: **JaCoCo 95%/80%/75% coverage gate fails** on the new code.
  Packages below threshold needing tests: service.hooks (0.09), model.hooks (0.30),
  service (0.82, engine+validator hook paths), commands (0.93), ui (0.88), exception (0.80).
- Test call sites in RunSuiteCommandTest were bulk-updated: constructor gained
  `mock(HookRunner.class)`, every runSuite(...) gained an `allowScripts=false` arg.
- TestProgressEventTest exhaustive switch updated with the 3 hook events.
- NOTE: `./mvnw test` runs jacoco:check. To run tests without the gate while iterating:
  `./mvnw test -Djacoco.skip=true` (verify this property works) or target specific classes.

## Files created (running list)
- model/hooks/HookPhase.java, Hook.java, ScriptHook.java, WebHook.java, Hooks.java
- model/HookRunMetadata.java

## Files modified (running list)
- model/TestSuite.java (hooks field + convenience 8-arg ctor + withFilteredTests)
- service/TestSuiteLoader.java (thread hooks through both load methods)
- model/SuiteRunContext.java (hookRunMetadata field + withHookRunMetadata wither + getter)
- event/TestProgressEvent.java (3 hook event records)
- ui/TerminalUiController.java (pre-grid before-all render, buffered after-all render, helpers)
- ApiTesterCliApplication.java (RegisterReflectionForBinding: Hook/HookPhase/Hooks/ScriptHook/WebHook)

## Notes / open items during implementation
- Build: `./mvnw test` (spotless runs on verify, not test — run `./mvnw spotless:apply` before done).
- Java: project targets GraalVM Java 25; terminal default may be 21. Use
  `JAVA_HOME=~/.sdkman/candidates/java/current` only if a build fails without it.
