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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.github.snytkine.apitester.api_tester_cli.event.TestProgressEvent;
import io.github.snytkine.apitester.api_tester_cli.event.TestStatus;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;

class TerminalUiListenerTest {

    @Test
    void onProgressEnqueuesSuiteStarted() {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiListener listener = new TerminalUiListener(queue);
        TestProgressEvent event = new TestProgressEvent.SuiteStarted("suite", 3, Instant.now());

        listener.onProgress(event);

        assertThat(queue.poll()).isSameAs(event);
    }

    @Test
    void onProgressEnqueuesTestStarted() {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiListener listener = new TerminalUiListener(queue);
        TestProgressEvent event = new TestProgressEvent.TestStarted(0, "my-test");

        listener.onProgress(event);

        assertThat(queue.poll()).isSameAs(event);
    }

    @Test
    void onProgressEnqueuesTestCompleted() {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiListener listener = new TerminalUiListener(queue);
        TestProgressEvent event = new TestProgressEvent.TestCompleted(0, "my-test", TestStatus.PASS, 50L, null);

        listener.onProgress(event);

        assertThat(queue.poll()).isSameAs(event);
    }

    @Test
    void onProgressEnqueuesSuiteCompleted() {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiListener listener = new TerminalUiListener(queue);
        TestProgressEvent event = new TestProgressEvent.SuiteCompleted(2, 1, 300L);

        listener.onProgress(event);

        assertThat(queue.poll()).isSameAs(event);
    }

    @Test
    void multipleEventsAreEnqueuedInOrder() {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiListener listener = new TerminalUiListener(queue);

        TestProgressEvent e1 = new TestProgressEvent.SuiteStarted("suite", 1, Instant.now());
        TestProgressEvent e2 = new TestProgressEvent.TestStarted(0, "test");
        TestProgressEvent e3 = new TestProgressEvent.TestCompleted(0, "test", TestStatus.PASS, 100L, null);
        TestProgressEvent e4 = new TestProgressEvent.SuiteCompleted(1, 0, 100L);

        listener.onProgress(e1);
        listener.onProgress(e2);
        listener.onProgress(e3);
        listener.onProgress(e4);

        assertThat(queue.poll()).isSameAs(e1);
        assertThat(queue.poll()).isSameAs(e2);
        assertThat(queue.poll()).isSameAs(e3);
        assertThat(queue.poll()).isSameAs(e4);
        assertThat(queue).isEmpty();
    }

    @Test
    void onProgressIsNonBlockingAndDoesNotThrow() {
        LinkedBlockingQueue<TestProgressEvent> queue = new LinkedBlockingQueue<>();
        TerminalUiListener listener = new TerminalUiListener(queue);
        TestProgressEvent event = new TestProgressEvent.SuiteCompleted(0, 0, 0L);

        assertThatNoException().isThrownBy(() -> listener.onProgress(event));
    }
}
