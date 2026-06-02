# Debug Logging

The API Tester CLI suppresses all log output by default to keep the terminal output clean. File-based
logging can be activated at runtime by setting two environment variables — no configuration file
changes or rebuilds required.

## Activating logging

Set both variables before running the CLI:

| Variable | Required | Description |
|---|---|---|
| `CLI_LOG_LEVEL` | Yes | Log verbosity. Must be one of: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` (case-insensitive). |
| `CLI_LOG_DIR` | Yes | Path to the directory where log files will be written. Created automatically if it does not exist. |

**Both variables must be set.** If either is absent, logging is not activated and the application
runs normally with no log output.

### Examples

```bash
# Inline (single command)
CLI_LOG_LEVEL=DEBUG CLI_LOG_DIR=/tmp/api-tester-logs \
  java -jar api-tester-cli.jar run-suite --suite=my-suite.yml

# Exported (current shell session)
export CLI_LOG_LEVEL=DEBUG
export CLI_LOG_DIR=/tmp/api-tester-logs
java -jar api-tester-cli.jar run-suite --suite=my-suite.yml

# Native binary
CLI_LOG_LEVEL=INFO CLI_LOG_DIR=./logs ./api-tester-cli run-suite --suite=my-suite.yml
```

## Log file location and naming

A new log file is created each time the application starts with logging activated. Files are placed
in `CLI_LOG_DIR` and named using the current timestamp at startup:

```
<CLI_LOG_DIR>/cli_log_yyyy_MM_dd_HHmmss.log
```

For example:

```
/tmp/api-tester-logs/cli_log_2026_06_02_143512.log
```

Old log files are never deleted automatically. Each run produces an independent file, so you can
safely run the CLI multiple times in the same directory and compare logs from different runs.

## Log line format

Each line in the log file follows this pattern:

```
2026-06-02 14:35:12.847 [main] DEBUG i.g.s.a.a.service.PureJavaTestEngine - Test [1] 'login': beginning execution
```

Full format string:

```
%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```

## Silent failure cases

Log activation is best-effort. If any of the following conditions occur, the application starts
normally with no log file and no error message:

- **`CLI_LOG_LEVEL` is not a recognised level** — values like `VERBOSE`, `ALL`, `OFF`, or
  misspellings are rejected. Only `TRACE`, `DEBUG`, `INFO`, `WARN`, and `ERROR` are accepted.
- **`CLI_LOG_DIR` cannot be created** — for example, a parent directory in the path does not exist
  and cannot itself be created, or the process lacks write permission on the parent.
- **The log file itself cannot be created** — for example, the directory exists but the process
  does not have write permission to create files inside it.
- **Only one of the two variables is set** — both `CLI_LOG_LEVEL` and `CLI_LOG_DIR` must be
  present; a missing variable is treated the same as logging being disabled.

In all of these cases the CLI continues to run and produce its normal output. If you expect a log
file and do not find one, check:

1. Both variables are exported and visible to the process (`printenv | grep CLI_LOG`).
2. `CLI_LOG_LEVEL` is spelled correctly and is one of the five accepted values.
3. The process has write permission on `CLI_LOG_DIR` (or its parent, if the directory needs to be
   created).
