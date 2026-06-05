# Contributing to api-tester-cli

Thank you for your interest in contributing! This project is a Spring Boot + Spring Shell CLI tool
that executes HTTP API test suites defined in YAML files. See [README.md](README.md) for a full
overview of features and usage.

---

## Prerequisites

- **GraalVM Java 25** — the project targets Java 25 and must compile as a GraalVM native image.
  The recommended way to install it is via [sdkman](https://sdkman.io/):
  ```bash
  sdk install java 25-graalce   # or the current GraalVM CE distribution for Java 25
  sdk use java 25-graalce
  ```
- **Maven** — a Maven Wrapper (`./mvnw`) is included; no separate Maven installation is required.

---

## Getting started

```bash
# Clone your fork
git clone https://github.com/snytkine/api-tester-cli.git
cd api-tester-cli

# JVM build (fast, for day-to-day development)
./mvnw clean package

# Native binary (optional; requires GraalVM native-image tool)
./mvnw -Pnative native:compile
```

---

## Running tests

```bash
# Full test suite
./mvnw test

# Single test class
./mvnw test -Dtest=TestSuiteLoaderTest

# Single test method
./mvnw test -Dtest=TestSuiteLoaderTest#twoStepLoadResolvesSuiteVariableReferencesInTestCases
```

---

## Code style

This project uses [Spotless](https://github.com/diffplug/spotless) with
**Palantir Java Format** to enforce consistent formatting.

**Always run before committing:**

```bash
./mvnw spotless:apply
```

Spotless also runs in check mode during `./mvnw verify`. CI will fail if formatting is not clean.

Additional conventions:

- **License header** — every Java file must begin with the Apache 2.0 header defined in
  `src/main/resources/license-header.txt`. Spotless enforces this automatically.
- **YAML files** — do **not** use your editor's format-on-save for `.yml` files; Spotless owns
  YAML formatting via its Jackson formatter and the two will conflict.
- **POM** — dependencies sorted by `scope,groupId,artifactId`; plugins by `groupId,artifactId`.
  Spotless enforces this too.

---

## JavaDoc requirement

Every new class and every new public or package-private method must have a JavaDoc block with
meaningful documentation. Describe *why* and *what*, not just a restatement of the signature.

---

## GraalVM native-image rules

The project must compile and run as a GraalVM native binary. Keep these constraints in mind when
adding new code:

- No runtime reflection without a corresponding entry in
  `src/main/resources/META-INF/native-image/.../reflect-config.json`.
- No dynamic proxies without `proxy-config.json`.
- No classpath scanning at runtime.
- Prefer constructor injection over field injection.
- New Jackson deserializers must be registered in `reflect-config.json` so GraalVM can
  instantiate them at runtime.
- When adding a new dependency, verify it ships GraalVM reachability metadata or add hints
  manually.

---

## Thread safety

All Spring singleton beans (`@Service`, `@Component`, `@Configuration`) must be stateless and
thread-safe. Per-invocation data (e.g. a `RestClient` built from suite config, a soft-assertions
collector) must live on the call stack — never in instance fields. Document thread-safety
guarantees explicitly in each class-level JavaDoc.

---

## Submitting a pull request

1. Fork the repository and create a focused feature branch:
   ```bash
   git checkout -b feat/my-feature
   ```
2. Make your changes and add or update unit tests to cover the new behaviour.
3. Run formatting and tests — both must pass cleanly:
   ```bash
   ./mvnw spotless:apply
   ./mvnw test
   ```
4. Open a pull request against `main` with a clear description of *what* changed and *why*.
5. Keep PRs focused — one logical change per PR makes review faster and history cleaner.

---

## Reporting bugs and suggesting features

Open a [GitHub Issue](https://github.com/snytkine/api-tester-cli/issues). For bug reports,
please include:

- Steps to reproduce the problem
- Expected behaviour vs. actual behaviour
- The test-suite YAML that triggers the issue (if applicable)
- Java version and OS

---

## License

By contributing you agree that your changes will be licensed under the
[Apache License 2.0](LICENSE.txt), the same license as the rest of the project.
