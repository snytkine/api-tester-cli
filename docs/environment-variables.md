# Environment Variables & Debugging

## Debug logging

The tool can write structured logs to a file for debugging test execution. To enable debug logging, set **both** of these environment variables:

| Variable | Description |
|----------|-------------|
| `CLI_LOG_LEVEL` | Log level (case-insensitive): `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`. Any other value disables logging silently. |
| `CLI_LOG_DIR` | Directory path where log files are written. Created automatically if it doesn't exist. |

**Both must be set together.** If either is missing, logging is not activated.

### Usage

```bash
export CLI_LOG_LEVEL=DEBUG
export CLI_LOG_DIR=/tmp/api-tester-logs
rs --suite=/path/to/suite.yml
```

Log files are created with the pattern: `cli_log_yyyy_MM_dd_HHmmss.log`

Each run creates a **new log file**; old files are never deleted automatically.

Log line format:
```
2026-06-05 14:30:45.123 [main] DEBUG io.github.snytkine.apitester... - Message here
```

## Terminal UI behavior

The tool automatically detects your environment and chooses an output mode (interactive UI or JSON). These environment variables influence that detection:

| Variable | Effect |
|----------|--------|
| `NO_COLOR` | If set to any value, disables ANSI color output and the interactive UI (follows [no-color.org](https://no-color.org/) convention). |
| `CI` | If set to any value, disables the interactive UI. Most CI systems (GitHub Actions, GitLab CI, Jenkins, CircleCI, etc.) set this automatically. |
| `COLUMNS` | Terminal width detection override. Parsed as an integer; defaults to 80 if absent or unparseable. The interactive UI is disabled if the detected width is below 40 columns. |

### Output mode selection (evaluated in order)

1. If `--ui` flag is supplied → **interactive UI always** (overrides all environment variables)
2. If `--no-ui` flag is supplied → **JSON output always** (overrides all environment variables)
3. Otherwise, auto-detect based on the above variables and TTY state

### Examples

**Force JSON output in CI:**

```bash
# CI environment variable is already set by the CI platform
rs --suite=/path/to/suite.yml --no-ui > results.json
```

**Disable colors but keep interactive UI:**

```bash
# Set NO_COLOR but use --ui to override the UI disabling
NO_COLOR=1 rs --suite=/path/to/suite.yml --ui
```

**Disable UI in Docker with TTY:**

```bash
docker run \
  -e NO_COLOR=1 \
  -v $(pwd):/tests \
  api-tester-cli \
  rs --suite=/tests/suite.yml --no-ui
```

---

## Using `.env` files for secrets

Place a `.env` file in the same directory as your test suite YAML. It will be automatically loaded and merged with environment variables.

**.env file (in suite directory):**

```
API_KEY=sk-12345abcde
DB_PASSWORD=postgres123
OAUTH_TOKEN=oauth_abc123def456
```

In your test suite or body files:

```yaml
request:
  headers:
    Authorization: "Bearer [[${env.API_KEY}]]"
    x-api-key: "[[${env.API_KEY}]]"
```

**Priority:** System environment variables take precedence. If both `.env` and a system environment variable define the same key, the system value is used.

**Best practice:** Never commit `.env` to version control. Add `.env` to your `.gitignore`. This way, each developer and CI environment can have its own secrets without risk of accidental exposure.

### `.env` file format

```
KEY1=value1
KEY2=value2
# Comments are allowed
KEY3=value with spaces
```

The `dotenv-java` library supports the standard `.env` file format.
