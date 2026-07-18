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

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link HookRunMetadata} and the {@link SuiteRunContext} metadata wither. */
class HookRunMetadataTest {

    @Test
    void emptyMetadataHasNoValues() {
        HookRunMetadata empty = HookRunMetadata.empty();
        assertThat(empty.interactive()).isFalse();
        assertThat(empty.reportDir()).isNull();
        assertThat(empty.reportPath()).isNull();
        assertThat(empty.tagFilter()).isNull();
        assertThat(empty.testNameFilter()).isNull();
        assertThat(empty.envFilePath()).isNull();
    }

    @Test
    void suiteRunContextReturnsEmptyMetadataByDefault() {
        SuiteRunContext ctx = SuiteRunContext.of(Map.of(), Map.of());
        assertThat(ctx.hookRunMetadata()).isNotNull();
        assertThat(ctx.hookRunMetadata().interactive()).isFalse();
    }

    @Test
    void witherAttachesMetadataAndPreservesRunId() {
        SuiteRunContext ctx = SuiteRunContext.of(Map.of("E", "1"), Map.of("C", "2"));
        String runId = ctx.getRunID();
        HookRunMetadata meta =
                new HookRunMetadata(true, "/rep", Path.of("/rep/r.html"), "smoke", "MyTest", Path.of("/x/.env"));

        SuiteRunContext enriched = ctx.withHookRunMetadata(meta);

        assertThat(enriched.getRunID()).isEqualTo(runId);
        assertThat(enriched.env()).containsEntry("E", "1");
        assertThat(enriched.cli()).containsEntry("C", "2");
        assertThat(enriched.hookRunMetadata()).isSameAs(meta);
        assertThat(enriched.hookRunMetadata().interactive()).isTrue();
        assertThat(enriched.hookRunMetadata().reportDir()).isEqualTo("/rep");
    }
}
