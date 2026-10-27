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

package org.sophonhq.sophon.integrationtests.modelling.command;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.sophonhq.sophon.common.Registration;
import org.sophonhq.sophon.eventhandling.AbstractEventBus;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.eventhandling.TrackingEventStream;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.eventsourcing.EventSourcingRepository;
import org.sophonhq.sophon.eventsourcing.eventstore.DomainEventStream;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import org.sophonhq.sophon.integrationtests.utils.StubAggregate;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CommandHandlingTest {

    private EventSourcingRepository<StubAggregate> repository;
    private String aggregateIdentifier;
    private StubEventStore stubEventStore;

    @BeforeEach
    void setUp() {
        stubEventStore = StubEventStore.builder().build();
        repository = EventSourcingRepository.builder(StubAggregate.class)
                .eventStore(stubEventStore)
                .build();
        aggregateIdentifier = "testAggregateIdentifier";
    }

    @Test
    void commandHandlerLoadsSameAggregateTwice() throws Exception {
        DefaultUnitOfWork.startAndGet(null);
        repository.newInstance(() -> new StubAggregate(aggregateIdentifier)).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet(null);
        repository.load(aggregateIdentifier).execute(StubAggregate::doSomething);
        repository.load(aggregateIdentifier).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        Iterator<? extends DomainEventMessage<?>> es = stubEventStore.readEvents(aggregateIdentifier);
        assertTrue(es.hasNext());
        assertEquals((Object) 0L, es.next().getSequenceNumber());
        assertTrue(es.hasNext());
        assertEquals((Object) 1L, es.next().getSequenceNumber());
        assertTrue(es.hasNext());
        assertEquals((Object) 2L, es.next().getSequenceNumber());
        assertFalse(es.hasNext());
    }

    private static class StubEventStore extends AbstractEventBus implements EventStore {

        private final List<DomainEventMessage<?>> storedEvents = new LinkedList<>();

        private StubEventStore(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
        }

        @Override
        public DomainEventStream readEvents(@NonNull String identifier) {
            return DomainEventStream.of(new ArrayList<>(storedEvents));
        }

        @Override
        protected void commit(List<? extends EventMessage<?>> events) {
            storedEvents.addAll(events.stream().map(StubEventStore::asDomainEventMessage).collect(Collectors.toList()));
        }

        private static DomainEventMessage<?> asDomainEventMessage(EventMessage<?> event) {
            return event instanceof DomainEventMessage<?>
                    ? (DomainEventMessage<?>) event
                    : new GenericDomainEventMessage<>(null, event.getIdentifier(), 0L, event, event::getTimestamp);
        }

        @Override
        public void storeSnapshot(@NonNull DomainEventMessage<?> snapshot) {
        }

        @Override
        public TrackingEventStream openStream(TrackingToken trackingToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration subscribe(@NonNull Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            throw new UnsupportedOperationException();
        }

        private static class Builder extends AbstractEventBus.Builder {

            private StubEventStore build() {
                return new StubEventStore(this);
            }
        }
    }
}
