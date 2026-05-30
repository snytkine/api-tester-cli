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
package io.github.snytkine.apitester.api_tester_cli.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlyphsTest {

  @Test
  void passGlyphIsNonEmpty() {
    assertThat(Glyphs.PASS).isNotEmpty();
  }

  @Test
  void failGlyphIsNonEmpty() {
    assertThat(Glyphs.FAIL).isNotEmpty();
  }

  @Test
  void runningGlyphIsNonEmpty() {
    assertThat(Glyphs.RUNNING).isNotEmpty();
  }

  @Test
  void pendingGlyphIsNonEmpty() {
    assertThat(Glyphs.PENDING).isNotEmpty();
  }

  @Test
  void spinnerFramesHasTenEntries() {
    assertThat(Glyphs.SPINNER_FRAMES).hasSize(10);
  }

  @Test
  void spinnerFramesContainsNoBlanks() {
    assertThat(Glyphs.SPINNER_FRAMES).doesNotContain((String) null).doesNotContain("");
  }

  @Test
  void passAndFailGlyphsAreDistinct() {
    assertThat(Glyphs.PASS).isNotEqualTo(Glyphs.FAIL);
  }
}
