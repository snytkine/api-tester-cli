# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Role

You are an experienced Spring Boot developer and an expert with the Thymeleaf templating engine and the REST-assured testing framework.

## Commands

```bash
# Build
./mvnw clean package

# Run tests
./mvnw test

# Run a single test class
./mvnw test -pl . -Dtest=TestSuiteLoaderTest

# Run a single test method
./mvnw test -Dtest=TestSuiteLoaderTest#twoStepLoadResolvesSuiteVariableReferencesInTestCases

# Apply code formatting (spotless)
./mvnw spotless:apply

# Check formatting without applying
./mvnw spotless:check
```

Spotless runs automatically during `verify` (but not `test`). Run `spotless:apply` before committing to avoid CI failures.


## Architecture

This is a Spring Boot 4.0.6 + Spring Shell 4.0.2 CLI that executes HTTP API test suites defined in YAML files.

### Test Suite YAML format

Test suites are YAML files validated against `.vscode/schemas/test-suite-schema.json`. The top-level structure:

- `variables` — suite-level key/value pairs; values can be Thymeleaf expressions evaluated against `cli.*` variables passed at runtime
- `tests` — list of `TestCase` objects, each with its own `variables`, a `request`, and a list of `assertions`

`RequestBody` and assertion expected values of types `json_schema`/`json_match` use a `ContentReference` (type + content) where `type: file` means `content` is a relative path resolved alongside the test suite file.

### Template processing (TestSuiteLoader)

`TestSuiteLoader.load(Path, SuiteRunContext)` processes a test suite YAML through Thymeleaf TEXT mode in two passes:

1. **Step 1** — processes the full YAML with `cli`, `env`, `test` from `SuiteRunContext` and an empty `suite` map. The result provides the resolved `variables` map.
2. **Step 2** — processes the full YAML again with the same `cli`/`env`/`test`, but with `suite` set to the Step 1 resolved variables, so that test-case expressions like `[[${suite.api_base_url}]]` resolve correctly.

The final `TestSuite` uses Step 1's `resolvedVariables` and Step 2's test cases.

### Thymeleaf variable namespaces

Four top-level context variables are available in all template expressions:

| Variable | Access pattern | Source |
|----------|---------------|--------|
| `cli`    | `[[${cli.my_var}]]` | CLI `key=value` positional args |
| `env`    | `[[${env.MY_VAR}]]` | `.env` file + process environment |
| `suite`  | `[[${suite.my_var}]]` | Resolved suite-level `variables` block (Step 2 only) |
| `test`   | `[[${test.my_var}]]` | Per-test-case `variables` block |

### Thymeleaf expression rules

- Use `[[${...}]]` (inline, escaped) for variable substitution in TEXT mode.
- OGNL does **not** support the Elvis operator `?:`. Use full ternary: `[[${cli.foo != null ? cli.foo : 'default'}]]`.
- Missing map keys resolve to empty string (not an exception) — no custom evaluator needed.
- Available utilities: `#temporals.createToday()`, `#temporals.format(date, pattern)`, `#strings.randomAlphanumeric(n)`.

### Package layout

```
model/       — Java records for TestSuite, TestCase, Request, RequestBody,
               SuiteRunContext, Assertion subtypes, ObjectExpectedValue, enums
service/     — TestSuiteLoader (YAML load + Thymeleaf template processing)
util/        — FileLoader (load file relative to a base path; parse content as Thymeleaf template)
```

### Rules

- After every code change or addition, run `./mvnw spotless:apply`.
- After every code change or addition, make sure to add or modify unit tests to cover new code then run `./mvnw test`  and make sure they pass.
- Every new class and every new method must have a JavaDoc block with detailed documentation.
- **Documentation sync** — after any change that affects how an end-user invokes a command, alters command-line options or their behaviour, adds or removes environment variables, or introduces new configuration options, update **both** `README.md` and the relevant file(s) in `docs/`. The files most likely to need updating are `docs/cli-reference.md`, `docs/getting-started.md`, `docs/environment-variables.md`, and `docs/test-suite-configuration.md`. Documentation must always reflect the current behaviour.
- **GraalVM native compilation** — this project must compile and run as a GraalVM native image. Avoid any patterns that break ahead-of-time compilation: no runtime reflection without a `reflect-config.json` entry, no dynamic proxies without `proxy-config.json`, no classpath scanning at runtime. Prefer constructor injection over field injection. Lambdas and method references are safe. Jackson must be configured with explicit type registration rather than relying on classpath discovery where possible. When adding a new dependency verify it ships GraalVM metadata or add hints manually.
- **Thread safety** — every class, object, and method must be thread-safe. Spring singleton beans (`@Service`, `@Component`, `@Configuration`) must hold no mutable per-request state. All per-invocation data (e.g. a `RestClient` built from suite config, a `SoftAssertions` collector) must live on the call stack, never in instance fields. Document thread-safety guarantees explicitly in class-level JavaDoc.
- **GitHub issue lifecycle** — NEVER close, reopen, or otherwise change the state of a GitHub issue unless the user explicitly asks. Do not add closing comments or mark issues as completed on your own initiative after implementing a feature.

### Code style

- All Java files require the Apache 2.0 license header (see `src/main/resources/license-header.txt`). Spotless enforces this.
- Google Java Format (GOOGLE style, 2-space indents).
- POM dependencies sorted by `scope,groupId,artifactId`; plugins by `groupId,artifactId`.
- YAML files use spotless Jackson formatter (compact list notation: list dashes not indented relative to parent key). Do **not** run VS Code format-on-save for YAML — it conflicts with spotless output.
- `yamllint` max line length is 88 characters.
