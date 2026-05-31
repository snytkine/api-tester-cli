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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.snytkine.apitester.api_tester_cli.model.ApiResponse;
import io.github.snytkine.apitester.api_tester_cli.model.ResponseTimeAssertion;
import io.github.snytkine.apitester.api_tester_cli.util.FailureCollector;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;

class ResponseTimeAssertionEvaluatorTest {

    private static ApiResponse responseWithTime(long responseTimeMs) {
        return new ApiResponse(200, Map.of(), null, responseTimeMs);
    }

    @Test
    void responseWithinThresholdPasses() {
        FailureCollector collector = new FailureCollector();
        new ResponseTimeAssertionEvaluator(new ResponseTimeAssertion(500)).evaluate(responseWithTime(200), collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void responseExactlyAtThresholdPasses() {
        FailureCollector collector = new FailureCollector();
        new ResponseTimeAssertionEvaluator(new ResponseTimeAssertion(500)).evaluate(responseWithTime(500), collector);

        assertThatCode(collector::assertAll).doesNotThrowAnyException();
    }

    @Test
    void responseExceedingThresholdFails() {
        FailureCollector collector = new FailureCollector();
        new ResponseTimeAssertionEvaluator(new ResponseTimeAssertion(500)).evaluate(responseWithTime(501), collector);

        assertThatThrownBy(collector::assertAll)
                .isInstanceOf(MultipleFailuresError.class)
                .hasMessageContaining("500 ms")
                .hasMessageContaining("501 ms");
    }

    @Test
    void nullResponseTimeFails() {
        ApiResponse response = new ApiResponse(200, Map.of(), null);

        FailureCollector collector = new FailureCollector();
        new ResponseTimeAssertionEvaluator(new ResponseTimeAssertion(500)).evaluate(response, collector);

        assertThatThrownBy(collector::assertAll).isInstanceOf(MultipleFailuresError.class);
    }
}
