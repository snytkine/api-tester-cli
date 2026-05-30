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
package io.github.snytkine.apitester.api_tester_cli.ui;

import org.jspecify.annotations.Nullable;

/**
 * Utility class that detects terminal capabilities and determines whether the interactive terminal
 * UI should be activated for a given run.
 *
 * <p>The primary entry point is {@link #shouldUseUi(boolean, boolean)}. A lower-level overload
 * {@link #shouldUseUi(boolean, boolean, boolean, boolean, boolean, int)} accepts all capability
 * inputs directly and is fully testable without environment manipulation.
 *
 * <p>Detection rules (evaluated in order):
 *
 * <ol>
 *   <li>If {@code --ui} was passed, always return {@code true} (force-enable).
 *   <li>If {@code --no-ui} was passed, always return {@code false}.
 *   <li>If stdout is not attached to a TTY ({@link #isTty()} returns {@code false}), return {@code
 *       false}.
 *   <li>If the {@code NO_COLOR} environment variable is set (per <a
 *       href="https://no-color.org">no-color.org</a>), return {@code false}.
 *   <li>If the {@code CI} environment variable is set (many CI systems render escape codes
 *       literally), return {@code false}.
 *   <li>If the detected terminal width is below {@link #MIN_TERMINAL_WIDTH}, return {@code false}.
 *   <li>Otherwise return {@code true}.
 * </ol>
 *
 * <p>Thread-safe: all methods are stateless and read-only with respect to shared state.
 */
public final class TtyDetector {

  /** Minimum terminal column count below which the UI is considered unreadable. */
  public static final int MIN_TERMINAL_WIDTH = 40;

  private TtyDetector() {}

  /**
   * Returns {@code true} when stdout is attached to an interactive terminal.
   *
   * <p>Uses {@link System#console()} as the detection mechanism; this returns {@code null} when
   * stdout is redirected to a file or pipe.
   *
   * @return {@code true} if a console is available
   */
  public static boolean isTty() {
    return System.console() != null;
  }

  /**
   * Returns {@code true} when the terminal is expected to support ANSI colour sequences.
   *
   * <p>Returns {@code false} when the {@code NO_COLOR} environment variable is set to any value,
   * per the <a href="https://no-color.org">no-color.org</a> convention.
   *
   * @return {@code true} if colour output is permitted
   */
  public static boolean supportsColor() {
    return System.getenv("NO_COLOR") == null;
  }

  /**
   * Returns {@code true} when the process appears to be running inside a CI environment.
   *
   * <p>Many CI systems set the {@code CI} environment variable. Because some of them attach a
   * pseudo-TTY but still render ANSI escape codes literally, the UI defaults to disabled in CI
   * unless the user passes {@code --ui} explicitly.
   *
   * @return {@code true} if the {@code CI} environment variable is present
   */
  public static boolean isCI() {
    return System.getenv("CI") != null;
  }

  /**
   * Returns the terminal width in columns.
   *
   * <p>Reads the {@code COLUMNS} environment variable first; falls back to {@code 80} when the
   * variable is absent or unparseable. The caller is responsible for comparing this value against
   * {@link #MIN_TERMINAL_WIDTH}.
   *
   * @return terminal width in columns, or {@code 80} if unknown
   */
  public static int getTerminalWidth() {
    return parseColumns(System.getenv("COLUMNS"));
  }

  /**
   * Pure-logic overload used by tests. All environmental inputs are supplied by the caller, making
   * this method deterministic and environment-independent.
   *
   * @param forceUi {@code true} when the {@code --ui} flag was passed
   * @param noUi {@code true} when the {@code --no-ui} flag was passed
   * @param hasTty {@code true} when stdout is attached to a TTY
   * @param hasNoColor {@code true} when the {@code NO_COLOR} env var is set
   * @param isCi {@code true} when the {@code CI} env var is set
   * @param terminalWidth terminal width in columns
   * @return {@code true} if the terminal UI should be activated
   */
  static boolean shouldUseUi(
      boolean forceUi,
      boolean noUi,
      boolean hasTty,
      boolean hasNoColor,
      boolean isCi,
      int terminalWidth) {
    if (forceUi) return true;
    if (noUi) return false;
    if (!hasTty) return false;
    if (hasNoColor) return false;
    if (isCi) return false;
    return terminalWidth >= MIN_TERMINAL_WIDTH;
  }

  /**
   * Determines whether the interactive terminal UI should be activated for the current run.
   *
   * <p>Reads environment variables and the console state from the running JVM and delegates to
   * {@link #shouldUseUi(boolean, boolean, boolean, boolean, boolean, int)}.
   *
   * @param forceUi {@code true} when the user passed {@code --ui} on the command line
   * @param noUi {@code true} when the user passed {@code --no-ui} on the command line
   * @return {@code true} if all conditions for activating the terminal UI are met
   */
  public static boolean shouldUseUi(boolean forceUi, boolean noUi) {
    return shouldUseUi(
        forceUi,
        noUi,
        isTty(),
        !supportsColor(),
        isCI(),
        getTerminalWidth());
  }

  /**
   * Parses the {@code COLUMNS} env-var value into an integer, returning {@code 80} on failure.
   *
   * @param columns raw value of the {@code COLUMNS} environment variable; may be {@code null}
   * @return parsed column count, or {@code 80} if {@code columns} is null or not a valid integer
   */
  static int parseColumns(@Nullable String columns) {
    if (columns == null) return 80;
    try {
      return Integer.parseInt(columns.trim());
    } catch (NumberFormatException e) {
      return 80;
    }
  }
}
