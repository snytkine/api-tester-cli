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
package io.github.snytkine.apitester.api_tester_cli.service.hooks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AsyncHookHandles}. */
class AsyncHookHandlesTest {

    @Test
    void awaitAllWaitsForSubmittedTasks() {
        AtomicInteger counter = new AtomicInteger();
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            for (int i = 0; i < 5; i++) {
                handles.submit(() -> {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    counter.incrementAndGet();
                });
            }
            handles.awaitAll();
            assertThat(counter.get()).isEqualTo(5);
            assertThat(handles.runningCount()).isZero();
        }
    }

    @Test
    void runningCountReflectsOutstandingTasks() throws InterruptedException {
        AsyncHookHandles handles = new AsyncHookHandles();
        Object lock = new Object();
        synchronized (lock) {
            handles.submit(() -> {
                synchronized (lock) {
                    // Blocks until the test releases the lock.
                }
            });
            Thread.sleep(50);
            assertThat(handles.runningCount()).isEqualTo(1);
        }
        handles.close();
        assertThat(handles.runningCount()).isZero();
    }

    @Test
    void taskThrowingIsSwallowedByAwaitAll() {
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            handles.submit(() -> {
                throw new RuntimeException("boom");
            });
            handles.awaitAll(); // must not propagate
        }
    }

    @Test
    void awaitAllReturnsWhenInterrupted() throws InterruptedException {
        try (AsyncHookHandles handles = new AsyncHookHandles()) {
            handles.submit(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            Thread waiter = new Thread(handles::awaitAll);
            waiter.start();
            Thread.sleep(50);
            waiter.interrupt();
            waiter.join(1000);
            assertThat(waiter.isAlive()).isFalse();
        }
    }
}
