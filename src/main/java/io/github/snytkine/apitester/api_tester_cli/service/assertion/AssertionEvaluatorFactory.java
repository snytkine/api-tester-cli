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
import io.github.snytkine.apitester.api_tester_cli.model.Assertion;
import io.github.snytkine.apitester.api_tester_cli.model.JsonMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.JsonPathAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.JsonSchemaAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.StatusCodeAssertion;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Factory that maps each {@link Assertion} subtype to the corresponding {@link AssertionEvaluator}
 * implementation.
 *
 * <p>Extending the assertion model with a new type requires adding one new evaluator class and one
 * branch in {@link #create(Assertion, Path)} — nothing else in the engine changes.
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
   * @param assertion the assertion from the test case
   * @param suiteDir directory of the test-suite YAML file, or {@code null} if unavailable
   * @return a matching {@link AssertionEvaluator}
   */
  public AssertionEvaluator create(Assertion assertion, @Nullable Path suiteDir) {
    return switch (assertion) {
      case StatusCodeAssertion a -> new StatusCodeAssertionEvaluator(a);
      case JsonPathAssertion a -> new JsonPathAssertionEvaluator(a);
      case JsonSchemaAssertion a -> new JsonSchemaAssertionEvaluator(a, suiteDir);
      case JsonMatchAssertion a -> new JsonMatchAssertionEvaluator(a, suiteDir, jsonMapper);
    };
  }
}
