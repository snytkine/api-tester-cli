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
 * Silent {@link TestProgressListener} used when the terminal UI is disabled (non-TTY output, {@code
 * --no-ui} flag, or {@code NO_COLOR} environment variable set).
 *
 * <p>All events are silently discarded. The singleton {@link #INSTANCE} should be preferred over
 * constructing new instances.
 *
 * <p>Thread-safe: {@link #onProgress} contains no state and is safe to call from any thread.
 */
public final class NoOpProgressListener implements TestProgressListener {

    /** Shared singleton; avoids repeated allocation in the common non-UI path. */
    public static final NoOpProgressListener INSTANCE = new NoOpProgressListener();

    private NoOpProgressListener() {}

    /** Discards the event without any side effects. */
    @Override
    public void onProgress(TestProgressEvent event) {
        // intentionally no-op
    }
}
