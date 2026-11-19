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

package org.sophonhq.sophon.eventhandling.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.messaging.MetaData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating the {@link PropertySequencingPolicy}.
 *
 * @author Nils Christian Ehmke
 */
@DisplayName("Unit-Test for the PropertySequencingPolicy")
final class PropertySequencingPolicyTest {

    @Test
    void propertyExtractorShouldReadCorrectValue() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyExtractor(TestEvent::getId)
                .build();

        assertThat(sequencingPolicy.getSequenceIdentifierFor(newStubDomainEvent(new TestEvent("42")))).isEqualTo("42");
    }

    @Test
    void propertyShouldReadCorrectValue() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyName("Id")
                .build();

        assertThat(sequencingPolicy.getSequenceIdentifierFor(newStubDomainEvent(new TestEvent("42")))).isEqualTo("42");
    }

    @Test
    void defaultFallbackShouldThrowException() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyName("Id")
                .build();

        assertThatThrownBy(() -> sequencingPolicy.getSequenceIdentifierFor(newStubDomainEvent("42")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fallbackShouldBeApplied() {
        final PropertySequencingPolicy<TestEvent, String> sequencingPolicy = PropertySequencingPolicy
                .builder(TestEvent.class, String.class)
                .propertyName("Id")
                .fallbackSequencingPolicy(SequentialPerAggregatePolicy.instance())
                .build();

        assertThat(sequencingPolicy.getSequenceIdentifierFor(newStubDomainEvent("42"))).isEqualTo("A");
    }

    private DomainEventMessage<?> newStubDomainEvent(final Object payload) {
        return new GenericDomainEventMessage<>("type", "A", 0L, payload, MetaData.emptyInstance());
    }

    private static class TestEvent {

        private final String id;

        public TestEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }
}
