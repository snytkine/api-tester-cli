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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Launches a lifecycle script hook via {@link ProcessBuilder}, enforcing the feature's mandatory
 * security model. This is a stateless, thread-safe Spring singleton: every per-invocation value
 * (the process, its reader threads, captured output) lives on the call stack of {@link #execute}.
 *
 * <p>Security guarantees implemented here (see issue #65):
 *
 * <ul>
 *   <li><b>No shell.</b> The command is a list — resolved path plus each already-template-resolved
 *       {@code key=value} argument as its own argv element. Shell metacharacters are inert.
 *   <li><b>Executability re-check.</b> {@link Files#isExecutable(Path)} is verified immediately
 *       before launch (a cheap defence against the validate-then-execute gap).
 *   <li><b>Timeout.</b> The process (and its descendants) is force-destroyed when it exceeds the
 *       supplied timeout, and reported as timed out.
 *   <li><b>Output hygiene.</b> stdout and stderr are read via pipes on dedicated threads (never
 *       {@code inheritIO()}); control characters are stripped from every forwarded line.
 *   <li><b>stdin closed.</b> The child's stdin is closed at launch so a script reading stdin sees
 *       EOF instead of blocking.
 * </ul>
 *
 * <p>This class never logs the resolved arguments (they may contain secrets); it only ever receives
 * already-substituted values from the caller.
 */
@Service
public class ScriptHookExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScriptHookExecutor.class);

    /**
     * Executes a script hook and returns its outcome.
     *
     * @param suiteDir the directory of the suite YAML, used to resolve a relative {@code rawPath};
     *     may be {@code null} only when {@code rawPath} is absolute
     * @param rawPath the script path exactly as declared (already template-resolved); absolute or
     *     relative to {@code suiteDir}
     * @param args the ordered {@code key=value} argument strings (system args followed by user
     *     parameters), each passed as its own argv element
     * @param extraEnv additional environment variables to overlay on the inherited process
     *     environment (the merged {@code .env} map), so scripts can read secrets from the environment
     * @param timeoutSeconds maximum seconds to wait before force-killing the process
     * @param stdoutSink receives each control-char-stripped stdout line; may be {@code null} to drop
     * @param stderrSink receives each control-char-stripped stderr line; may be {@code null} to drop
     * @return the {@link HookExecutionResult} describing exit code / timeout / duration
     */
    public HookExecutionResult execute(
            Path suiteDir,
            String rawPath,
            List<String> args,
            Map<String, String> extraEnv,
            int timeoutSeconds,
            Consumer<String> stdoutSink,
            Consumer<String> stderrSink) {

        long start = System.currentTimeMillis();
        Path resolved = resolvePath(suiteDir, rawPath);

        if (!Files.isExecutable(resolved)) {
            return HookExecutionResult.failure(
                    HookExecutionResult.NO_STATUS,
                    System.currentTimeMillis() - start,
                    "Script is not executable: " + resolved);
        }

        List<String> command = new ArrayList<>(args.size() + 1);
        command.add(resolved.toString());
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(extraEnv);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return HookExecutionResult.failure(
                    HookExecutionResult.NO_STATUS,
                    System.currentTimeMillis() - start,
                    "Failed to launch script: " + e.getMessage());
        }

        // Close the child's stdin immediately so a script that reads stdin sees EOF.
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // Non-fatal: some platforms may already have closed it.
        }

        List<String> capturedStderr = new ArrayList<>();
        Thread outThread = startReader(process.getInputStream(), stdoutSink, null);
        Thread errThread = startReader(process.getErrorStream(), stderrSink, capturedStderr);

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyTree(process);
            joinQuietly(outThread);
            joinQuietly(errThread);
            return HookExecutionResult.failure(
                    HookExecutionResult.NO_STATUS,
                    System.currentTimeMillis() - start,
                    "Interrupted while waiting for script");
        }

        if (!finished) {
            destroyTree(process);
            joinQuietly(outThread);
            joinQuietly(errThread);
            return HookExecutionResult.timedOut(
                    System.currentTimeMillis() - start,
                    "Script exceeded timeout of " + timeoutSeconds + "s and was terminated");
        }

        joinQuietly(outThread);
        joinQuietly(errThread);
        int exit = process.exitValue();
        long duration = System.currentTimeMillis() - start;
        if (exit == 0) {
            return HookExecutionResult.success(exit, duration);
        }
        String stderrSummary =
                capturedStderr.isEmpty() ? "" : String.join(" ", capturedStderr).trim();
        return HookExecutionResult.failure(exit, duration, stderrSummary);
    }

    /**
     * Resolves {@code rawPath} to an absolute path: used as-is when absolute, otherwise resolved
     * against {@code suiteDir}.
     *
     * @param suiteDir the suite directory (may be {@code null} for an absolute {@code rawPath})
     * @param rawPath the declared script path
     * @return the resolved path
     */
    public static Path resolvePath(Path suiteDir, String rawPath) {
        Path p = Path.of(rawPath);
        if (p.isAbsolute() || suiteDir == null) {
            return p;
        }
        return suiteDir.resolve(p);
    }

    /**
     * Starts a daemon thread that reads {@code stream} line by line, strips control characters, and
     * forwards each line to {@code sink} (when non-null) and appends it to {@code collector} (when
     * non-null).
     *
     * @param stream the process stream to read
     * @param sink the per-line consumer, or {@code null} to drop lines
     * @param collector a list to append stripped lines to, or {@code null} to not collect
     * @return the started reader thread
     */
    private static Thread startReader(InputStream stream, Consumer<String> sink, List<String> collector) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String clean = stripControlChars(line);
                    if (sink != null) {
                        sink.accept(clean);
                    }
                    if (collector != null) {
                        synchronized (collector) {
                            collector.add(clean);
                        }
                    }
                }
            } catch (IOException e) {
                log.debug("Error reading script output stream: {}", e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Force-destroys a process and all of its descendants.
     *
     * @param process the process whose tree to destroy
     */
    private static void destroyTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    /**
     * Joins a thread, restoring the interrupt flag if interrupted.
     *
     * @param t the thread to join
     */
    private static void joinQuietly(Thread t) {
        try {
            t.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Removes control characters from {@code line}: every code point below {@code U+0020} except tab
     * ({@code U+0009}), plus {@code U+007F}. This prevents a malicious or compromised script from
     * emitting ANSI escape sequences that rewrite the terminal or corrupt the TUI grid.
     *
     * @param line the raw line read from the script's stdout/stderr
     * @return the line with control characters removed
     */
    public static String stripControlChars(String line) {
        StringBuilder sb = new StringBuilder(line.length());
        line.codePoints().forEach(cp -> {
            if (cp == '\t' || (cp >= 0x20 && cp != 0x7F)) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }
}
