# api-tester-cli

[![Java CI with Maven](https://github.com/snytkine/api-tester-cli/actions/workflows/maven.yml/badge.svg)](https://github.com/snytkine/api-tester-cli/actions/workflows/maven.yml)

[![codecov](https://codecov.io/github/snytkine/api-tester-cli/graph/badge.svg?token=GNN9UATDU8)](https://codecov.io/github/snytkine/api-tester-cli)

[![Known Vulnerabilities](https://snyk.io/test/github/snytkine/api-tester-cli/badge.svg?targetFile=package.json)](https://snyk.io/test/github/{username}/{repo}?targetFile=package.json)


`api-tester-cli` is a Spring Boot + Spring Shell command-line tool for running HTTP API test suites defined in YAML. Test suites can use Thymeleaf expressions to inject command-line values, values from a local `.env` file, suite-level variables, and per-test variables into requests and assertions.

The project can run as a regular JVM application or as a GraalVM native binary. The JVM build is easiest for development; the native build starts faster and runs without a JVM at runtime.

**Full documentation:** [https://cmdrest.com/docs](https://cmdrest.com/docs)

![TUI test run results](docs/tui-test-run-results.png)

## What It Does

- Executes HTTP test suites described in YAML
- Supports one default `rest-client` or multiple named `rest-clients` with per-request selection — each with `base-url`, `connect-timeout`, shared headers, and HTTP Basic Auth
- Supports per-request HTTP Basic Auth with automatic precedence handling
- Applies Thymeleaf templating before execution
- Evaluates a broad set of response assertions, including status, JSON, headers, strings, ranges, arrays, and response time
- Supports lifecycle hooks — local scripts or outbound web calls fired at suite-execution phases (before/after all, before/after each test, and around report generation); script hooks are gated behind `--allow-scripts` / `APITESTER_ALLOW_SCRIPTS=true` (see [Lifecycle Hooks](https://cmdrest.com/docs/lifecycle-hooks))
- Emits JSON results in non-interactive mode
- Can show an interactive terminal UI when running in a compatible TTY
- Can generate a self-contained single-page HTML execution report with `--report` (browser-side JSON formatting via optional inline JS)
- Can write debug logs to files when `CLI_LOG_LEVEL` and `CLI_LOG_DIR` are set
- Checks GitHub in the background on startup for a newer release and surfaces an upgrade message in the HTML report and terminal UI (see [Upgrade Notifications](https://cmdrest.com/docs/version-check)); never blocks startup or a run and can be disabled via `apitester.version-check.enabled=false`

## Build And Run

### Standard JVM build

```bash
./mvnw clean package
java -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar
```

### GraalVM native binary

This path requires a GraalVM JDK with `native-image` installed and available on `PATH`.

```bash
./mvnw -Pnative native:compile
./target/api-tester-cli
```

The native executable starts significantly faster than the JVM jar and does not require a JVM on the target machine.

## Running A Test Suite

The main command is `run-suite` with the alias `rs`.

```bash
# Simplest form — run test-suite.yml in the current directory
java -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar run-suite

# Explicit suite path (JVM jar)
java -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar run-suite \
  --suite ./src/test/resources/test-suite-1.yml \
  api_base_url=https://api.restful-api.dev

# Alias form
java -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar rs \
  --suite ./src/test/resources/test-suite-1.yml \
  api_base_url=https://api.restful-api.dev

# Native binary (no --suite — uses test-suite.yml in current directory)
./target/api-tester-cli run-suite
```

CLI variables are passed as positional `key=value` arguments after all named options. They become available in Thymeleaf expressions as `[[${cli.key}]]`.

Example:

```yaml
variables:
  api_base_url: "[[${cli.api_base_url}]]"
```

Important behavior:

- `--suite` is optional. When omitted, the CLI looks for `test-suite.yml` in the current working directory and uses it automatically. If that file is not found either, the command exits with an error.
- `--tag=smoke` runs only tests tagged `smoke`. Prefix with `!` to invert: `--tag="!slow"` runs all tests except those tagged `slow` (tests with no tags are always included under a negated filter).
- `--ui` forces the interactive terminal UI.
- `--no-ui` disables the UI and writes JSON results to stdout.
- `--env-file=<path>` loads environment variables from an explicit file (which need not be named `.env`), letting you run the same suite against different environments (e.g. `--env-file=/path/to/staging.env`). See [The `.env` File](#the-env-file) for the full resolution order.
- Relative file references inside the suite, such as body files or JSON schema files, are resolved relative to the suite file's directory.
- Positional variables must not be written as `--key=value`; Spring Shell treats `--...` tokens as options instead of suite variables.

## Test Suite YAML Format

The top-level structure looks like this:

```yaml
name: "User API smoke suite"
description: "Basic end-to-end checks for the user endpoints."

rest-client:
  base-url: "[[${cli.base_url != null ? cli.base_url : 'http://localhost:8080'}]]"
  connect-timeout: 30000
  headers:
    Accept: "application/json"

variables:
  auth_token: "[[${env.AUTH_TOKEN}]]"
  request_id: "[[${#strings.randomAlphanumeric(12)}]]"

tests:
  - name: "Get all users"
    description: "Returns a non-empty user list."
    variables:
      users_path: "/users"
    request:
      method: "GET"
      url: "[[${suite.base_url}]][[${test.users_path}]]"
      headers:
        Authorization: "Bearer [[${suite.auth_token}]]"
    assertions:
      - type: "status_code"
        expected: 200
      - type: "array_is_not_empty"
        path: "response.body.json"

  - name: "Create user"
    request:
      method: "POST"
      url: "[[${suite.base_url}]]/users"
      headers:
        Authorization: "Bearer [[${suite.auth_token}]]"
        Content-Type: "application/json"
      body:
        type: "inline"
        content: |
          {
            "name": "Alice"
          }
    assertions:
      - type: "status_code"
        expected: 201
      - type: "not_null"
        path: "response.body.json.id"
      - type: "response_time"
        max_ms: 1000
```

### Top-level keys

- `name`: required suite name
- `description`: optional suite description
- `rest-client` / `rest-clients`: HTTP client(s) — exactly one is required (see below)
- `variables`: optional suite-level variables
- `tests`: required list of test cases

### `rest-client` / `rest-clients`

Every suite must declare its HTTP client(s) using **exactly one** of two mutually
exclusive keys. Declaring neither — or both — is a validation error.

**`rest-client` (singular)** — shorthand for a single default client:

```yaml
rest-client:
  base-url: "https://api.example.com"
  connect-timeout: 30000
  headers:
    Accept: "application/json"
```

**`rest-clients` (plural)** — multiple named clients, each with an `id`. A request selects
one via its own `rest-client` property; requests without one use the client whose `id` is
`default`:

```yaml
rest-clients:
  - id: default
    base-url: "https://api.example.com"
  - id: payments
    base-url: "https://payments.example.com"
    connect-timeout: 10000

tests:
  - name: "Pay invoice"
    request:
      rest-client: payments   # selects the 'payments' client
      method: "POST"
      url: "/invoices/pay"
    assertions:
      - type: "status_code"
        expected: 200
```

Each client supports:

- `id`: client id (required when more than one client is configured; implicitly `default` for a single unnamed client)
- `base-url`: prepended to relative request URLs
- `connect-timeout`: timeout in milliseconds; defaults to `30000`
- `headers`: default headers added to every request using the client
- `auth`: optional HTTP Basic Auth (client-level default)

Per-test headers override same-named client-level headers. Per-test authentication and explicit `Authorization` headers in request headers override client-level authentication. The per-request `rest-client` selector is ignored (a warning is logged) when the singular `rest-client` form is used.

#### HTTP Basic Auth

Declare client-level authentication with `auth`:

```yaml
rest-client:
  base-url: "https://api.example.com"
  auth:
    type: "basic"
    username: "[[${env.API_USER}]]"
    password: "[[${env.API_PASSWORD}]]"
```

Or override with per-request authentication:

```yaml
tests:
  - name: "Admin task"
    request:
      method: "GET"
      url: "/admin/users"
      auth:
        type: "basic"
        username: "[[${env.ADMIN_USER}]]"
        password: "[[${env.ADMIN_PASSWORD}]]"
    assertions:
      - type: "status_code"
        expected: 200
```

**Best Practice:** Store usernames and passwords in a `.env` file or environment variables, then reference them via `[[${env.API_USER}]]` and `[[${env.API_PASSWORD}]]` (never hardcode credentials in the YAML).

Precedence (lowest to highest):
1. Client-level `rest-client.auth` (applied as default to all requests using the client)
2. Per-request `request.auth` (overrides client-level)
3. Explicit `Authorization` header in `request.headers` (always wins)

### Test cases

Each item in `tests` supports:

- `name`: required test name
- `description`: optional explanation
- `skip`: optional skip reason; when non-blank, the test is skipped
- `variables`: per-test variables exposed as `test.<name>`
- `request`: required HTTP request definition
- `assertions`: ordered list of assertions
- `saved-session`: optional list of response-value captures stored into the suite-wide `session` namespace (see [Chaining tests](#chaining-tests-session-capture-depends-on-and-transient))
- `depends-on`: optional list of other test names that must run before this test
- `transient`: optional boolean; when `true` the test runs **only** as another test's dependency, never standalone

### Request bodies

Requests with methods such as `POST`, `PUT`, `PATCH`, and `DELETE` can include:

```yaml
body:
  type: "inline"
  content: |
    {"name":"Alice"}
```

or:

```yaml
body:
  type: "file"
  content: "request-body.json"
```

## Thymeleaf Variable Namespaces

The CLI exposes five variable namespaces during template resolution:

| Namespace | Example | Source |
|---|---|---|
| `cli` | `[[${cli.base_url}]]` | Positional `key=value` arguments after `--suite` |
| `env` | `[[${env.AUTH_TOKEN}]]` | Variables loaded from a `.env` file in the suite directory |
| `suite` | `[[${suite.auth_token}]]` | Values resolved from the suite-level `variables` block |
| `test` | `[[${test.users_path}]]` | Values from the current test case's `variables` block |
| `session` | `[[${session.recordId}]]` | Values captured from earlier tests' responses via `saved-session` |

The suite is resolved in two passes:

1. `cli` and `env` are used to resolve the top-level `variables` block.
2. The resolved suite variables are then exposed through `suite.*` while the full file is processed again.

That means values like `[[${suite.base_url}]]` are the supported form for suite-level references in request URLs, headers, and assertions.

The `session` namespace is different: like `test`, it is resolved **only during test execution** (when building a test's request URL, headers, and body — including bodies loaded from a `type: file`), never during the two suite-level passes above. It starts empty and is populated as tests run.

## Chaining tests: session capture, `depends-on`, and `transient`

A test can **capture values from its own response** into the suite-wide `session` namespace, and other tests can **depend on** it so it runs first and its captured values are available.

### Capturing values with `saved-session`

```yaml
tests:
  - name: "CreateRecord"
    transient: true
    request:
      method: "POST"
      url: "[[${suite.base_url}]]/records"
      body:
        type: "inline"
        content: '{"label":"widget"}'
    saved-session:
      - name: "recordId"          # stored under session.recordId
        path: "response.body.json.$.id"
        type: "integer"           # optional: string | integer | double | boolean
        required: true            # fail the test if nothing is extracted (and no default)
      - name: "etag"
        path: "response.headers.etag"
    assertions:
      - type: "status_code"
        expected: 201

  - name: "GetRecord"
    depends-on: ["CreateRecord"]
    request:
      method: "GET"
      url: "[[${suite.base_url}]]/records/[[${session.recordId}]]"
    assertions:
      - type: "status_code"
        expected: 200
```

Each `saved-session` entry has:

- `name` — key under which the value is stored (used later as `[[${session.<name>}]]`). Name uniqueness across tests is the user's responsibility; duplicates are overwritten last-write-wins.
- `path` — a `response.*` expression (the same language used by assertions), e.g. `response.statusCode`, `response.headers.<h>`, `response.body.text`, or `response.body.json.$.<jsonpath>`.
- `type` — optional coercion to `string`, `integer`, `double`, or `boolean`. A value that cannot be converted fails the test.
- `default` — optional fallback used when the path extracts nothing; when set, the capture always resolves.
- `required` — optional (default `false`); when `true` and no `default` is set, a missing/unextractable value fails the test.

**Captured values must be primitives** (`string`, `integer`, `double`, `boolean`). Extracting an object or array is an error — you cannot store a JSON object/array in `session`.

**Captures happen only after all of a test's assertions pass.** `saved-session` values are extracted and written to the `session` namespace only once the test succeeds. If any assertion fails, the test is marked failed and **none** of its `saved-session` values are stored — so a dependent test never runs against values captured from a failed parent (it is marked failed via the `depends-on` propagation described below).

### `depends-on`

`depends-on` lists other test names that must run before this test. Dependencies run in the listed order, resolved transitively (`A → B → C` runs `C`, then `B`, then `A`). A `depends-on` that names an unknown test, or that forms a cycle, is reported as a validation error before any test runs.

**A depended-on test runs at most once per suite run.** If several tests depend on the same test, it executes a single time and its result — including its captured `session` values — is reused by every dependent (its request is not re-sent). If a dependency ends up failed or errored, each test that depends on it is automatically marked failed and its own request is not sent. Because such a test sends no request and evaluates no assertions of its own, it is reported as a distinct kind of failure: the terminal shows a single `Error` row reading `This test depends on a failed parent test "<name>".` (no assertion/expected/actual rows), and the HTML report shows a **Failed parent test** expandable block with the same message instead of Request, Response, or Failed Assertions sections.

### `transient`

A `transient: true` test runs **only** when another test depends on it — never as a standalone test. This is useful for setup steps (like `CreateRecord` above) that only exist to feed values into dependents. Transient tests also **do not fire `before-each` or `after-each` hooks** — those hooks fire for the dependent (non-transient) test that triggered them.

## The `.env` File

The CLI loads environment variables from a `.env`-style file and exposes those values through the `env` namespace.

Example:

```dotenv
AUTH_TOKEN=supersecret
BASE_URL=https://api.example.com
```

Example usage inside a suite:

```yaml
variables:
  auth_token: "[[${env.AUTH_TOKEN}]]"
```

This is the right place for secrets or machine-specific configuration that should not be committed into the suite itself.

### Resolution Order

The file that supplies the `env` namespace is resolved in the following order:

1. **`--env-file=<path>` supplied** — that exact file is used. It need not be named `.env`, so you can keep per-environment files (e.g. `dev.env`, `staging.env`, `prod.env`) and swap them per invocation. If the path does not point to an existing regular file, the command **fails with an error and aborts** — this is the only case where a missing env file is treated as an error, because you asked for a specific file.
2. **No `--env-file`** — the CLI looks for a `.env` file in the **current working directory**.
3. **No `.env` in the current working directory** — the CLI falls back to a `.env` file in the **directory containing the suite YAML file** (the historical default).

Cases 2 and 3 fail **silently**: if no `.env` is found anywhere the run proceeds with only the process/system environment variables. System environment variables always take precedence over same-named entries in the env file.

Example — run the same suite against staging:

```bash
rs --suite=/path/to/suite.yml --env-file=/path/to/staging.env
```

## Supported Assertions

Every assertion is declared inside a test case's `assertions` list. The `type` field selects the evaluator.

| Type | Purpose | Minimal YAML example |
|---|---|---|
| `status_code` | Exact HTTP status match | `- type: "status_code"\n  expected: 200` |
| `status_in` | HTTP status must match one of several values | `- type: "status_in"\n  expected: [200, 202]` |
| `response_time` | Response duration must stay below a threshold in ms | `- type: "response_time"\n  max_ms: 1000` |
| `json_schema` | Validate JSON against an inline or file-backed schema | `- type: "json_schema"\n  path: "response.body.json"\n  expected:\n    type: "file"\n    content: "schemas/user.json"` |
| `json_match` | Compare JSON against an expected structure, with optional ignored fields | `- type: "json_match"\n  path: "response.body.json"\n  expected:\n    type: "inline"\n    content: '{"status":"ok"}'` |
| `string_match` | String equality check | `- type: "string_match"\n  path: "response.header.content-type"\n  expected: "application/json"` |
| `string_contains` | String contains a substring | `- type: "string_contains"\n  path: "response.body.text"\n  expected: "success"` |
| `regex_match` | String must match a regex pattern | `- type: "regex_match"\n  path: "response.body.text"\n  expected: "^[A-Z0-9_-]+$"` |
| `starts_with` | String prefix check | `- type: "starts_with"\n  path: "response.body.text"\n  expected: "Bearer "` |
| `ends_with` | String suffix check | `- type: "ends_with"\n  path: "response.body.text"\n  expected: ".json"` |
| `not_empty` | Value must exist and not be empty | `- type: "not_empty"\n  path: "response.body.text"` |
| `not_null` | Value must exist and not be null | `- type: "not_null"\n  path: "response.body.json.id"` |
| `is_null` | Value must be null | `- type: "is_null"\n  path: "response.body.json.deletedAt"` |
| `has_header` | Response must contain a named header | `- type: "has_header"\n  name: "x-request-id"` |
| `value_type` | Value must have a given JSON type | `- type: "value_type"\n  path: "response.body.json.id"\n  expected: "number"` |
| `one_of` | Value must equal one item from a list | `- type: "one_of"\n  path: "response.body.json.status"\n  expected: ["pending", "active"]` |
| `assert_true` | Value must resolve to true | `- type: "assert_true"\n  path: "response.body.json.enabled"` |
| `assert_false` | Value must resolve to false | `- type: "assert_false"\n  path: "response.body.json.archived"` |
| `greater_than` | Numeric value must be greater than expected | `- type: "greater_than"\n  path: "response.body.json.total"\n  expected: 0` |
| `greater_than_or_equal` | Numeric value must be at least expected | `- type: "greater_than_or_equal"\n  path: "response.body.json.total"\n  expected: 1` |
| `less_than` | Numeric value must be less than expected | `- type: "less_than"\n  path: "response.body.json.total"\n  expected: 100` |
| `less_than_or_equal` | Numeric value must be at most expected | `- type: "less_than_or_equal"\n  path: "response.body.json.total"\n  expected: 100` |
| `range` | Numeric value must fall within a min/max range | `- type: "range"\n  path: "response.body.json.score"\n  min: 0\n  max: 100` |
| `array_contains` | Array must contain a specific item | `- type: "array_contains"\n  path: "response.body.json.roles"\n  expected: "admin"` |
| `array_contains_all` | Array must contain all listed items | `- type: "array_contains_all"\n  path: "response.body.json.roles"\n  expected: ["read", "write"]` |
| `array_is_empty` | Array must be empty | `- type: "array_is_empty"\n  path: "response.body.json.errors"` |
| `array_is_not_empty` | Array must not be empty | `- type: "array_is_not_empty"\n  path: "response.body.json.items"` |
| `array_size` | Array must have an exact length | `- type: "array_size"\n  path: "response.body.json.items"\n  expected: 3` |
| `array_size_min` | Array length must be at least a minimum | `- type: "array_size_min"\n  path: "response.body.json.items"\n  min: 1` |
| `array_size_max` | Array length must be at most a maximum | `- type: "array_size_max"\n  path: "response.body.json.items"\n  max: 10` |

Common response paths used by assertions include:

- `response.status`
- `response.timeMs`
- `response.body.text`
- `response.body.json`
- `response.body.json.<field>`
- `response.header.<header-name>`

## Exporting The JSON Schema

The CLI bundles a JSON Schema for the test-suite YAML format. Export a local copy with:

```bash
# JVM jar
java -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar export-schema --out ./schemas

# Native binary
./target/api-tester-cli export-schema --out ./schemas

# Using the alias
./target/api-tester-cli es --out ./schemas
```

The `--out` option accepts an absolute or relative path to an **output directory**. The file is
always written as `test-suite-schema.json` inside that directory. The directory is created
automatically if it does not exist. An existing file is overwritten. On success the command prints
the absolute path of the written file:

```
Schema written to: /path/to/schemas/test-suite-schema.json
```

> **Tip:** `es` is a short alias for `export-schema` — both invoke the same command.

### Using the schema for IDE validation

Once you have a local copy of the schema you can wire it to your test-suite YAML files so that your IDE validates the document as you type and provides field-level completions and inline documentation.

The simplest approach — supported by virtually all editors that have
[yaml-language-server](https://github.com/redhat-developer/yaml-language-server) active — is to
add a single comment at the very top of each test-suite YAML file:

```yaml
# yaml-language-server: $schema=./schemas/test-suite-schema.json
```

Use the real path (absolute or relative) to the schema file you exported. No IDE-level
configuration is needed; the language server picks up the directive automatically when the file is
opened.

For IDE-wide mappings that apply without modifying individual files:

- **VS Code** — install the [YAML extension by Red Hat](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-yaml) and add a mapping in `.vscode/settings.json`:
  ```json
  {
    "yaml.schemas": {
      "./schemas/test-suite-schema.json": "**/*-suite*.yml"
    }
  }
  ```
- **IntelliJ IDEA / WebStorm** — go to *Preferences → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings* and add the exported file mapped to your YAML glob pattern.
- **Other editors** — most editors that support the [Language Server Protocol](https://microsoft.github.io/language-server-protocol/) can be configured with [yaml-language-server](https://github.com/redhat-developer/yaml-language-server); consult your editor's documentation.

> **Note:** Some IDEs require a third-party YAML plugin to enable schema-based validation and autocompletion. If hints do not appear after wiring the schema, check whether a YAML or JSON Schema plugin is installed and enabled.

For a full walkthrough see [Schema Support](https://cmdrest.com/docs/schema-support).

## Checking The Version

Print the application version with the `version` command:

```bash
# JVM jar
java -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar version

# Native binary
./target/api-tester-cli version
```

Output:

```
Api Tester CLI version 0.2.1
```

The version is embedded at build time from `pom.xml`, so it is accurate for both the JVM jar and
the GraalVM native binary. The same version appears in the footer of generated HTML reports.

## Generating an HTML Report

Add `--report=<directory>` to any `run-suite` invocation to write a self-contained HTML report
after the run completes. Pass the **absolute path to a directory**; the file name is generated
automatically.

```bash
java -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar run-suite \
  --suite ./src/test/resources/test-suite-1.yml \
  --report /tmp/reports \
  api_base_url=https://api.restful-api.dev
```

The CLI prints the exact path once the file is written:

```
Report written to /tmp/reports/test-suite_Test_Suite_1_20260606142300.html
```

### Filename format

```
test-suite_<suiteName>_<yyyyMMddHHmmss>.html
```

`<suiteName>` is the `name` field from your YAML with all non-alphanumeric characters replaced
by underscores.

### What the report contains

- **Header** — suite name, optional description, and generation timestamp
- **Summary cards** — passed / failed / skipped / error / total counts
- **Per-test cards** — one card per test case, showing:
  - Result badge and assertion pass/fail counts
  - Expandable **Request** section (method, URL, headers, body)
  - Expandable **Response** section (status code, response time, headers, pretty-printed body)
  - Expandable **Failed Assertions** section (description, expected vs actual, error message)

The report is fully self-contained (all CSS embedded, no CDN dependencies) and opens in any
browser without an internet connection. By default a small inline JavaScript formatter is
included to pretty-print JSON bodies in the browser; set `REPORT_NO_JS=true` to disable it.
Set `REPORT_NO_MINIFY=true` to skip HTML minification. Both flags can be set in the OS
environment or in the suite's `.env` file.

For full documentation, including a behaviour matrix for these options, see
[HTML Execution Report](https://cmdrest.com/docs/html-report).

## Debug Logging

File-based logging is controlled by two variables rather than a command-line flag. Provide both
through **either** the OS environment **or** a `.env` file (including one passed with `--env-file`):

- `CLI_LOG_LEVEL`: one of `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`
- `CLI_LOG_DIR`: directory where log files should be written

Example (exported environment):

```bash
CLI_LOG_LEVEL=DEBUG CLI_LOG_DIR=./logs \
  java -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar run-suite \
  --suite ./src/test/resources/test-suite-1.yml
```

Example (via a `.env` file, no export needed):

```bash
printf 'CLI_LOG_LEVEL=DEBUG\nCLI_LOG_DIR=./logs\n' > .env
java -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar run-suite \
  --suite ./src/test/resources/test-suite-1.yml
```

Exported OS variables are honoured from startup; `.env`/`--env-file` values are honoured when the
`run-suite` command loads them (the OS environment wins if a variable is set in both). When either
variable is missing or invalid, the CLI runs normally without creating a log file.

For more detail, see [DEBUG_LOGGING_README.md](./DEBUG_LOGGING_README.md).

## Development Notes

- Spring Boot version: `4.0.6`
- Spring Shell version: `4.0.2`
- Java version: `25`
- Build tool: Maven Wrapper via `./mvnw`

Useful repository files:

- [HELP.md](./HELP.md)
- [DEBUG_LOGGING_README.md](./DEBUG_LOGGING_README.md)
- [native-build-support.md](./native-build-support.md)
- [src/main/resources/test-suite.yml](./src/main/resources/test-suite.yml)

## Validation

Before opening a PR:

```bash
./mvnw test
```
