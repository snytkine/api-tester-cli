# Implementation Plan — Issue #15: Single-page HTML execution report from `TestRunResult`

## Context

The CLI can emit JSON results (`--no-ui`) and a live terminal UI, but there is no shareable,
human-friendly artifact of a run. This adds a `HtmlReportGenerator` service that renders a
`TestRunResult` + originating `TestSuite` into a **self-contained single-page HTML report**
(embedded CSS, native `<details>`/`<summary>` expand-collapse, no JavaScript), written to a path
supplied via a new `--report` option on `run-suite`. Issue #14 (`apiResponse` on `TestCaseResult`)
is merged, so request **and** response data are available per test.

## Key design decision (confirmed with user)

The project renders Thymeleaf via a **plain `TemplateEngine` using OGNL** (`util/FileLoader.java`).
OGNL resolves `${obj.prop}` to `getProp()`/public field — it does **not** read Java *record*
accessors (`name()`). All model types here are records. **Therefore the report binds Maps, not
records:** `HtmlReportGenerator` converts records → `Map<String,Object>` / `List`/`String` via
private helper methods before populating the Thymeleaf `Context`, exactly mirroring how
`FileLoader.parseFile` passes `Map`s today. This is OGNL-native and GraalVM-friendly (Maps need no
per-type reflection metadata).

**Conscious deviations from the issue text** (call out in PR description):
- **No `TestCaseReportView` record.** Helpers convert `TestCaseResult` → `Map` directly (the
  Map *is* the view). Drops that checklist item by design, per user guidance.
- **No constructor-injected `ObjectMapper`.** There is no Jackson bean in this project (every
  service news its own — see `RunSuiteCommand`, `PureJavaTestEngine`). The generator creates its
  own `ObjectMapper` field. Document thread-safety.
- **No model reflection hints needed** for the template (Maps/Lists/Strings only). Only a
  `resource-config.json` entry for the template file is required for native image.

## Relevant existing code (verified)

- `util/FileLoader.java` — `static final TemplateEngine` built in a static block with
  `StringTemplateResolver` + `TemplateMode.TEXT`; `parseFile` sets Map vars on a `Context`. Mirror
  this pattern for the HTML engine.
- `commands/RunSuiteCommand.java` — Spring Shell `@Command(name="run-suite", alias={"rs"})`;
  options use **method-parameter** injection with `@Option(longName = "...")` (singular attribute
  name — match this, not the issue's `longNames`). Constructor injects services; `jsonMapper` is
  `new ObjectMapper()`. Output via `context.outputWriter().println(...)` + `.flush()`.
  - **UI branch (~line 230):** `testEngine.runConfigurationSuite(...)` return value is **discarded**,
    then `controller.await()`.
  - **Non-UI branch (~line 255):** captures `TestRunResult result`, prints `toJson(...)`.
- Models (records, package `model`): `TestRunResult(passedCount,failedCount,skippedCount,errorCount,results,appliedOptions)`,
  `TestCaseResult(name,result,passedAssertions,failures,skipReason,requestInfo,apiResponse)`,
  `ApiResponse(statusCode,headers,Body body,responseTimeMs)` + `Body(text,json)`,
  `ExecutedRequestInfo(method,url,headers,body)`, `AssertionFailure(description,expected,actual,error)`,
  `TestSuite(name,description,...)`, enum `TestResult{PASSED,FAILED,SKIPPED,ERROR}`.
- Native-image metadata dir exists: `src/main/resources/META-INF/native-image/io.github.snytkine.apitester/api-tester-cli/`
  with `reflect-config.json` only — **no** `resource-config.json` yet.

## Changes

### 1. New template — `src/main/resources/templates/suite-report.html`
Full HTML document, `lang="en"`, all CSS embedded in a `<style>` block in `<head>` (self-contained).
Thymeleaf HTML-mode attributes bind to the Map context (below). Structure:
- **Header**: `th:text="${suiteName}"`, optional `th:if="${suiteDescription}"` description, and
  `generated: ` + `th:text="${generatedAt}"` (pre-formatted String).
- **Stats block**: flex row of 5 stat `<div>`s (PASSED/FAILED/SKIPPED/ERROR/TOTAL) with colored top
  borders, `th:text` from `${passedCount}` etc. and `${totalCount}`.
- **Per-test**: `<article th:each="t : ${tests}" th:classappend="${t.statusClass}">` with a
  left border colored by status; `:hover` box-shadow.
  - Badge `<span class="badge" th:classappend="${t.statusClass}" th:text="${t.result}">`.
  - `Assertions passed: <span th:text="${t.passedAssertions}">` and, when `${t.failedAssertions > 0}`,
    `failed: <span th:text="${t.failedAssertions}">`.
  - **SKIPPED**: show `Reason: ` + `th:text="${t.skipReason}"`; hide request/response (`th:if="${t.hasRequest}"`).
  - `<details><summary>request</summary>…</details>` (when `${t.hasRequest}`): method+url, a header
    grid, and a `<pre><code th:text="${t.requestBody}">` when body present.
  - `<details><summary>response</summary>…</details>` (when `${t.hasResponse}`): status,
    `responseTimeMs`, header grid, `<pre><code th:text="${t.formattedResponseBody}">`.
  - `<details th:if="${t.failedAssertions > 0}" open><summary>Failed Assertions</summary>` →
    `<ul><li th:each="f : ${t.failures}">` showing `f.description`, and `expected`/`actual`/`error`
    in `<code>` spans.
  - Header grids render from `List<Map>` of `{name,value}`: `th:each="h : ${t.requestHeaders}"` →
    `${h.name}` / `${h.value}` (lists of maps, **not** raw `Map.Entry`, to stay OGNL/native-safe).
- CSS palette from the issue: page `#f5f7fa`, card `#ffffff`, header accent `#1e3a5f`, PASSED
  `#2e7d32`, FAILED `#c62828`, SKIPPED `#e65100`, ERROR `#6a1a6a`, border `#dde1e7`; page max-width
  `960px`, centered, `font-family: system-ui, sans-serif`; `<summary>` styled as a pill/badge.

### 2. New service — `service/HtmlReportGenerator.java`
`@Service`, thread-safe singleton (document in class Javadoc). Members:
- `private static final TemplateEngine HTML_ENGINE` built in a `static {}` block (mirror
  `FileLoader`): `ClassPathTemplateResolver` with `setPrefix("templates/")`, `setSuffix(".html")`,
  `setTemplateMode(TemplateMode.HTML)`, `setCharacterEncoding("UTF-8")`, `setCacheable(true)`.
  Document that `TemplateEngine` is thread-safe after initialization.
- `private final ObjectMapper jsonMapper = new ObjectMapper();` (own instance; no bean exists).

Public method:
```java
public void generate(TestRunResult result, TestSuite suite, Path outputPath) throws IOException
```
- Build a `Context`; `setVariable` for: `suiteName`, `suiteDescription`, `generatedAt`
  (`LocalDateTime.now()` formatted via `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")` → String),
  `passedCount/failedCount/skippedCount/errorCount`, `totalCount` (sum), and
  `tests` = `result.results().stream().map(this::toTestMap).toList()`.
- `String html = HTML_ENGINE.process("suite-report", ctx);`
- `Files.createDirectories(outputPath.toAbsolutePath().getParent())` then `Files.writeString(outputPath, html)`.

Private helper methods (the record→Map converters the user asked for):
- `Map<String,Object> toTestMap(TestCaseResult tc)` — keys: `name`, `result` (`tc.result().name()`),
  `statusClass` (lowercase, e.g. `passed`), `passedAssertions`, `failedAssertions`
  (`tc.failures().size()`), `skipReason`, `hasRequest` (`requestInfo != null`), `requestMethod`,
  `requestUrl`, `requestBody`, `requestHeaders` (via `headersToList`), `hasResponse`
  (`apiResponse != null`), `responseStatus`, `responseTimeMs`, `responseHeaders`,
  `formattedResponseBody` (via `formatBody`), `failures` (via `failuresToList`). Null-safe for every
  nullable field.
- `List<Map<String,String>> headersToList(@Nullable Map<String,String> headers)` → `[{name,value}…]`
  (empty list when null).
- `List<Map<String,Object>> failuresToList(List<AssertionFailure> failures)` → per failure
  `{description,expected,actual,error}`.
- `@Nullable String formatBody(@Nullable ApiResponse resp)` — if `resp==null` return null; if
  `body.json()!=null` return `jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)`;
  else return `body.text()`.

### 3. Native image — `META-INF/native-image/io.github.snytkine.apitester/api-tester-cli/resource-config.json`
New file including the template as a runtime resource:
```json
{ "resources": { "includes": [ { "pattern": "templates/suite-report.html" } ] } }
```
No `reflect-config`/`@RegisterReflectionForBinding` additions needed (template binds Maps, not
records). TEXT-mode Thymeleaf already runs in the native binary, so engine + OGNL + standard dialect
are already reachable; HTML mode adds the attoparser HTML path — verify via native build (below).

### 4. `commands/RunSuiteCommand.java`
- Constructor: add `HtmlReportGenerator htmlReportGenerator` param + `private final` field.
- Add option parameter to `runSuite`, matching this file's existing `@Option` style:
  `@Option(longName = "report", description = "Write an HTML execution report to this file path.") @Nullable String reportPath`
  (add the file's supported short-name attribute for `-r` if present in its `@Option` API; existing
  options use none, so long name is the baseline).
- Capture the run result in **both** branches: declare `TestRunResult result = null;` before the
  `if (useUi)`; assign `result = testEngine.runConfigurationSuite(...)` in the UI branch (currently
  discarded) and keep the assignment in the non-UI branch.
- After the if/else (and after `controller.await()` in UI mode), once, generate the report when
  requested and a run actually occurred:
  ```java
  if (reportPath != null && result != null) {
      Path out = Path.of(reportPath);
      htmlReportGenerator.generate(result, suiteToRun, out);
      context.outputWriter().println("Report written to " + out.toAbsolutePath());
      context.outputWriter().flush();
  }
  ```
  Early-return validation/empty-tag paths leave `result == null`, so no report is written then.

## Tests
- **New `service/HtmlReportGeneratorTest.java`** — build a `TestRunResult` with three
  `TestCaseResult`s: PASSED (with `requestInfo` + `apiResponse`), FAILED (with `failures` +
  `apiResponse` JSON body), SKIPPED (with `skipReason`, null request/response). Call `generate(...)`
  to a JUnit `@TempDir` path, read the file, assert the HTML contains: suite name, the 4 counts and
  TOTAL, each test name, the status words (`PASSED`/`FAILED`/`SKIPPED`), `<details>`/`<summary>`,
  the skip reason, a failure `description`/`expected`/`actual`, and the pretty-printed JSON body.
  Assert it starts with `<!DOCTYPE html>`/contains `<style>` (self-contained).
- **`commands/RunSuiteCommandTest.java`** — every site constructing `new RunSuiteCommand(...)` must
  pass a (Mockito) `HtmlReportGenerator` (constructor arity changed). Add a test: invoking
  `runSuite` with the report path set calls `htmlReportGenerator.generate(...)` and the output
  contains `Report written to`. Existing no-report tests verify `generate` is never called.

## Verification
```bash
./mvnw spotless:apply
./mvnw test
./mvnw test -Dtest=HtmlReportGeneratorTest
./mvnw test -Dtest=RunSuiteCommandTest
```
Manual (JVM): `rs --suite ./src/test/resources/<suite>.yml --report /tmp/report.html api_base_url=…`
then open `/tmp/report.html` in a browser — confirm header/counts, per-test badges, and that
`<details>` sections expand/collapse with no JS, JSON bodies are pretty-printed, FAILED tests show
the failures section open.
Native (project requirement — the key risk to confirm): `./mvnw -Pnative native:compile` then
`./target/api-tester-cli rs --suite … --report /tmp/report.html` — confirms the template resource is
bundled (resource-config) and HTML-mode Thymeleaf renders in the native binary. If the native run
reports missing resources/reflection, extend `resource-config.json` / add hints accordingly.

## Checklist (issue, adjusted for the confirmed Map approach)
- [ ] `HtmlReportGenerator` `@Service` added; thread-safety documented; own `ObjectMapper`
- [ ] `templates/suite-report.html` created (self-contained, embedded CSS, `<details>`/`<summary>`)
- [ ] Dedicated HTML-mode `TemplateEngine` (`ClassPathTemplateResolver`), not the TEXT engine
- [ ] Record→Map helper methods (`toTestMap`/`headersToList`/`failuresToList`/`formatBody`)
- [ ] `formattedResponseBody` via `ObjectMapper.writerWithDefaultPrettyPrinter()`
- [ ] `resource-config.json` includes `templates/suite-report.html`
- [ ] `RunSuiteCommand` gains `--report`; result captured in UI **and** non-UI branches
- [ ] Header (name/description/generated time), stats block, per-test badges + assertion counts
- [ ] Failures `<details>` open on FAILED; SKIPPED hides request/response and shows reason
- [ ] `HtmlReportGeneratorTest` + `RunSuiteCommandTest` updated (constructor arity + `--report`)
- [ ] `spotless:apply`; all tests pass; native build renders a report
