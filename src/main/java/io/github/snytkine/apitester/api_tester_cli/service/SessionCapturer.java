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
package io.github.snytkine.apitester.api_tester_cli.service;

import io.github.snytkine.apitester.api_tester_cli.exception.SessionCaptureException;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.SavedSession;
import io.github.snytkine.apitester.api_tester_cli.model.SessionValueType;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies a test case's {@code saved-session} captures against its HTTP response, storing the
 * extracted primitive values into the suite-wide {@code session} namespace map.
 *
 * <p>Each capture resolves its {@link SavedSession#path()} via {@link ResponseValueExtractor} (so
 * captures support the same {@code response.statusCode} / {@code response.headers.<name>} / {@code
 * response.body.json.<jsonpath>} expressions as assertions), then:
 *
 * <ul>
 *   <li>rejects non-primitive results (JSON objects/arrays) — session values may only hold
 *       primitives;
 *   <li>coerces the primitive to the declared {@link SavedSession#type()} when one is given,
 *       rejecting values that cannot be converted;
 *   <li>falls back to {@link SavedSession#defaultValue()} when the path extracts nothing, and
 *       otherwise honours {@link SavedSession#required()} by failing when a required value is
 *       missing;
 *   <li>stores the canonical textual form under {@link SavedSession#name()} (last-write-wins on
 *       duplicate names) and logs the capture at {@code DEBUG} level.
 * </ul>
 *
 * <p>Hard failures (required-but-missing, non-primitive, or a failed type conversion) raise a {@link
 * SessionCaptureException} carrying a user-facing message; the engine records the owning test as a
 * failure.
 *
 * <p>All methods are static; this class is not instantiable. It holds no mutable state and is
 * inherently thread-safe. The caller owns the {@code sessionVars} map and is responsible for
 * confining it to a single suite-run call stack.
 */
public final class SessionCapturer {

    private static final Logger log = LoggerFactory.getLogger(SessionCapturer.class);

    private SessionCapturer() {}

    /**
     * Applies every capture in {@code captures} against {@code response}, mutating {@code
     * sessionVars} in place. Captures are processed in declared order; the first hard failure aborts
     * with a {@link SessionCaptureException} (so later captures for the same test are not applied).
     *
     * @param testName the owning test case's name (used only in error messages)
     * @param captures the test's {@code saved-session} definitions; may be {@code null} or empty
     * @param response the HTTP response to extract values from
     * @param sessionVars the mutable {@code session} namespace map to populate
     * @throws SessionCaptureException when a required value is missing, a non-primitive is extracted,
     *     or a value cannot be coerced to its declared type
     */
    public static void capture(
            String testName,
            @Nullable List<SavedSession> captures,
            ApiResponse response,
            Map<String, String> sessionVars) {
        if (captures == null || captures.isEmpty()) {
            return;
        }
        for (SavedSession capture : captures) {
            applyOne(testName, capture, response, sessionVars);
        }
    }

    /**
     * Applies a single capture, storing its resolved value into {@code sessionVars} or leaving the
     * map unchanged when the value is absent and not required.
     *
     * @param testName the owning test case's name
     * @param capture the capture definition
     * @param response the HTTP response to extract from
     * @param sessionVars the mutable {@code session} namespace map
     * @throws SessionCaptureException on a hard failure (see {@link #capture})
     */
    private static void applyOne(
            String testName, SavedSession capture, ApiResponse response, Map<String, String> sessionVars) {
        String name = capture.name();
        String path = capture.path();
        ResponseValueExtractor.Result result = ResponseValueExtractor.extract(response, path);

        @Nullable Object extracted;
        if (result instanceof ResponseValueExtractor.Result.Found found && found.value() != null) {
            extracted = found.value();
        } else {
            // Missing, Found(null), or an extraction Error all count as "no value".
            if (result instanceof ResponseValueExtractor.Result.Error error) {
                log.warn(
                        "Session parameter '{}' in test '{}' at path '{}': extraction error: {}",
                        name,
                        testName,
                        path,
                        error.message());
            }
            handleAbsent(testName, capture, sessionVars);
            return;
        }

        if (!isPrimitive(extracted)) {
            throw new SessionCaptureException(
                    "Extracted session parameter '%s' in test '%s' at path '%s' is not a primitive type"
                            .formatted(name, testName, path));
        }

        String stored = coerce(testName, capture, extracted);
        sessionVars.put(name, stored);
        log.debug("Captured session parameter '{}' = '{}' from test '{}' at path '{}'", name, stored, testName, path);
    }

    /**
     * Handles the case where a capture's path extracted no value: uses the declared default when
     * present, fails when the capture is required, or silently skips otherwise.
     *
     * @param testName the owning test case's name
     * @param capture the capture definition
     * @param sessionVars the mutable {@code session} namespace map
     * @throws SessionCaptureException when the capture is required and no default is set
     */
    private static void handleAbsent(String testName, SavedSession capture, Map<String, String> sessionVars) {
        if (capture.defaultValue() != null) {
            sessionVars.put(capture.name(), capture.defaultValue());
            log.debug(
                    "Session parameter '{}' not found in test '{}' at path '{}'; using default '{}'",
                    capture.name(),
                    testName,
                    capture.path(),
                    capture.defaultValue());
            return;
        }
        if (capture.required()) {
            throw new SessionCaptureException("Failed to extract session parameter '%s' from response at path '%s'"
                    .formatted(capture.name(), capture.path()));
        }
        log.debug(
                "Session parameter '{}' not found in test '{}' at path '{}'; skipped (optional, no default)",
                capture.name(),
                testName,
                capture.path());
    }

    /**
     * Returns whether {@code value} is a JSON primitive (string, number, or boolean). JSON objects
     * ({@code Map}) and arrays ({@code List}) are not primitives and may not be captured.
     *
     * @param value the extracted value (never {@code null})
     * @return {@code true} when {@code value} is a primitive, {@code false} otherwise
     */
    private static boolean isPrimitive(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    /**
     * Coerces a primitive {@code value} to the capture's declared {@link SessionValueType},
     * returning its canonical textual form. When no type is declared the value's textual
     * representation is returned unchanged.
     *
     * @param testName the owning test case's name (for error messages)
     * @param capture the capture definition
     * @param value the extracted primitive value
     * @return the canonical string to store in the {@code session} namespace
     * @throws SessionCaptureException when {@code value} cannot be coerced to the declared type
     */
    private static String coerce(String testName, SavedSession capture, Object value) {
        SessionValueType type = capture.type();
        if (type == null || type == SessionValueType.STRING) {
            return String.valueOf(value);
        }
        return switch (type) {
            case INTEGER -> toInteger(testName, capture, value);
            case DOUBLE -> toDouble(testName, capture, value);
            case BOOLEAN -> toBoolean(testName, capture, value);
            case STRING -> String.valueOf(value); // unreachable; handled above
        };
    }

    /**
     * Coerces a primitive value to a whole-number string, rejecting fractional numbers and
     * non-numeric strings.
     *
     * @param testName the owning test case's name
     * @param capture the capture definition
     * @param value the extracted primitive value
     * @return the value as a base-10 {@code long} string
     * @throws SessionCaptureException when the value is not an integer
     */
    private static String toInteger(String testName, SavedSession capture, Object value) {
        if (value instanceof Number num) {
            double d = num.doubleValue();
            if (Double.isFinite(d) && d == Math.floor(d)) {
                return Long.toString(num.longValue());
            }
            throw conversionError(testName, capture, value);
        }
        if (value instanceof String s) {
            try {
                return Long.toString(Long.parseLong(s.trim()));
            } catch (NumberFormatException e) {
                throw conversionError(testName, capture, value);
            }
        }
        throw conversionError(testName, capture, value);
    }

    /**
     * Coerces a primitive value to a floating-point string.
     *
     * @param testName the owning test case's name
     * @param capture the capture definition
     * @param value the extracted primitive value
     * @return the value as a {@code double} string
     * @throws SessionCaptureException when the value is not a number
     */
    private static String toDouble(String testName, SavedSession capture, Object value) {
        if (value instanceof Number num) {
            return Double.toString(num.doubleValue());
        }
        if (value instanceof String s) {
            try {
                return Double.toString(Double.parseDouble(s.trim()));
            } catch (NumberFormatException e) {
                throw conversionError(testName, capture, value);
            }
        }
        throw conversionError(testName, capture, value);
    }

    /**
     * Coerces a primitive value to a boolean string ({@code "true"}/{@code "false"}).
     *
     * @param testName the owning test case's name
     * @param capture the capture definition
     * @param value the extracted primitive value
     * @return {@code "true"} or {@code "false"}
     * @throws SessionCaptureException when the value is not a boolean literal
     */
    private static String toBoolean(String testName, SavedSession capture, Object value) {
        if (value instanceof Boolean b) {
            return b.toString();
        }
        if (value instanceof String s) {
            String t = s.trim();
            if (t.equalsIgnoreCase("true")) {
                return "true";
            }
            if (t.equalsIgnoreCase("false")) {
                return "false";
            }
        }
        throw conversionError(testName, capture, value);
    }

    /**
     * Builds a {@link SessionCaptureException} describing a failed type conversion.
     *
     * @param testName the owning test case's name
     * @param capture the capture definition
     * @param value the value that could not be converted
     * @return a ready-to-throw exception
     */
    private static SessionCaptureException conversionError(String testName, SavedSession capture, Object value) {
        return new SessionCaptureException(
                "Session parameter '%s' in test '%s' at path '%s' cannot be converted to %s: value '%s'"
                        .formatted(
                                capture.name(),
                                testName,
                                capture.path(),
                                capture.type().name().toLowerCase(),
                                value));
    }
}
