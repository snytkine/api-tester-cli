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
package io.github.snytkine.apitester.api_tester_cli.event;

/**
 * Observer that receives {@link TestProgressEvent}s as a test suite executes.
 *
 * <p>Implementations must be <strong>thread-safe</strong>: in the parallel execution model multiple
 * test worker threads may call {@link #onProgress} concurrently. Callers never block waiting for
 * this method to return; implementations should therefore be non-blocking (e.g. enqueue the event
 * and return immediately).
 *
 * <p>This is a {@link FunctionalInterface} so lambda or method-reference implementations can be
 * used in tests.
 */
@FunctionalInterface
public interface TestProgressListener {

    /**
     * Called by the test runner when a progress milestone is reached.
     *
     * @param event the event describing the milestone; never {@code null}
     */
    void onProgress(TestProgressEvent event);
}
