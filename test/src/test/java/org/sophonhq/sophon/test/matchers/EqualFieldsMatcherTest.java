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

package org.sophonhq.sophon.test.matchers;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.test.aggregate.MyEvent;
import org.sophonhq.sophon.test.aggregate.MyOtherEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link EqualFieldsMatcher}.
 *
 * @author Allard Buijze
 */
class EqualFieldsMatcherTest {

    private EqualFieldsMatcher<MyEvent> testSubject;
    private MyEvent expectedEvent;
    private final String aggregateId = "AggregateId";

    @BeforeEach
    void setUp() {
        expectedEvent = new MyEvent(aggregateId, 1);
        testSubject = new EqualFieldsMatcher<>(expectedEvent);
    }

    @Test
    void matches_SameInstance() {
        assertThat(testSubject.matches(expectedEvent)).isTrue();
    }

    @Test
    void matches_EqualInstance() {
        assertThat(testSubject.matches(new MyEvent(aggregateId, 1))).isTrue();
    }

    @Test
    void matches_WrongEventType() {
        assertThat(testSubject.matches(new MyOtherEvent())).isFalse();
    }

    @Test
    void matches_WrongFieldValue() {
        assertThat(testSubject.matches(new MyEvent(aggregateId, 2))).isFalse();
        assertThat(testSubject.getFailedField().getName()).isEqualTo("someValue");
    }

    @Test
    void matches_WrongFieldValueInIgnoredField() {
        testSubject = new EqualFieldsMatcher<>(expectedEvent, field -> !field.getName().equals("someValue"));
        assertThat(testSubject.matches(new MyEvent(aggregateId, 2))).isTrue();
    }

    @Test
    void matches_WrongFieldValueInArray() {
        assertThat(testSubject.matches(new MyEvent(aggregateId, 1, new byte[]{1, 2}))).isFalse();
        assertThat(testSubject.getFailedField().getName()).isEqualTo("someBytes");
    }

    @Test
    void description_AfterSuccess() {
        testSubject.matches(expectedEvent);
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        assertThat(description).hasToString("org.sophonhq.sophon.test.aggregate.MyEvent");
    }

    @Test
    void description_AfterMatchWithWrongType() {
        testSubject.matches(new MyOtherEvent());
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        assertThat(description).hasToString("org.sophonhq.sophon.test.aggregate.MyEvent");
    }

    @Test
    void description_AfterMatchWithWrongFieldValue() {
        testSubject.matches(new MyEvent(aggregateId, 2));
        StringDescription description = new StringDescription();
        testSubject.describeTo(description);
        assertThat(description).hasToString("org.sophonhq.sophon.test.aggregate.MyEvent (failed on field 'someValue')");
    }
}
