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
package io.github.snytkine.apitester.api_tester_cli.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link VersionComparator}. */
class VersionComparatorTest {

    @Test
    void newerVersion_whenLatestIsNumericallyGreater_returnsLatest() {
        Optional<String> result = VersionComparator.newerVersion("0.4.1", "0.5.0");
        assertThat(result).contains("0.5.0");
    }

    @Test
    void newerVersion_whenLatestIsNumericallyLesser_returnsEmpty() {
        Optional<String> result = VersionComparator.newerVersion("0.5.0", "0.4.1");
        assertThat(result).isEmpty();
    }

    @Test
    void newerVersion_whenVersionsAreEqual_returnsEmpty() {
        Optional<String> result = VersionComparator.newerVersion("0.5.0", "0.5.0");
        assertThat(result).isEmpty();
    }

    @Test
    void newerVersion_whenRunningIsSnapshotOfSameRelease_returnsLatest() {
        Optional<String> result = VersionComparator.newerVersion("0.4.1-SNAPSHOT", "0.4.1");
        assertThat(result).contains("0.4.1");
    }

    @Test
    void newerVersion_whenRunningIsPreReleaseSuffixOfSameRelease_returnsLatest() {
        Optional<String> result = VersionComparator.newerVersion("0.4.1-rc1", "0.4.1");
        assertThat(result).contains("0.4.1");
    }

    @Test
    void newerVersion_whenRunningIsUnknown_returnsEmpty() {
        Optional<String> result = VersionComparator.newerVersion("unknown", "0.5.0");
        assertThat(result).isEmpty();
    }

    @Test
    void newerVersion_whenRunningIsMalformed_returnsEmpty() {
        Optional<String> result = VersionComparator.newerVersion("not-a-version", "0.5.0");
        assertThat(result).isEmpty();
    }

    @Test
    void newerVersion_whenLatestIsMalformed_returnsEmpty() {
        Optional<String> result = VersionComparator.newerVersion("0.4.1", "not-a-version");
        assertThat(result).isEmpty();
    }

    @Test
    void newerVersion_toleratesLeadingVOnEitherSide() {
        assertThat(VersionComparator.newerVersion("v0.4.1", "v0.5.0")).contains("v0.5.0");
    }

    @Test
    void newerVersion_whenRunningIsNumericallyAheadOfLatest_returnsEmpty() {
        // A local/dev build ahead of the last tagged release should not trigger an upgrade notice.
        Optional<String> result = VersionComparator.newerVersion("1.0.0", "0.9.0");
        assertThat(result).isEmpty();
    }
}
