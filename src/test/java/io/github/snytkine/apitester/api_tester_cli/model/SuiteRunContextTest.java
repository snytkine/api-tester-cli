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
package io.github.snytkine.apitester.api_tester_cli.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SuiteRunContext}, focusing on the auto-generated {@code runID} field and
 * the preservation of the variable-namespace accessors.
 */
class SuiteRunContextTest {

    /** Verifies that {@link SuiteRunContext#getRunID()} returns a non-blank identifier. */
    @Test
    void getRunIdReturnsNonBlankValue() {
        SuiteRunContext context = SuiteRunContext.of(Map.of(), Map.of());

        assertThat(context.getRunID()).isNotNull().isNotBlank();
    }

    /** Verifies that the generated {@code runID} is a valid UUID string. */
    @Test
    void getRunIdIsAValidUuid() {
        SuiteRunContext context = SuiteRunContext.of(Map.of(), Map.of());

        // Throws IllegalArgumentException if the value is not a valid UUID.
        UUID parsed = UUID.fromString(context.getRunID());

        assertThat(parsed.toString()).isEqualTo(context.getRunID());
    }

    /** Verifies that the {@code runID} does not change across repeated calls on the same instance. */
    @Test
    void getRunIdIsStablePerInstance() {
        SuiteRunContext context = SuiteRunContext.of(Map.of(), Map.of());

        assertThat(context.getRunID()).isEqualTo(context.getRunID());
    }

    /** Verifies that two separately constructed instances receive different run IDs. */
    @Test
    void differentInstancesHaveDifferentRunIds() {
        SuiteRunContext first = SuiteRunContext.of(Map.of(), Map.of());
        SuiteRunContext second = SuiteRunContext.of(Map.of(), Map.of());

        assertThat(first.getRunID()).isNotEqualTo(second.getRunID());
    }

    /**
     * Verifies that a large number of instances created via the four-argument constructor all
     * receive unique run IDs.
     */
    @Test
    void manyInstancesAllHaveUniqueRunIds() {
        int count = 10_000;
        Set<String> runIds = new HashSet<>();
        for (int i = 0; i < count; i++) {
            runIds.add(new SuiteRunContext(Map.of(), Map.of(), Map.of(), Map.of()).getRunID());
        }

        assertThat(runIds).hasSize(count);
    }

    /**
     * Verifies that run IDs remain unique even when many instances are created concurrently from
     * multiple threads, confirming thread-safe generation.
     */
    @Test
    void concurrentlyCreatedInstancesHaveUniqueRunIds() throws Exception {
        int threads = 16;
        int perThread = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            Set<String> runIds = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
            Future<?>[] futures = new Future<?>[threads];
            for (int t = 0; t < threads; t++) {
                futures[t] = pool.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        runIds.add(SuiteRunContext.of(Map.of(), Map.of()).getRunID());
                    }
                });
            }
            for (Future<?> future : futures) {
                future.get();
            }

            assertThat(runIds).hasSize(threads * perThread);
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /** Verifies that the four variable-namespace accessors expose the supplied maps. */
    @Test
    void accessorsExposeSuppliedMaps() {
        SuiteRunContext context =
                new SuiteRunContext(Map.of("E", "1"), Map.of("C", "2"), Map.of("S", "3"), Map.of("T", "4"));

        assertThat(context.env()).containsEntry("E", "1");
        assertThat(context.cli()).containsEntry("C", "2");
        assertThat(context.suite()).containsEntry("S", "3");
        assertThat(context.test()).containsEntry("T", "4");
    }

    /** Verifies that the maps returned by the accessors are unmodifiable defensive copies. */
    @Test
    void accessorMapsAreUnmodifiable() {
        SuiteRunContext context = SuiteRunContext.of(Map.of("k", "v"), Map.of());

        assertThrows(UnsupportedOperationException.class, () -> context.env().put("x", "y"));
    }

    /** Verifies that {@link SuiteRunContext#of(Map, Map)} initializes suite and test as empty. */
    @Test
    void ofFactoryLeavesSuiteAndTestEmpty() {
        SuiteRunContext context = SuiteRunContext.of(Map.of("e", "1"), Map.of("c", "2"));

        assertThat(context.suite()).isEmpty();
        assertThat(context.test()).isEmpty();
    }

    /** Verifies that {@code toString} contains the run ID but not raw variable values. */
    @Test
    void toStringContainsRunIdAndOmitsSecrets() {
        SuiteRunContext context = SuiteRunContext.of(Map.of("token", "super-secret"), Map.of());

        String text = context.toString();

        assertThat(text).contains(context.getRunID());
        assertThat(text).doesNotContain("super-secret");
    }
}
