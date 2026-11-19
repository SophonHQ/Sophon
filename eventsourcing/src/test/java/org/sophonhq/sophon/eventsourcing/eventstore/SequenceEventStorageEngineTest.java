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

package org.sophonhq.sophon.eventsourcing.eventstore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.eventhandling.GenericTrackedDomainEventMessage;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.TrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SequenceEventStorageEngine}.
 *
 * @author Allard Buijze
 */
class SequenceEventStorageEngineTest {

    private EventStorageEngine activeStorage;
    private EventStorageEngine historicStorage;

    private SequenceEventStorageEngine testSubject;

    @BeforeEach
    void setUp() {
        activeStorage = mock(EventStorageEngine.class, "activeStorage");
        historicStorage = mock(EventStorageEngine.class, "historicStorage");
        testSubject = new SequenceEventStorageEngine(historicStorage, activeStorage);

        when(historicStorage.readSnapshot(anyString())).thenReturn(Optional.empty());
        when(activeStorage.readSnapshot(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void publishEventsSendsToActiveStorageOnly() {
        List<EventMessage<Object>> events = singletonList(GenericEventMessage.asEventMessage("test"));
        testSubject.appendEvents(events);

        verify(historicStorage, never()).appendEvents(anyList());
        verify(activeStorage).appendEvents(events);
    }

    @Test
    void aggregateEventsAreReadFromHistoricThenActive() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");
        when(historicStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.of(event1));
        when(activeStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.of(event2));

        DomainEventStream actual = testSubject.readEvents("aggregate", 0);
        assertThat((long) actual.getLastSequenceNumber()).isZero();

        assertThat(actual).hasNext();
        assertThat(actual.peek()).isSameAs(event1);
        assertThat(actual.next()).isSameAs(event1);
        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertThat(actual).hasNext();
        assertThat(actual.peek()).isSameAs(event2);
        assertThat(actual.next()).isSameAs(event2);
        assertThat((long) actual.getLastSequenceNumber()).isOne();

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 0);
        inOrder.verify(activeStorage).readEvents("aggregate", 1);

        assertThat(actual).isExhausted();
    }

    @Test
    void aggregateEventsAreReadFromActiveWhenNoHistoricEventsAvailable() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");
        when(historicStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.empty());
        when(activeStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.of(event1, event2));

        DomainEventStream actual = testSubject.readEvents("aggregate", 0);
        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertThat(actual.peek()).isSameAs(event1);
        assertThat(actual.next()).isSameAs(event1);

        assertThat(actual.peek()).isSameAs(event2);
        assertThat(actual.next()).isSameAs(event2);

        Long seq = actual.getLastSequenceNumber();
        assertThat(seq).isNotNull();

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 0);
        inOrder.verify(activeStorage).readEvents("aggregate", 0);

        assertThat(actual).isExhausted();
    }

    @Test
    void aggregateEventsAreReadFromHistoricWhenNoActiveEventsAvailable() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");
        when(historicStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.of(event1, event2));
        when(activeStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.empty());

        DomainEventStream actual = testSubject.readEvents("aggregate", 0);
        assertThat((long) actual.getLastSequenceNumber()).isOne();
        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertThat(actual.peek()).isSameAs(event1);
        assertThat(actual.next()).isSameAs(event1);

        assertThat(actual.peek()).isSameAs(event2);
        assertThat(actual.next()).isSameAs(event2);

        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertThat(actual).isExhausted(); // this initializes second domain stream
        verify(activeStorage).readEvents("aggregate", 2);
        assertThat((long) actual.getLastSequenceNumber()).isOne();

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 0);
        inOrder.verify(activeStorage).readEvents("aggregate", 2);

        assertThat(actual).isExhausted();
    }

    @Test
    void snapshotsStoredInActiveStorage() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        testSubject.storeSnapshot(event1);

        verify(activeStorage).storeSnapshot(event1);
        verify(historicStorage, never()).storeSnapshot(any());
    }

    @Test
    void eventStreamedFromHistoricThenActive() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");
        TrackingToken token1 = new GlobalSequenceTrackingToken(1);
        TrackingToken token2 = new GlobalSequenceTrackingToken(2);

        TrackedEventMessage<?> trackedEvent1 = new GenericTrackedDomainEventMessage<>(token1, event1);
        TrackedEventMessage<?> trackedEvent2 = new GenericTrackedDomainEventMessage<>(token2, event2);

        doReturn(Stream.of(trackedEvent1)).when(historicStorage).readEvents(any(TrackingToken.class), anyBoolean());
        doReturn(Stream.of(trackedEvent2)).when(activeStorage).readEvents(any(TrackingToken.class), anyBoolean());

        GlobalSequenceTrackingToken startToken = new GlobalSequenceTrackingToken(0);
        Stream<? extends TrackedEventMessage<?>> actual = testSubject.readEvents(startToken, true);
        List<? extends TrackedEventMessage<?>> actualList = actual.collect(toList());

        assertThat(actualList.size()).isEqualTo(2);
        assertThat(actualList).isEqualTo(Arrays.asList(trackedEvent1, trackedEvent2));

        verify(historicStorage).readEvents(startToken, true);
        verify(activeStorage).readEvents(token1, true);
    }

    @Test
    void snapshotReadFromActiveThenHistoric() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");

        when(historicStorage.readSnapshot("aggregate")).thenReturn(Optional.of(event1));
        when(activeStorage.readSnapshot("aggregate")).thenReturn(Optional.empty());

        Optional<DomainEventMessage<?>> actual = testSubject.readSnapshot("aggregate");
        assertThat(actual).isPresent();
        assertThat(actual.get()).isSameAs(event1);

        InOrder inOrder = inOrder(historicStorage, activeStorage);
        inOrder.verify(activeStorage).readSnapshot("aggregate");
        inOrder.verify(historicStorage).readSnapshot("aggregate");
    }

    @Test
    void snapshotReadFromActive() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");

        when(historicStorage.readSnapshot("aggregate")).thenReturn(Optional.of(event2));
        when(activeStorage.readSnapshot("aggregate")).thenReturn(Optional.of(event1));

        Optional<DomainEventMessage<?>> actual = testSubject.readSnapshot("aggregate");
        assertThat(actual).isPresent();
        assertThat(actual.get()).isSameAs(event1);

        InOrder inOrder = inOrder(historicStorage, activeStorage);
        inOrder.verify(activeStorage).readSnapshot("aggregate");
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void createTailToken() {
        testSubject.createTailToken();

        verify(historicStorage).createTailToken();
    }

    @Test
    void createHeadToken() {
        testSubject.createHeadToken();

        verify(activeStorage).createHeadToken();
    }

    @Test
    void createTokenAtWhenIsPresentInActiveStorage() {
        Instant now = Instant.now();
        TrackingToken mockTrackingToken = new GlobalSequenceTrackingToken(3);
        when(activeStorage.createTokenAt(now)).thenReturn(mockTrackingToken);

        TrackingToken tokenAt = testSubject.createTokenAt(now);

        assertThat(tokenAt).isEqualTo(mockTrackingToken);
        verify(historicStorage, times(0)).createTokenAt(now);
    }

    @Test
    void createTokenAtWhenIsNotPresentInActiveStorage() {
        Instant now = Instant.now();
        TrackingToken mockTrackingToken = new GlobalSequenceTrackingToken(3);
        when(activeStorage.createTokenAt(now)).thenReturn(null);
        when(historicStorage.createTokenAt(now)).thenReturn(mockTrackingToken);

        TrackingToken tokenAt = testSubject.createTokenAt(now);

        assertThat(tokenAt).isEqualTo(mockTrackingToken);
    }

    @Test
    void streamFromPositionInActiveStorage() {
        historicStorage = new InMemoryEventStorageEngine();
        activeStorage = new InMemoryEventStorageEngine(1);

        testSubject = new SequenceEventStorageEngine(historicStorage, activeStorage);

        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> event2 = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");
        DomainEventMessage<String> event3 = new GenericDomainEventMessage<>("type", "aggregate", 2, "test3");
        historicStorage.appendEvents(event1);

        activeStorage.appendEvents(event2, event3);

        Stream<? extends TrackedEventMessage<?>> stream = testSubject.readEvents(null, true);
        TrackedEventMessage<?> firstEvent = stream.findFirst().orElseThrow(IllegalStateException::new);
        assertThat(firstEvent.getPayload()).isEqualTo("test1");

        Stream<? extends TrackedEventMessage<?>> stream2 = testSubject.readEvents(firstEvent.trackingToken(), true);
        List<TrackedEventMessage<?>> secondBatch = stream2.collect(toList());
        assertThat(secondBatch).hasSize(2);
        assertThat(secondBatch.get(0).getPayload()).isEqualTo("test2");
        assertThat(secondBatch.get(1).getPayload()).isEqualTo("test3");

        Stream<? extends TrackedEventMessage<?>> stream3 =
                testSubject.readEvents(secondBatch.get(0).trackingToken(), true);
        List<TrackedEventMessage<?>> thirdBatch = stream3.collect(toList());
        assertThat(thirdBatch).hasSize(1);
        assertThat(thirdBatch.get(0).getPayload()).isEqualTo("test3");

        Stream<? extends TrackedEventMessage<?>> stream4 =
                testSubject.readEvents(secondBatch.get(1).trackingToken(), true);
        assertThat(stream4.findFirst()).isNotPresent();
    }

    @Test
    void aggregateEventsAreReadFromFirstSequenceNumber() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> snapshotEvent = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");
        DomainEventMessage<String> event3 = new GenericDomainEventMessage<>("type", "aggregate", 2, "test3");

        when(historicStorage.readEvents(eq("aggregate"), eq(0L))).thenReturn(DomainEventStream.of(event1));
        when(historicStorage.readEvents(eq("aggregate"), longThat(l -> l > 0))).thenReturn(DomainEventStream.empty());

        when(activeStorage.readEvents(eq("aggregate"), longThat(l -> l < 2)))
                .thenReturn(DomainEventStream.of(snapshotEvent, event3));
        when(activeStorage.readEvents(eq("aggregate"), eq(2L))).thenReturn(DomainEventStream.of(event3));
        when(activeStorage.readEvents(eq("aggregate"), longThat(l -> l > 2))).thenReturn(DomainEventStream.empty());

        // Emulate readEvents from AbstractEventStore.readEvents(java.lang.String) after snapshot
        DomainEventStream actual = testSubject.readEvents("aggregate", 2);

        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertThat(actual).hasNext();
        assertThat(actual.peek()).isSameAs(event3);
        assertThat(actual.next()).isSameAs(event3);
        assertThat((long) actual.getLastSequenceNumber()).isEqualTo(2L);

        assertThat(actual).isExhausted();

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 2);
        inOrder.verify(activeStorage).readEvents("aggregate", 2);
    }

    @Test
    void aggregateEventsAreReadFromFirstSequenceNumberHistoricOnly() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> snapshotEvent = new GenericDomainEventMessage<>("type", "aggregate", 1, "test2");
        DomainEventMessage<String> event3 = new GenericDomainEventMessage<>("type", "aggregate", 2, "test3");

        when(historicStorage.readEvents(eq("aggregate"), eq(0L)))
                .thenReturn(DomainEventStream.of(event1, snapshotEvent, event3));
        when(historicStorage.readEvents(eq("aggregate"), eq(1L)))
                .thenReturn(DomainEventStream.of(snapshotEvent, event3));
        when(historicStorage.readEvents(eq("aggregate"), eq(2L))).thenReturn(DomainEventStream.of(event3));
        when(historicStorage.readEvents(eq("aggregate"), longThat(l -> l > 2))).thenReturn(DomainEventStream.empty());

        when(activeStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.empty());

        // Emulate readEvents from AbstractEventStore.readEvents(java.lang.String) after snapshot
        DomainEventStream actual = testSubject.readEvents("aggregate", 2);

        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertThat(actual).hasNext();
        assertThat(actual.peek()).isSameAs(event3);
        assertThat(actual.next()).isSameAs(event3);
        assertThat((long) actual.getLastSequenceNumber()).isEqualTo(2L);

        assertThat(actual).isExhausted();

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 2);
        inOrder.verify(activeStorage).readEvents("aggregate", 3);
    }

    @Test
    void aggregateEventsAreReadFromFirstSequenceNumberActiveOnly() {
        DomainEventMessage<String> event1 = new GenericDomainEventMessage<>("type", "aggregate", 0, "test1");
        DomainEventMessage<String> snapshotEvent = new GenericDomainEventMessage<>("type", "aggregate", 1, "test3");
        DomainEventMessage<String> event3 = new GenericDomainEventMessage<>("type", "aggregate", 2, "test4");

        when(historicStorage.readEvents(eq("aggregate"), anyLong())).thenReturn(DomainEventStream.empty());

        when(activeStorage.readEvents(eq("aggregate"), eq(0L)))
                .thenReturn(DomainEventStream.of(event1, snapshotEvent, event3));
        when(activeStorage.readEvents(eq("aggregate"), eq(1L))).thenReturn(DomainEventStream.of(snapshotEvent, event3));
        when(activeStorage.readEvents(eq("aggregate"), eq(2L))).thenReturn(DomainEventStream.of(event3));
        when(activeStorage.readEvents(eq("aggregate"), longThat(l -> l > 2))).thenReturn(DomainEventStream.empty());

        // Emulate readEvents from AbstractEventStore.readEvents(java.lang.String) after snapshot
        DomainEventStream actual = testSubject.readEvents("aggregate", 2);

        verify(activeStorage, never()).readEvents(anyString(), anyLong());

        assertThat(actual).hasNext();
        assertThat(actual.peek()).isSameAs(event3);
        assertThat(actual.next()).isSameAs(event3);
        assertThat((long) actual.getLastSequenceNumber()).isEqualTo(2L);

        assertThat(actual).isExhausted();

        InOrder inOrder = Mockito.inOrder(historicStorage, activeStorage);
        inOrder.verify(historicStorage).readEvents("aggregate", 2);
        inOrder.verify(activeStorage).readEvents("aggregate", 2);
    }
}
