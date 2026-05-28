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
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Executes a list of {@link TestCase} objects sequentially using Spring's {@link RestClient} backed
 * by Java's built-in {@code java.net.http.HttpClient}, collecting pass/fail counts and error
 * messages into a {@link TestRunResult}.
 */
@Service
@Slf4j
public class PureJavaTestEngine {

  private final RestClient restClient;

  /**
   * Constructs the engine with the application-scoped {@link RestClient}.
   *
   * @param restClient the REST client used to execute HTTP requests
   */
  public PureJavaTestEngine(RestClient restClient) {
    this.restClient = restClient;
  }

  /**
   * Runs all test cases in the provided list sequentially and returns an aggregated result.
   *
   * @param configurations the list of test cases to execute
   * @return a {@link TestRunResult} containing pass count, fail count, and error messages
   */
  public TestRunResult runConfigurationSuite(List<TestCase> configurations) {
    long passedCount = 0;
    long failedCount = 0;
    List<String> errorMessages = new ArrayList<>();

    for (TestCase config : configurations) {
      try {
        executeSingleTest(config);
        passedCount++;
      } catch (Throwable e) {
        failedCount++;
        errorMessages.add(config.name() + " failed: " + e.getMessage());
      }
    }

    return new TestRunResult(passedCount, failedCount, errorMessages);
  }

  /**
   * Builds and executes a single HTTP request from the given {@link TestCase} using {@link
   * RestClient}. Logs an error and rethrows if the request fails.
   *
   * @param config the test case describing the request to execute
   * @throws Exception if the HTTP request cannot be executed
   */
  private void executeSingleTest(TestCase config) throws Exception {
    log.debug(
        "Executing test case '{}': {} {}",
        config.name(),
        config.request().method(),
        config.request().url());
    try {
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

      ResponseEntity<String> response = requestSpec.retrieve().toEntity(String.class);

      log.debug(
          "Test case '{}' received status: {}", config.name(), response.getStatusCode().value());
    } catch (Exception e) {
      log.error("Error executing test case '{}': {}", config.name(), e.getMessage(), e);
      throw e;
    }
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
