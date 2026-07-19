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
package io.github.snytkine.apitester.api_tester_cli.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static utility that conditionally activates file-based logging for the entire application.
 *
 * <p>File logging is activated only when both of the following environment variables are set:
 *
 * <ul>
 *   <li>{@code CLI_LOG_LEVEL} — one of the SLF4J-supported levels: {@code TRACE}, {@code DEBUG},
 *       {@code INFO}, {@code WARN}, {@code ERROR} (case-insensitive)
 *   <li>{@code CLI_LOG_DIR} — path to a directory where log files will be written; the directory is
 *       created automatically if it does not yet exist
 * </ul>
 *
 * <p>When activated, a new log file is created using the naming pattern {@code
 * cli_log_yyyy_MM_dd_HHmmss.log} (based on the current time at activation). All SLF4J loggers in
 * the application write to this file using the format:
 *
 * <pre>
 * %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
 * </pre>
 *
 * <p>If any of the following conditions occur, the activation is silently skipped:
 *
 * <ul>
 *   <li>Either environment variable is absent
 *   <li>{@code CLI_LOG_LEVEL} is not one of the five recognised SLF4J levels
 *   <li>{@code CLI_LOG_DIR} cannot be created
 *   <li>The log file itself cannot be created (e.g., insufficient permissions)
 *   <li>The SLF4J binding is not Logback (so the {@link LoggerContext} is unavailable)
 * </ul>
 *
 * <p>Console output is not affected: when file logging is activated, Spring Boot's default {@link
 * ch.qos.logback.core.ConsoleAppender} is detached from the root logger so that log lines do not
 * appear on stdout/stderr alongside the CLI output.
 *
 * <p>This class is thread-safe; it holds no mutable state.
 */
public final class LogFileActivator {

    /** Environment variable that specifies the desired log level. */
    static final String ENV_LOG_LEVEL = "CLI_LOG_LEVEL";

    /** Environment variable that specifies the directory for log files. */
    static final String ENV_LOG_DIR = "CLI_LOG_DIR";

    private static final String LOG_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n";

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss");

    /** The five SLF4J-level names that are accepted as valid values for {@code CLI_LOG_LEVEL}. */
    static final Set<String> VALID_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    /**
     * Guard ensuring file logging is configured at most once per process. The first <em>successful</em>
     * activation — whether from the OS environment at startup or from the merged {@code .env}/{@code
     * --env-file} variables when {@code run-suite} executes — wins; later calls are no-ops. Guarded by
     * synchronising on {@link #activateGuarded(String, String)} and {@link #resetForTesting()}.
     */
    private static boolean activated = false;

    private LogFileActivator() {}

    /**
     * Reads {@code CLI_LOG_LEVEL} and {@code CLI_LOG_DIR} from the OS process environment and, if both
     * are present and valid, configures a Logback {@link FileAppender} on the root logger.
     *
     * <p>This is the entry point called at application startup (via {@link
     * io.github.snytkine.apitester.api_tester_cli.config.LoggingActivatorRunner}). It only sees
     * <em>exported</em> environment variables; values supplied through a {@code .env} file or {@code
     * --env-file} are not part of the process environment at startup and are instead honoured later by
     * {@link #activateFromEnv(Map)} when the {@code run-suite} command has loaded them.
     */
    public static void activate() {
        activateGuarded(System.getenv(ENV_LOG_LEVEL), System.getenv(ENV_LOG_DIR));
    }

    /**
     * Activates file logging from an already-resolved environment map, honouring {@code CLI_LOG_LEVEL}
     * and {@code CLI_LOG_DIR} entries sourced from a {@code .env} file, an explicit {@code --env-file},
     * and/or the OS environment (merged by the caller). This is the entry point that lets logging be
     * configured from a {@code .env} file — the startup {@link #activate()} path only sees exported OS
     * variables.
     *
     * <p>Delegates to the same {@link #activateGuarded(String, String)} logic as startup activation, so
     * whichever source fires first (and succeeds) wins and no duplicate log file is created.
     *
     * @param env the merged environment variables (for example the map produced by the CLI's {@code
     *     .env}/{@code --env-file} loading); {@code null} is treated as an empty map
     */
    public static void activateFromEnv(Map<String, String> env) {
        if (env == null) {
            return;
        }
        activateGuarded(env.get(ENV_LOG_LEVEL), env.get(ENV_LOG_DIR));
    }

    /**
     * Activates file logging at most once per process. Returns immediately when either value is absent
     * or when a previous call has already activated logging successfully; otherwise delegates to {@link
     * #activate(String, String)} and records success so subsequent calls become no-ops.
     *
     * <p>Synchronised so the check-then-activate sequence is atomic across the startup thread and the
     * command-execution thread.
     *
     * @param rawLevel candidate {@code CLI_LOG_LEVEL} value, or {@code null}
     * @param rawDir candidate {@code CLI_LOG_DIR} value, or {@code null}
     */
    private static synchronized void activateGuarded(String rawLevel, String rawDir) {
        if (rawLevel == null || rawDir == null || activated) {
            return;
        }
        if (activate(rawLevel, rawDir)) {
            activated = true;
        }
    }

    /**
     * Resets the one-time activation guard. Intended solely for unit tests that need to exercise the
     * guarded activation paths deterministically; not used in production.
     */
    static synchronized void resetForTesting() {
        activated = false;
    }

    /**
     * Core activation logic, exposed package-privately so unit tests can inject arbitrary values
     * without manipulating the process environment.
     *
     * <p>Steps performed:
     *
     * <ol>
     *   <li>Return immediately if either argument is {@code null}.
     *   <li>Normalise {@code rawLevel} to upper-case and verify it is in {@link #VALID_LEVELS};
     *       return if not.
     *   <li>Ensure {@code rawDir} exists as a directory, creating it if necessary; return on {@link
     *       IOException}.
     *   <li>Generate a timestamped log file path.
     *   <li>Obtain the Logback {@link LoggerContext}; return if the SLF4J binding is not Logback.
     *   <li>Build and start a {@link PatternLayoutEncoder} and a {@link FileAppender}; return if the
     *       appender fails to start (i.e., the file could not be created).
     *   <li>Detach all existing appenders from the root logger and attach the new file appender.
     *   <li>Set the root logger level to the requested {@link Level}.
     * </ol>
     *
     * @param rawLevel raw value of the {@code CLI_LOG_LEVEL} environment variable; may be {@code
     *     null}
     * @param rawDir raw value of the {@code CLI_LOG_DIR} environment variable; may be {@code null}
     * @return {@code true} when file logging was configured, {@code false} when activation was skipped
     *     (missing/invalid values, directory or file creation failure, or a non-Logback SLF4J binding)
     */
    static boolean activate(String rawLevel, String rawDir) {
        if (rawLevel == null || rawDir == null) {
            return false;
        }

        String normalizedLevel = rawLevel.toUpperCase(Locale.ROOT);
        if (!VALID_LEVELS.contains(normalizedLevel)) {
            return false;
        }

        Level level = Level.toLevel(normalizedLevel);

        Path dirPath = Path.of(rawDir);
        if (!Files.exists(dirPath)) {
            try {
                Files.createDirectories(dirPath);
            } catch (IOException e) {
                return false;
            }
        }

        String timestamp = TIMESTAMP_FMT.format(LocalDateTime.now());
        Path logFile = dirPath.resolve(String.format("cli_log_%s.log", timestamp));

        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext loggerContext)) {
            return false;
        }

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern(LOG_PATTERN);
        encoder.start();

        FileAppender<ILoggingEvent> appender = new FileAppender<>();
        appender.setContext(loggerContext);
        appender.setFile(logFile.toString());
        appender.setAppend(false);
        appender.setEncoder(encoder);
        appender.start();

        if (!appender.isStarted()) {
            return false;
        }

        ch.qos.logback.classic.Logger root = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        root.detachAndStopAllAppenders();
        root.addAppender(appender);
        root.setLevel(level);

        // Spring Boot applies logging.level.* from application.properties before any
        // ApplicationRunner fires, leaving explicit per-logger levels (e.g. the project's own
        // "logging.level.io.github.snytkine.apitester=OFF") that would otherwise override the
        // root level we just set. Reset every non-root logger to null (inherit from root) so
        // the requested level takes effect uniformly across the whole application.
        for (ch.qos.logback.classic.Logger logger : loggerContext.getLoggerList()) {
            if (logger != root && logger.getLevel() != null) {
                logger.setLevel(null);
            }
        }

        // JLine logs ProcessBuilder.start() calls and terminal-setup internals at DEBUG/TRACE on
        // these loggers. Pin them back to WARN after the reset above so they stay silent.
        loggerContext.getLogger("java.lang.ProcessBuilder").setLevel(Level.WARN);
        loggerContext.getLogger("org.jline").setLevel(Level.WARN);

        LoggerFactory.getLogger(LogFileActivator.class).info("Application Started");
        return true;
    }
}
