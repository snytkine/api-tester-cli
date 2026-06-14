# CLI Reference

## Commands

| Command | Description |
|---------|-------------|
| [`run-suite`](#run-suite) | Load and execute a test-suite YAML file |
| [`export-schema`](#export-schema) | Export the bundled JSON Schema to a local file |
| [`version`](#version) | Display the application version |

---

## `run-suite`

The command is `run-suite` with alias `rs`.

## Syntax

```
rs [--suite=<path>] [--tag=<value>] [--test=<name>] [--ui|--no-ui] [--report=<dir>] [key=value ...]
```

## Options

| Option | Required | Description |
|--------|----------|-------------|
| `--suite=<path>` | No | Path to the test-suite YAML file. When omitted, the CLI looks for `test-suite.yml` in the current working directory. An error is shown if neither is found. |
| `--tag=<value>` | No | Run only test cases whose `tag` field contains this value. Prefix with `!` to invert: `--tag="!slow"` runs all tests **except** those tagged `slow`; tests with no tags are always included under a negated filter. Cannot be used together with `--test`. |
| `--test=<name>` | No | Run only the single test case whose `name` field exactly matches this value. Use double quotes if the name contains spaces: `--test="My Test Name"`. Cannot be used together with `--tag`. |
| `--no-ui` | No | Force JSON output even when stdout looks like a TTY. |
| `--ui` | No | Force the interactive terminal UI even when stdout does not look like a TTY. |
| `--report=<dir>` | No | Absolute path to a directory where the HTML execution report will be written. The filename is auto-generated as `test-suite_<name>_yyyyMMddHHmmss.html`. The directory is created if it does not exist. See [HTML Report](html-report.md). |

## Positional arguments (CLI variables)

After all named options, pass `key=value` tokens to inject variables into the Thymeleaf template engine:

```bash
rs --suite=/path/to/suite.yml api_base_url=https://api.example.com admin_system=IBM timeout=30
```

**Important:** Do NOT prefix variable names with `--`. Any token without an `=` sign is silently skipped.

These variables are accessible in your YAML as `[[${cli.api_base_url}]]`, `[[${cli.admin_system}]]`, etc.

## Mutual exclusion

`--tag` and `--test` cannot be used together. If both are supplied, the run aborts with an error:

```
Options --tag and --test cannot be used together. Use one or the other.
```

## Output mode selection (evaluated in order)

1. If `--ui` is supplied → interactive terminal UI is activated regardless of environment
2. If `--no-ui` is supplied → JSON output is forced regardless of TTY
3. Otherwise → auto-detect based on:
   - TTY attached to stdout
   - `NO_COLOR` environment variable (disables UI if set)
   - `CI` environment variable (disables UI if set)
   - Terminal width (UI disabled if below 40 columns)

## Examples

### Run test-suite.yml in the current directory (no arguments required)
```bash
rs
```

### Run all tests in an explicit suite file
```bash
rs --suite=/path/to/suite.yml
```

### Run only tests tagged "smoke"
```bash
rs --suite=/path/to/suite.yml --tag=smoke
```

### Run all tests except those tagged "slow" (negated filter)
```bash
rs --suite=/path/to/suite.yml --tag="!slow"
```

### Run a single test by name
```bash
rs --suite=/path/to/suite.yml --test="Login Test"
```

### Pass CLI variables
```bash
rs --suite=/path/to/suite.yml api_url=https://staging.example.com user_name=testuser password=secret123
```

### Force JSON output for CI
```bash
rs --suite=/path/to/suite.yml --no-ui > results.json
```

### Force interactive UI on non-TTY (e.g., in a Docker container with TTY support)
```bash
rs --suite=/path/to/suite.yml --ui
```

### Generate an HTML execution report
```bash
rs --suite=/path/to/suite.yml --report=/path/to/reports
```

The file is written to `/path/to/reports/test-suite_<suiteName>_<timestamp>.html`. See
[HTML Execution Report](html-report.md) for the full description of report contents.

---

## `export-schema`

Exports the bundled `test-suite-schema.json` to a local directory so you can wire it to your
test-suite YAML files for IDE validation and autocompletion.

The command is `export-schema` with alias `es`.

### Syntax

```
export-schema --out <dir>
es --out <dir>
```

### Options

| Option | Required | Description |
|--------|----------|-------------|
| `--out=<dir>` | **Yes** | Absolute or relative path to the **output directory**. The file is always written as `test-suite-schema.json` inside this directory. The directory is created automatically if it does not exist. An existing file is overwritten. |

### Examples

```bash
# Write to a local schemas/ directory (using the full command name)
export-schema --out ./schemas

# Same using the alias
es --out ./schemas

# Write to an absolute directory path
es --out /home/user/schemas
```

On success the command prints the absolute path of the written file:

```
Schema written to: /home/user/schemas/test-suite-schema.json
```

For instructions on wiring the schema to your IDE see [Schema Support](schema-support.md).

---

## `version`

Prints the application version. The version is read from build metadata embedded at build time by
the `spring-boot-maven-plugin` `build-info` goal, so it always reflects the version declared in
`pom.xml` — for both the JAR and the GraalVM native binary. The same version appears in the footer
of generated [HTML reports](html-report.md).

The command is `version` (no alias) and takes no options.

### Syntax

```
version
```

### Example

```bash
version
```

Output:

```
Api Tester CLI version 0.2.1
```
