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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-run registry of asynchronously-dispatched lifecycle hooks. Created once per suite run (never a
 * Spring singleton), it owns a small daemon thread pool onto which {@code async} hooks are submitted
 * and tracks their {@link Future}s so the run can wait for them before the CLI exits.
 *
 * <p>Because each hook bounds itself with its own timeout, waiting here can never hang: {@link
 * #awaitAll()} simply joins the already-time-bounded tasks. This type is thread-safe — the futures
 * list is copy-on-write and the executor is concurrent — so hooks dispatched from any phase may
 * register concurrently.
 */
public final class AsyncHookHandles implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncHookHandles.class);

    private final ExecutorService executor;
    private final List<Future<?>> futures = new CopyOnWriteArrayList<>();

    /** Creates a registry backed by a cached daemon thread pool. */
    public AsyncHookHandles() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "async-hook");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Submits an async hook task to the pool and tracks it.
     *
     * @param task the hook execution task (already bounded by the hook's own timeout)
     */
    public void submit(Runnable task) {
        futures.add(executor.submit(task));
    }

    /**
     * Returns the number of async hooks still running (not yet completed).
     *
     * @return the count of outstanding async hook tasks
     */
    public long runningCount() {
        return futures.stream().filter(f -> !f.isDone()).count();
    }

    /** Blocks until every submitted async hook task has finished. */
    public void awaitAll() {
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // The hook task never throws (it returns a result and logs its own warnings); any
                // unexpected throwable is logged and swallowed so one bad task can't fail the run.
                log.debug("Async hook task ended exceptionally: {}", e.getMessage());
            }
        }
    }

    /** Awaits all outstanding tasks, then shuts the pool down. */
    @Override
    public void close() {
        awaitAll();
        executor.shutdownNow();
    }
}
