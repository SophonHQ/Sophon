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

package org.sophonhq.sophon.modelling.command;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.internal.stubbing.answers.Returns;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.common.legacyjpa.SimpleEntityManagerProvider;
import org.sophonhq.sophon.eventhandling.*;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import org.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository;
import org.sophonhq.sophon.tracing.TestSpanFactory;

import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.LockModeType;
import javax.persistence.Version;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;

/**
 * Test class validating the {@link org.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository}.
 *
 * @author Allard Buijze
 */
class LegacyGenericJpaRepositoryTest {

    private EntityManager mockEntityManager;
    private org.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository<StubJpaAggregate> testSubject;
    private String aggregateId;
    private StubJpaAggregate aggregate;
    private Function<String, ?> identifierConverter;
    private EventBus eventBus;
    private TestSpanFactory spanFactory;

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        mockEntityManager = mock(EntityManager.class);
        //noinspection unchecked
        identifierConverter = mock(Function.class);
        eventBus = SimpleEventBus.builder().build();
        when(identifierConverter.apply(anyString())).thenAnswer(i -> i.getArguments()[0]);
        testSubject = org.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository.builder(StubJpaAggregate.class)
                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                .eventBus(eventBus)
                .identifierConverter(identifierConverter)
                .spanFactory(DefaultRepositorySpanFactory.builder().spanFactory(spanFactory).build())
                .build();
        DefaultUnitOfWork.startAndGet(null);
        aggregateId = "123";
        aggregate = new StubJpaAggregate(aggregateId);
        when(mockEntityManager.find(eq(StubJpaAggregate.class), eq("123"), any(LockModeType.class)))
                .thenReturn(aggregate);
    }

    @AfterEach
    void cleanUp() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void aggregateStoredBeforeEventsPublished() throws Exception {
        //noinspection unchecked
        Consumer<List<? extends EventMessage<?>>> mockConsumer = mock(Consumer.class);
        eventBus.subscribe(mockConsumer);

        testSubject.newInstance(() -> new StubJpaAggregate("test", "payload1", "payload2"));
        CurrentUnitOfWork.commit();

        InOrder inOrder = inOrder(mockEntityManager, mockConsumer);
        inOrder.verify(mockEntityManager).persist(any());
        inOrder.verify(mockConsumer).accept(anyList());
    }

    @Test
    void loadAggregate() {
        Aggregate<StubJpaAggregate> actualResult = testSubject.load(aggregateId);
        assertThat(actualResult.invoke(Function.identity())).isSameAs(aggregate);
    }

    @Test
    void loadAggregateTracing() {
        when(mockEntityManager.find(eq(StubJpaAggregate.class), eq("123"), any(LockModeType.class)))
                .thenAnswer(invocation -> {
                    spanFactory.verifySpanCompleted("Repository.obtainLock");
                    spanFactory.verifySpanActive("Repository.load");
                    return aggregate;
                });
        testSubject.load(aggregateId);
        spanFactory.verifySpanCompleted("Repository.load");
        spanFactory.verifySpanHasAttributeValue("Repository.load", "sophon.aggregateId", aggregateId);
    }

    @Test
    void loadAggregateWithConverter() {
        when(identifierConverter.apply("original")).thenAnswer(new Returns(aggregateId));
        Aggregate<StubJpaAggregate> actualResult = testSubject.load("original");
        assertThat(actualResult.invoke(Function.identity())).isSameAs(aggregate);
    }

    @Test
    void aggregateCreatesSequenceNumbersForNewAggregatesWhenUsingDomainEventSequenceAwareEventBus() {
        DomainSequenceAwareEventBus testEventBus = new DomainSequenceAwareEventBus();

        testSubject = org.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository.builder(StubJpaAggregate.class)
                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                .eventBus(testEventBus)
                .identifierConverter(identifierConverter)
                .build();

        DefaultUnitOfWork.startAndGet(null).executeWithResult(() -> {
            Aggregate<StubJpaAggregate> agg =
                    testSubject.newInstance(() -> new StubJpaAggregate("id", "test1", "test2"));
            agg.execute(e -> e.doSomething("test3"));
            return null;
        });
        CurrentUnitOfWork.commit();

        List<EventMessage<?>> publishedEvents = testEventBus.getPublishedEvents();
        assertThat(publishedEvents).hasSize(3);

        EventMessage<?> eventOne = publishedEvents.get(0);
        assertThat(eventOne).isInstanceOf(DomainEventMessage.class);
        DomainEventMessage<?> domainEventOne = (DomainEventMessage<?>) eventOne;
        assertThat(domainEventOne.getPayload()).isEqualTo("test1");
        assertThat(domainEventOne.getSequenceNumber()).isZero();
        assertThat(domainEventOne.getAggregateIdentifier()).isEqualTo("id");

        EventMessage<?> eventTwo = publishedEvents.get(1);
        assertThat(eventTwo).isInstanceOf(DomainEventMessage.class);
        DomainEventMessage<?> domainEventTwo = (DomainEventMessage<?>) eventTwo;
        assertThat(domainEventTwo.getPayload()).isEqualTo("test2");
        assertThat(domainEventTwo.getSequenceNumber()).isOne();
        assertThat(domainEventTwo.getAggregateIdentifier()).isEqualTo("id");

        EventMessage<?> eventThree = publishedEvents.get(2);
        assertThat(eventThree).isInstanceOf(DomainEventMessage.class);
        DomainEventMessage<?> domainEventThree = (DomainEventMessage<?>) eventThree;
        assertThat(domainEventThree.getPayload()).isEqualTo("test3");
        assertThat(domainEventThree.getSequenceNumber()).isEqualTo(2);
        assertThat(domainEventThree.getAggregateIdentifier()).isEqualTo("id");
    }

    @Test
    void aggregateDoesNotCreateSequenceNumbersWhenEventBusIsNotDomainEventSequenceAware() {
        SimpleEventBus testEventBus = spy(SimpleEventBus.builder().build());

        testSubject = org.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository.builder(StubJpaAggregate.class)
                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                .eventBus(testEventBus)
                .identifierConverter(identifierConverter)
                .build();

        DefaultUnitOfWork.startAndGet(null).executeWithResult(() -> {
            Aggregate<StubJpaAggregate> agg = testSubject.load(aggregateId);
            agg.execute(e -> e.doSomething("test2"));
            return null;
        });

        CurrentUnitOfWork.commit();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<? extends EventMessage<?>>> eventCaptor =
                ArgumentCaptor.forClass((Class<List<? extends EventMessage<?>>>) (Class<?>) List.class);

        verify(testEventBus).publish(eventCaptor.capture());
        List<? extends EventMessage<?>> capturedEvents = eventCaptor.getValue();

        assertThat(capturedEvents.size()).isOne();
        EventMessage<?> eventOne = capturedEvents.get(0);
        assertThat(eventOne).isNotInstanceOf(DomainEventMessage.class);
        assertThat(eventOne.getPayload()).isEqualTo("test2");
    }

    @Test
    void aggregateDoesNotCreateSequenceNumbersWhenSequenceNumberGenerationIsDisabled() {
        String expectedFirstPayload = "test1";
        String expectedSecondPayload = "test2";
        String expectedThirdPayload = "test3";

        DomainSequenceAwareEventBus testEventBus = new DomainSequenceAwareEventBus();
        testSubject = org.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository.builder(StubJpaAggregate.class)
                .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                .eventBus(testEventBus)
                .identifierConverter(identifierConverter)
                .disableSequenceNumberGeneration()
                .build();

        DefaultUnitOfWork.startAndGet(null)
                .executeWithResult(() -> {
                    Aggregate<StubJpaAggregate> aggregate = testSubject.newInstance(
                            () -> new StubJpaAggregate("id", expectedFirstPayload, expectedSecondPayload)
                    );
                    aggregate.execute(e -> e.doSomething(expectedThirdPayload));
                    return null;
                });
        CurrentUnitOfWork.commit();

        List<EventMessage<?>> publishedEvents = testEventBus.getPublishedEvents();
        assertThat(publishedEvents).hasSize(3);

        EventMessage<?> eventOne = publishedEvents.get(0);
        assertThat(eventOne).isNotInstanceOf(DomainEventMessage.class);
        assertThat(eventOne.getPayload()).isEqualTo(expectedFirstPayload);

        EventMessage<?> eventTwo = publishedEvents.get(1);
        assertThat(eventTwo).isNotInstanceOf(DomainEventMessage.class);
        assertThat(eventTwo.getPayload()).isEqualTo(expectedSecondPayload);

        EventMessage<?> eventThree = publishedEvents.get(2);
        assertThat(eventThree).isNotInstanceOf(DomainEventMessage.class);
        assertThat(eventThree.getPayload()).isEqualTo(expectedThirdPayload);
    }

    @Test
    void loadAggregate_NotFound() {
        String aggregateIdentifier = UUID.randomUUID().toString();
        try {
            testSubject.load(aggregateIdentifier);
            fail("Expected AggregateNotFoundException");
        } catch (AggregateNotFoundException e) {
            assertThat(e.getAggregateIdentifier()).isEqualTo(aggregateIdentifier);
        }
    }

    @Test
    void loadAggregate_WrongVersion() {
        try {
            testSubject.load(aggregateId, 2L);
            fail("Expected ConflictingAggregateVersionException");
        } catch (ConflictingAggregateVersionException e) {
            assertThat(e.getExpectedVersion()).isEqualTo(2L);
            assertThat(e.getActualVersion()).isZero();
        }
    }

    @Test
    void persistAggregate_DefaultFlushMode() {
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager).flush();
    }

    @Test
    void persistAggregate_ExplicitFlushModeOn() {
        testSubject.setForceFlushOnSave(true);
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager).flush();
    }

    @Test
    void persistAggregate_ExplicitFlushModeOff() {
        testSubject.setForceFlushOnSave(false);
        testSubject.doSave(testSubject.load(aggregateId));
        verify(mockEntityManager).persist(aggregate);
        verify(mockEntityManager, never()).flush();
    }

    @Test
    void buildWithNullSubtypesThrowsSophonConfigurationException() {
        org.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository.Builder<StubJpaAggregate> builderTestSubject =
                org.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository.builder(StubJpaAggregate.class)
                        .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                        .eventBus(eventBus);

        assertThatThrownBy(() -> builderTestSubject.subtypes(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNullSubtypeThrowsSophonConfigurationException() {
        org.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository.Builder<StubJpaAggregate> builderTestSubject =
                GenericJpaRepository.builder(StubJpaAggregate.class)
                        .entityManagerProvider(new SimpleEntityManagerProvider(mockEntityManager))
                        .eventBus(eventBus);

        assertThatThrownBy(() -> builderTestSubject.subtype(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    private class StubJpaAggregate {

        @Id
        private final String identifier;

        @SuppressWarnings("unused")
        @AggregateVersion
        @Version
        private long version;

        private StubJpaAggregate(String identifier) {
            this.identifier = identifier;
        }

        private StubJpaAggregate(String identifier, String... payloads) {
            this(identifier);
            for (String payload : payloads) {
                apply(payload);
            }
        }

        public void doSomething(String newValue) {
            apply(newValue);
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    private static class DomainSequenceAwareEventBus extends SimpleEventBus implements DomainEventSequenceAware {

        private final List<EventMessage<?>> publishedEvents = new ArrayList<>();
        private final Map<String, Long> sequencePerAggregate = new HashMap<>();

        DomainSequenceAwareEventBus() {
            super(SimpleEventBus.builder());
        }

        @Override
        public void publish(@NonNull List<? extends EventMessage<?>> events) {
            publishedEvents.addAll(events);
            super.publish(events);
        }

        List<EventMessage<?>> getPublishedEvents() {
            return publishedEvents;
        }

        @Override
        public Optional<Long> lastSequenceNumberFor(String aggregateIdentifier) {
            if (!sequencePerAggregate.containsKey(aggregateIdentifier)) {
                sequencePerAggregate.put(aggregateIdentifier, 0L);
            }
            return Optional.ofNullable(
                    sequencePerAggregate.computeIfPresent(aggregateIdentifier, (aggregateId, seqNo) -> seqNo++)
            );
        }
    }
}
