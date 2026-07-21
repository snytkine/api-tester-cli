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
package io.github.snytkine.apitester.api_tester_cli.model.hooks;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for the {@code model.hooks} records and enum. */
class HookModelTest {

    @Test
    void hookDefaultsForAsyncTimeoutAndId() {
        ScriptHook noOpts = new ScriptHook(null, null, null, "s.sh", null);
        assertThat(noOpts.isAsync()).isFalse();
        assertThat(noOpts.effectiveTimeoutSeconds()).isEqualTo(Hook.DEFAULT_TIMEOUT_SECONDS);
        assertThat(noOpts.effectiveId(HookPhase.BEFORE_ALL, 1)).isEqualTo("before-all-1");

        ScriptHook withOpts = new ScriptHook("seed", Boolean.TRUE, 30, "s.sh", null);
        assertThat(withOpts.isAsync()).isTrue();
        assertThat(withOpts.effectiveTimeoutSeconds()).isEqualTo(30);
        assertThat(withOpts.effectiveId(HookPhase.BEFORE_ALL, 1)).isEqualTo("seed");

        ScriptHook blankId = new ScriptHook("  ", Boolean.FALSE, null, "s.sh", null);
        assertThat(blankId.isAsync()).isFalse();
        assertThat(blankId.effectiveId(HookPhase.AFTER_EACH, 3)).isEqualTo("after-each-3");
    }

    @Test
    void webHookEffectiveDefaults() {
        WebHook bare = new WebHook(null, null, null, null, "/u", null, null, null);
        assertThat(bare.effectiveMethod()).isEqualTo(HttpMethod.POST);
        assertThat(bare.effectiveRestClient()).isEqualTo("default");
        assertThat(bare.isAttachReport()).isFalse();

        WebHook full = new WebHook(null, null, null, "client-a", "/u", HttpMethod.PUT, Map.of(), Boolean.TRUE);
        assertThat(full.effectiveMethod()).isEqualTo(HttpMethod.PUT);
        assertThat(full.effectiveRestClient()).isEqualTo("client-a");
        assertThat(full.isAttachReport()).isTrue();

        WebHook blankClient = new WebHook(null, null, null, "  ", "/u", null, null, null);
        assertThat(blankClient.effectiveRestClient()).isEqualTo("default");
    }

    @Test
    void hookPhaseYamlKeys() {
        assertThat(HookPhase.SUITE_VALIDATION_FAILED.yamlKey()).isEqualTo("suite-validation-failed");
        assertThat(HookPhase.BEFORE_ALL.yamlKey()).isEqualTo("before-all");
        assertThat(HookPhase.BEFORE_EACH.yamlKey()).isEqualTo("before-each");
        assertThat(HookPhase.AFTER_EACH.yamlKey()).isEqualTo("after-each");
        assertThat(HookPhase.AFTER_ALL.yamlKey()).isEqualTo("after-all");
        assertThat(HookPhase.BEFORE_REPORT.yamlKey()).isEqualTo("before-report");
        assertThat(HookPhase.AFTER_REPORT.yamlKey()).isEqualTo("after-report");
    }

    @Test
    void hooksForPhaseReturnsEmptyForAbsentPhase() {
        Hooks hooks = new Hooks(null, null, null, null, null, null, null);
        assertThat(hooks.forPhase(HookPhase.BEFORE_ALL)).isEmpty();
        assertThat(hooks.isEmpty()).isTrue();
        assertThat(hooks.hasAnyScriptHook()).isFalse();
        assertThat(hooks.allHooks()).isEmpty();
    }

    @Test
    void hooksAggregateAcrossPhases() {
        Hook script = new ScriptHook(null, null, null, "s.sh", null);
        Hook web = new WebHook(null, null, null, null, "/u", null, null, null);
        Hooks hooks = new Hooks(null, List.of(script), null, List.of(web), null, null, null);

        assertThat(hooks.forPhase(HookPhase.BEFORE_ALL)).containsExactly(script);
        assertThat(hooks.forPhase(HookPhase.AFTER_EACH)).containsExactly(web);
        assertThat(hooks.allHooks()).containsExactly(script, web);
        assertThat(hooks.hasAnyScriptHook()).isTrue();
        assertThat(hooks.isEmpty()).isFalse();
    }

    @Test
    void hooksWithOnlyWebHasNoScriptHook() {
        Hook web = new WebHook(null, null, null, null, "/u", null, null, null);
        Hooks hooks = new Hooks(null, null, null, null, List.of(web), null, null);
        assertThat(hooks.hasAnyScriptHook()).isFalse();
    }
}
