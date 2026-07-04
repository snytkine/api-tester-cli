# Application Introduction.

**api-tester-cli** is a Spring Boot + Spring Shell command-line tool for running HTTP API test suites defined in YAML files. Test suites can use Thymeleaf template expressions to inject values from the command line, environment variables, suite-level configuration, and per-test variables into requests and assertions.

The tool runs as a regular JVM application (fast builds, simplest development) or as a GraalVM native binary (fast startup, no JVM runtime required).

## Application Key features.

- **Thymeleaf templating** — inject command-line values (`[[${cli.api_url}]]`), environment variables (`[[${env.DB_PASSWORD}]]`), suite-level config (`[[${suite.base_url}]]`), and per-test variables (`[[${test.username}]]`) into any YAML string
- **25+ assertion types** — validate status codes, response headers, body content (with JSONPath and JSON Schema), array elements, null checks, numeric ranges, regex patterns, and more
- **Tag and test filtering** — run only tests tagged with `--tag smoke` or a single test by name with `--test="My Test"`
- **Interactive terminal UI** — real-time test progress with colour-coded pass/fail indicators (or JSON output for CI)
- **Environment file support** — store secrets in a `.env` file; values are securely merged with command-line variables
- **GraalVM native binary** — compile to a standalone executable with no JVM dependency
- **JSONPath and JSON Schema validation** — validate complex JSON responses with powerful path queries and schema definitions
- **File-based bodies and schemas** — reference request bodies and expected responses as separate files alongside your suite YAML
- **HTML execution report** — generate a self-contained, single-page HTML report after a run with `--report=/path/to/dir`; includes per-test request/response details and assertion failure breakdowns
- **IDE schema validation** — export the bundled JSON Schema with `export-schema --out <path>` and wire it to your YAML editor for inline validation and field-level autocompletion while writing test suites
- **Upgrade notifications** — a background check on startup surfaces an "upgrade available" message in the HTML report and terminal UI when a newer release is published on GitHub; never blocks startup or a run

## Quick example

```yaml
name: "Example Test Suite"
rest-client:
  connect-timeout: 30000
variables:
  api_base_url: "[[${cli.api_url}]]"
tests:
- name: "Check API Health"
  request:
    method: "GET"
    url: "[[${suite.api_base_url}]]/health"
  assertions:
  - type: "status_code"
    expected: 200
```

Run it:

```bash
rs --suite=/path/to/suite.yml api_url=https://api.example.com
```

---

**Ready to get started?** Continue to [Getting Started](getting-started.md).
