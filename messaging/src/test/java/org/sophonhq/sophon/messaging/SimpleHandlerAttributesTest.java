/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.sophonhq.sophon.messaging;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link SimpleHandlerAttributes}.
 *
 * @author Steven van Beelen
 */
class SimpleHandlerAttributesTest {

    private static final String ATTRIBUTE_KEY = "some-handler.some-attribute";
    private static final int ATTRIBUTE = 42;

    @Test
    void constructEmptyHandlerAttributes() {
        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(Collections.emptyMap());

        assertThat(testSubject.isEmpty()).isTrue();
    }

    @Test
    void constructNonEmptyHandlerAttributes() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(testAttributes);

        assertThat(testSubject.isEmpty()).isFalse();
        assertThat(testSubject.contains(ATTRIBUTE_KEY)).isTrue();
        assertThat(testSubject.getAll()).containsExactlyInAnyOrderEntriesOf(testAttributes);
    }

    @Test
    void get() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(testAttributes);

        assertThat((int) testSubject.get(ATTRIBUTE_KEY)).isEqualTo(ATTRIBUTE);
    }

    @Test
    void getAll() {
        Map<String, Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(expectedAttributes);

        assertThat(testSubject.getAll()).containsExactlyInAnyOrderEntriesOf(expectedAttributes);
    }

    @Test
    void contains() {
        Map<String, Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(expectedAttributes);

        assertThat(testSubject.contains(ATTRIBUTE_KEY)).isTrue();
        assertThat(testSubject.contains("some-other-handler")).isFalse();
    }

    @Test
    void mergedWith() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);
        Map<String, Object> testOtherAttributes = new HashMap<>();
        testOtherAttributes.put("some-other-key", 1729);
        SimpleHandlerAttributes testOther = new SimpleHandlerAttributes(testOtherAttributes);

        Map<String, Object> expectedAttributes = new HashMap<>();
        expectedAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);
        expectedAttributes.put("some-other-key", 1729);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(testAttributes);

        HandlerAttributes result = testSubject.mergedWith(testOther);

        assertThat(result.getAll()).containsExactlyInAnyOrderEntriesOf(expectedAttributes);
    }

    @Test
    void mergedWithReturnsThis() {
        Map<String, Object> testAttributes = new HashMap<>();
        testAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(testAttributes);

        assertThat(testSubject.mergedWith(new SimpleHandlerAttributes(Collections.emptyMap()))).isEqualTo(testSubject);
    }

    @Test
    void mergedWithReturnsAdditionalAttributes() {
        Map<String, Object> testOtherAttributes = new HashMap<>();
        testOtherAttributes.put(ATTRIBUTE_KEY, ATTRIBUTE);
        SimpleHandlerAttributes testOther = new SimpleHandlerAttributes(testOtherAttributes);

        SimpleHandlerAttributes expected = new SimpleHandlerAttributes(testOtherAttributes);

        SimpleHandlerAttributes testSubject = new SimpleHandlerAttributes(Collections.emptyMap());

        assertThat(testSubject.mergedWith(testOther)).isEqualTo(expected);
    }
}