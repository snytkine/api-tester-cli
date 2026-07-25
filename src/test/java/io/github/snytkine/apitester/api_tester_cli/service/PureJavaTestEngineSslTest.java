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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.event.NoOpProgressListener;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test that a {@code rest-client.ssl} block is deserialized from YAML and that the engine
 * builds and runs a suite whose default client requests skip certificate validation. Transport is
 * stubbed, so the {@code skip-certificate-validation} setting is exercised through {@link
 * PureJavaTestEngine}'s client-building path without a real TLS handshake.
 */
class PureJavaTestEngineSslTest {

    @Test
    void sslSkipConfigParsesAndSuiteRuns() throws Exception {
        var factory = new StubClientHttpRequestFactory().stub("/objects", 200, "{}", "application/json");
        var engine = new PureJavaTestEngine(factory, new AssertionEvaluatorFactory(), new ResponseResolver());
        Path path =
                Path.of(getClass().getResource("/test-suite-stub-ssl-skip.yml").toURI());

        TestSuite suite = new TestSuiteLoader().load(path, SuiteRunContext.of(Map.of(), Map.of()));

        RestClientConfig defaultClient = suite.defaultRestClient();
        assertThat(defaultClient).isNotNull();
        assertThat(defaultClient.ssl()).isNotNull();
        assertThat(defaultClient.ssl().skip()).isTrue();

        TestRunResult result = engine.runConfigurationSuite(
                suite, SuiteRunContext.of(Map.of(), Map.of()), NoOpProgressListener.INSTANCE);

        assertThat(result.failedCount()).isZero();
        assertThat(result.passedCount()).isEqualTo(1);
    }
}
