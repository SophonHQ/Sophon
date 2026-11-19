/*
 * Copyright (c) 2010-2022. Axon Framework
 * Modifications Copyright (c) 2026. SophonHQ
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sophonhq.sophon.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.common.StringUtils.lowerCaseFirstCharacterOf;

/**
 * Test class validating the {@link StringUtils}.
 *
 * @author Steven van Beelen
 */
class StringUtilsTest {

    @Test
    void nonEmptyOrNullReturnsFalseForEmptyString() {
        assertThat(StringUtils.nonEmptyOrNull("")).isFalse();
    }

    @Test
    void nonEmptyOrNullReturnsFalseForNullString() {
        assertThat(StringUtils.nonEmptyOrNull(null)).isFalse();
    }

    @Test
    void nonEmptyOrNullReturnsTrueForNonEmptyString() {
        assertThat(StringUtils.nonEmptyOrNull("some-string")).isTrue();
    }

    @Test
    void emptyOrNullReturnsTrueForEmptyStrings() {
        assertThat(StringUtils.emptyOrNull("")).isTrue();
    }

    @Test
    void emptyOrNullReturnsTrueForNullString() {
        assertThat(StringUtils.emptyOrNull(null)).isTrue();
    }

    @Test
    void emptyOrNullReturnsFalseForNonEmptyString() {
        assertThat(StringUtils.emptyOrNull("some-string")).isFalse();
    }

    @Test
    void lowerCaseFirstCharacterOfAdjustsFirstCharacterToLowerCase() {
        String fullUppercase = "FOO";
        String lowerCasedOutputOfFullUppercase = "fOO";
        assertThat(lowerCaseFirstCharacterOf(fullUppercase)).isEqualTo(lowerCasedOutputOfFullUppercase);
        assertThat(lowerCaseFirstCharacterOf(lowerCasedOutputOfFullUppercase))
                .isEqualTo(lowerCasedOutputOfFullUppercase);

        String partialUppercase = "FOo";
        String partialLowercase = "fOo";
        assertThat(lowerCaseFirstCharacterOf(partialUppercase)).isEqualTo(partialLowercase);
        assertThat(lowerCaseFirstCharacterOf(partialLowercase)).isEqualTo(partialLowercase);
    }
}