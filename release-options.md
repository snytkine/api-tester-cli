# Release Strategy Options

This document outlines two approaches for automating versioning, git tagging, and release management for `api-tester-cli`.

---

## Option 1 — Maven Release Plugin

### Overview

The Maven Release Plugin manages version numbers in `pom.xml` and creates/pushes git tags. Only `release:prepare` is used — `release:perform` is never run, so no artifacts are deployed to Maven Central.

### How it works

Running `release:prepare` locally (or in CI):

1. Verifies the working tree is clean and all tests pass (`mvn package`)
2. Strips `-SNAPSHOT` from the version in `pom.xml` and commits it
3. Creates and pushes the git tag (e.g. `v1.2.0`)
4. Bumps `pom.xml` to the next `-SNAPSHOT` version and commits it

The pushed tag then triggers the GitHub Actions `release.yml` workflow which builds the GraalVM native binaries.

### Required `pom.xml` configuration

```xml
<scm>
  <connection>scm:git:https://github.com/snytkine/api-tester-cli.git</connection>
  <developerConnection>scm:git:git@github.com:snytkine/api-tester-cli.git</developerConnection>
  <url>https://github.com/snytkine/api-tester-cli</url>
  <tag>HEAD</tag>
</scm>

<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-release-plugin</artifactId>
  <version>3.1.1</version>
  <configuration>
    <!-- Prevents deployment to Maven Central -->
    <goals>package</goals>
    <!-- Tag format: v1.2.0 -->
    <tagNameFormat>v@{project.version}</tagNameFormat>
    <pushChanges>true</pushChanges>
  </configuration>
</plugin>
```

### Running a release

```bash
./mvnw release:prepare \
  -DreleaseVersion=1.2.0 \
  -DdevelopmentVersion=1.3.0-SNAPSHOT \
  -Dtag=v1.2.0
```

Or interactively (the plugin will prompt for versions):

```bash
./mvnw release:prepare
```

### Trade-offs

| Pro | Con |
|---|---|
| Standard Maven tooling, no new concepts | Requires clean working tree and passing tests locally |
| Works without adopting Conventional Commits | Makes two commits per release (release + next SNAPSHOT) |
| Full control over version numbers | `pom.xml.releaseBackup` must be `.gitignore`d |
| `mvn package` (JVM build) runs as verification — fast | Manual step: someone must run the command to trigger a release |

---

## Option 2 — `release-please` GitHub Action

### Overview

`release-please` is a GitHub Action by Google that automates versioning and changelog generation entirely from commit messages. No local tooling is required — everything happens in CI.

### How it works

1. On every push to the configured target branch, `release-please` scans commit messages since the last release tag
2. If it finds relevant commits, it opens (or updates) a **Release PR** that bumps the version in `pom.xml` and updates `CHANGELOG.md`
3. When you **merge the Release PR**, `release-please` creates the git tag and GitHub Release
4. The pushed tag triggers your `release.yml` workflow to build the GraalVM native binaries

### Workflow file

```yaml
# .github/workflows/release-please.yml
name: release-please

on:
  push:
    branches:
      - main        # or 'release' — see Branch Configuration below

permissions:
  contents: write
  pull-requests: write

jobs:
  release-please:
    runs-on: ubuntu-latest
    steps:
      - uses: googleapis/release-please-action@v4
        with:
          release-type: maven
          target-branch: main   # branch to watch
```

### Branch configuration

By default `release-please` watches `main`. To trigger only from a dedicated `release` branch:

```yaml
on:
  push:
    branches:
      - release

jobs:
  release-please:
    steps:
      - uses: googleapis/release-please-action@v4
        with:
          release-type: maven
          target-branch: release
```

With this setup:
- Day-to-day development continues on `main` (or feature branches)
- When ready to ship, merge `main` → `release`
- `release-please` scans the new commits on `release` and opens a Release PR against `release`
- Merging the Release PR creates the tag and triggers the native build

---

## How `release-please` uses commit messages

`release-please` requires commit messages to follow the [Conventional Commits](https://www.conventionalcommits.org/) specification. It reads the **subject line** of every commit since the last release tag.

### Commit message format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Version bump rules

| Commit type | Example subject | Effect |
|---|---|---|
| `fix` | `fix: correct error when suite file missing` | Patch bump `1.0.0 → 1.0.1` |
| `feat` | `feat: add optional --suite with fallback` | Minor bump `1.0.0 → 1.1.0` |
| `feat!` or `BREAKING CHANGE:` in footer | `feat!: remove --no-ui flag` | Major bump `1.0.0 → 2.0.0` |
| `chore`, `docs`, `refactor`, `test`, `style` | `chore: update dependencies` | No bump — logged in CHANGELOG but does not trigger a version increase |

If no `feat` or `fix` commits are found since the last release, `release-please` does nothing — no Release PR is opened.

### What goes into `CHANGELOG.md`

The changelog is built entirely from commit messages. For each commit, `release-please` uses:

- **Subject line** → the changelog entry text
- **Body** → optionally included as additional detail
- **Footer** → `Closes #21` / `Fixes #21` links are turned into issue references; `BREAKING CHANGE:` triggers a major bump and a dedicated section
- **Commit SHA** → turned into a link to the commit on GitHub

#### Example commit

```
feat: add optional --suite argument with test-suite.yml fallback

If --suite is omitted, the tool now looks for test-suite.yml in the
current working directory before reporting an error.

Closes #21
```

#### Resulting CHANGELOG entry

```markdown
## [1.1.0](https://github.com/snytkine/api-tester-cli/compare/v1.0.0...v1.1.0) - 2026-06-09

### Features

* add optional --suite argument with test-suite.yml fallback ([a3f9c12](…)) — Closes #21
```

### CHANGELOG location

`release-please` writes and maintains `CHANGELOG.md` in the root of the repository. Each release prepends a new section at the top; the full history accumulates over time. The same content is also copied into the **GitHub Release notes** on the Releases page.

The `CHANGELOG.md` update is part of the Release PR, so you can review (and edit) the changelog before merging and triggering the release.

### Implications for commit discipline

Because the CHANGELOG is derived solely from commit messages:
- Vague messages (`fix: bug fix`) produce vague changelog entries
- The subject line should describe the change from the **user's perspective**, not the implementation
- `chore:`/`refactor:`/`test:` commits are silently excluded from the version bump decision — use them freely for internal changes that users don't need to know about

### Trade-offs

| Pro | Con |
|---|---|
| Fully automated — no local tooling required | Requires adopting Conventional Commits format |
| Release PR gives human review gate before tag is created | Team discipline needed for consistent commit messages |
| CHANGELOG generated automatically | Freeform commit messages produce poor changelogs |
| Integrates naturally with GitHub Actions release workflow | Additional workflow file to maintain |
| Version bump magnitude is self-documenting in commit history | |

---

## Comparison summary

| | Maven Release Plugin | release-please |
|---|---|---|
| **Trigger** | Manual command (`mvn release:prepare`) | Automatic on push to target branch |
| **Conventional Commits required** | No | Yes |
| **CHANGELOG** | Not generated | Auto-generated from commit messages |
| **Human gate before release** | Whoever runs the command | Merging the Release PR |
| **Local tooling needed** | Yes (`mvn`) | No |
| **Two commits per release** | Yes | Yes (version bump + post-release SNAPSHOT) |
| **Works from a specific branch** | Yes — run the command on that branch | Yes — configure `target-branch` |
