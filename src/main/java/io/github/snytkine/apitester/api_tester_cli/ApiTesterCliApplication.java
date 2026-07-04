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

import io.github.snytkine.apitester.api_tester_cli.model.AssertionFailure;
import io.github.snytkine.apitester.api_tester_cli.model.AuthType;
import io.github.snytkine.apitester.api_tester_cli.model.BodyType;
import io.github.snytkine.apitester.api_tester_cli.model.BodylessRequest;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.ObjectExpectedValue;
import io.github.snytkine.apitester.api_tester_cli.model.ObjectExpectedValueDeserializer;
import io.github.snytkine.apitester.api_tester_cli.model.PayloadRequest;
import io.github.snytkine.apitester.api_tester_cli.model.Request;
import io.github.snytkine.apitester.api_tester_cli.model.RequestAuth;
import io.github.snytkine.apitester.api_tester_cli.model.RequestBody;
import io.github.snytkine.apitester.api_tester_cli.model.RequestDeserializer;
import io.github.snytkine.apitester.api_tester_cli.model.RestClientConfig;
import io.github.snytkine.apitester.api_tester_cli.model.SuiteRunContext;
import io.github.snytkine.apitester.api_tester_cli.model.TestCase;
import io.github.snytkine.apitester.api_tester_cli.model.TestCaseResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestRunResult;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayContainsAllAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayIsEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArrayIsNotEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeMaxAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ArraySizeMinAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.AssertFalseAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.AssertTrueAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.Assertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.EndsWithAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.GreaterThanAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.GreaterThanOrEqualAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.HasHeaderAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.IsNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.JsonMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.JsonSchemaAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.LessThanAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.LessThanOrEqualAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.NotEmptyAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.NotNullAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.OneOfAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.RangeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.RegexMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ResponseTimeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StartsWithAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusCodeAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusInAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StringContainsAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StringMatchAssertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.ValueTypeAssertion;
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
    ArrayContainsAllAssertion.class,
    ArrayContainsAssertion.class,
    ArrayIsEmptyAssertion.class,
    ArrayIsNotEmptyAssertion.class,
    ArraySizeAssertion.class,
    ArraySizeMaxAssertion.class,
    ArraySizeMinAssertion.class,
    Assertion.class,
    AssertFalseAssertion.class,
    AssertTrueAssertion.class,
    AssertionFailure.class,
    AuthType.class,
    BodylessRequest.class,
    BodyType.class,
    SuiteRunContext.class,
    EndsWithAssertion.class,
    GreaterThanAssertion.class,
    GreaterThanOrEqualAssertion.class,
    HasHeaderAssertion.class,
    HttpMethod.class,
    IsNullAssertion.class,
    JsonMatchAssertion.class,
    JsonSchemaAssertion.class,
    LessThanAssertion.class,
    LessThanOrEqualAssertion.class,
    NotEmptyAssertion.class,
    NotNullAssertion.class,
    ObjectExpectedValue.class,
    ObjectExpectedValueDeserializer.class,
    OneOfAssertion.class,
    PayloadRequest.class,
    RangeAssertion.class,
    RegexMatchAssertion.class,
    Request.class,
    RequestAuth.class,
    RequestBody.class,
    RequestDeserializer.class,
    ResponseTimeAssertion.class,
    RestClientConfig.class,
    StartsWithAssertion.class,
    StatusCodeAssertion.class,
    StatusInAssertion.class,
    StringContainsAssertion.class,
    StringMatchAssertion.class,
    TestCase.class,
    TestCaseResult.class,
    TestRunResult.class,
    TestSuite.class,
    ValueTypeAssertion.class,
})
public class ApiTesterCliApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ApiTesterCliApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setDefaultProperties(Map.of("logging.level.root", "OFF"));
        app.run(args);
    }
}
