/*
 * Copyright (c) 2010-2021. Axon Framework
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

import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link ObjectUtils}.
 *
 * @author Steven van Beelen
 */
class ObjectUtilsTest {

    private static final String NULL_INSTANCE = null;
    private static final String INSTANCE = "instance";
    private static final String DEFAULT_VALUE = "default";

    @Test
    void getOrDefaultUsingValueProvider() {
        Function<String, String> valueProvider = o -> o;
        assertThat(ObjectUtils.getOrDefault(NULL_INSTANCE, valueProvider, DEFAULT_VALUE)).isEqualTo(DEFAULT_VALUE);
        assertThat(ObjectUtils.getOrDefault(INSTANCE, valueProvider, DEFAULT_VALUE)).isEqualTo(INSTANCE);
    }

    @Test
    void supplySameInstance() {
        Supplier<Object> testSubject = ObjectUtils.sameInstanceSupplier(Object::new);
        assertThat(testSubject.get()).isSameAs(testSubject.get());
    }
}