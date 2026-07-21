# Suggested Improvements — api-tester-cli

A review of the project's source code, design, and architecture. All suggestions stay within
the current stack (Spring Boot 4 + Spring Shell 4, Thymeleaf, Jackson, GraalVM native image).

## Overall Assessment

This is a well-built project — clearly above average for a solo-maintained CLI tool.

**Strengths:**

- **Sound layering.** The command layer (`RunSuiteCommand`), loading/templating
  (`TestSuiteLoader` + `FileLoader`), validation (`TestSuiteValidator`), execution
  (`PureJavaTestEngine`), assertion evaluation (`AssertionEvaluatorFactory` + 30 evaluators),
  and reporting (`HtmlReportGenerator`, terminal UI) are cleanly separated with narrow
  interfaces (`TestEngine`, `AssertionEvaluator`, `TestProgressListener`).
- **Modern, idiomatic Java.** Records for the entire model, a sealed `Assertion` interface
  with exhaustive pattern-matching switches, constructor injection everywhere, and jspecify
  nullability annotations. The sealed-interface + `@JsonSubTypes` combination gives
  compile-time exhaustiveness *and* GraalVM-safe explicit type registration — a good fit for
  the native-image constraint.
- **Deliberate engineering discipline.** Thread-safety guarantees are documented per class
  and actually hold (per-invocation state is stack-confined). The event-driven progress
  system (`TestProgressEvent` sealed hierarchy → listener → queue → `TerminalUiController`)
  decouples execution from presentation nicely. Test coverage is strong (~330 tests, engine
  tests via a stubbed `ClientHttpRequestFactory` rather than live HTTP).
- **Good operational touches.** Best-effort version check that can never break a run,
  silent non-interactive mode with meaningful exit codes, credential masking in reports.

**Main weaknesses**, elaborated below:

1. The **trust boundary of a test-suite file is undefined**: a suite YAML gets full
   Thymeleaf/OGNL expression evaluation plus access to the *entire* process environment,
   so a suite file must be treated as executable code — but nothing documents or enforces
   that.
2. **`PureJavaTestEngine` and `RunSuiteCommand` are doing too much** (client construction,
   auth/URL resolution, per-test template re-resolution, assertion loop, capture callbacks
   in one class; filtering, validation, UI orchestration, reporting, exit codes in the other).
3. The **per-test full-suite re-parse** template strategy works but is the part of the design
   that will hurt most when the planned parallel execution lands.

None of these are structural flaws — they are all fixable incrementally without changing the
framework.

---

## 1. Security

### 1.1 Define and document the suite-file trust boundary (highest priority)

A test-suite YAML is processed through Thymeleaf TEXT mode with OGNL expressions
(`FileLoader.parseFile`). OGNL can invoke methods on any object reachable from the context,
so **a suite file is effectively executable code**, not passive data. Combined with 1.2
below, running a suite obtained from a third party is dangerous.

- Document prominently (README + `docs/`) that suite files must be trusted, the same way a
  Makefile or a Postman pre-request script must be trusted.
- Consider registering a restricted `IExpressionObjectFactory` / expression context so only
  the documented utilities (`#temporals`, `#strings`) and the four variable maps are
  reachable, rather than the default full OGNL surface.

### 1.2 Don't expose the entire process environment to templates

`DotEnvLoader.loadDotEnv` merges the `.env` file with **all** process environment variables,
and every value is addressable from any suite expression (`[[${env.AWS_SECRET_ACCESS_KEY}]]`)
and can be interpolated into a request URL, header, or body sent to an arbitrary host — a
ready-made exfiltration channel for a malicious or compromised suite file.

- Prefer an allowlist: only `.env` entries plus process variables matching a documented
  prefix (e.g. `APITESTER_*`), or an explicit `--env-passthrough` flag for the current
  behaviour.

### 1.3 Mask sensitive request headers in the HTML report

`HtmlReportGenerator` masks `auth.username`/`auth.password` (`MASKED_VALUE`), but
`requestHeaders` are rendered verbatim. A suite that authenticates via
`Authorization: Bearer <token>` or `X-Api-Key: …` headers (the common case for API testing)
leaks live credentials into every generated report — which users then attach to tickets or
commit accidentally.

- Mask a known set of sensitive header names (`Authorization`, `Proxy-Authorization`,
  `Cookie`, `X-Api-Key`, …) in `toTestMap`/`headersToList`, ideally with an opt-out.
- Same applies to any future JSON output of `ExecutedRequestInfo`: `RequestAuth` carries raw
  credentials and has no serialization masking (`@JsonIgnore`/custom serializer) — masking
  currently exists only in the HTML path.

### 1.4 Disable remote `$ref` resolution in JSON-schema validation

`JsonSchemaAssertionEvaluator` builds the networknt `JsonSchema` without a
`SchemaValidatorsConfig`. A schema file containing a remote `$ref` (e.g.
`"$ref": "http://internal-host/…"`) can trigger network fetches during validation — an SSRF
vector and a source of nondeterministic test runs. Configure the factory to forbid (or
explicitly allowlist) non-local schema resolution.

### 1.5 Constrain suite-relative file references

`FileLoader.loadFile(directory, file)` uses `directory.resolve(file)` with no normalization,
so `content: ../../../../etc/passwd` (request body) or an absolute path escapes the suite
directory, and the file content is then sent in an HTTP request. For a local CLI this is
low severity, but combined with an untrusted suite it becomes an arbitrary-file-read +
exfiltration primitive. Normalize the resolved path and (at least by default) require it to
stay under the suite directory.

### 1.6 Ignore generated reports and logs in git

`.gitignore` covers `*.log` and `.vscode/*.yml`, but the generated
`test-suite_*.html` reports (several are sitting untracked in `.vscode/` right now) are not
ignored. Reports embed full request/response bodies and headers — exactly the kind of file
that should never land in a public repo by accident. Add `test-suite_*.html` (or a dedicated
default report directory) to `.gitignore`.

### 1.7 Smaller items

- **Warn on Basic auth over plain `http://`** base URLs — credentials go over the wire
  base64-encoded only.
- **Debug logging** (`PureJavaTestEngine`) logs fully-resolved URLs; resolved URLs may embed
  secrets from variables (query-string tokens). Worth a note in the logging docs, since
  `CLI_LOG_DIR` persists these to disk.
- **Supply-chain hygiene:** versions are properly BOM-managed; add Dependabot/Renovate and an
  OWASP `dependency-check` (or `mvn versions:display-dependency-updates` in CI) so the two
  pinned non-BOM deps (`dotenv-java`, `json-schema-validator`) don't silently age.

---

## 2. Code Quality

### 2.1 `VersionChecker.fetchWithTimeout` can block far past its timeout

The try-with-resources on the `ExecutorService` is the problem: when `future.get(timeout)`
throws `TimeoutException`, `executor.close()` still **waits for the in-flight HTTP call to
finish** before the exception propagates, so the configured timeout is not actually
enforced — a hung connection stalls each retry attempt indefinitely (daemon thread, so it
won't block JVM exit, but the retry loop and the log message lie about the timeout). Call
`future.cancel(true)` and use `shutdownNow()` instead of relying on `close()`; also consider
creating the executor once per check rather than once per attempt.

### 2.2 Missing-default-client NPE in the engine

In `PureJavaTestEngine.runConfigurationSuite`, `defaultRestClient` is
`restClients.get("default")`. With the plural `rest-clients` form where every client has an
explicit id and none is named `default`, a request that omits `rest-client:` gets a `null`
client and NPEs at `buildRequestSpec`. `TestSuiteValidator.validateRestClients` doesn't
guard this. Either add a validation rule ("when multiple clients are declared, one must have
id `default`, or every request must select a client") or treat the first declared client as
the default.

### 2.3 `catch (Throwable)` in the test loop is too broad

The ERROR branch in `runConfigurationSuite` catches `Throwable`, which swallows
`OutOfMemoryError`/`StackOverflowError` and converts them into a test result. Catch
`Exception` (plus restore interrupt status for `InterruptedException` when parallel execution
arrives). Also, `new AssertionFailure(e.getMessage(), …)` produces a `null` description for
message-less exceptions (`NullPointerException` typically) — fall back to
`e.getClass().getSimpleName()`.

### 2.4 Deduplicate the two execution branches in `RunSuiteCommand.runSuite`

The UI and non-UI paths both build `validationErrors`, both compute the identical
"empty after tag filter" message, and both run the engine. Extract a
`validateAndRun(suiteToRun, listener, errorSink)` helper — the method is 200+ lines and this
is the biggest single win. Likewise the `runSuite` signature (7 parameters) would read
better with an options record.

### 2.5 Consolidate `ObjectMapper` creation

At least five separately configured mappers exist (`TestSuiteLoader`, `PureJavaTestEngine`
— both YAML with identical config — `RunSuiteCommand`, `HtmlReportGenerator`,
`AssertionEvaluatorFactory`, `ResponseResolver`, `VersionChecker`). Define two beans
(`yamlMapper`, `jsonMapper`) in a small `JacksonConfig` and inject them. This removes config
drift risk (e.g. one YAML mapper tolerating unknown properties while another doesn't) and is
friendlier to native-image analysis by having one registration point.

### 2.6 Remove the unused Lombok dependency

`grep` finds no Lombok usage under `src/main` — records replaced it. Dropping it removes an
annotation processor from the build and one moving part from the GraalVM story.

### 2.7 Reduce the 30× evaluator boilerplate with a base class

Most evaluators follow the same shape: extract value via `ResponseValueExtractor` → handle
`Missing`/`Error` → compare → `collector.fail(...)`. A small abstract base (template method:
`extract`, `compare`, `describeFailure`) would collapse a lot of near-identical code
(`GreaterThan`/`GreaterThanOrEqual`/`LessThan`/…, the three `array_size*` variants, the
string-prefix/suffix/contains family) while keeping one class per assertion type and the
exhaustive factory switch. This also shrinks the surface to keep consistent when failure
formatting changes.

### 2.8 Smaller items

- `TestSuiteLoader.load(Path)` reads the file twice (`Files.readString` +
  `yamlMapper.readValue(filePath.toFile(), …)`); parse the string you already read. The two
  `load` overloads also share unwiring that could be one private method.
- `PureJavaTestEngine.buildRestClient` references
  `org.springframework.http.client.JdkClientHttpRequestFactory` by fully-qualified name
  inline — import it. More substantively: `RestClientConfig` supports only
  `connectTimeout`; there is no read/response timeout, so a server that accepts the
  connection and never responds hangs a suite run forever. Add a `readTimeout` and apply it
  via `JdkClientHttpRequestFactory#setReadTimeout`.
- The namespace keys `"cli"`, `"env"`, `"suite"`, `"test"` are string literals repeated
  across `TestSuiteLoader`, `PureJavaTestEngine`, `FileLoader` docs, and evaluators —
  promote them to constants (or a small enum) in one place.
- The regex-based `HtmlReportGenerator.minify` is clever but fragile (sentinel substitution,
  seven ordered passes over the whole document). Since the input is your own template, the
  cheapest robust fix is to keep the template itself compact (or minify it once at build
  time) and delete the runtime minifier.
- `buildCliVariables` silently drops positional args without `=`. A typo like
  `api_base_url:https://…` vanishes without a trace; log a warning per skipped token.
- The single-element-array capture pattern (`ExecutedRequestInfo[] capturedRequest`) works
  but is a code smell — see architecture item 3.2 for the structural fix.

---

## 3. Architecture

### 3.1 Rework per-test template resolution before adding parallelism

Today each test with `test.*` variables re-renders the **entire suite YAML** through
Thymeleaf and re-parses it with Jackson, then finds its own test case by name
(`executeSingleTest`). This is O(tests × suite-size), and more importantly it couples every
test's resolution to the whole-file template — a problem for the planned `dependsOn`
parallel execution, where you want per-test resolution to be an isolated pure function.

Suggestion: at load time, split the raw YAML into per-test-case template fragments (the
loader already knows the structure after Step 1), and at execution time render only the
fragment for the test being run with its `test` namespace. Same semantics, no cross-test
coupling, and each execution becomes independently schedulable on any thread.

### 3.2 Decompose `PureJavaTestEngine` and return richer results instead of capture callbacks

The engine currently owns five distinct concerns: RestClient construction
(`buildRestClient`), client/auth/URL resolution (`selectRestClient`,
`resolveEffectiveAuth`, `resolveFullUrl` — note the JavaDoc admits these "mirror" logic
implemented elsewhere, i.e. the same rule lives in two places), per-test template
resolution, request building, and the assertion loop. Extract:

- `SuiteRestClients` (build once per run; owns id→client map, default-client rule, and
  effective-auth/full-URL answers so the rule exists exactly once),
- `TestCaseResolver` (the template re-resolution from 3.1),
- keep the engine as the orchestration loop.

Then let `executeSingleTest` **return** a `TestCaseExecution` record (resolved request info,
response, failures) instead of mutating single-element arrays through `Consumer` callbacks —
the try/catch in `runConfigurationSuite` collapses and the parallel version falls out
naturally (`Callable<TestCaseExecution>` per test).

### 3.3 Give ERROR results a first-class representation

`TestCaseResult` models an engine-level error as a one-element `failures` list with nulls in
the expected/actual slots. Downstream code (report, summary, UI) must "know" that ERROR
results abuse the assertion-failure shape. Add an explicit `@Nullable String errorMessage`
(or a small sealed `TestOutcome` type) so renderers stop pattern-matching on convention.

### 3.4 Add machine-readable report formats behind an interface

`HtmlReportGenerator` is concrete and HTML-only, and its `Map<String,Object>` template model
is stringly-typed. Two low-cost moves:

- Introduce a `ReportRenderer` interface (`generate(TestRunResult, TestSuite, Path,
  ReportOptions)`) with the HTML implementation as the first member; a **JUnit-XML renderer**
  is the single most valuable addition for CI adoption of a tool like this, and a JSON
  renderer is nearly free (the records already serialize).
- Replace the map-building in `toTestMap` with small dedicated view records — Thymeleaf
  reads record accessors fine, and you regain compile-time safety between generator and
  template.

### 3.5 Collapse the three per-assertion switch sites

Adding an assertion type currently touches: the sealed `permits` clause, `@JsonSubTypes`,
`AssertionEvaluatorFactory.create`, and `AssertionDescriber.describe`. The first two are
inherent (and good for GraalVM). For the last two, consider moving `describe()` onto the
`Assertion` subtypes themselves and having each assertion expose its evaluator constructor
reference — the factory switch then disappears, and "add an assertion" becomes one record +
one evaluator + one `@JsonSubTypes` line. (If you prefer keeping model records logic-free,
at least co-locate `create` and `describe` in one switch so they can't drift.)

### 3.6 Prefer Spring Boot exit-code machinery over injected `System::exit`

The `IntConsumer exitHandler` (defaulting to `System::exit`) inside a command bean works and
is testable, but `System.exit` from inside a running Spring context skips orderly shutdown.
Spring Boot's `ExitCodeGenerator` + `SpringApplication.exit(...)` gives the same testability
(the generator is a bean) with proper context close, and removes the need for the dual
constructor.

### 3.7 Package layout nits

- `interfaces/` as a package name is organization-by-kind; `TestEngine` belongs next to its
  implementation in `service/` (or an `engine/` feature package), and `AssertionEvaluator`
  next to the evaluators. Package-by-feature will also keep the planned parallel-execution
  code from smearing across four packages.
- `PureJavaTestEngine` is named for what it isn't (a historical contrast with REST-assured,
  presumably). `RestClientTestEngine` or simply `DefaultTestEngine` describes what it is.

---

*Generated by a code review on 2026-07-06 against commit `f484efe`.*
