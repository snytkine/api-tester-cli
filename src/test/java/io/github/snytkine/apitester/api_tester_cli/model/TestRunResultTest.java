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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TestRunResult}. */
class TestRunResultTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void appliedOptionsIsEmptyByDefault() {
        TestRunResult result = new TestRunResult(1, 0, 0, 0, List.of(), Map.of());

        assertThat(result.appliedOptions()).isEmpty();
    }

    @Test
    void withAppliedOptionsStampsOptions() {
        TestRunResult result = new TestRunResult(1, 0, 0, 0, List.of(), Map.of());

        TestRunResult stamped = result.withAppliedOptions(Map.of("tag", "smoke"));

        assertThat(stamped.appliedOptions()).containsEntry("tag", "smoke");
    }

    @Test
    void withAppliedOptionsReturnsNewInstanceWithOriginalCountsUnchanged() {
        TestRunResult result = new TestRunResult(3, 1, 2, 0, List.of(), Map.of());

        TestRunResult stamped = result.withAppliedOptions(Map.of("tag", "smoke"));

        assertThat(stamped.passedCount()).isEqualTo(3);
        assertThat(stamped.failedCount()).isEqualTo(1);
        assertThat(stamped.skippedCount()).isEqualTo(2);
        assertThat(stamped.errorCount()).isEqualTo(0);
    }

    @Test
    void appliedOptionsAppearsInJsonOutput() throws Exception {
        TestRunResult result = new TestRunResult(1, 0, 0, 0, List.of(), Map.of("tag", "smoke"));

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"appliedOptions\"");
        assertThat(json).contains("\"tag\"");
        assertThat(json).contains("\"smoke\"");
    }

    @Test
    void emptyAppliedOptionsSerializesAsEmptyObject() throws Exception {
        TestRunResult result = new TestRunResult(1, 0, 0, 0, List.of(), Map.of());

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"appliedOptions\":{}").doesNotContain("\"tag\"");
    }
}
