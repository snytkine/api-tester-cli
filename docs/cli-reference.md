# CLI Reference

The command is `run-suite` with alias `rs`.

## Syntax

```
rs --suite=<path> [--tag=<value>] [--test=<name>] [--ui|--no-ui] [--report=<dir>] [key=value ...]
```

## Options

| Option | Required | Description |
|--------|----------|-------------|
| `--suite=<path>` | **Yes** | Absolute path to the test-suite YAML file. |
| `--tag=<value>` | No | Run only test cases whose `tag` field contains this value. Cannot be used together with `--test`. |
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

### Run all tests
```bash
rs --suite=/path/to/suite.yml
```

### Run only tests tagged "smoke"
```bash
rs --suite=/path/to/suite.yml --tag=smoke
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
