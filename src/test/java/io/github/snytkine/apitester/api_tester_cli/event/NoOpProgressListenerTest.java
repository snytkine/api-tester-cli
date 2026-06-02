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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link NoOpProgressListener}. */
class NoOpProgressListenerTest {

    @Test
    void instanceIsSingleton() {
        assertThat(NoOpProgressListener.INSTANCE).isSameAs(NoOpProgressListener.INSTANCE);
    }

    @Test
    void onProgressDoesNotThrowForAnyEvent() {
        NoOpProgressListener listener = NoOpProgressListener.INSTANCE;

        assertThatNoException().isThrownBy(() -> {
            listener.onProgress(new TestProgressEvent.SuiteStarted("s", 2, Instant.now()));
            listener.onProgress(new TestProgressEvent.TestStarted("0", 0, "t1"));
            listener.onProgress(new TestProgressEvent.TestCompleted("0", 0, "t1", TestStatus.PASS, 10L, 2, List.of()));
            listener.onProgress(new TestProgressEvent.TestStarted("1", 1, "t2"));
            listener.onProgress(new TestProgressEvent.TestCompleted(
                    "1", 1, "t2", TestStatus.FAIL, 20L, 2, List.of("assertion failed")));
            listener.onProgress(new TestProgressEvent.SuiteCompleted(1L, 1L, 0L, 0L, 30L));
        });
    }

    @Test
    void implementsTestProgressListener() {
        assertThat(NoOpProgressListener.INSTANCE).isInstanceOf(TestProgressListener.class);
    }
}
