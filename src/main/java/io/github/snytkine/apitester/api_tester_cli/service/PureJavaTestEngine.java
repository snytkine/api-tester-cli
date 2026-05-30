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

import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.interfaces.TestEngine;
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.AssertionEvaluatorFactory;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseResolver;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Executes the test cases in a {@link TestSuite} sequentially using Spring's {@link RestClient},
 * collecting pass/fail counts and error messages into a {@link TestRunResult}.
 *
 * <p>A fresh {@link RestClient} is built for each suite run using the {@link RestClientConfig}
 * embedded in the suite (base URL and connect timeout). The underlying HTTP transport is supplied
 * as a {@link ClientHttpRequestFactory} at construction time, keeping transport configuration
 * separate from per-suite settings.
 *
 * <p>Assertions are evaluated via {@link AssertionEvaluatorFactory}, which maps each assertion type
 * to its evaluator. All assertion failures within a single test case are collected by {@link
 * io.github.snytkine.apitester.api_tester_cli.util.FailureCollector} and surfaced together rather
 * than stopping at the first failure.
 *
 * <p>This class is a thread-safe Spring singleton: all per-invocation state is confined to the call
 * stack of {@link #runConfigurationSuite(TestSuite)}.
 */
@Service
public class PureJavaTestEngine implements TestEngine {

  private static final Logger log = LoggerFactory.getLogger(PureJavaTestEngine.class);

  private final ClientHttpRequestFactory requestFactory;
  private final AssertionEvaluatorFactory evaluatorFactory;
  private final ResponseResolver responseResolver;

  /**
   * Constructs the engine with the required collaborators.
   *
   * @param requestFactory the HTTP transport factory used to back each per-suite {@link RestClient}
   * @param evaluatorFactory maps assertion model objects to their evaluator implementations
   * @param responseResolver converts a {@link RestClient.ResponseSpec} into an {@link ApiResponse}
   */
  public PureJavaTestEngine(
      ClientHttpRequestFactory requestFactory,
      AssertionEvaluatorFactory evaluatorFactory,
      ResponseResolver responseResolver) {
    this.requestFactory = requestFactory;
    this.evaluatorFactory = evaluatorFactory;
    this.responseResolver = responseResolver;
  }

  /**
   * Runs all test cases in the provided {@link TestSuite} sequentially and returns an aggregated
   * result.
   *
   * <p>A {@link RestClient} is built from the suite's {@link RestClientConfig} (base URL and
   * connect timeout) before iteration begins and shared across all test cases in the suite. The
   * suite's file path (when present) is used to resolve relative file references in assertions.
   *
   * @param testSuite the loaded test suite whose {@link TestSuite#tests()} are executed
   * @return a {@link TestRunResult} with per-test-case results including structured failure detail
   */
  @Override
  public TestRunResult runConfigurationSuite(TestSuite testSuite) {
    RestClient restClient = buildRestClient(testSuite.restClientConfig());
    Path suiteDir = testSuite.filePath() != null ? testSuite.filePath().getParent() : null;
    Map<String, String> suiteVariables =
        Objects.requireNonNullElse(testSuite.variables(), Map.of());

    long passedCount = 0;
    long failedCount = 0;
    List<TestCaseResult> results = new ArrayList<>();

    for (TestCase config : testSuite.tests()) {
      try {
        executeSingleTest(restClient, config, suiteDir, suiteVariables);
        passedCount++;
        results.add(new TestCaseResult(config.name(), true, config.assertions().size(), List.of()));
      } catch (MultipleFailuresError e) {
        failedCount++;
        List<AssertionFailure> failures = extractFailures(e);
        int passedAssertions = config.assertions().size() - failures.size();
        results.add(new TestCaseResult(config.name(), false, passedAssertions, failures));
        log.debug(
            "Test case '{}' failed with {} assertion failure(s)",
            config.name(),
            e.getFailures().size());
      } catch (Throwable e) {
        failedCount++;
        results.add(
            new TestCaseResult(
                config.name(),
                false,
                0,
                List.of(new AssertionFailure(e.getMessage(), null, null))));
        log.debug("Test case '{}' failed: {}", config.name(), e.getMessage(), e);
      }
    }

    return new TestRunResult(passedCount, failedCount, results);
  }

  /**
   * Converts the individual failures inside a {@link MultipleFailuresError} into a list of {@link
   * AssertionFailure} records, preserving actual and expected values when available.
   *
   * <p>Each failure is an {@link AssertionFailedError} when produced by an AssertJ comparison (e.g.
   * {@code isEqualTo()}), in which case the actual and expected values are extracted via {@link
   * AssertionFailedError#getActual()} and {@link AssertionFailedError#getExpected()}. Free- form
   * failures from {@code soft.fail("message")} produce an {@code AssertionFailedError} with no
   * defined actual/expected, so both fields are {@code null} in that case.
   *
   * @param e the composite error from {@link FailureCollector#assertAll()}
   * @return a non-empty list of individual {@link AssertionFailure} records
   */
  private List<AssertionFailure> extractFailures(MultipleFailuresError e) {
    return e.getFailures().stream()
        .map(
            failure -> {
              if (failure instanceof AssertionFailedError afe) {
                Object actual = afe.isActualDefined() ? afe.getActual().getValue() : null;
                Object expected = afe.isExpectedDefined() ? afe.getExpected().getValue() : null;
                return new AssertionFailure(failure.getMessage(), expected, actual);
              }
              return new AssertionFailure(failure.getMessage(), null, null);
            })
        .toList();
  }

  /**
   * Executes a single test case: builds and fires the HTTP request, resolves the response into an
   * {@link ApiResponse}, then evaluates all assertions via {@link SoftAssertions}.
   *
   * <p>HTTP errors (network failures, DNS issues) propagate as unchecked exceptions. Assertion
   * failures surface as {@code MultipleFailuresError} from {@link SoftAssertions#assertAll()}. Both
   * are caught by the {@code catch (Throwable)} in {@link #runConfigurationSuite}.
   *
   * @param restClient the configured client for this suite run
   * @param config the test case to execute
   * @param suiteDir the directory of the suite file, or {@code null} if unavailable
   * @param suiteVariables resolved suite-level variables, forwarded to assertion evaluators that
   *     process expected content as Thymeleaf templates
   */
  private void executeSingleTest(
      RestClient restClient,
      TestCase config,
      @Nullable Path suiteDir,
      Map<String, String> suiteVariables) {
    log.debug(
        "Executing test case '{}': {} {}",
        config.name(),
        config.request().method(),
        config.request().url());

    RestClient.RequestBodySpec requestSpec = buildRequestSpec(restClient, config);
    RestClient.ResponseSpec responseSpec = requestSpec.retrieve();

    Map<String, String> testVariables = Objects.requireNonNullElse(config.variables(), Map.of());
    List<AssertionEvaluator> evaluators =
        config.assertions().stream()
            .map(a -> evaluatorFactory.create(a, suiteDir, suiteVariables, testVariables))
            .toList();

    ApiResponse apiResponse = responseResolver.resolve(responseSpec, config.assertions());
    log.debug("Test case '{}' received status: {}", config.name(), apiResponse.statusCode());

    FailureCollector collector = new FailureCollector();
    evaluators.forEach(e -> e.evaluate(apiResponse, collector));
    collector.assertAll();
  }

  /**
   * Builds a {@link RestClient.RequestBodySpec} from the test case's request definition, applying
   * headers and logging a warning when a request body is declared but not yet supported.
   *
   * @param restClient the client to use for building the request
   * @param config the test case whose request is being built
   * @return a fully configured request spec ready for {@link RestClient.RequestBodySpec#retrieve()}
   */
  private RestClient.RequestBodySpec buildRequestSpec(RestClient restClient, TestCase config) {
    RestClient.RequestBodySpec requestSpec =
        restClient
            .method(toSpringHttpMethod(config.request().method()))
            .uri(config.request().url());

    if (config.request().headers() != null) {
      for (Map.Entry<String, String> header : config.request().headers().entrySet()) {
        requestSpec.header(header.getKey(), header.getValue());
      }
    }

    // RequestBody is a descriptor (type + file path / inline content).
    // Body loading from file is not yet implemented.
    if (config.request().body() != null) {
      log.warn(
          "Request body handling is not yet implemented for test case '{}'; body skipped.",
          config.name());
    }

    return requestSpec;
  }

  /**
   * Builds a {@link RestClient} configured from the given {@link RestClientConfig}.
   *
   * <p>If {@code config} carries a non-blank {@code baseUrl} it is set as the client's default base
   * URL. When a {@code connectTimeout} is present a new {@link
   * org.springframework.http.client.JdkClientHttpRequestFactory} is created with that timeout,
   * replacing the injected factory for this suite.
   *
   * @param config the suite-level REST client settings
   * @return a fully configured {@link RestClient} ready for use
   */
  private RestClient buildRestClient(RestClientConfig config) {
    RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
    if (StringUtils.hasText(config.baseUrl())) {
      builder.baseUrl(config.baseUrl());
    }
    if (config.connectTimeout() != null) {
      builder.requestFactory(
          new org.springframework.http.client.JdkClientHttpRequestFactory(
              java.net.http.HttpClient.newBuilder()
                  .connectTimeout(Duration.ofMillis(config.connectTimeout()))
                  .build()));
    }
    return builder.build();
  }

  /**
   * Converts this project's {@link HttpMethod} enum to a Spring {@link
   * org.springframework.http.HttpMethod}.
   *
   * @param method the HTTP method from the test case model
   * @return the corresponding Spring HTTP method
   */
  private org.springframework.http.HttpMethod toSpringHttpMethod(HttpMethod method) {
    return org.springframework.http.HttpMethod.valueOf(method.name());
  }
}
