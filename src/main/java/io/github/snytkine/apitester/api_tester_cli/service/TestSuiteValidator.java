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
package io.github.snytkine.apitester.api_tester_cli.service;

import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.HookPhase;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.Hooks;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.ScriptHook;
import io.github.snytkine.apitester.api_tester_cli.model.hooks.WebHook;
import io.github.snytkine.apitester.api_tester_cli.service.hooks.ScriptHookExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Validates a fully-parsed {@link TestSuite} for structural constraints that cannot be expressed in
 * the YAML schema.
 *
 * <p>Two groups of rules are enforced:
 *
 * <ul>
 *   <li>{@link #validate(TestSuite)} — every {@link TestCase#name()} within a suite must be unique
 *       (case-sensitive). Duplicates are reported as {@code Duplicate test name: "<name>" appears N
 *       times}.
 *   <li>{@link #validateRestClients(TestSuite)} — exactly one of {@code rest-client} /
 *       {@code rest-clients} must be present, ids must be unique, ids are required when multiple
 *       clients are configured, and a request may only reference a defined client id.
 * </ul>
 *
 * <p>This class is a Spring singleton and is thread-safe. All methods are stateless; per-call data
 * lives on the call stack.
 */
@Service
public class TestSuiteValidator {

    /**
     * Reserved system argument / payload keys supplied by the runner. A hook's user-defined {@code
     * parameters} (scripts) or {@code payload} (web) may not collide with any of these.
     */
    private static final Set<String> SYSTEM_HOOK_KEYS = Set.of(
            "suite_name",
            "run_id",
            "hook_id",
            "phase",
            "interactive",
            "timeout_seconds",
            "report_dir",
            "report_path",
            "test_name",
            "tag",
            "env_file",
            "url",
            "method",
            "test_status",
            "tests_total",
            "tests_passed",
            "tests_failed",
            "tests_errors",
            "duration_ms",
            "headers",
            "body");

    /**
     * Validates that all test case names within {@code testSuite} are unique.
     *
     * <p>Names are compared using their exact string value (case-sensitive). The returned list is
     * sorted by name to produce deterministic output regardless of the order tests appear in the YAML
     * file.
     *
     * @param testSuite the fully-loaded test suite to validate; must not be {@code null}
     * @return a non-null, possibly-empty list of error messages; empty means the suite is valid
     */
    public List<String> validate(TestSuite testSuite) {
        Map<String, Long> nameCounts =
                testSuite.tests().stream().collect(Collectors.groupingBy(TestCase::name, Collectors.counting()));
        List<String> errors = new ArrayList<>();
        nameCounts.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .sorted(Map.Entry.comparingByKey())
                .forEach(e ->
                        errors.add("Duplicate test name: \"" + e.getKey() + "\" appears " + e.getValue() + " times"));
        return errors;
    }

    /**
     * Validates the suite's REST client declaration.
     *
     * <p>Composes four independent, fail-fast rules (each implemented as its own static method):
     *
     * <ol>
     *   <li>exactly one of {@code rest-client} / {@code rest-clients} must be present;
     *   <li>{@code id} values in a {@code rest-clients} list must be unique;
     *   <li>every {@code rest-clients} entry must have an {@code id} when more than one is present;
     *   <li>a request's {@code rest-client} selector must reference a defined client id.
     * </ol>
     *
     * @param suite the fully-loaded test suite to validate; must not be {@code null}
     * @return a non-null, possibly-empty list of error messages; empty means the declaration is valid
     */
    public List<String> validateRestClients(TestSuite suite) {
        List<String> errors = new ArrayList<>();
        errors.addAll(validateExactlyOnePresent(suite));
        errors.addAll(validateUniqueIds(suite));
        errors.addAll(validateIdsPresentWhenMultiple(suite));
        errors.addAll(validateRequestReferences(suite));
        return errors;
    }

    /**
     * Rule 1: exactly one of {@code rest-client} (singular) or {@code rest-clients} (plural) must be
     * declared.
     *
     * @param suite the suite to check
     * @return a singleton error list when neither or both are present, otherwise an empty list
     */
    private static List<String> validateExactlyOnePresent(TestSuite suite) {
        boolean hasSingle = suite.restClient() != null;
        boolean hasMultiple = suite.restClients() != null;
        if (!hasSingle && !hasMultiple) {
            return List.of("Test suite must define exactly one of 'rest-client' or 'rest-clients', but found neither");
        }
        if (hasSingle && hasMultiple) {
            return List.of("Test suite must define exactly one of 'rest-client' or 'rest-clients', but found both");
        }
        return List.of();
    }

    /**
     * Rule 2: {@code id} values across all entries of a {@code rest-clients} list must be unique.
     *
     * @param suite the suite to check
     * @return one error per duplicate id (in first-seen order), otherwise an empty list
     */
    private static List<String> validateUniqueIds(TestSuite suite) {
        List<RestClientConfig> clients = suite.restClients();
        if (clients == null) {
            return List.of();
        }
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RestClientConfig client : clients) {
            String id = client.id();
            if (id != null && !seen.add(id)) {
                errors.add("Duplicate rest-client id: '" + id + "'");
            }
        }
        return errors;
    }

    /**
     * Rule 3: when a {@code rest-clients} list contains more than one entry, every entry must declare
     * an {@code id}.
     *
     * @param suite the suite to check
     * @return one error per entry missing an id (with its zero-based index), otherwise an empty list
     */
    private static List<String> validateIdsPresentWhenMultiple(TestSuite suite) {
        List<RestClientConfig> clients = suite.restClients();
        if (clients == null || clients.size() <= 1) {
            return List.of();
        }
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).id() == null) {
                errors.add("rest-client at index " + i
                        + " is missing required 'id' (required when multiple rest-clients are configured)");
            }
        }
        return errors;
    }

    /**
     * Rule 4: any request that selects a {@code rest-client} id must reference a client defined in
     * the suite's {@code rest-clients} list. Only meaningful when the plural form is used.
     *
     * @param suite the suite to check
     * @return one error per request referencing an unknown id, otherwise an empty list
     */
    private static List<String> validateRequestReferences(TestSuite suite) {
        List<RestClientConfig> clients = suite.restClients();
        if (clients == null) {
            return List.of();
        }
        Set<String> definedIds = clients.stream()
                .map(RestClientConfig::id)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        List<String> errors = new ArrayList<>();
        for (TestCase test : suite.tests()) {
            String requestedId = test.request().restClient();
            if (requestedId != null && !definedIds.contains(requestedId)) {
                errors.add("Test '" + test.name() + "' references unknown rest-client id: '" + requestedId + "'");
            }
        }
        return errors;
    }

    /**
     * Validates the suite's {@code hooks} block (lifecycle script/web hooks) against the structural
     * and security constraints that cannot be expressed in the JSON schema.
     *
     * <p>Rules enforced (see issue #65):
     *
     * <ul>
     *   <li>explicit hook {@code id}s must be unique across the whole suite;
     *   <li>{@code timeout-seconds}, when present, must be positive;
     *   <li>user {@code parameters}/{@code payload} keys must not collide with reserved system keys;
     *   <li>script paths must exist and be executable (resolved against the suite directory), must
     *       not end in {@code .bat}/{@code .cmd}, and must not contain NUL; script parameter values
     *       must not contain NUL;
     *   <li>web hooks must use {@code POST} or {@code PUT}, reference a defined rest-client, and only
     *       set {@code attach-report} on {@code after-report} hooks.
     * </ul>
     *
     * <p>Script paths are validated against their template-resolved values, so this must run after
     * {@code TestSuiteLoader} has processed the suite.
     *
     * @param suite the fully-loaded, template-resolved test suite; must not be {@code null}
     * @return a non-null, possibly-empty list of error messages; empty means the hooks are valid
     */
    public List<String> validateHooks(TestSuite suite) {
        Hooks hooks = suite.hooks();
        if (hooks == null || hooks.isEmpty()) {
            return List.of();
        }

        List<String> errors = new ArrayList<>();
        Path suiteDir = suite.filePath() != null ? suite.filePath().getParent() : null;
        Set<String> definedClientIds = suite.restClientsById().keySet();
        Set<String> seenIds = new HashSet<>();

        for (HookPhase phase : HookPhase.values()) {
            List<Hook> phaseHooks = hooks.forPhase(phase);
            for (int i = 0; i < phaseHooks.size(); i++) {
                Hook hook = phaseHooks.get(i);
                int index = i + 1;
                String where = phase.yamlKey() + " hook " + index;

                validateHookId(hook, phase, index, where, seenIds, errors);
                validateTimeout(hook, where, errors);

                if (hook instanceof ScriptHook script) {
                    validateScriptHook(script, suiteDir, where, errors);
                } else if (hook instanceof WebHook web) {
                    validateWebHook(web, phase, definedClientIds, where, errors);
                }
            }
        }
        return errors;
    }

    /**
     * Validates a hook's explicit id for cross-suite uniqueness.
     *
     * @param hook the hook
     * @param phase the phase
     * @param index the 1-based index
     * @param where a location label for messages
     * @param seenIds ids already seen (mutated)
     * @param errors error accumulator (mutated)
     */
    private static void validateHookId(
            Hook hook, HookPhase phase, int index, String where, Set<String> seenIds, List<String> errors) {
        String id = hook.id();
        if (id != null && !id.isBlank() && !seenIds.add(id)) {
            errors.add(where + ": duplicate hook id '" + id + "'");
        }
    }

    /**
     * Validates that a hook's {@code timeout-seconds}, when present, is positive.
     *
     * @param hook the hook
     * @param where a location label for messages
     * @param errors error accumulator (mutated)
     */
    private static void validateTimeout(Hook hook, String where, List<String> errors) {
        Integer t = hook.timeoutSeconds();
        if (t != null && t <= 0) {
            errors.add(where + ": timeout-seconds must be positive, but was " + t);
        }
    }

    /**
     * Validates a script hook: executable path, no {@code .bat}/{@code .cmd}, no NUL in path or
     * parameters, and no parameter-key collision with system keys.
     *
     * @param script the script hook
     * @param suiteDir the suite directory for resolving relative paths, or {@code null}
     * @param where a location label for messages
     * @param errors error accumulator (mutated)
     */
    private static void validateScriptHook(ScriptHook script, Path suiteDir, String where, List<String> errors) {
        String rawPath = script.path();
        String lowerPath = rawPath.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".bat") || lowerPath.endsWith(".cmd")) {
            errors.add(where + ": .bat/.cmd scripts are rejected on all platforms"
                    + " (Windows argument-injection risk): " + rawPath);
        }
        if (containsNul(rawPath)) {
            errors.add(where + ": script path contains a NUL character");
        } else {
            Path resolved = ScriptHookExecutor.resolvePath(suiteDir, rawPath);
            if (!Files.exists(resolved)) {
                errors.add(where + ": script path does not exist: " + resolved);
            } else if (!Files.isExecutable(resolved)) {
                errors.add(where + ": script is not executable: " + resolved);
            }
        }
        if (script.parameters() != null) {
            for (Map.Entry<String, String> e : script.parameters().entrySet()) {
                if (SYSTEM_HOOK_KEYS.contains(e.getKey())) {
                    errors.add(where + ": parameter key '" + e.getKey() + "' collides with a reserved system key");
                }
                if (containsNul(e.getValue())) {
                    errors.add(where + ": parameter '" + e.getKey() + "' value contains a NUL character");
                }
            }
        }
    }

    /**
     * Validates a web hook: method restricted to POST/PUT, a defined rest-client, {@code
     * attach-report} only on {@code after-report} hooks, and no payload-key collision with system
     * keys.
     *
     * @param web the web hook
     * @param phase the phase this hook belongs to
     * @param definedClientIds the ids of defined rest-clients
     * @param where a location label for messages
     * @param errors error accumulator (mutated)
     */
    private static void validateWebHook(
            WebHook web, HookPhase phase, Set<String> definedClientIds, String where, List<String> errors) {
        HttpMethod method = web.effectiveMethod();
        if (method != HttpMethod.POST && method != HttpMethod.PUT) {
            errors.add(where + ": web hook method must be POST or PUT, but was " + method);
        }
        String clientId = web.effectiveRestClient();
        if (!definedClientIds.contains(clientId)) {
            errors.add(where + ": references unknown rest-client id: '" + clientId + "'");
        }
        if (web.isAttachReport() && phase != HookPhase.AFTER_REPORT) {
            errors.add(where + ": attach-report is only allowed on after-report hooks");
        }
        if (web.payload() != null) {
            for (String key : web.payload().keySet()) {
                if (SYSTEM_HOOK_KEYS.contains(key)) {
                    errors.add(where + ": payload key '" + key + "' collides with a reserved system key");
                }
            }
        }
    }

    /**
     * Returns whether {@code s} contains a NUL ({@code U+0000}) character.
     *
     * @param s the string to check
     * @return {@code true} when {@code s} contains {@code U+0000}
     */
    private static boolean containsNul(String s) {
        return s.indexOf('\0') >= 0;
    }
}
