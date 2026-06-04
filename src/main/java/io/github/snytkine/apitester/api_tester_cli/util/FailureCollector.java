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
import org.jspecify.annotations.Nullable;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

/**
 * Native-image-safe soft-assertions collector that accumulates {@link AssertionFailedError}
 * instances and can throw them all at once.
 *
 * <p>AssertJ's {@code SoftAssertions} uses Byte Buddy to generate dynamic proxies at runtime. Byte
 * Buddy requires a live JVM to emit bytecode, which is fundamentally incompatible with GraalVM
 * native images. This class provides the same soft-assertion semantics — collect all failures and
 * throw once at the end — using only {@link AssertionFailedError} instances, with no Byte Buddy
 * involvement.
 *
 * <p>Each {@link AssertionFailedError} carries a human-readable message and, where applicable,
 * structured {@code expected} and {@code actual} values for use in failure reports. The static
 * {@link #rewrap(String, AssertionError)} helper assists evaluators in merging AssertJ-generated
 * expected/actual into a new error carrying the evaluator's own message.
 *
 * <p>Instances are not thread-safe and must be created per test-case invocation on the call stack.
 */
public final class FailureCollector {

    private final List<AssertionFailedError> failures = new ArrayList<>();

    /**
     * Records the given {@link AssertionFailedError} directly, preserving its message, expected,
     * and actual fields.
     *
     * @param e the failure to record
     */
    public void fail(AssertionFailedError e) {
        failures.add(e);
    }

    /**
     * Records a failure with the given message and optional cause. A new {@link
     * AssertionFailedError} is created with no structured expected/actual fields.
     *
     * @param message the failure description
     * @param cause the underlying exception, or {@code null} if there is none
     */
    public void fail(String message, @Nullable Throwable cause) {
        failures.add(new AssertionFailedError(message, cause));
    }

    /**
     * Returns the list of failures recorded so far. The list is live; callers must not modify it.
     *
     * @return the current failure list
     */
    public List<AssertionFailedError> getFailures() {
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

    /**
     * Creates a new {@link AssertionFailedError} with the given message, copying the structured
     * {@code expected} and {@code actual} from {@code caught} when it is an {@link
     * AssertionFailedError} with both fields defined (i.e., when AssertJ provided them via an
     * equality check). When {@code caught} is a plain {@link AssertionError} or an {@link
     * AssertionFailedError} without structured fields, the returned error carries only the message.
     *
     * @param message the human-readable failure description to use as the error message
     * @param caught the AssertJ assertion error caught by the evaluator
     * @return a new {@link AssertionFailedError}
     */
    public static AssertionFailedError rewrap(String message, AssertionError caught) {
        if (caught instanceof AssertionFailedError afe && afe.isExpectedDefined() && afe.isActualDefined()) {
            return new AssertionFailedError(
                    message, afe.getExpected().getValue(), afe.getActual().getValue());
        }
        return new AssertionFailedError(message);
    }
}
