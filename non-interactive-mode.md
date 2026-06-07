# Non-Interactive Mode

By default the application starts Spring Shell's **interactive** REPL (the `shell:>` prompt). Setting
the `DISABLE_INTERACTIVE_MODE` environment variable switches it to **non-interactive** mode, where the
application executes a single command passed on the command line and then exits. This works
identically on the JVM and in the GraalVM native binary.

## Enabling non-interactive mode

Set the environment variable to `true` (case-insensitive) and pass the command to run as arguments:

```bash
# Native binary
DISABLE_INTERACTIVE_MODE=true ./target/api-tester-cli run-suite --suite=/path/to/suite.yml

# JVM (development)
DISABLE_INTERACTIVE_MODE=true ./mvnw spring-boot:run -Dspring-boot.run.arguments="run-suite --suite=/path/to/suite.yml"
```

Any command works, e.g. `help`:

```bash
DISABLE_INTERACTIVE_MODE=true ./target/api-tester-cli help
```

| Value of `DISABLE_INTERACTIVE_MODE` | Mode |
|-------------------------------------|------|
| `true` (any case)                   | Non-interactive — run the supplied command and exit |
| unset, `false`, or anything else    | Interactive — start the `shell:>` REPL |

### Behavior notes

- **A command is required.** In non-interactive mode the application expects at least one argument
  (the command, e.g. `run-suite ...`). Running with no command logs an error and exits non-zero:

  ```
  ERROR o.s.s.core.NonInteractiveShellRunner : In non interactive mode, it expected to have at
  least one argument: the command to execute or the script file
  ```

- **Exit code reflects the command.** Because the process runs one command and exits, it is suitable
  for CI pipelines and scripts.
- **Runtime decision.** The mode is chosen each time the application starts, based on the environment
  variable at that moment — the same binary can run interactively or non-interactively on different
  invocations.

### Output format

In non-interactive mode, output is concise and suitable for CI logs:

- **Summary counts:** `Passed: X, Failed: Y, Errors: Z, Skipped: W`
- **Failed test details** (if any failures): test name, then "Failed assertions:" label, then each assertion error message indented
- **Report path** (if `--report` was used): `Test report generated at <path>`

Example:

```
Passed: 1, Failed: 1, Errors: 0, Skipped: 0

Objects Test
Failed assertions:
  - string_match response.headers.content-type failed
  - json_match response.body.json failed

Test report generated at /tmp/test-suite_MyApp_20260607185044.html
```

For building the native binary, see [native-build-support.md](native-build-support.md)
(`./mvnw clean -Pnative native:compile`; the binary is written to `target/api-tester-cli`).

---

## Implementation: blockers and the solution

This section documents why enabling non-interactive mode via an environment variable was
non-trivial for the **GraalVM native image**, and the approach that finally worked. It is intended
for maintainers.

### Background: how Spring Shell selects the runner

Spring Shell's `ShellRunnerAutoConfiguration` picks the runner with `@ConditionalOnProperty`:

```java
@Bean @ConditionalOnProperty(prefix = "spring.shell.interactive", name = "enabled",
       havingValue = "true", matchIfMissing = true)      // interactive  (SystemShellRunner)
@Bean @ConditionalOnProperty(prefix = "spring.shell.interactive", name = "enabled",
       havingValue = "false")                            // non-interactive (NonInteractiveShellRunner)
```

The runner is injected as a single bean into `springShellApplicationRunner`; there is **no runtime
`canRun()` dispatch** — the choice is made entirely by which bean the condition creates.

### What worked on the JVM but not in native

On the JVM, `@Conditional` is evaluated at runtime, so setting `spring.shell.interactive.enabled=false`
(directly, or via an `EnvironmentPostProcessor` translating a custom variable) flips the selection
before the condition runs. This was the first implementation and it worked under
`mvn spring-boot:run`.

In the **native image it had no effect** — the binary always started interactively regardless of the
environment variable.

### Root cause

**In a GraalVM native image, `@Conditional` evaluations are performed at AOT _build_ time and frozen
into the generated bean definitions.** Because the environment variable is not set during the build,
the condition resolves to its default (`matchIfMissing = true`) and the **interactive** runner is
baked in; the non-interactive bean is never generated. At runtime no environment variable can change
a frozen condition — this affected not only the custom `DISABLE_INTERACTIVE_MODE` variable but even
Spring's own `SPRING_SHELL_INTERACTIVE_ENABLED=false`.

### Blockers encountered along the way

1. **`EnvironmentPostProcessor` registration.** The first attempt registered the post-processor via
   `META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports`. That `.imports`
   mechanism applies only to `@AutoConfiguration`; `EnvironmentPostProcessor` discovery uses
   `SpringFactoriesLoader` and must be registered in `META-INF/spring.factories` under the
   `org.springframework.boot.EnvironmentPostProcessor` key. With the wrong registration the
   post-processor never ran at all.

2. **`EnvironmentPostProcessor` in native needs hints — but that still wasn't enough.** Making the
   post-processor load in native required a resource hint for `META-INF/spring.factories` and a
   reflection hint for the class. Even after it ran at native runtime and set the property, the shell
   still started interactively, because the `@ConditionalOnProperty` decision had already been frozen
   at build time. (Spring Boot's AOT-generated environment post-processor only re-applies captured
   **active profiles**, not arbitrary property sources, so it could not help either.)

3. **Spring Shell's runner beans are not reliably generated in AOT.** Inspecting
   `target/spring-aot/.../ShellRunnerAutoConfiguration__BeanDefinitions.java` showed that the
   conditional `systemShellRunner` and `nonInteractiveShellRunner` beans were **absent** from the
   generated bean definitions. So even a runtime selector could not simply fetch Spring Shell's
   interactive/non-interactive runner beans from the context — they may not exist in the native image.

### The solution

Move the decision out of `@Conditional` and into runtime code, and **construct both runners directly
with `new`** so nothing depends on frozen conditions or on Spring Shell's conditional beans.

`config/InteractiveModeRunnerConfiguration` contributes an `ApplicationRunner` bean **named
`springShellApplicationRunner`**. Spring Shell's own runner is declared
`@ConditionalOnMissingBean(name = "springShellApplicationRunner")`, so contributing a bean with that
exact name makes Spring Shell back off (user configuration is processed before auto-configuration, so
the back-off applies cleanly).

At invocation time the runner reads `DISABLE_INTERACTIVE_MODE` from the live `Environment` and builds
the appropriate runner:

```java
if (disableInteractive) {
    return new NonInteractiveShellRunner(commandParser, commandRegistry);
}
SystemShellRunner runner = new SystemShellRunner(consoleInputProvider, commandParser, commandRegistry);
runner.setDebugMode(environment.getProperty("spring.shell.debug.enabled", Boolean.class, false));
return runner;
```

Why this works in native:

- **No `@Conditional` on the mode.** The interactive/non-interactive choice is plain runtime code, so
  it is never frozen at build time.
- **No reflection, no factory resources.** Both runner classes are referenced with `new`, which keeps
  them statically reachable for AOT — no `reflect-config.json`/`resource-config.json` entries needed.
- **Only always-present collaborators.** `ConsoleInputProvider`, `CommandParser`, and
  `CommandRegistry` are unconditional beans that are reliably generated in AOT, so they are available
  in the native image.

The environment variable itself is read via `Environment#getProperty`, which resolves against the
live `systemEnvironment` property source at runtime (not AOT-frozen) — so it reflects the value set
for that particular invocation.

The earlier `EnvironmentPostProcessor`, its `spring.factories` registration, and the related native
hints were removed, since the runtime runner reads the variable directly.

### Verification

Confirmed end-to-end on the native binary:

| Invocation | Runner selected | Evidence |
|------------|-----------------|----------|
| `DISABLE_INTERACTIVE_MODE=true ./api-tester-cli` (no command) | NonInteractive | `NonInteractiveShellRunner: ... expected to have at least one argument` |
| `DISABLE_INTERACTIVE_MODE=true ./api-tester-cli help` | NonInteractive | prints help and exits 0 |
| `./api-tester-cli help` (no variable) | Interactive | `InteractiveShellRunner: Running in interactive mode, arguments will be ignored` |

### Key takeaway

To make a behavior runtime-configurable in a GraalVM native image, do **not** gate it with
`@ConditionalOnProperty`/`@Conditional` — those freeze at build time. Read the environment variable or
property in code at runtime instead.
