# Issue #21: Remove JSON output, use concise human-readable format for --no-ui mode

**Date:** 2026-06-07  
**Status:** Open

## Problem

When `--no-ui` is passed (forcing non-interactive output instead of the interactive UI), the command currently dumps the entire `TestRunResult` as JSON. This includes:
- Full test suite configuration
- Complete request/response details for every test
- All `AssertionFailure` fields

This is too verbose for CI logs and script parsing. The output is unreadable and clutters log files.

## Desired behavior

Replace JSON output with a concise, human-readable text format:

### 1. Summary line
Single line with all counts:
```
Passed: 1, Failed: 1, Errors: 0, Skipped: 1
```

### 2. Failed test details (only if there are failures/errors)
For each failed or errored test:
```
Objects Test
Failed assertions:
  - string_match response.headers.content-type failed
  - json_match response.body.json failed

Another Failed Test
Failed assertions:
  - status_code equals 200 but was 500
```

### 3. Report path (if `--report` was used)
```
Test report generated at /var/tmp/test-suite_MyApp_20260607185044.html
```

### 4. Errors (if any exception/parsing error occurs)
Short error message only, **never a full stack trace**:
```
Error: Unable to parse suite file: invalid YAML syntax
```

## Example complete output

```
Passed: 2, Failed: 1, Errors: 0, Skipped: 1

Objects Test
Failed assertions:
  - string_match response.headers.content-type failed
  - json_match response.body.json failed

Test report generated at /tmp/test-suite_MyApp_20260607185044.html
```

## Implementation notes

- This applies when `--no-ui` is passed OR when non-interactive mode is active (`DISABLE_INTERACTIVE_MODE=true`)
- The detailed HTML report (via `--report`) remains the source of truth for deep analysis
- Exit codes remain: 0 (pass), 1 (failure/error), 2 (options error)
- Debug logging (`CLI_LOG_DIR`/`CLI_LOG_LEVEL`) is unaffected and works independently

## Related files

- `src/main/java/io/github/snytkine/apitester/api_tester_cli/commands/RunSuiteCommand.java`
- `non-interactive-mode.md` (documentation)
