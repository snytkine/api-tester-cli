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

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;

/**
 * Indicates how much of the HTTP response must be extracted to satisfy a set of assertions.
 *
 * <p>The engine selects the minimal level needed so that the response body is not read from the
 * wire when no assertion requires it.
 */
enum ResponseResolutionLevel {

    /**
     * Only the HTTP status code and headers are needed. The response body is not consumed, allowing
     * the underlying connection to be released without reading the payload.
     */
    STATUS_ONLY,

    /**
     * The full response is needed: status code, headers, and the body. The body is read and stored in
     * {@link ApiResponse.Body} as both raw text and a parsed JSON object.
     */
    FULL
}
