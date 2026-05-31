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

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

/**
 * Native-image-safe soft-assertions collector.
 *
 * <p>AssertJ's {@code SoftAssertions} uses Byte Buddy to generate dynamic proxies at runtime. Byte
 * Buddy requires a live JVM to emit bytecode, which is fundamentally incompatible with GraalVM
 * native images. This class provides the same soft-assertion semantics — collect all failures and
 * throw once at the end — using only standard (non-proxy) AssertJ assertions wrapped in try-catch
 * blocks, with no Byte Buddy involvement.
 *
 * <p>Instances are not thread-safe and must be created per test-case invocation on the call stack.
 */
public final class FailureCollector {

    private final List<Throwable> failures = new ArrayList<>();

    /**
     * Records an unconditional failure with the given message.
     *
     * @param message the failure description; may contain {@link String#format} placeholders when
     *     {@code args} are supplied
     * @param args optional format arguments
     */
    public void fail(String message, Object... args) {
        String formatted = args.length == 0 ? message : String.format(message, args);
        failures.add(new AssertionFailedError(formatted));
    }

    /**
     * Returns a pending string assertion whose failures are recorded rather than thrown immediately.
     *
     * @param actual the string value under test
     * @return a {@link StringAssertion} for chaining
     */
    public StringAssertion assertThat(String actual) {
        return new StringAssertion(actual, this);
    }

    /**
     * Returns a pending integer assertion whose failures are recorded rather than thrown immediately.
     *
     * @param actual the integer value under test
     * @return an {@link IntAssertion} for chaining
     */
    public IntAssertion assertThat(int actual) {
        return new IntAssertion(actual, this);
    }

    /**
     * Returns the list of failures recorded so far. The list is live; callers must not modify it.
     *
     * @return the current failure list
     */
    public List<Throwable> getFailures() {
        return failures;
    }

    /**
     * Throws {@link MultipleFailuresError} if any failures have been recorded; otherwise returns
     * normally.
     *
     * @throws MultipleFailuresError when at least one failure was collected
     */
    public void assertAll() {
        if (!failures.isEmpty()) {
            throw new MultipleFailuresError("", failures);
        }
    }

    private void addFailure(String description, AssertionError e) {
        if (description != null) {
            failures.add(new AssertionFailedError("[" + description + "] " + e.getMessage()));
        } else {
            failures.add(e);
        }
    }

    /**
     * Fluent string assertion that records failures into the enclosing {@link FailureCollector}
     * instead of throwing immediately.
     *
     * <p>Call {@link #as(String, Object...)} before the terminal method to attach a context label to
     * the failure message.
     */
    public static final class StringAssertion {

        private final String actual;
        private final FailureCollector collector;
        private String description;

        private StringAssertion(String actual, FailureCollector collector) {
            this.actual = actual;
            this.collector = collector;
        }

        /**
         * Attaches a context label to any failure produced by the terminal method.
         *
         * @param description the label; may contain {@link String#format} placeholders
         * @param args optional format arguments
         * @return this assertion for chaining
         */
        public StringAssertion as(String description, Object... args) {
            this.description = args.length == 0 ? description : String.format(description, args);
            return this;
        }

        /**
         * Asserts that the actual value equals {@code expected}, recording a failure if not.
         *
         * @param expected the expected string
         */
        public void isEqualTo(String expected) {
            try {
                applyDescription(Assertions.assertThat(actual)).isEqualTo(expected);
            } catch (AssertionError e) {
                collector.addFailure(description, e);
            }
        }

        /**
         * Asserts that the actual value equals {@code expected} ignoring case, recording a failure if
         * not.
         *
         * @param expected the expected string
         */
        public void isEqualToIgnoringCase(String expected) {
            try {
                applyDescription(Assertions.assertThat(actual)).isEqualToIgnoringCase(expected);
            } catch (AssertionError e) {
                collector.addFailure(description, e);
            }
        }

        /**
         * Asserts that the actual value contains {@code expected} as a substring, recording a failure
         * if not.
         *
         * @param expected the expected substring
         */
        public void contains(String expected) {
            try {
                applyDescription(Assertions.assertThat(actual)).contains(expected);
            } catch (AssertionError e) {
                collector.addFailure(description, e);
            }
        }

        /**
         * Asserts that the actual value contains {@code expected} as a substring ignoring case,
         * recording a failure if not.
         *
         * @param expected the expected substring
         */
        public void containsIgnoringCase(String expected) {
            try {
                applyDescription(Assertions.assertThat(actual)).containsIgnoringCase(expected);
            } catch (AssertionError e) {
                collector.addFailure(description, e);
            }
        }

        private org.assertj.core.api.AbstractStringAssert<?> applyDescription(
                org.assertj.core.api.AbstractStringAssert<?> assertion) {
            return description != null ? assertion.as(description) : assertion;
        }
    }

    /**
     * Fluent integer assertion that records failures into the enclosing {@link FailureCollector}
     * instead of throwing immediately.
     */
    public static final class IntAssertion {

        private final int actual;
        private final FailureCollector collector;
        private String description;

        private IntAssertion(int actual, FailureCollector collector) {
            this.actual = actual;
            this.collector = collector;
        }

        /**
         * Attaches a context label to any failure produced by the terminal method.
         *
         * @param description the label; may contain {@link String#format} placeholders
         * @param args optional format arguments
         * @return this assertion for chaining
         */
        public IntAssertion as(String description, Object... args) {
            this.description = args.length == 0 ? description : String.format(description, args);
            return this;
        }

        /**
         * Asserts that the actual value equals {@code expected}, recording a failure if not.
         *
         * @param expected the expected integer
         */
        public void isEqualTo(int expected) {
            try {
                org.assertj.core.api.AbstractIntegerAssert<?> assertion = Assertions.assertThat(actual);
                if (description != null) {
                    assertion = assertion.as(description);
                }
                assertion.isEqualTo(expected);
            } catch (AssertionError e) {
                collector.addFailure(description, e);
            }
        }
    }
}
