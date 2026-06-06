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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.service.assertion.ResponseValueExtractor.Result;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ResponseValueExtractor}. */
class ResponseValueExtractorTest {

    @Test
    void pathNotStartingWithResponseReturnsError() {
        ApiResponse response = new ApiResponse(200, Map.of(), null);
        Result result = ResponseValueExtractor.extract(response, "invalid.path");
        assertThat(result).isInstanceOf(Result.Error.class);
        assertThat(((Result.Error) result).message()).contains("must start with 'response.'");
    }

    @Test
    void statusCodeReturnsFoundWithCode() {
        ApiResponse response = new ApiResponse(201, Map.of(), null);
        Result result = ResponseValueExtractor.extract(response, "response.statusCode");
        assertThat(result).isInstanceOf(Result.Found.class);
        assertThat(((Result.Found) result).value()).isEqualTo(201);
    }

    @Test
    void headerPresentReturnsFoundWithValue() {
        ApiResponse response = new ApiResponse(200, Map.of("content-type", "application/json"), null);
        Result result = ResponseValueExtractor.extract(response, "response.headers.content-type");
        assertThat(result).isInstanceOf(Result.Found.class);
        assertThat(((Result.Found) result).value()).isEqualTo("application/json");
    }

    @Test
    void headerAbsentFromPresentHeadersReturnsMissing() {
        // containsKey check fires first → absent key returns Missing, not Found(null)
        ApiResponse response = new ApiResponse(200, Map.of("content-type", "application/json"), null);
        Result result = ResponseValueExtractor.extract(response, "response.headers.x-missing");
        assertThat(result).isInstanceOf(Result.Missing.class);
    }

    @Test
    void headersNullReturnsMissing() {
        ApiResponse response = new ApiResponse(200, null, null);
        Result result = ResponseValueExtractor.extract(response, "response.headers.content-type");
        assertThat(result).isInstanceOf(Result.Missing.class);
    }

    @Test
    void bodyTextNullBodyReturnsMissing() {
        ApiResponse response = new ApiResponse(200, Map.of(), null);
        Result result = ResponseValueExtractor.extract(response, "response.body.text");
        assertThat(result).isInstanceOf(Result.Missing.class);
    }

    @Test
    void bodyTextWithBodyReturnsFoundWithText() {
        ApiResponse response = new ApiResponse(200, Map.of(), new ApiResponse.Body("hello world", null));
        Result result = ResponseValueExtractor.extract(response, "response.body.text");
        assertThat(result).isInstanceOf(Result.Found.class);
        assertThat(((Result.Found) result).value()).isEqualTo("hello world");
    }

    @Test
    void bodyJsonNullBodyReturnsMissing() {
        ApiResponse response = new ApiResponse(200, Map.of(), null);
        Result result = ResponseValueExtractor.extract(response, "response.body.json");
        assertThat(result).isInstanceOf(Result.Missing.class);
    }

    @Test
    void bodyJsonWithBodyReturnsFoundWithParsedJson() {
        Object json = Map.of("id", 1);
        ApiResponse response = new ApiResponse(200, Map.of(), new ApiResponse.Body("{\"id\":1}", json));
        Result result = ResponseValueExtractor.extract(response, "response.body.json");
        assertThat(result).isInstanceOf(Result.Found.class);
        assertThat(((Result.Found) result).value()).isEqualTo(json);
    }

    @Test
    void bodyJsonSubpathNullBodyReturnsMissing() {
        ApiResponse response = new ApiResponse(200, Map.of(), null);
        Result result = ResponseValueExtractor.extract(response, "response.body.json.$.id");
        assertThat(result).isInstanceOf(Result.Missing.class);
    }

    @Test
    void bodyJsonSubpathNullTextReturnsMissing() {
        ApiResponse response = new ApiResponse(200, Map.of(), new ApiResponse.Body(null, null));
        Result result = ResponseValueExtractor.extract(response, "response.body.json.$.id");
        assertThat(result).isInstanceOf(Result.Missing.class);
    }

    @Test
    void bodyJsonSubpathFoundReturnsFoundWithValue() {
        ApiResponse response =
                new ApiResponse(200, Map.of(), new ApiResponse.Body("{\"name\":\"Alice\"}", Map.of("name", "Alice")));
        Result result = ResponseValueExtractor.extract(response, "response.body.json.$.name");
        assertThat(result).isInstanceOf(Result.Found.class);
        assertThat(((Result.Found) result).value()).isEqualTo("Alice");
    }

    @Test
    void bodyJsonSubpathNotFoundReturnsMissing() {
        ApiResponse response =
                new ApiResponse(200, Map.of(), new ApiResponse.Body("{\"name\":\"Alice\"}", Map.of("name", "Alice")));
        Result result = ResponseValueExtractor.extract(response, "response.body.json.$.missing");
        assertThat(result).isInstanceOf(Result.Missing.class);
    }

    @Test
    void bodyJsonSubpathWithInvalidJsonpathExpressionReturnsError() {
        // An invalid JSONPath syntax (unclosed bracket) throws InvalidPathException,
        // which is caught by the general catch(Exception) branch and returns Error.
        ApiResponse response = new ApiResponse(200, Map.of(), new ApiResponse.Body("{\"name\":\"Alice\"}", null));
        Result result = ResponseValueExtractor.extract(response, "response.body.json.$.[");
        assertThat(result).isInstanceOf(Result.Error.class);
        assertThat(((Result.Error) result).message()).contains("Failed to evaluate JSONPath");
    }

    @Test
    void unrecognisedResponseSubpathReturnsError() {
        ApiResponse response = new ApiResponse(200, Map.of(), null);
        Result result = ResponseValueExtractor.extract(response, "response.unknown.field");
        assertThat(result).isInstanceOf(Result.Error.class);
        assertThat(((Result.Error) result).message()).contains("Unsupported path");
    }
}
