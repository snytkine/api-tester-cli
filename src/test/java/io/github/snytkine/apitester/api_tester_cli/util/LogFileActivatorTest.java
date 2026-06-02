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

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link LogFileActivator}.
 *
 * <p>Tests use the package-private {@code activate(String, String)} overload so that environment
 * variable values can be injected directly without manipulating the process environment. Each test
 * saves the root logger's level and appender list before running and restores them in {@code
 * AfterEach} to ensure full test isolation.
 */
class LogFileActivatorTest {

    private Level savedRootLevel;

    @BeforeEach
    void saveRootLoggerState() {
        savedRootLevel = rootLogger().getLevel();
    }

    @AfterEach
    void restoreRootLoggerState() {
        ch.qos.logback.classic.Logger root = rootLogger();
        root.detachAndStopAllAppenders();
        root.setLevel(savedRootLevel);
    }

    // --- No-op cases ---

    @Test
    void nullLogLevelSkipsActivation(@TempDir Path tempDir) {
        LogFileActivator.activate(null, tempDir.toString());

        assertThat(rootLogger().getLevel()).isEqualTo(savedRootLevel);
        assertThat(logFilesIn(tempDir)).isEmpty();
    }

    @Test
    void nullLogDirSkipsActivation() {
        LogFileActivator.activate("DEBUG", null);

        assertThat(rootLogger().getLevel()).isEqualTo(savedRootLevel);
    }

    @Test
    void bothNullSkipsActivation() {
        LogFileActivator.activate(null, null);

        assertThat(rootLogger().getLevel()).isEqualTo(savedRootLevel);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VERBOSE", "ALL", "OFF", "FINEST", "WARNING", "", "  "})
    void invalidLogLevelSkipsActivation(String level, @TempDir Path tempDir) {
        LogFileActivator.activate(level, tempDir.toString());

        assertThat(rootLogger().getLevel()).isEqualTo(savedRootLevel);
        assertThat(logFilesIn(tempDir)).isEmpty();
    }

    // --- Happy-path cases ---

    @Test
    void validLevelAndExistingDirCreatesLogFile(@TempDir Path tempDir) {
        LogFileActivator.activate("DEBUG", tempDir.toString());

        assertThat(logFilesIn(tempDir)).hasSize(1);
    }

    @Test
    void validLevelAndNonExistentSubdirCreatesDirectory(@TempDir Path tempDir) {
        Path subDir = tempDir.resolve("nested").resolve("logs");
        assertThat(subDir).doesNotExist();

        LogFileActivator.activate("INFO", subDir.toString());

        assertThat(subDir).isDirectory();
        assertThat(logFilesIn(subDir)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"})
    void allValidLevelsActivateLogging(String level, @TempDir Path tempDir) {
        LogFileActivator.activate(level, tempDir.toString());

        assertThat(logFilesIn(tempDir)).hasSize(1);
        assertThat(rootLogger().getLevel()).isEqualTo(Level.toLevel(level));
    }

    @ParameterizedTest
    @ValueSource(strings = {"debug", "Debug", "DEBUG", "iNfO", "warn", "Error", "trace"})
    void logLevelIsAcceptedCaseInsensitively(String level, @TempDir Path tempDir) {
        LogFileActivator.activate(level, tempDir.toString());

        assertThat(logFilesIn(tempDir)).hasSize(1);
    }

    @Test
    void rootLoggerLevelMatchesRequestedLevel(@TempDir Path tempDir) {
        LogFileActivator.activate("WARN", tempDir.toString());

        assertThat(rootLogger().getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void rootLoggerHasExactlyOneFileAppenderAfterActivation(@TempDir Path tempDir) {
        LogFileActivator.activate("DEBUG", tempDir.toString());

        List<Appender<ILoggingEvent>> appenders = collectRootAppenders();
        assertThat(appenders).hasSize(1);
        assertThat(appenders.get(0)).isInstanceOf(FileAppender.class);
    }

    @Test
    void fileAppenderPointsToCorrectDirectory(@TempDir Path tempDir) {
        LogFileActivator.activate("DEBUG", tempDir.toString());

        List<Appender<ILoggingEvent>> appenders = collectRootAppenders();
        assertThat(appenders).hasSize(1);
        FileAppender<ILoggingEvent> fa = (FileAppender<ILoggingEvent>) appenders.get(0);
        assertThat(Path.of(fa.getFile()).getParent()).isEqualTo(tempDir);
    }

    @Test
    void logFileNameMatchesTimestampPattern(@TempDir Path tempDir) throws IOException {
        LogFileActivator.activate("DEBUG", tempDir.toString());

        List<Path> files = logFilesIn(tempDir);
        assertThat(files).hasSize(1);
        String name = files.get(0).getFileName().toString();
        // cli_log_yyyy_MM_dd_HHmmss.log
        assertThat(name).matches("cli_log_\\d{4}_\\d{2}_\\d{2}_\\d{6}\\.log");
    }

    @Test
    void consoleLevelIsNotAffectedWhenBothVarsAbsent() {
        // No activation; root level must remain unchanged — verifies no side effects
        LogFileActivator.activate(null, null);

        assertThat(rootLogger().getLevel()).isEqualTo(savedRootLevel);
    }

    @Test
    void explicitOffOverridesOnPackageLoggerAreClearedOnActivation(@TempDir Path tempDir) {
        // Simulate what Spring Boot does when application.properties contains
        // "logging.level.io.github.snytkine.apitester=OFF" — the package logger gets an
        // explicit OFF that would otherwise hide the "Application Started" message.
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger appLogger = ctx.getLogger("io.github.snytkine.apitester");
        appLogger.setLevel(Level.OFF);

        LogFileActivator.activate("DEBUG", tempDir.toString());

        // The explicit OFF must have been cleared; effective level should now be DEBUG (from root).
        assertThat(appLogger.getEffectiveLevel()).isEqualTo(Level.DEBUG);
    }

    @Test
    void jlineAndProcessBuilderLoggersArePinnedToWarnAfterActivation(@TempDir Path tempDir) {
        LogFileActivator.activate("DEBUG", tempDir.toString());

        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        assertThat(ctx.getLogger("org.jline").getLevel()).isEqualTo(Level.WARN);
        assertThat(ctx.getLogger("java.lang.ProcessBuilder").getLevel()).isEqualTo(Level.WARN);
    }

    // --- Failure cases ---

    @Test
    void dirCreationFailureSkipsActivation(@TempDir Path tempDir) throws IOException {
        // Create a regular FILE at the parent path so createDirectories() throws
        Path blockingFile = tempDir.resolve("not_a_dir.txt");
        Files.writeString(blockingFile, "block");
        String impossibleDir = blockingFile.resolve("subdir").toString();

        LogFileActivator.activate("DEBUG", impossibleDir);

        assertThat(rootLogger().getLevel()).isEqualTo(savedRootLevel);
    }

    // --- Valid-levels set contract ---

    @Test
    void validLevelsSetContainsExactlyFiveStandardSlf4jLevels() {
        assertThat(LogFileActivator.VALID_LEVELS).containsExactlyInAnyOrder("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
    }

    // --- Helpers ---

    private static ch.qos.logback.classic.Logger rootLogger() {
        return ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
    }

    private static List<Appender<ILoggingEvent>> collectRootAppenders() {
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        rootLogger().iteratorForAppenders().forEachRemaining(appenders::add);
        return appenders;
    }

    private static List<Path> logFilesIn(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().startsWith("cli_log_"))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
