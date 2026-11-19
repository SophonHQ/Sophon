/*
 * Copyright (c) 2010-2018. Axon Framework
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

class IdentifierValidatorTest {

    private final IdentifierValidator validator = IdentifierValidator.getInstance();

    @Test
    void boxedPrimitivesAreValidIdentifiers() {
        assertThat(validator.isValidIdentifier(Long.class)).isTrue();
        assertThat(validator.isValidIdentifier(Integer.class)).isTrue();
        assertThat(validator.isValidIdentifier(Double.class)).isTrue();
        assertThat(validator.isValidIdentifier(Short.class)).isTrue();
    }

    @Test
    void stringIsValidIdentifier() {
        assertThat(validator.isValidIdentifier(CharSequence.class)).isTrue();
    }

    @Test
    void typeWithoutToStringIsNotAccepted() {
        assertThat(validator.isValidIdentifier(CustomType.class)).isFalse();
    }

    @Test
    void typeWithOverriddenToString() {
        assertThat(validator.isValidIdentifier(CustomType2.class)).isTrue();
    }

    private static class CustomType {
    }

    private static class CustomType2 {
        @Override
        public String toString() {
            return "ok";
        }
    }
}