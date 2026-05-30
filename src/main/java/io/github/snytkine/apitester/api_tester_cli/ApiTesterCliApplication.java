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
package io.github.snytkine.apitester.api_tester_cli;

import io.github.snytkine.apitester.api_tester_cli.model.Assertion;
import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import io.github.snytkine.apitester.api_tester_cli.model.BodyType;
import io.github.snytkine.apitester.api_tester_cli.model.CliVariables;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.IsNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.JsonMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.JsonSchemaAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.NotEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.NotNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ObjectExpectedValue;
import io.github.snytkine.apitester.api_tester_cli.model.Request;
import io.github.snytkine.apitester.api_tester_cli.model.RequestBody;
import io.github.snytkine.apitester.api_tester_cli.model.ResponseTimeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.StatusCodeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.StringContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.StringMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.util.Map;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the API Tester CLI application.
 *
 * <p>{@link RegisterReflectionForBinding} registers all model classes for full reflection access
 * (constructors, fields, methods) in the GraalVM native image. Without this, Jackson cannot
 * deserialize YAML test-suite files into model records at native-image runtime because the static
 * analysis does not see the reflective instantiation that Jackson performs dynamically.
 */
@SpringBootApplication
@RegisterReflectionForBinding({
  Assertion.class,
  AssertionFailure.class,
  BodyType.class,
  CliVariables.class,
  HttpMethod.class,
  IsNullAssertion.class,
  JsonMatchAssertion.class,
  JsonSchemaAssertion.class,
  NotEmptyAssertion.class,
  NotNullAssertion.class,
  ObjectExpectedValue.class,
  Request.class,
  RequestBody.class,
  ResponseTimeAssertion.class,
  RestClientConfig.class,
  StatusCodeAssertion.class,
  StringContainsAssertion.class,
  StringMatchAssertion.class,
  TestCase.class,
  TestCaseResult.class,
  TestRunResult.class,
  TestSuite.class,
})
public class ApiTesterCliApplication {

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(ApiTesterCliApplication.class);
    app.setBannerMode(Banner.Mode.OFF);
    app.setDefaultProperties(Map.of("logging.level.root", "OFF"));
    app.run(args);
  }
}
