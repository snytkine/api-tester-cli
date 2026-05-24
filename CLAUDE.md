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

`TestSuiteLoader.load(Path, CliVariables)` processes a test suite YAML through Thymeleaf TEXT mode in two passes:

1. **Step 1** — processes the full YAML with `cli` (from `CliVariables`) and an empty `suite.variables`. The result provides the resolved `variables` map.
2. **Step 2** — processes the full YAML again with `cli` + the Step 1 resolved `suite.variables`, so that test-case expressions like `[[${suite.variables.api_base_url}]]` resolve correctly.

The final `TestSuite` uses Step 1's `resolvedVariables` and Step 2's test cases.

### Thymeleaf expression rules

- Use `[[${...}]]` (inline, escaped) for variable substitution in TEXT mode.
- OGNL does **not** support the Elvis operator `?:`. Use full ternary: `[[${cli.foo != null ? cli.foo : 'default'}]]`.
- Missing map keys resolve to empty string (not an exception) — no custom evaluator needed.
- Available utilities: `#temporals.createToday()`, `#temporals.format(date, pattern)`, `#strings.randomAlphanumeric(n)`.

### Package layout

```
model/       — Java records for TestSuite, TestCase, Request, RequestBody,
               CliVariables, Assertion subtypes, ObjectExpectedValue, enums
service/     — TestSuiteLoader (YAML load + Thymeleaf template processing)
util/        — FileLoader (load file relative to a base path; parse content as Thymeleaf template)
```

### Rules

- After every code change or addition, run `./mvnw spotless:apply`.
- Every new class and every new method must have a JavaDoc block with detailed documentation.

### Code style

- All Java files require the Apache 2.0 license header (see `src/main/resources/license-header.txt`). Spotless enforces this.
- Google Java Format (GOOGLE style, 2-space indents).
- POM dependencies sorted by `scope,groupId,artifactId`; plugins by `groupId,artifactId`.
- YAML files use spotless Jackson formatter (compact list notation: list dashes not indented relative to parent key). Do **not** run VS Code format-on-save for YAML — it conflicts with spotless output.
- `yamllint` max line length is 88 characters.
