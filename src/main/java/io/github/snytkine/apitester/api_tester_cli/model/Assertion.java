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
package io.github.snytkine.apitester.api_tester_cli.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = IsNullAssertion.class, name = "is_null"),
  @JsonSubTypes.Type(value = JsonMatchAssertion.class, name = "json_match"),
  @JsonSubTypes.Type(value = JsonSchemaAssertion.class, name = "json_schema"),
  @JsonSubTypes.Type(value = NotEmptyAssertion.class, name = "not_empty"),
  @JsonSubTypes.Type(value = NotNullAssertion.class, name = "not_null"),
  @JsonSubTypes.Type(value = ResponseTimeAssertion.class, name = "response_time"),
  @JsonSubTypes.Type(value = StatusCodeAssertion.class, name = "status_code"),
  @JsonSubTypes.Type(value = StringContainsAssertion.class, name = "string_contains"),
  @JsonSubTypes.Type(value = StringMatchAssertion.class, name = "string_match"),
})
public sealed interface Assertion
    permits IsNullAssertion,
        JsonMatchAssertion,
        JsonSchemaAssertion,
        NotEmptyAssertion,
        NotNullAssertion,
        ResponseTimeAssertion,
        StatusCodeAssertion,
        StringContainsAssertion,
        StringMatchAssertion {}
