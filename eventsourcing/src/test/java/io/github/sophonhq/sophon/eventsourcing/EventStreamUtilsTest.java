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

package io.github.sophonhq.sophon.eventsourcing;

import io.github.sophonhq.sophon.common.IdentifierFactory;
import io.github.sophonhq.sophon.eventhandling.DomainEventData;
import io.github.sophonhq.sophon.eventhandling.GenericDomainEventEntry;
import io.github.sophonhq.sophon.eventsourcing.eventstore.DomainEventStream;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.serialization.upcasting.event.EventUpcasterChain;
import io.github.sophonhq.sophon.serialization.upcasting.event.NoOpEventUpcaster;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Rene de Waele
 */
class EventStreamUtilsTest {

    private Serializer serializer;

    @BeforeEach
    void setUp() {
        serializer = mock(Serializer.class);
    }

    @Test
    void domainEventStream_lastSequenceNumberEqualToLastProcessedEntry() {
        DomainEventStream eventStream = EventStreamUtils
                .upcastAndDeserializeDomainEvents(Stream.of(createEntry(1)), serializer,
                                                  NoOpEventUpcaster.INSTANCE);
        assertNull(eventStream.getLastSequenceNumber());
        eventStream.forEachRemaining(Objects::requireNonNull);
        assertEquals(Long.valueOf(1L), eventStream.getLastSequenceNumber());
    }

    @Test
    void domainEventStream_lastSequenceNumberEqualToLastProcessedEntryAfterIgnoringLastEntry() {
        DomainEventStream eventStream = EventStreamUtils
                .upcastAndDeserializeDomainEvents(Stream.of(createEntry(1), createEntry(2), createEntry(3)), serializer,
                                                  new EventUpcasterChain(e -> e
                                                          .filter(entry -> entry.getSequenceNumber().get() < 2L))
                );
        assertNull(eventStream.getLastSequenceNumber());
        assertTrue(eventStream.hasNext());
        eventStream.forEachRemaining(Objects::requireNonNull);
        assertEquals(Long.valueOf(3L), eventStream.getLastSequenceNumber());
    }

    @Test
    void domainEventStream_lastSequenceNumberEqualToLastProcessedEntryAfterUpcastingToEmptyStream() {
        DomainEventStream eventStream = EventStreamUtils
                .upcastAndDeserializeDomainEvents(Stream.of(createEntry(1)), serializer,
                                                  new EventUpcasterChain(s -> s.filter(e -> false)));
        assertNull(eventStream.getLastSequenceNumber());
        assertFalse(eventStream.hasNext());
        eventStream.forEachRemaining(Objects::requireNonNull);
        assertEquals(Long.valueOf(1L), eventStream.getLastSequenceNumber());
    }

    @Test
    void domainEventStream_nullPointerExceptionOnEmptyEventStream() {
        DomainEventStream eventStream = EventStreamUtils.upcastAndDeserializeDomainEvents(Stream.empty(),
            serializer, NoOpEventUpcaster.INSTANCE);
        assertNull(eventStream.getLastSequenceNumber());
    }

    private static DomainEventData<?> createEntry(long sequenceNumber) {
        return new GenericDomainEventEntry<>("type", "testAggregate", sequenceNumber,
                                             IdentifierFactory.getInstance().generateIdentifier(), Instant.now(),
                                             String.class.getName(), null, "test", "metadata");
    }

}
