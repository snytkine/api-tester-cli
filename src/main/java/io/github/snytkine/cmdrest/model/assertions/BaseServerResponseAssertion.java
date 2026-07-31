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
package io.github.snytkine.cmdrest.model.assertions;

/**
 * Implicit assertion, added automatically to <em>every</em> test case, that passes when the request
 * was dispatched and any HTTP response came back before the client's timeout elapsed.
 *
 * <p>Only the fact that a response was received matters: the status code, headers and body are
 * irrelevant, so a {@code 500} response passes this assertion just as a {@code 200} does. It fails
 * only when no response could be obtained at all — connection refused, unknown host, TLS handshake
 * failure, or a connection that timed out.
 *
 * <p>This type is never declared in a test-suite YAML. It is deliberately absent from the {@code
 * @JsonSubTypes} list on {@link Assertion} (and from the JSON schema), so it cannot be deserialized
 * from a suite file; the engine injects one instance per test case at execution time.
 *
 * @param timeoutSeconds the effective timeout, in seconds, of the rest-client dispatching the
 *     request — reported verbatim in {@link #expectedDescription()}
 */
public record BaseServerResponseAssertion(int timeoutSeconds) implements Assertion {

    /** Name under which this assertion is reported in failure tables and HTML reports. */
    public static final String TYPE_NAME = "base_server_response";

    /**
     * Builds the {@code expected} text shown for this assertion in the terminal failure table and
     * the HTML report.
     *
     * @return the string {@code "service must respond within default timeout of <n> seconds"}
     */
    public String expectedDescription() {
        return "service must respond within default timeout of " + timeoutSeconds + " seconds";
    }
}
