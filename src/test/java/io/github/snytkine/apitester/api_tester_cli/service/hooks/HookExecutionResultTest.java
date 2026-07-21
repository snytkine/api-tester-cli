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

import org.junit.jupiter.api.Test;

/** Unit tests for {@link HookExecutionResult} factory methods. */
class HookExecutionResultTest {

    @Test
    void successFactory() {
        HookExecutionResult r = HookExecutionResult.success(200, 42);
        assertThat(r.success()).isTrue();
        assertThat(r.exitCodeOrStatus()).isEqualTo(200);
        assertThat(r.durationMs()).isEqualTo(42);
        assertThat(r.timedOut()).isFalse();
        assertThat(r.errorMessage()).isNull();
    }

    @Test
    void failureFactory() {
        HookExecutionResult r = HookExecutionResult.failure(7, 10, "boom");
        assertThat(r.success()).isFalse();
        assertThat(r.exitCodeOrStatus()).isEqualTo(7);
        assertThat(r.timedOut()).isFalse();
        assertThat(r.errorMessage()).isEqualTo("boom");
    }

    @Test
    void timedOutFactory() {
        HookExecutionResult r = HookExecutionResult.timedOut(1000, "too slow");
        assertThat(r.success()).isFalse();
        assertThat(r.timedOut()).isTrue();
        assertThat(r.exitCodeOrStatus()).isEqualTo(HookExecutionResult.NO_STATUS);
        assertThat(r.errorMessage()).isEqualTo("too slow");
    }
}
