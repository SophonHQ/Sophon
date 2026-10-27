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

package io.github.sophonhq.sophon.integrationtests.loopbacktest.synchronous;

import io.github.sophonhq.sophon.commandhandling.AnnotationCommandHandlerAdapter;
import io.github.sophonhq.sophon.commandhandling.CommandBus;
import io.github.sophonhq.sophon.commandhandling.CommandCallback;
import io.github.sophonhq.sophon.commandhandling.CommandHandler;
import io.github.sophonhq.sophon.commandhandling.SimpleCommandBus;
import io.github.sophonhq.sophon.common.lock.LockFactory;
import io.github.sophonhq.sophon.common.lock.PessimisticLockFactory;
import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.EventMessageHandler;
import io.github.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.PropagatingErrorHandler;
import io.github.sophonhq.sophon.eventhandling.SimpleEventHandlerInvoker;
import io.github.sophonhq.sophon.eventhandling.SubscribingEventProcessor;
import io.github.sophonhq.sophon.eventsourcing.EventSourcingHandler;
import io.github.sophonhq.sophon.eventsourcing.EventSourcingRepository;
import io.github.sophonhq.sophon.eventsourcing.GenericAggregateFactory;
import io.github.sophonhq.sophon.eventsourcing.eventstore.DomainEventStream;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import io.github.sophonhq.sophon.modelling.command.AggregateIdentifier;
import io.github.sophonhq.sophon.modelling.command.Repository;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.UUID;

import static io.github.sophonhq.sophon.commandhandling.GenericCommandMessage.asCommandMessage;
import static io.github.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for issue #119
 *
 * @author Allard Buijze
 */
public class SynchronousLoopbackTest {

    private CommandBus commandBus;
    private String aggregateIdentifier;
    private EventStore eventStore;
    private CommandCallback<Object, Object> reportErrorCallback;
    private CommandCallback<Object, Object> expectErrorCallback;

    private static List<DomainEventMessage<?>> anyEventList() {
        return anyList();
    }

    @BeforeEach
    void setUp() {
        aggregateIdentifier = UUID.randomUUID().toString();
        commandBus = SimpleCommandBus.builder().build();
        eventStore = spy(EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build());
        eventStore.publish(new GenericDomainEventMessage<>("test", aggregateIdentifier, 0,
                                                           new AggregateCreatedEvent(aggregateIdentifier), null));
        reset(eventStore);

        reportErrorCallback = (commandMessage, commandResultMessage) -> {
            if (commandResultMessage.isExceptional()) {
                Throwable cause = commandResultMessage.exceptionResult();
                throw new RuntimeException("Failure", cause);
            }
        };
        expectErrorCallback = (commandMessage, commandResultMessage) -> {
            if (commandResultMessage.isExceptional()) {
                Throwable cause = commandResultMessage.exceptionResult();
                assertEquals("Mock exception", cause.getMessage());
            } else {
                fail("Expected this command to fail");
            }
        };
    }

    protected void initializeRepository(LockFactory lockingStrategy) {
        EventSourcingRepository<CountingAggregate> repository =
                EventSourcingRepository.builder(CountingAggregate.class)
                                       .lockFactory(lockingStrategy)
                                       .aggregateFactory(new GenericAggregateFactory<>(CountingAggregate.class))
                                       .eventStore(eventStore)
                                       .build();

        new AnnotationCommandHandlerAdapter<>(new CounterCommandHandler(repository)).subscribe(commandBus);
    }

    @Test
    void loopBackKeepsProperEventOrder_PessimisticLocking() {
        initializeRepository(PessimisticLockFactory.usingDefaults());
        EventMessageHandler eventHandler = event -> {
            DomainEventMessage domainEvent = (DomainEventMessage) event;
            if (event.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event.getPayload();
                if (counterChangedEvent.getCounter() == 1) {
                    commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(domainEvent.getAggregateIdentifier(),
                                                                                  counterChangedEvent.getCounter() +
                                                                                          1)), reportErrorCallback);
                    commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(domainEvent.getAggregateIdentifier(),
                                                                                  counterChangedEvent.getCounter() +
                                                                                          2)), reportErrorCallback);
                }
            }
            return null;
        };
        SimpleEventHandlerInvoker eventHandlerInvoker = SimpleEventHandlerInvoker.builder()
                                                                                 .eventHandlers(eventHandler)
                                                                                 .build();
        SubscribingEventProcessor.builder()
                                 .name("processor")
                                 .eventHandlerInvoker(eventHandlerInvoker)
                                 .messageSource(eventStore)
                                 .build()
                                 .start();

        commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(aggregateIdentifier, 1)), reportErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents(aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        //noinspection Duplicates
        while (storedEvents.hasNext()) {
            DomainEventMessage next = storedEvents.next();
            if (next.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next.getPayload();
                assertEquals(event.getCounter(), next.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).publish(anyEventList());
    }

    @Test
    void loopBackKeepsProperEventOrder_PessimisticLocking_ProcessingFails() {
        initializeRepository(PessimisticLockFactory.usingDefaults());
        EventMessageHandler eventHandler = event -> {
            DomainEventMessage domainEvent = (DomainEventMessage) event;
            if (event.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent counterChangedEvent = (CounterChangedEvent) event.getPayload();
                if (counterChangedEvent.getCounter() == 1) {
                    commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(domainEvent.getAggregateIdentifier(),
                                                                                  counterChangedEvent.getCounter() +
                                                                                          1)), reportErrorCallback);
                    commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(domainEvent.getAggregateIdentifier(),
                                                                                  counterChangedEvent.getCounter() +
                                                                                          2)), reportErrorCallback);
                } else if (counterChangedEvent.getCounter() == 2) {
                    throw new RuntimeException("Mock exception");
                }
            }
            return null;
        };
        SimpleEventHandlerInvoker eventHandlerInvoker =
                SimpleEventHandlerInvoker.builder()
                                         .eventHandlers(eventHandler)
                                         .listenerInvocationErrorHandler(PropagatingErrorHandler.INSTANCE)
                                         .build();
        SubscribingEventProcessor.builder()
                                 .name("processor")
                                 .eventHandlerInvoker(eventHandlerInvoker)
                                 .messageSource(eventStore)
                                 .build()
                                 .start();

        commandBus.dispatch(asCommandMessage(new ChangeCounterCommand(aggregateIdentifier, 1)), expectErrorCallback);

        DomainEventStream storedEvents = eventStore.readEvents(aggregateIdentifier);
        assertTrue(storedEvents.hasNext());
        while (storedEvents.hasNext()) {
            DomainEventMessage next = storedEvents.next();
            if (next.getPayload() instanceof CounterChangedEvent) {
                CounterChangedEvent event = (CounterChangedEvent) next.getPayload();
                assertEquals(event.getCounter(), next.getSequenceNumber());
            }
        }

        verify(eventStore, times(3)).publish(anyEventList());
    }

    private static class CounterCommandHandler {

        private Repository<CountingAggregate> repository;

        private CounterCommandHandler(Repository<CountingAggregate> repository) {
            this.repository = repository;
        }

        @CommandHandler
        public void changeCounter(ChangeCounterCommand command) {
            repository.load(command.getAggregateId()).execute(r -> r.setCounter(command.getNewValue()));
        }
    }

    private static class ChangeCounterCommand {

        private String aggregateId;
        private int newValue;

        private ChangeCounterCommand(String aggregateId, int newValue) {
            this.aggregateId = aggregateId;
            this.newValue = newValue;
        }

        public String getAggregateId() {
            return aggregateId;
        }

        public int getNewValue() {
            return newValue;
        }
    }

    private static class AggregateCreatedEvent {

        private final String aggregateIdentifier;

        private AggregateCreatedEvent(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }
    }

    private static class CountingAggregate {

        private static final long serialVersionUID = -2927751585905120260L;

        private int counter = 0;

        @AggregateIdentifier
        private String identifier;

        private CountingAggregate(String identifier) {
            apply(new AggregateCreatedEvent(identifier));
        }

        CountingAggregate() {
        }

        public void setCounter(int newValue) {
            apply(new CounterChangedEvent(newValue));
        }

        @EventSourcingHandler
        private void handleCreatedEvent(AggregateCreatedEvent event) {
            this.identifier = event.getAggregateIdentifier();
        }

        @EventSourcingHandler
        private void handleCounterIncreased(CounterChangedEvent event) {
            this.counter = event.getCounter();
        }
    }

    private static class CounterChangedEvent {

        private final int counter;

        private CounterChangedEvent(int counter) {
            this.counter = counter;
        }

        public int getCounter() {
            return counter;
        }
    }
}
