/*
 * Copyright 2026 - 2026 Dmitri Snytkine. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.snytkine.apitester.api_tester_cli.service.hooks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link ScriptHookExecutor}. */
@DisabledOnOs(OS.WINDOWS)
class ScriptHookExecutorTest {

    private final ScriptHookExecutor executor = new ScriptHookExecutor();

    /**
     * Writes {@code body} to an executable script file in {@code dir} and returns its path.
     *
     * @param dir the directory to create the script in
     * @param name the script file name
     * @param body the shell script contents (a shebang is prepended)
     * @return the created executable script path
     * @throws IOException if the file cannot be written
     */
    private static Path writeScript(Path dir, String name, String body) throws IOException {
        Path script = dir.resolve(name);
        Files.writeString(script, "#!/bin/sh\n" + body, StandardCharsets.UTF_8);
        Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(script, perms);
        return script;
    }

    @Test
    void successfulScriptReturnsExitZeroAndForwardsStdout(@TempDir Path dir) throws IOException {
        Path script = writeScript(dir, "ok.sh", "echo hello\nexit 0\n");
        List<String> out = new CopyOnWriteArrayList<>();

        HookExecutionResult result =
                executor.execute(dir, script.toString(), List.of(), Map.of(), 10, out::add, s -> {});

        assertThat(result.success()).isTrue();
        assertThat(result.exitCodeOrStatus()).isZero();
        assertThat(result.timedOut()).isFalse();
        assertThat(out).contains("hello");
    }

    @Test
    void nonZeroExitIsReportedAsFailureWithStderr(@TempDir Path dir) throws IOException {
        Path script = writeScript(dir, "fail.sh", "echo boom 1>&2\nexit 7\n");

        HookExecutionResult result =
                executor.execute(dir, script.toString(), List.of(), Map.of(), 10, s -> {}, s -> {});

        assertThat(result.success()).isFalse();
        assertThat(result.exitCodeOrStatus()).isEqualTo(7);
        assertThat(result.errorMessage()).contains("boom");
    }

    @Test
    void argumentsArriveAsSingleIntactArgvElements(@TempDir Path dir) throws IOException {
        Path outFile = dir.resolve("args.txt");
        Path script = writeScript(dir, "args.sh", "printf '%s\\n' \"$@\" > \"" + outFile + "\"\n");

        List<String> args = List.of("a=has space", "b=semi;colon", "c=quote\"d");
        HookExecutionResult result = executor.execute(dir, script.toString(), args, Map.of(), 10, s -> {}, s -> {});

        assertThat(result.success()).isTrue();
        List<String> written = Files.readAllLines(outFile);
        assertThat(written).containsExactly("a=has space", "b=semi;colon", "c=quote\"d");
    }

    @Test
    void relativePathIsResolvedAgainstSuiteDir(@TempDir Path dir) throws IOException {
        writeScript(dir, "rel.sh", "exit 0\n");

        HookExecutionResult result = executor.execute(dir, "rel.sh", List.of(), Map.of(), 10, s -> {}, s -> {});

        assertThat(result.success()).isTrue();
    }

    @Test
    void extraEnvIsVisibleToScript(@TempDir Path dir) throws IOException {
        Path outFile = dir.resolve("env.txt");
        Path script = writeScript(dir, "env.sh", "printf '%s' \"$MY_SECRET\" > \"" + outFile + "\"\n");

        HookExecutionResult result = executor.execute(
                dir, script.toString(), List.of(), Map.of("MY_SECRET", "s3cr3t"), 10, s -> {}, s -> {});

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(outFile)).isEqualTo("s3cr3t");
    }

    @Test
    void timeoutKillsScriptAndReportsTimedOut(@TempDir Path dir) throws IOException {
        Path script = writeScript(dir, "slow.sh", "sleep 5\nexit 0\n");

        long start = System.currentTimeMillis();
        HookExecutionResult result = executor.execute(dir, script.toString(), List.of(), Map.of(), 1, s -> {}, s -> {});
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.timedOut()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(elapsed).isLessThan(4000L);
    }

    @Test
    void controlCharactersAreStrippedFromForwardedLines(@TempDir Path dir) throws IOException {
        // Emit an ANSI escape sequence (ESC = \033) around text.
        Path script = writeScript(dir, "ansi.sh", "printf '\\033[31mRED\\033[0m\\n'\n");
        List<String> out = new CopyOnWriteArrayList<>();

        executor.execute(dir, script.toString(), List.of(), Map.of(), 10, out::add, s -> {});

        assertThat(out).isNotEmpty();
        assertThat(out.get(0)).doesNotContain("\033").contains("RED");
    }

    @Test
    void scriptReadingStdinSeesEofAndTerminates(@TempDir Path dir) throws IOException {
        Path script = writeScript(dir, "stdin.sh", "cat > /dev/null\necho done\n");
        List<String> out = new ArrayList<>();

        HookExecutionResult result =
                executor.execute(dir, script.toString(), List.of(), Map.of(), 5, out::add, s -> {});

        assertThat(result.success()).isTrue();
        assertThat(result.timedOut()).isFalse();
        assertThat(out).contains("done");
    }

    @Test
    void nonExecutablePathIsReportedAsFailure(@TempDir Path dir) throws IOException {
        Path notExec = dir.resolve("plain.sh");
        Files.writeString(notExec, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(notExec, EnumSet.of(PosixFilePermission.OWNER_READ));

        HookExecutionResult result =
                executor.execute(dir, notExec.toString(), List.of(), Map.of(), 10, s -> {}, s -> {});

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("not executable");
    }

    @Test
    void resolvePathUsesAbsoluteAsIsAndRelativeAgainstSuiteDir(@TempDir Path dir) {
        Path abs = dir.resolve("x.sh").toAbsolutePath();
        assertThat(ScriptHookExecutor.resolvePath(dir, abs.toString())).isEqualTo(abs);
        assertThat(ScriptHookExecutor.resolvePath(dir, "y.sh")).isEqualTo(dir.resolve("y.sh"));
        assertThat(ScriptHookExecutor.resolvePath(null, "z.sh")).isEqualTo(Path.of("z.sh"));
    }

    @Test
    void stripControlCharsKeepsTabAndPrintable() {
        // BEL (U+0007), DEL (U+007F) and ESC (U+001B) are stripped; tab and printables kept.
        String input = "a\tb\u0007c\u007fd\u001b[0me";
        String cleaned = ScriptHookExecutor.stripControlChars(input);
        assertThat(cleaned).isEqualTo("a\tbcd[0me");
    }
}
