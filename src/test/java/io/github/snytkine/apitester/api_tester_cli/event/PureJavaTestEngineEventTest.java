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

import io.github.snytkine.apitester.api_tester_cli.config.HttpClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.CliVariables;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.PureJavaTestEngine;
import io.github.snytkine.apitester.api_tester_cli.service.TestSuiteLoader;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * Verifies that {@link PureJavaTestEngine} fires {@link TestProgressEvent}s in the correct order
 * and with correct values when executing a real test suite via the live API.
 */
class PureJavaTestEngineEventTest {

    private TestSuiteLoader loader;
    private PureJavaTestEngine engine;

    @BeforeEach
    void setUp() {
        loader = new TestSuiteLoader();
        ClientHttpRequestFactory factory = new HttpClientConfig().defaultClientHttpRequestFactory();
        engine = new PureJavaTestEngine(factory, new AssertionEvaluatorFactory(), new ResponseResolver());
    }

    @Test
    void eventsAreFiredInCorrectOrderForPassingSuite() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-1.yml").toURI());
        TestSuite suite = loader.load(path, new CliVariables(Map.of()));
        int testCount = suite.tests().size();

        List<TestProgressEvent> captured = new ArrayList<>();
        TestRunResult result = engine.runConfigurationSuite(suite, captured::add);

        // First event must be SuiteStarted
        assertThat(captured.get(0)).isInstanceOf(TestProgressEvent.SuiteStarted.class);
        TestProgressEvent.SuiteStarted suiteStarted = (TestProgressEvent.SuiteStarted) captured.get(0);
        assertThat(suiteStarted.suiteName()).isEqualTo(suite.name());
        assertThat(suiteStarted.totalTestCount()).isEqualTo(testCount);

        // Last event must be SuiteCompleted
        assertThat(captured.get(captured.size() - 1)).isInstanceOf(TestProgressEvent.SuiteCompleted.class);
        TestProgressEvent.SuiteCompleted suiteCompleted =
                (TestProgressEvent.SuiteCompleted) captured.get(captured.size() - 1);
        assertThat(suiteCompleted.passCount()).isEqualTo(result.passedCount());
        assertThat(suiteCompleted.failCount()).isEqualTo(result.failedCount());

        // Between suite start and suite complete: pairs of TestStarted / TestCompleted
        List<TestProgressEvent> middleEvents = captured.subList(1, captured.size() - 1);
        assertThat(middleEvents).hasSize(testCount * 2);
        for (int i = 0; i < testCount; i++) {
            assertThat(middleEvents.get(i * 2)).isInstanceOf(TestProgressEvent.TestStarted.class);
            assertThat(middleEvents.get(i * 2 + 1)).isInstanceOf(TestProgressEvent.TestCompleted.class);

            TestProgressEvent.TestStarted started = (TestProgressEvent.TestStarted) middleEvents.get(i * 2);
            TestProgressEvent.TestCompleted completed = (TestProgressEvent.TestCompleted) middleEvents.get(i * 2 + 1);

            assertThat(started.testIndex()).isEqualTo(i);
            assertThat(completed.testIndex()).isEqualTo(i);
            assertThat(completed.testName()).isEqualTo(started.testName());
        }
    }

    @Test
    void passedTestsFireCompletedWithPassStatus() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-1.yml").toURI());
        TestSuite suite = loader.load(path, new CliVariables(Map.of()));

        List<TestProgressEvent> captured = new ArrayList<>();
        TestRunResult result = engine.runConfigurationSuite(suite, captured::add);

        long passEventCount = captured.stream()
                .filter(e -> e instanceof TestProgressEvent.TestCompleted tc && tc.status() == TestStatus.PASS)
                .count();

        assertThat(passEventCount).isEqualTo(result.passedCount());
    }

    @Test
    void noOpListenerProducesIdenticalResultToDefaultOverload() throws Exception {
        Path path = Path.of(getClass().getResource("/test-suite-1.yml").toURI());
        TestSuite suite = loader.load(path, new CliVariables(Map.of()));

        TestRunResult resultDefault = engine.runConfigurationSuite(suite);
        TestRunResult resultWithNoop = engine.runConfigurationSuite(suite, NoOpProgressListener.INSTANCE);

        assertThat(resultWithNoop.passedCount()).isEqualTo(resultDefault.passedCount());
        assertThat(resultWithNoop.failedCount()).isEqualTo(resultDefault.failedCount());
    }
}
