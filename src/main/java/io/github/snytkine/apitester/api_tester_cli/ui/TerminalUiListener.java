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

import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.event.TestProgressListener;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Non-blocking {@link TestProgressListener} that enqueues every event into a {@link
 * LinkedBlockingQueue} shared with a {@link TerminalUiController}.
 *
 * <p>Test worker threads (producers) are never blocked: {@link LinkedBlockingQueue#offer(Object)}
 * is used rather than {@code put}, so an unexpectedly slow render thread can never stall a test.
 * Events are tiny immutable records; dropping them is theoretically possible only if the queue were
 * bounded and full, which cannot happen with an unbounded {@link LinkedBlockingQueue}.
 *
 * <p>Thread-safety: this class is thread-safe. {@link LinkedBlockingQueue#offer} is internally
 * synchronized and safe for concurrent invocations from multiple producer threads.
 */
public final class TerminalUiListener implements TestProgressListener {

    private final LinkedBlockingQueue<TestProgressEvent> queue;

    /**
     * Constructs the listener with the shared event queue.
     *
     * @param queue the blocking queue shared with a {@link TerminalUiController}; must not be null
     */
    public TerminalUiListener(LinkedBlockingQueue<TestProgressEvent> queue) {
        this.queue = queue;
    }

    /**
     * Enqueues {@code event} into the shared queue without blocking.
     *
     * <p>If the queue were to reject the offer (not possible with an unbounded {@link
     * LinkedBlockingQueue}), the event would be silently dropped rather than blocking the caller.
     *
     * @param event the progress event to enqueue; must not be null
     */
    @Override
    public void onProgress(TestProgressEvent event) {
        queue.offer(event);
    }
}
