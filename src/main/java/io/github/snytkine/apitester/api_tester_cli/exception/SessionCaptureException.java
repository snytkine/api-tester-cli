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
package io.github.snytkine.apitester.api_tester_cli.exception;

/**
 * Thrown when a {@code saved-session} capture cannot be satisfied for a test case.
 *
 * <p>Three conditions raise this exception:
 *
 * <ul>
 *   <li>a required capture ({@code required: true} with no {@code default}) extracts no value;
 *   <li>the extracted value is a JSON object or array rather than a primitive (session values may
 *       only hold primitives);
 *   <li>the extracted primitive cannot be coerced to the declared {@code type}.
 * </ul>
 *
 * <p>This is an internal control-flow signal within {@link
 * io.github.snytkine.apitester.api_tester_cli.service.PureJavaTestEngine}: it is raised while
 * capturing a test's response values and caught by the run loop, which records the owning test as
 * a failure whose message is this exception's message.
 *
 * <p>Thread-safety: immutable after construction; safe to read from any thread.
 */
public class SessionCaptureException extends RuntimeException {

    /**
     * Constructs a {@code SessionCaptureException} with the given human-readable failure message.
     *
     * @param message describes which capture failed and why; surfaced as the test's failure message
     */
    public SessionCaptureException(String message) {
        super(message);
    }
}
