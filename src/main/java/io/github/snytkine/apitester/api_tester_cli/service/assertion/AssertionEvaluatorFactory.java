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
package io.github.snytkine.apitester.api_tester_cli.service.assertion;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.snytkine.apitester.api_tester_cli.interfaces.AssertionEvaluator;
import io.github.snytkine.apitester.api_tester_cli.model.ArrayContainsAllAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ArrayContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ArrayIsEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ArrayIsNotEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ArraySizeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ArraySizeMaxAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ArraySizeMinAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.AssertFalseAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.AssertTrueAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.Assertion;
import io.github.snytkine.apitester.api_tester_cli.model.EndsWithAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.GreaterThanAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.GreaterThanOrEqualAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.HasHeaderAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.IsNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.JsonMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.JsonSchemaAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.LessThanAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.LessThanOrEqualAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.NotEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.NotNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.OneOfAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.RangeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.RegexMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ResponseTimeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.StartsWithAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.StatusCodeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.StatusInAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.StringContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.StringMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.ValueTypeAssertion;
import java.nio.file.Path;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Factory that maps each {@link Assertion} subtype to the corresponding {@link AssertionEvaluator}
 * implementation.
 *
 * <p>Extending the assertion model with a new type requires adding one new evaluator class and one
 * branch in {@link #create(Assertion, Path, Map, Map)} — nothing else in the engine changes.
 *
 * <p>This component is a thread-safe singleton: the only shared field is a configured {@link
 * ObjectMapper}, which is immutable after construction and passed by reference to evaluators that
 * need JSON comparison.
 */
@Component
public class AssertionEvaluatorFactory {

  private final ObjectMapper jsonMapper;

  /**
   * Constructs the factory with an {@link ObjectMapper} shared by evaluators that perform JSON
   * comparison. No extra modules are registered; standard Jackson databind covers all use cases in
   * this project and avoids classpath-scanning module discovery that is incompatible with GraalVM
   * native compilation.
   */
  public AssertionEvaluatorFactory() {
    this.jsonMapper = new ObjectMapper();
  }

  /**
   * Returns an {@link AssertionEvaluator} appropriate for the given {@link Assertion}.
   *
   * <p>The {@code suiteDir} parameter is required only by evaluators that load expected content
   * from files (currently {@link JsonMatchAssertionEvaluator} and {@link
   * JsonSchemaAssertionEvaluator}). It may be {@code null} when the suite was not loaded from disk,
   * in which case file-reference assertions will record a soft failure rather than throw.
   *
   * <p>The {@code suiteVariables} and {@code testVariables} maps are forwarded to {@link
   * JsonMatchAssertionEvaluator} so that expected JSON files can contain Thymeleaf expressions
   * resolved against suite- and test-level variables.
   *
   * @param assertion the assertion from the test case
   * @param suiteDir directory of the test-suite YAML file, or {@code null} if unavailable
   * @param suiteVariables resolved suite-level variables; must not be {@code null}
   * @param testVariables test-case-level variables; must not be {@code null}
   * @return a matching {@link AssertionEvaluator}
   */
  public AssertionEvaluator create(
      Assertion assertion,
      @Nullable Path suiteDir,
      Map<String, String> suiteVariables,
      Map<String, String> testVariables) {
    return switch (assertion) {
      case ArrayContainsAllAssertion a -> new ArrayContainsAllAssertionEvaluator(a);
      case ArrayContainsAssertion a -> new ArrayContainsAssertionEvaluator(a);
      case ArrayIsEmptyAssertion a -> new ArrayIsEmptyAssertionEvaluator(a);
      case ArrayIsNotEmptyAssertion a -> new ArrayIsNotEmptyAssertionEvaluator(a);
      case ArraySizeAssertion a -> new ArraySizeAssertionEvaluator(a);
      case ArraySizeMaxAssertion a -> new ArraySizeMaxAssertionEvaluator(a);
      case ArraySizeMinAssertion a -> new ArraySizeMinAssertionEvaluator(a);
      case AssertFalseAssertion a -> new AssertFalseAssertionEvaluator(a);
      case AssertTrueAssertion a -> new AssertTrueAssertionEvaluator(a);
      case EndsWithAssertion a -> new EndsWithAssertionEvaluator(a);
      case GreaterThanAssertion a -> new GreaterThanAssertionEvaluator(a);
      case GreaterThanOrEqualAssertion a -> new GreaterThanOrEqualAssertionEvaluator(a);
      case HasHeaderAssertion a -> new HasHeaderAssertionEvaluator(a);
      case IsNullAssertion a -> new IsNullAssertionEvaluator(a);
      case JsonMatchAssertion a ->
          new JsonMatchAssertionEvaluator(a, suiteDir, jsonMapper, suiteVariables, testVariables);
      case JsonSchemaAssertion a -> new JsonSchemaAssertionEvaluator(a, suiteDir, jsonMapper);
      case LessThanAssertion a -> new LessThanAssertionEvaluator(a);
      case LessThanOrEqualAssertion a -> new LessThanOrEqualAssertionEvaluator(a);
      case NotEmptyAssertion a -> new NotEmptyAssertionEvaluator(a);
      case NotNullAssertion a -> new NotNullAssertionEvaluator(a);
      case OneOfAssertion a -> new OneOfAssertionEvaluator(a);
      case RangeAssertion a -> new RangeAssertionEvaluator(a);
      case RegexMatchAssertion a -> new RegexMatchAssertionEvaluator(a);
      case ResponseTimeAssertion a -> new ResponseTimeAssertionEvaluator(a);
      case StartsWithAssertion a -> new StartsWithAssertionEvaluator(a);
      case StatusCodeAssertion a -> new StatusCodeAssertionEvaluator(a);
      case StatusInAssertion a -> new StatusInAssertionEvaluator(a);
      case StringContainsAssertion a -> new StringContainsAssertionEvaluator(a);
      case StringMatchAssertion a -> new StringMatchAssertionEvaluator(a);
      case ValueTypeAssertion a -> new ValueTypeAssertionEvaluator(a);
    };
  }
}
