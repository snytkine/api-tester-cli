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
import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.Assertion;
import io.github.snytkine.apitester.api_tester_cli.model.assertions.StatusCodeAssertion;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Converts a {@link RestClient.ResponseSpec} into a fully-populated {@link ApiResponse}.
 *
 * <p>The resolution level is determined from the assertion list: when all assertions are {@link
 * StatusCodeAssertion} instances only the status code and headers are read from the wire; otherwise
 * the body is also consumed, parsed as JSON where possible, and stored in {@link ApiResponse.Body}.
 *
 * <p>All 4xx and 5xx responses are captured without throwing — the custom {@code onStatus} handler
 * suppresses Spring's default error-throwing behaviour so every status code reaches the assertion
 * layer.
 *
 * <p>This class is a thread-safe Spring singleton: the only shared field is a configured {@link
 * ObjectMapper}, which is immutable after construction.
 */
@Component
public class ResponseResolver {

    private final ObjectMapper jsonMapper;

    /**
     * Constructs the resolver with an {@link ObjectMapper} for JSON body parsing. No extra modules
     * are registered; standard Jackson databind covers all response body types encountered in
     * practice and avoids classpath-scanning module discovery that is incompatible with GraalVM
     * native compilation.
     */
    public ResponseResolver() {
        this.jsonMapper = new ObjectMapper();
    }

    /**
     * Determines the minimum {@link ResponseResolutionLevel} required by the given assertions.
     *
     * <p>Returns {@link ResponseResolutionLevel#STATUS_ONLY} when every assertion is a {@link
     * StatusCodeAssertion}; otherwise returns {@link ResponseResolutionLevel#FULL}.
     *
     * @param assertions the assertions to examine
     * @return the minimum resolution level needed
     */
    private ResponseResolutionLevel determineLevel(List<Assertion> assertions) {
        return assertions.stream().allMatch(a -> a instanceof StatusCodeAssertion)
                ? ResponseResolutionLevel.STATUS_ONLY
                : ResponseResolutionLevel.FULL;
    }

    /**
     * Resolves a {@link RestClient.ResponseSpec} into an {@link ApiResponse} using the minimum
     * extraction level dictated by the assertion list.
     *
     * <p>The response body is read at most once. All HTTP status codes (including 4xx and 5xx) are
     * captured rather than thrown as exceptions.
     *
     * @param responseSpec the response spec to consume
     * @param assertions the assertions that will be evaluated against the result
     * @return a fully populated {@link ApiResponse}
     */
    public ApiResponse resolve(RestClient.ResponseSpec responseSpec, List<Assertion> assertions) {
        // Suppress Spring's default error-throwing for all status codes.
        RestClient.ResponseSpec spec = responseSpec.onStatus(status -> true, (req, res) -> {});

        if (determineLevel(assertions) == ResponseResolutionLevel.STATUS_ONLY) {
            long startNs = System.nanoTime();
            ResponseEntity<Void> entity = spec.toBodilessEntity();
            long responseTimeMs = (System.nanoTime() - startNs) / 1_000_000;
            return new ApiResponse(
                    entity.getStatusCode().value(), flattenHeaders(entity.getHeaders()), null, responseTimeMs);
        }

        long startNs = System.nanoTime();
        ResponseEntity<String> entity = spec.toEntity(String.class);
        long responseTimeMs = (System.nanoTime() - startNs) / 1_000_000;
        String bodyText = entity.getBody();
        return new ApiResponse(
                entity.getStatusCode().value(),
                flattenHeaders(entity.getHeaders()),
                new ApiResponse.Body(bodyText, tryParseJson(bodyText)),
                responseTimeMs);
    }

    /**
     * Flattens Spring's multi-value {@link HttpHeaders} into a single-value map with lower-cased
     * header names. When a header has multiple values only the first is kept.
     *
     * @param headers the headers from a Spring {@link ResponseEntity}
     * @return a mutable, lower-cased, single-value header map
     */
    private Map<String, String> flattenHeaders(HttpHeaders headers) {
        Map<String, String> flat = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (!values.isEmpty()) {
                flat.put(name.toLowerCase(), values.get(0));
            }
        });
        return flat;
    }

    /**
     * Attempts to parse {@code text} as JSON using Jackson.
     *
     * @param text the raw response body text, may be {@code null} or blank
     * @return the parsed JSON value (a {@link Map}, {@link List}, or scalar), or {@code null} if
     *     {@code text} is absent or not valid JSON
     */
    @Nullable private Object tryParseJson(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(text, Object.class);
        } catch (Exception e) {
            return null;
        }
    }
}
