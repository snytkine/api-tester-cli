# Upgrade Notifications

On startup, `api-tester-cli` checks GitHub in the background for a newer published release than
the one currently running. When a newer version is available, an upgrade message is shown in two
places:

1. The **HTML execution report** — a banner near the footer, with a link to the latest release.
2. The **interactive terminal UI** — a one-line notice printed after the pass/fail summary at the
   end of a suite run.

The check is entirely best-effort and never affects the outcome of a run:

- It runs on a background daemon thread started at application startup, so it never delays the
  shell prompt or a suite run.
- Because the CLI is short-lived, the check may not finish before a single command completes —
  in that case no message is shown for that run, silently.
- Any failure (no network, GitHub unreachable, unexpected response) is retried a configurable
  number of times and then abandoned silently — it is never printed to the console and never
  affects the suite's pass/fail result or process exit code.

---

## Configuration

All settings live in `application.properties` under the `apitester.version-check.*` prefix:

```properties
apitester.version-check.enabled=true
apitester.version-check.url=https://api.github.com/repos/snytkine/api-tester-cli/releases/latest
apitester.version-check.upgrade-page-url=https://github.com/snytkine/api-tester-cli/releases/latest
apitester.version-check.timeout-seconds=10
apitester.version-check.max-retries=3
apitester.version-check.retry-interval-seconds=5
apitester.version-check.upgrade-message=Version {latestVersion} is available.
```

| Property | Meaning |
|---|---|
| `enabled` | Set to `false` to disable the background check entirely (no network call is made). Useful for offline environments, CI, or tests. |
| `url` | The GitHub Releases API endpoint queried for the latest published release. |
| `upgrade-page-url` | The human-facing page linked from the upgrade message. |
| `timeout-seconds` | Per-request timeout for the version-check HTTP call. |
| `max-retries` | Total number of attempts made before giving up silently. |
| `retry-interval-seconds` | Seconds to sleep between retry attempts. |
| `upgrade-message` | Template for the upgrade message; supports the `{latestVersion}` placeholder. |

Since these are standard Spring Boot configuration properties, any of them can be overridden
without rebuilding the jar — via an OS environment variable (relaxed binding: dots and hyphens
become underscores, e.g. `APITESTER_VERSION_CHECK_ENABLED=false`) or a JVM system property
(`-Dapitester.version-check.enabled=false`).

```bash
# Disable the check for a single run via an environment variable
APITESTER_VERSION_CHECK_ENABLED=false rs --suite=suite.yml

# Or via a system property
java -Dapitester.version-check.enabled=false -jar target/api-tester-cli-0.0.1-SNAPSHOT.jar run-suite --suite=suite.yml
```

---

## Version comparison

The running version (from the build's version metadata) is compared against the latest GitHub
release tag using numeric `major.minor.patch` ordering:

- A pre-release suffix on the *running* version (e.g. `0.4.1-SNAPSHOT`) is treated as older than
  the same numeric published release (`0.4.1`), so an upgrade notice still appears.
- If the running version cannot be determined (reported as `unknown`, which happens when the
  build was not produced through the normal Maven build), the check is skipped.
- Malformed or unparseable version strings on either side are treated as "no upgrade available".
