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
package io.github.snytkine.apitester.api_tester_cli.model.hooks;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.snytkine.apitester.api_tester_cli.model.HttpMethod;
import io.github.snytkine.apitester.api_tester_cli.model.TestSuite;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A lifecycle hook that issues an outbound HTTP request ({@code type: web}).
 *
 * <p>The request is sent through one of the suite's named rest-clients (defaulting to {@code
 * default}), which supplies the base URL, default headers, auth, and connect timeout. The {@code
 * url} is absolute or relative to that client's base URL. The method defaults to {@code POST} and
 * validation restricts it to {@code POST} or {@code PUT}. Optional {@code payload} entries are
 * merged into the top level of the JSON request body. All string values may contain Thymeleaf
 * expressions, resolved by {@code TestSuiteLoader} before deserialisation.
 *
 * <p>{@code attachReport} is only meaningful for {@code after-report} hooks: when {@code true} the
 * request is sent as {@code multipart/form-data} with the generated HTML report attached.
 *
 * <p>This record is immutable and thread-safe.
 *
 * @param id explicit hook id, or {@code null} to derive a default
 * @param async explicit async flag, or {@code null} (defaults to {@code false})
 * @param timeoutSeconds explicit per-hook timeout, or {@code null} (defaults to {@link
 *     Hook#DEFAULT_TIMEOUT_SECONDS})
 * @param restClient id of the rest-client to use, or {@code null} (defaults to {@code default})
 * @param url the request URL; absolute or relative to the client's base URL
 * @param method the HTTP method, or {@code null} (defaults to {@code POST}); must be {@code POST} or
 *     {@code PUT}
 * @param payload optional user-defined fields merged into the JSON body; may be {@code null}
 * @param attachReport whether to attach the HTML report ({@code after-report} hooks only); may be
 *     {@code null} (defaults to {@code false})
 */
public record WebHook(
        @JsonProperty("id") @Nullable String id,
        @JsonProperty("async") @Nullable Boolean async,
        @JsonProperty("timeout-seconds") @Nullable Integer timeoutSeconds,
        @JsonProperty("rest-client") @Nullable String restClient,
        @JsonProperty("url") String url,
        @JsonProperty("method") @Nullable HttpMethod method,
        @JsonProperty("payload") @Nullable Map<String, String> payload,
        @JsonProperty("attach-report") @Nullable Boolean attachReport)
        implements Hook {

    /**
     * The effective HTTP method, defaulting to {@link HttpMethod#POST} when the YAML omitted it.
     *
     * @return the method actually used for this hook's request
     */
    public HttpMethod effectiveMethod() {
        return method != null ? method : HttpMethod.POST;
    }

    /**
     * The effective rest-client id, defaulting to {@link TestSuite#DEFAULT_REST_CLIENT_ID} when the
     * YAML omitted it (or left it blank).
     *
     * @return the rest-client id whose configuration is used to build the request
     */
    public String effectiveRestClient() {
        return (restClient != null && !restClient.isBlank()) ? restClient : TestSuite.DEFAULT_REST_CLIENT_ID;
    }

    /**
     * Whether the generated HTML report should be attached to this hook's request.
     *
     * @return {@code true} when {@code attach-report} was declared {@code true}
     */
    public boolean isAttachReport() {
        return Boolean.TRUE.equals(attachReport);
    }
}
