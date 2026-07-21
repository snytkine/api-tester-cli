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

import io.github.snytkine.apitester.api_tester_cli.config.VersionCheckProperties;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link VersionChecker}, using {@link StubClientHttpRequestFactory} to stand in
 * for the GitHub Releases API (same stubbing pattern used throughout {@code
 * PureJavaTestEngineTest} — no live network calls).
 */
class VersionCheckerTest {

    private static final String URL = "https://api.github.com/repos/o/r/releases/latest";

    private static BuildProperties runningVersion(String version) {
        Properties props = new Properties();
        props.setProperty("version", version);
        return new BuildProperties(props);
    }

    private static VersionCheckProperties properties(boolean enabled, int maxRetries) {
        return new VersionCheckProperties(
                enabled,
                URL,
                "https://example.invalid/releases/latest",
                5,
                maxRetries,
                0,
                "Version {latestVersion} is available.");
    }

    /** Wraps a delegate factory, counting every {@code createRequest} invocation. */
    private static ClientHttpRequestFactory counting(ClientHttpRequestFactory delegate, AtomicInteger counter) {
        return (uri, method) -> {
            counter.incrementAndGet();
            return delegate.createRequest(uri, method);
        };
    }

    @Test
    void checkForUpdate_whenLatestIsNewer_populatesHolder() {
        StubClientHttpRequestFactory stub =
                new StubClientHttpRequestFactory().stub(URL, 200, "{\"tag_name\": \"v0.9.0\"}", "application/json");
        RestClient restClient = RestClient.builder().requestFactory(stub).build();
        LatestVersionHolder holder = new LatestVersionHolder();
        VersionChecker checker = new VersionChecker(restClient, properties(true, 3), runningVersion("0.4.1"), holder);

        checker.checkForUpdate();

        assertThat(holder.get()).contains("0.9.0");
    }

    @Test
    void checkForUpdate_whenLatestIsSameOrOlder_holderStaysEmpty() {
        StubClientHttpRequestFactory stub =
                new StubClientHttpRequestFactory().stub(URL, 200, "{\"tag_name\": \"0.4.1\"}", "application/json");
        RestClient restClient = RestClient.builder().requestFactory(stub).build();
        LatestVersionHolder holder = new LatestVersionHolder();
        VersionChecker checker = new VersionChecker(restClient, properties(true, 3), runningVersion("0.4.1"), holder);

        checker.checkForUpdate();

        assertThat(holder.get()).isEmpty();
    }

    @Test
    void checkForUpdate_whenDisabled_makesNoHttpCallAndHolderStaysEmpty() {
        AtomicInteger callCount = new AtomicInteger();
        StubClientHttpRequestFactory stub =
                new StubClientHttpRequestFactory().stub(URL, 200, "{\"tag_name\": \"v9.9.9\"}", "application/json");
        RestClient restClient =
                RestClient.builder().requestFactory(counting(stub, callCount)).build();
        LatestVersionHolder holder = new LatestVersionHolder();
        VersionChecker checker = new VersionChecker(restClient, properties(false, 3), runningVersion("0.4.1"), holder);

        checker.checkForUpdate();

        assertThat(callCount).hasValue(0);
        assertThat(holder.get()).isEmpty();
    }

    @Test
    void checkForUpdate_whenRunningVersionUnknown_makesNoHttpCallAndHolderStaysEmpty() {
        AtomicInteger callCount = new AtomicInteger();
        StubClientHttpRequestFactory stub =
                new StubClientHttpRequestFactory().stub(URL, 200, "{\"tag_name\": \"v9.9.9\"}", "application/json");
        RestClient restClient =
                RestClient.builder().requestFactory(counting(stub, callCount)).build();
        LatestVersionHolder holder = new LatestVersionHolder();
        VersionChecker checker = new VersionChecker(restClient, properties(true, 3), runningVersion("unknown"), holder);

        checker.checkForUpdate();

        assertThat(callCount).hasValue(0);
        assertThat(holder.get()).isEmpty();
    }

    @Test
    void checkForUpdate_whenEveryAttemptFails_retriesExactlyMaxRetriesTimesThenGivesUpSilently() {
        AtomicInteger callCount = new AtomicInteger();
        StubClientHttpRequestFactory stub = new StubClientHttpRequestFactory().stub(URL, 500, "boom", "text/plain");
        RestClient restClient =
                RestClient.builder().requestFactory(counting(stub, callCount)).build();
        LatestVersionHolder holder = new LatestVersionHolder();
        VersionChecker checker = new VersionChecker(restClient, properties(true, 3), runningVersion("0.4.1"), holder);

        checker.checkForUpdate();

        assertThat(callCount).hasValue(3);
        assertThat(holder.get()).isEmpty();
    }

    @Test
    void checkForUpdate_whenResponseHasNoTagName_holderStaysEmpty() {
        StubClientHttpRequestFactory stub =
                new StubClientHttpRequestFactory().stub(URL, 200, "{\"name\": \"a release\"}", "application/json");
        RestClient restClient = RestClient.builder().requestFactory(stub).build();
        LatestVersionHolder holder = new LatestVersionHolder();
        VersionChecker checker = new VersionChecker(restClient, properties(true, 3), runningVersion("0.4.1"), holder);

        checker.checkForUpdate();

        assertThat(holder.get()).isEmpty();
    }
}
