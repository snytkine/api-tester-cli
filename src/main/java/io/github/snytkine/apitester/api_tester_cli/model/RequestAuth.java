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

/**
 * Authentication configuration for a request or a suite's {@code rest_client} block.
 *
 * <p>The {@code username} and {@code password} may be Thymeleaf template expressions; they are
 * resolved by {@code TestSuiteLoader} during YAML processing before Jackson deserialisation.
 *
 * <p>This record is immutable and thread-safe.
 *
 * @param type the authentication scheme; currently only {@link AuthType#BASIC} is supported
 * @param username the username; supports Thymeleaf expressions, resolved before deserialisation
 * @param password the password; supports Thymeleaf expressions, resolved before deserialisation
 */
public record RequestAuth(AuthType type, String username, String password) {}
