# Native Image Build Support

This document summarises the changes required to compile and run this Spring Boot CLI
application as a GraalVM native executable.

---

## 1. Why GraalVM and why Java 25?

Spring Boot 4.x requires **Java 25** as its minimum JDK. For native image compilation it
also requires a **GraalVM distribution** of that JDK — the standard Oracle or OpenJDK does
not ship the `native-image` tool.

Running `./mvnw -Pnative clean package` with a non-GraalVM JDK fails immediately with:

```
native-image is not installed in your JAVA_HOME.
This probably means that the JDK at '...' is not a GraalVM distribution.
```

---

## 2. Installing GraalVM JDK 25 via SDKMAN

[SDKMAN](https://sdkman.io) is the recommended way to manage multiple JDK versions on
macOS/Linux.

```bash
# Install SDKMAN (if not already installed)
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh

# List available GraalVM builds
sdk list java | grep graal

# Install GraalVM JDK 25 (use the exact identifier shown in the list)
sdk install java 25.0.1-graal

# Set it as the permanent default
sdk default java 25.0.1-graal

# Reload your shell and verify
source ~/.zshrc
java -version          # should show Oracle GraalVM 25.x
native-image --version # must resolve; if not found, see note below
```

> **Note — SDKMAN and JAVA_HOME**: if your shell profile contains a manual
> `export JAVA_HOME=$(...)` line, it will override SDKMAN's setting. Remove or comment
> that line and let SDKMAN manage `JAVA_HOME` exclusively.

---

## 3. pom.xml — native profile

The `native-maven-plugin` listed in `<build><plugins>` without executions does not trigger
any goals on its own. A dedicated profile with explicit phase bindings is required:

```xml
<profiles>
  <profile>
    <id>native</id>
    <build>
      <plugins>
        <plugin>
          <groupId>org.graalvm.buildtools</groupId>
          <artifactId>native-maven-plugin</artifactId>
          <configuration>
            <!-- suppress SLF4J "no provider" warning from the compiler JVM -->
            <buildArgs>
              <arg>-J-Dslf4j.internal.verbosity=ERROR</arg>
            </buildArgs>
          </configuration>
          <executions>
            <execution>
              <id>build-native</id>
              <goals><goal>compile-no-fork</goal></goals>
              <phase>package</phase>
            </execution>
          </executions>
        </plugin>
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <executions>
            <execution>
              <id>process-aot</id>
              <goals><goal>process-aot</goal></goals>
              <!-- explicit phase guarantees AOT runs before native compilation -->
              <phase>prepare-package</phase>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```

The `java.version` property must also target Java 25:

```xml
<properties>
  <java.version>25</java.version>
</properties>
```

---

## 4. Correct build command

Always use the full Maven lifecycle, never invoke `native:compile` directly:

```bash
# correct — runs process-aot then compile-no-fork via lifecycle
./mvnw -Pnative clean package -DskipTests

# wrong — skips process-aot, AOT initializer is never generated
./mvnw -Pnative native:compile -DskipTests
```

Calling `native:compile` directly bypasses the lifecycle and skips `spring-boot:process-aot`,
which generates the `ApplicationContextInitializer` class that Spring Boot requires at
native-image startup.

The compiled binary is written to `target/api-tester-cli`.

---

## 5. Disabling the Spring Boot banner and root logging

`application.properties` settings are sometimes applied too late in native-image startup.
Both must be set programmatically in `main()`:

```java
SpringApplication app = new SpringApplication(ApiTesterCliApplication.class);
app.setBannerMode(Banner.Mode.OFF);
app.setDefaultProperties(Map.of("logging.level.root", "OFF"));
app.run(args);
```

---

## 6. Lombok @Slf4j compatibility

Lombok's `@Slf4j` annotation processor does not work reliably with Java 25. Replace it
with an explicit logger field in every affected class:

```java
// remove: @Slf4j annotation and its import
// add:
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
```

---

## 7. Reflection registration for Jackson and AssertJ

GraalVM's static analysis cannot see classes instantiated reflectively at runtime.
Two categories of classes require explicit registration:

### Model classes (Jackson deserialisation)

Jackson deserialises YAML test-suite files into model records using reflection. All model
classes are registered via `@RegisterReflectionForBinding` on the main application class:

```java
@SpringBootApplication
@RegisterReflectionForBinding({
  TestSuite.class, TestCase.class, Request.class, RequestBody.class,
  Assertion.class, StatusCodeAssertion.class, JsonSchemaAssertion.class,
  JsonMatchAssertion.class, StringContainsAssertion.class, StringMatchAssertion.class,
  ObjectExpectedValue.class, RestClientConfig.class, HttpMethod.class, BodyType.class,
  CliVariables.class, TestRunResult.class, TestCaseResult.class, AssertionFailure.class,
  SoftAssertions.class,
})
public class ApiTesterCliApplication { ... }
```

### AssertJ SoftAssertions

`SoftAssertions.assertSoftly()` instantiates `SoftAssertions` via `Class.getConstructor()`.
Without reflection registration this fails at runtime with `NoSuchMethodException`. It is
included in the list above.

### Adding more classes

If a `NoSuchMethodException` or `ClassNotFoundException` appears at runtime, add the named
class to the `@RegisterReflectionForBinding` list and rebuild.

---

## 8. AOT initializer reflect-config.json

Spring Boot looks up the AOT context initializer by class name using `Class.forName()`.
A static `reflect-config.json` in the source tree guarantees this class is registered
unconditionally, independent of any conditional entries generated by the AOT processor:

**`src/main/resources/META-INF/native-image/io.github.snytkine.apitester/api-tester-cli/reflect-config.json`**

```json
[
  {
    "name": "io.github.snytkine.apitester.api_tester_cli.ApiTesterCliApplication__ApplicationContextInitializer",
    "allPublicConstructors": true,
    "allDeclaredConstructors": true
  }
]
```

---

## 9. Replacing AssertJ SoftAssertions with FailureCollector

AssertJ's `SoftAssertions` uses **Byte Buddy** to generate dynamic proxy classes at runtime.
Byte Buddy emits JVM bytecode on the fly, which is fundamentally incompatible with GraalVM
native images — there is no live JVM to emit bytecode into at native-image runtime.

The failure surfaces as:

```
java.lang.RuntimeException: java.lang.reflect.InvocationTargetException
  Caused by: java.lang.ExceptionInInitializerError
    Caused by: java.lang.RuntimeException: Could not self-attach to current VM...
      at net.bytebuddy.agent.ByteBuddy.<init>(ByteBuddy.java:...)
```

Adding `SoftAssertions.class` to `@RegisterReflectionForBinding` does not fix this — the
problem is not missing reflection metadata, it is that Byte Buddy requires a live JVM to
generate bytecode.

### Fix: custom FailureCollector class

Replace `SoftAssertions` entirely with a custom class that uses standard (non-proxy) AssertJ
assertions in `try-catch` blocks:

**`src/main/java/.../util/FailureCollector.java`**

```java
public final class FailureCollector {
  private final List<Throwable> failures = new ArrayList<>();

  public void fail(String message, Object... args) { ... }
  public StringAssertion assertThat(String actual) { ... }
  public IntAssertion assertThat(int actual) { ... }
  public void assertAll() {
    if (!failures.isEmpty()) throw new MultipleFailuresError("", failures);
  }

  public static final class StringAssertion {
    public void isEqualTo(String expected) {
      try { Assertions.assertThat(actual).isEqualTo(expected); }
      catch (AssertionError e) { collector.addFailure(description, e); }
    }
    // contains, containsIgnoringCase, isEqualToIgnoringCase ...
  }

  public static final class IntAssertion {
    public void isEqualTo(int expected) { ... }
  }
}
```

### Changes required

1. **`AssertionEvaluator` interface** — change the `evaluate` method signature:
   ```java
   // before:
   void evaluate(ApiResponse response, SoftAssertions soft);
   // after:
   void evaluate(ApiResponse response, FailureCollector collector);
   ```

2. **All evaluator implementations** — rename the parameter and replace every `soft.` call
   with `collector.` in both the method signature and the method body. The affected classes:
   - `StatusCodeAssertionEvaluator`
   - `JsonMatchAssertionEvaluator`
   - `JsonSchemaAssertionEvaluator`
   - `StringContainsAssertionEvaluator`
   - `StringMatchAssertionEvaluator`

3. **`PureJavaTestEngine`** — replace the `SoftAssertions.assertSoftly(...)` call:
   ```java
   // before:
   SoftAssertions.assertSoftly(soft -> evaluators.forEach(e -> e.evaluate(apiResponse, soft)));

   // after:
   FailureCollector collector = new FailureCollector();
   evaluators.forEach(e -> e.evaluate(apiResponse, collector));
   collector.assertAll();
   ```

4. **Remove `SoftAssertions` from `@RegisterReflectionForBinding`** — it is no longer used.

5. **Test files** — update all evaluator test classes to instantiate `FailureCollector`
   instead of `SoftAssertions`.

> **Pitfall:** a batch rename tool may rename the method *parameter* to `collector` while
> leaving all the `soft.` call sites inside the method body unchanged. Always verify the
> method body, not just the signature.

---

## 10. Reducing native image binary size

The default GraalVM native image optimizes for execution speed. Adding the `-Os` flag instructs
the compiler to optimize for binary size instead, which has negligible performance impact for a
CLI tool.

Add `-Os` to the `buildArgs` in the `native` profile in `pom.xml`:

```xml
<buildArgs>
  <arg>-Os</arg>
  <arg>-J-Dslf4j.internal.verbosity=ERROR</arg>
</buildArgs>
```

Result for this project: **86 MB → 54 MB (37% reduction)** with a single flag change.

### Further reduction with UPX (optional)

[UPX](https://upx.github.io) compresses the binary with a self-extracting stub. Install via
`brew install upx`, then run after each native build:

```bash
upx --best target/api-tester-cli
```

Typically reduces the binary by another 60–70% (e.g. 54 MB → ~18 MB). The tradeoff is a
~50–100 ms decompression overhead on each startup, which eliminates the near-instant cold-start
advantage of native images. Not recommended if fast startup is a priority.

---

## 11. SLF4J warning during compilation (not a problem)

During `[2/8] Performing analysis`, the native-image compiler JVM prints:

```
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
```

This comes from the compiler's own JVM, not from the compiled binary. Logback is correctly
bundled in the native executable and works at runtime. The warning is suppressed by the
`-J-Dslf4j.internal.verbosity=ERROR` build argument added in section 3.
