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

package org.sophonhq.sophon.eventsourcing.eventstore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.messaging.MetaData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConcatenatingDomainEventStreamTest {

    private DomainEventMessage<String> event1;
    private DomainEventMessage<String> event2;
    private DomainEventMessage<String> event3;
    private DomainEventMessage<String> event4;
    private DomainEventMessage<String> event5;

    @BeforeEach
    void setUp() {
        event1 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), 0,
                                                 "Mock contents 1", MetaData.emptyInstance());
        event2 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), 1,
                                                 "Mock contents 2", MetaData.emptyInstance());
        event3 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), 2,
                                                 "Mock contents 3", MetaData.emptyInstance());
        event4 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), 3,
                                                 "Mock contents 4", MetaData.emptyInstance());
        event5 = new GenericDomainEventMessage<>("type", UUID.randomUUID().toString(), 4,
                                                 "Mock contents 5", MetaData.emptyInstance());
    }

    @Test
    void forEachRemaining() {
        List<DomainEventMessage> expectedMessages = Arrays.asList(event1, event2, event3, event4, event5);

        DomainEventStream concat = new ConcatenatingDomainEventStream(
                DomainEventStream.of(event1, event2), // Initial stream - add all elements
                DomainEventStream.of(event3, event4), // No overlap with previous stream - add all elements
                DomainEventStream.of(event3, event4), // Complete overlap with previous stream - add no elements
                DomainEventStream.of(event4, event5) // Partial overlap with previous stream - add some elements
        );

        List<DomainEventMessage<?>> actualMessages = new ArrayList<>();
        concat.forEachRemaining(actualMessages::add);

        assertThat(actualMessages).isEqualTo(expectedMessages);
    }

    @Test
    void forEachRemainingKeepsDuplicateSequenceIdEventsInSameStream() {
        List<DomainEventMessage> expectedMessages =
                Arrays.asList(event1, event1, event2, event3, event4, event4, event5);

        DomainEventStream concat = new ConcatenatingDomainEventStream(
                DomainEventStream.of(event1, event1, event2),
                DomainEventStream.of(event2, event3),
                DomainEventStream.empty(),
                DomainEventStream.of(event3, event3),
                DomainEventStream.of(event3, event4, event4),
                DomainEventStream.of(event4, event5)
        );

        List<DomainEventMessage<?>> actualMessages = new ArrayList<>();
        concat.forEachRemaining(actualMessages::add);

        assertThat(actualMessages).isEqualTo(expectedMessages);
    }

    @Test
    void concatSkipsDuplicateEvents() {
        DomainEventStream concat = new ConcatenatingDomainEventStream(DomainEventStream.of(event1, event2),
                                                                      DomainEventStream.of(event2, event3),
                                                                      DomainEventStream.of(event3, event4));

        assertThat(concat).hasNext();
        assertThat(concat.next().getPayload()).isSameAs(event1.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event2.getPayload());

        assertThat(concat.peek().getPayload()).isSameAs(event3.getPayload());

        assertThat(concat.next().getPayload()).isSameAs(event3.getPayload());

        assertThat(concat.peek().getPayload()).isSameAs(event4.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event4.getPayload());
        assertThat(concat).isExhausted();
    }

    @Test
    public void concatDoesNotSkipDuplicateSequencesInSameStream() {
        DomainEventStream concat = new ConcatenatingDomainEventStream(DomainEventStream.of(event1, event1, event2),
                                                                      DomainEventStream.of(event2, event2, event3),
                                                                      DomainEventStream.of(event3, event4));

        assertThat(concat).hasNext();
        assertThat(concat.next().getPayload()).isSameAs(event1.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event1.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event2.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event3.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event4.getPayload());
        assertThat(concat).isExhausted();
    }


    @Test
    void lastKnownSequenceReturnsTheLastEventItsSequence() {
        DomainEventStream lastStream = DomainEventStream.of(event4, event5);
        DomainEventStream concat = new ConcatenatingDomainEventStream(DomainEventStream.of(event1),
                                                                      DomainEventStream.of(event2, event3),
                                                                      lastStream);

        assertThat(concat).hasNext();

        assertThat(concat.next().getPayload()).isSameAs(event1.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event2.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event3.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event4.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event5.getPayload());

        assertThat(concat).isExhausted();

        assertThat(concat.getLastSequenceNumber()).isEqualTo(lastStream.getLastSequenceNumber());
    }

    @Test
    void lastKnownSequenceReturnsTheLastEventItsSequenceEventIfEventsHaveGaps() {
        DomainEventStream lastStream = DomainEventStream.of(event4, event5);
        DomainEventStream concat = new ConcatenatingDomainEventStream(DomainEventStream.of(event1, event3),
                                                                      lastStream);

        assertThat(concat).hasNext();

        assertThat(concat.next().getPayload()).isSameAs(event1.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event3.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event4.getPayload());
        assertThat(concat.next().getPayload()).isSameAs(event5.getPayload());

        assertThat(concat).isExhausted();

        assertThat(concat.getLastSequenceNumber()).isEqualTo(lastStream.getLastSequenceNumber());
    }

    @Test
    void lastSequenceNumberWhenLastEventIsFilteredOut() {
        // the last sequence number is the one from the event that is filtered out, e.g. when using upcasters
        DomainEventStream concat = new ConcatenatingDomainEventStream(
                DomainEventStream.of(
                        Stream.of(event1, event2).filter(x -> x != event2),
                        () -> event2.getSequenceNumber()));

        assertThat(concat.next()).isSameAs(event1);
        assertThat(concat).isExhausted();
        assertThat(concat.getLastSequenceNumber()).isEqualTo(event2.getSequenceNumber());
    }

    @Test
    void maxLastSequenceNumberFromDelegates() {
        DomainEventStream concat = new ConcatenatingDomainEventStream(
                DomainEventStream.of(event1, event3),
                DomainEventStream.of(event2));

        assertThat(concat.next()).isSameAs(event1);
        assertThat(concat.next()).isSameAs(event3);
        assertThat(concat).isExhausted();

        assertThat(concat.getLastSequenceNumber()).isEqualTo(event3.getSequenceNumber());
    }

    @Test
    void nullSequenceNumberFromDelegates_NullPointer() {
        DomainEventStream concat = new ConcatenatingDomainEventStream(
                DomainEventStream.of(event1),
                DomainEventStream.of(Stream.empty(), () -> null));

        assertThat(concat.getLastSequenceNumber()).isEqualTo(event1.getSequenceNumber());
    }

}
