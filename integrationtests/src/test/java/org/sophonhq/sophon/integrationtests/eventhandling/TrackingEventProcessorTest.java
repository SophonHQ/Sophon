/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.sophonhq.sophon.integrationtests.eventhandling;

import org.junit.jupiter.api.*;
import org.mockito.*;
import org.sophonhq.sophon.common.transaction.NoTransactionManager;
import org.sophonhq.sophon.common.transaction.Transaction;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.eventhandling.DefaultEventBusSpanFactory;
import org.sophonhq.sophon.eventhandling.DefaultEventProcessorSpanFactory;
import org.sophonhq.sophon.eventhandling.EventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.EventMessageHandler;
import org.sophonhq.sophon.eventhandling.EventTrackerStatus;
import org.sophonhq.sophon.eventhandling.EventTrackerStatusChangeListener;
import org.sophonhq.sophon.eventhandling.GapAwareTrackingToken;
import org.sophonhq.sophon.eventhandling.GenericTrackedEventMessage;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.MultiEventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.PropagatingErrorHandler;
import org.sophonhq.sophon.eventhandling.ReplayToken;
import org.sophonhq.sophon.eventhandling.Segment;
import org.sophonhq.sophon.eventhandling.SimpleEventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.TrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackingEventProcessor;
import org.sophonhq.sophon.eventhandling.TrackingEventProcessorConfiguration;
import org.sophonhq.sophon.eventhandling.TrackingEventStream;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import org.sophonhq.sophon.eventhandling.tokenstore.UnableToClaimTokenException;
import org.sophonhq.sophon.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.SequenceEventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.sophonhq.sophon.integrationtests.utils.MockException;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.StreamableMessageSource;
import org.sophonhq.sophon.messaging.timeout.UnitOfWorkTimeoutInterceptor;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.serialization.SerializationException;
import org.sophonhq.sophon.tracing.TestSpanFactory;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySortedSet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.eventhandling.EventUtils.asTrackedEventMessage;
import static org.sophonhq.sophon.integrationtests.utils.AssertUtils.assertUntil;
import static org.sophonhq.sophon.integrationtests.utils.AssertUtils.assertWithin;
import static org.sophonhq.sophon.integrationtests.utils.EventTestUtils.*;

/**
 * Test class validating the {@link TrackingEventProcessor}. This test class is part of the {@code integrationtests}
 * module as it relies on both the {@code messaging} (where the {@code TrackingEventProcessor} resides) and
 * {@code eventsourcing} modules.
 * <p>
 * This test class suppresses the "BusyWait" warning that occurs through the {@link Thread#sleep(long)} operations used
 * throughout this test class. Although not ideal, the effort to solve this needs to wait a little for future effort.
 * What would be ideal, is if there's a form of abstract
 * {@link org.sophonhq.sophon.eventhandling.StreamingEventProcessor} test suite, that's used by all implementations.
 * Using the current {@link org.sophonhq.sophon.eventhandling.pooled.PooledStreamingEventProcessor} test for this as a
 * base is suggested.
 *
 * @author Rene de Waele
 */
@SuppressWarnings("BusyWait")
class TrackingEventProcessorTest {

    private static final Object NO_RESET_PAYLOAD = null;

    private TrackingEventProcessor testSubject;
    private EmbeddedEventStore eventBus;
    private TokenStore tokenStore;
    private EventHandlerInvoker eventHandlerInvoker;
    private EventMessageHandler mockHandler;
    private List<Long> sleepInstructions;
    private TransactionManager mockTransactionManager;
    private Transaction mockTransaction;
    private TestSpanFactory spanFactory;

    private static TrackingEventStream trackingEventStreamOf(Iterator<TrackedEventMessage<?>> iterator) {
        return trackingEventStreamOf(iterator, c -> {
        });
    }

    private static TrackingEventStream trackingEventStreamOf(Iterator<TrackedEventMessage<?>> iterator,
                                                             Consumer<Class<?>> skippedPayloadListener) {
        return new TrackingEventStream() {
            private boolean hasPeeked;
            private TrackedEventMessage<?> peekEvent;

            @Override
            public Optional<TrackedEventMessage<?>> peek() {
                if (!hasPeeked) {
                    if (!hasNextAvailable()) {
                        return Optional.empty();
                    }
                    peekEvent = iterator.next();
                    hasPeeked = true;
                }
                return Optional.of(peekEvent);
            }

            @Override
            public boolean hasNextAvailable(int timeout, TimeUnit unit) {
                if (timeout > 0) {
                    // To keep tests speedy, we don't wait, but we do give other threads a chance
                    Thread.yield();
                }
                return hasPeeked || iterator.hasNext();
            }

            @Override
            public TrackedEventMessage<?> nextAvailable() {
                if (!hasPeeked) {
                    return iterator.next();
                }
                TrackedEventMessage<?> result = peekEvent;
                peekEvent = null;
                hasPeeked = false;
                return result;
            }

            @Override
            public void close() {

            }

            @Override
            public void skipMessagesWithPayloadTypeOf(TrackedEventMessage<?> ignoredMessage) {
                skippedPayloadListener.accept(ignoredMessage.getPayloadType());
            }
        };
    }

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        tokenStore = spy(new InMemoryTokenStore());
        mockHandler = mock(EventMessageHandler.class);
        when(mockHandler.canHandle(any())).thenReturn(true);
        when(mockHandler.supportsReset()).thenReturn(true);
        eventHandlerInvoker = spy(
                SimpleEventHandlerInvoker.builder()
                                         .eventHandlers(mockHandler)
                                         .listenerInvocationErrorHandler(PropagatingErrorHandler.instance())
                                         .build()
        );
        mockTransaction = mock(Transaction.class);
        mockTransactionManager = mock(TransactionManager.class);
        when(mockTransactionManager.startTransaction()).thenReturn(mockTransaction);

        //noinspection unchecked
        when(mockTransactionManager.fetchInTransaction(any(Supplier.class))).thenAnswer(i -> {
            Supplier<?> s = i.getArgument(0);
            return s.get();
        });
        doAnswer(i -> {
            Runnable r = i.getArgument(0);
            r.run();
            return null;
        }).when(mockTransactionManager).executeInTransaction(any(Runnable.class));
        eventBus = EmbeddedEventStore.builder()
                                     .storageEngine(new InMemoryEventStorageEngine())
                                     .spanFactory(
                                             DefaultEventBusSpanFactory.builder()
                                                                       .spanFactory(spanFactory)
                                                                       .build()
                                     )
                                     .build();
        sleepInstructions = new CopyOnWriteArrayList<>();

        initDefaultProcessor();
    }

    private void initDefaultProcessor() {
        initProcessor(TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                         .andEventAvailabilityTimeout(100, TimeUnit.MILLISECONDS));
    }

    private void initProcessor(TrackingEventProcessorConfiguration config) {
        initProcessor(config, UnaryOperator.identity());
    }

    private void initProcessor(UnaryOperator<TrackingEventProcessor.Builder> customization) {
        initProcessor(TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                         .andEventAvailabilityTimeout(100, TimeUnit.MILLISECONDS),
                      customization);
    }

    private void initProcessor(TrackingEventProcessorConfiguration config,
                               UnaryOperator<TrackingEventProcessor.Builder> customization) {
        TrackingEventProcessor.Builder eventProcessorBuilder =
                TrackingEventProcessor.builder()
                                      .name("test")
                                      .eventHandlerInvoker(eventHandlerInvoker)
                                      .messageSource(eventBus)
                                      .trackingEventProcessorConfiguration(config)
                                      .tokenStore(tokenStore)
                                      .transactionManager(mockTransactionManager);
        testSubject = new TrackingEventProcessor(customization.apply(eventProcessorBuilder)) {
            @Override
            protected void doSleepFor(long millisToSleep) {
                if (isRunning()) {
                    sleepInstructions.add(millisToSleep);
                    Thread.yield();
                }
            }

            @Override
            protected void doSleepFor(long millisToSleep, AtomicBoolean interruptFlag) {
                if (isRunning()) {
                    sleepInstructions.add(millisToSleep);
                    Thread.yield();
                }
            }
        };
    }

    @AfterEach
    void tearDown() {
        testSubject.shutDown();
        eventBus.shutDown();
    }

    @Test
    void sequenceEventStorageReceivesEachEventOnlyOnce() throws Exception {
        InMemoryEventStorageEngine historic = new InMemoryEventStorageEngine();
        InMemoryEventStorageEngine active = new InMemoryEventStorageEngine(2);
        SequenceEventStorageEngine sequenceEventStorageEngine = new SequenceEventStorageEngine(historic, active);

        EmbeddedEventStore sequenceEventBus = EmbeddedEventStore.builder()
                                                                .storageEngine(sequenceEventStorageEngine)
                                                                .build();

        initProcessor(builder -> builder.messageSource(sequenceEventBus));

        historic.appendEvents(createEvent(AGGREGATE, 1L, "message1"), createEvent(AGGREGATE, 2L, "message2"));
        // to make sure tracking tokens match, we need to offset the InMemoryEventStorageEngine
        active.appendEvents(createEvent(AGGREGATE, 3L, "message3"), createEvent(AGGREGATE, 4L, "message4"),
                            createEvent(AGGREGATE, 5L, "message5"));

        int expectedEventCount = 5;

        CountDownLatch countDownLatch = new CountDownLatch(expectedEventCount);
        AtomicInteger counter = new AtomicInteger(0);
        doAnswer(invocation -> {
            int cnt = counter.incrementAndGet();
            countDownLatch.countDown();
            assertThat(cnt).isLessThanOrEqualTo(expectedEventCount);
            return null;
        }).when(mockHandler).handle(any());

        testSubject.start();

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Handler to have received 4 published events")
                .isTrue();
        assertThat(counter.get()).as("Handler should only receive each event once").isEqualTo(expectedEventCount);
    }

    @Test
    void sequenceMaxCapacity() {
        testSubject.start();
        assertThat(testSubject.maxCapacity()).isOne();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(testSubject.availableProcessorThreads()).isZero());
        assertThat(testSubject.activeProcessorThreads()).isOne();
    }

    @Test
    void publishedEventsGetPassedToHandler() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvents(2));
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Handler to have received 2 published events")
                .isTrue();
    }

    @Test
    void handlersAreTraced() throws Exception {
        initProcessor(builder -> builder.spanFactory(DefaultEventProcessorSpanFactory.builder()
                                                                                     .spanFactory(spanFactory)
                                                                                     .build()));

        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            Message<?> message = invocation.getArgument(0, Message.class);
            spanFactory.verifySpanActive("StreamingEventProcessor.batch");
            spanFactory.verifySpanActive("StreamingEventProcessor.process", message);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvents(2));
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Handler to have received 2 published events")
                .isTrue();
        spanFactory.verifySpanCompleted("StreamingEventProcessor.process");
        spanFactory.verifySpanCompleted("StreamingEventProcessor.batch");
    }

    @Test
    void blacklist() {
        when(mockHandler.canHandle(any())).thenReturn(false);
        when(mockHandler.canHandleType(String.class)).thenReturn(false);
        Set<Class<?>> skipped = new HashSet<>();

        EmbeddedEventStore mockEventBus = mock(EmbeddedEventStore.class);
        TrackingToken trackingToken = new GlobalSequenceTrackingToken(0);
        List<TrackedEventMessage<?>> events =
                createEvents(2).stream().map(event -> asTrackedEventMessage(event, trackingToken)).collect(toList());
        when(mockEventBus.openStream(null)).thenReturn(trackingEventStreamOf(events.iterator(), skipped::add));
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(mockEventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> {
            assertThat(skipped)
                    .hasSize(1)
                    .contains(String.class);
        });
    }

    @Test
    void unhandledPayloadTypesAreSkippedDuringReplay() {
        when(mockHandler.canHandle(any())).thenReturn(false);
        when(mockHandler.canHandleType(String.class)).thenReturn(false);
        Set<Class<?>> skipped = ConcurrentHashMap.newKeySet();

        EmbeddedEventStore mockEventBus = mock(EmbeddedEventStore.class);
        TrackingToken trackingToken = new GlobalSequenceTrackingToken(0);
        List<TrackedEventMessage<?>> events = createEvents(2)
                .stream()
                .map(event -> asTrackedEventMessage(event, trackingToken))
                .collect(toList());
        when(mockEventBus.openStream(null)).thenReturn(trackingEventStreamOf(events.iterator(), skipped::add));
        tokenStore.initializeTokenSegments(
                "test", 1, ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(1))
        );
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(mockEventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();

        testSubject.start();

        assertWithin(8, TimeUnit.SECONDS, () -> assertThat(skipped).contains(String.class));
    }

    @Test
    void processorExposesErrorStateOnHandlerException() throws Exception {
        doReturn(Object.class).when(mockHandler).getTargetType();
        AtomicBoolean errorFlag = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (errorFlag.get()) {
                throw new MockException("Simulating issues");
            }
            return null;
        }).when(mockHandler).handle(any());
        int segmentId = 0;

        testSubject.start();
        eventBus.publish(createEvents(2));

        assertWithin(2, TimeUnit.SECONDS, () -> {
            EventTrackerStatus status = testSubject.processingStatus().get(segmentId);
            assertThat(status).isNotNull();
            assertThat(status.isErrorState()).isTrue();
            assertThat(status.getError().getClass()).isEqualTo(MockException.class);
        });

        errorFlag.set(false);

        assertWithin(5, TimeUnit.SECONDS, () -> {
            EventTrackerStatus status = testSubject.processingStatus().get(segmentId);
            assertThat(status).isNotNull();
            assertThat(status.isErrorState()).isFalse();
            assertThat(status.getError()).isNull();
        });
    }

    @Test
    void handlerIsInvokedInTransactionScope() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AtomicInteger counter = new AtomicInteger();
        AtomicInteger counterAtHandle = new AtomicInteger();
        when(mockTransactionManager.startTransaction()).thenAnswer(i -> {
            counter.incrementAndGet();
            return mockTransaction;
        });
        doAnswer(i -> counter.decrementAndGet()).when(mockTransaction).rollback();
        doAnswer(i -> counter.decrementAndGet()).when(mockTransaction).commit();
        doAnswer(invocation -> {
            counterAtHandle.set(counter.get());
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);
        eventBus.publish(createEvents(2));
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Handler to have received 2 published events")
                .isTrue();
        assertThat(counterAtHandle.get()).isOne();
    }

    @Test
    void processorStopsOnNonTransientExceptionWhenLoadingToken() {
        doThrow(new SerializationException("Faking a serialization issue")).when(tokenStore).fetchToken("test", 0);

        testSubject.start();

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.isRunning()).as("Expected processor to have stopped").isFalse()
        );
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.isError()).as("Expected processor to set the error flag").isTrue()
        );
        assertThat(sleepInstructions).isEmpty();
    }

    @Test
    void processorRetriesOnTransientExceptionWhenLoadingToken() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(invocation -> {
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        doThrow(new RuntimeException("Faking a recoverable issue"))
                .doCallRealMethod()
                .when(tokenStore).fetchToken("test", 0);

        testSubject.start();

        eventBus.publish(createEvent());

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Handler to have received published event")
                .isTrue();
        assertThat(testSubject.isRunning()).isTrue();
        assertThat(testSubject.isError()).isFalse();
        assertThat(sleepInstructions).containsExactly(5000L);
    }

    @Test
    void tokenIsStoredWhenEventIsRead() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        eventBus.publish(createEvent());
        testSubject.start();
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Unit of Work to have reached clean up phase")
                .isTrue();
        verify(tokenStore).storeToken(any(), eq(testSubject.getName()), eq(0));
        assertThat(tokenStore.fetchToken(testSubject.getName(), 0)).isNotNull();
    }

    @Test
    void segmentReleasedIsInvokedOnInvokerWhenSegmentIsReleased() {
        eventBus.publish(createEvent());
        testSubject.start();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> verify(tokenStore).storeToken(any(), eq(testSubject.getName()), eq(0))
        );
        testSubject.releaseSegment(0);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> verify(eventHandlerInvoker, times(1)).segmentReleased(any(Segment.class))
        );
    }

    @Test
    void tokenIsExtendedAtStartAndStoredAtEndOfEventBatch_WithStoringTokensAfterProcessingSetting()
            throws Exception {
        initProcessor(
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing().andBatchSize(100),
                TrackingEventProcessor.Builder::storingTokensAfterProcessing
        );
        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicInteger invocationsInUnitOfWork = new AtomicInteger();
        doAnswer(i -> {
            if (CurrentUnitOfWork.isStarted()) {
                invocationsInUnitOfWork.incrementAndGet();
            }
            return i.callRealMethod();
        }).when(tokenStore).extendClaim(anyString(), anyInt());
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Unit of Work to have reached clean up phase for 2 messages")
                .isTrue();
        InOrder inOrder = inOrder(tokenStore);
        inOrder.verify(tokenStore, times(1)).extendClaim(eq(testSubject.getName()), anyInt());
        inOrder.verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());

        assertThat(tokenStore.fetchToken(testSubject.getName(), 0)).isNotNull();
        assertThat(invocationsInUnitOfWork.get())
                .as("Unexpected number of invocations of token extension in unit of work")
                .isOne();
    }

    @Test
    void tokenStoredAtEndOfEventBatchAndNotExtendedWhenUsingANoTransactionManager() throws Exception {
        TrackingEventProcessorConfiguration tepConfig =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing().andBatchSize(100);
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .trackingEventProcessorConfiguration(tepConfig)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicInteger invocationsInUnitOfWork = new AtomicInteger();
        doAnswer(i -> {
            if (CurrentUnitOfWork.isStarted()) {
                invocationsInUnitOfWork.incrementAndGet();
            }
            return i.callRealMethod();
        }).when(tokenStore).extendClaim(anyString(), anyInt());

        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Unit of Work to have reached clean up phase for 2 messages")
                .isTrue();

        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertThat(tokenStore.fetchToken(testSubject.getName(), 0)).isNotNull();

        assertThat(invocationsInUnitOfWork.get())
                .as("Unexpected number of invocations of token extension in unit of work")
                .isOne();
    }

    @Test
    void tokenStoredAtEndOfEventBatchAndNotExtendedWhenTransactionManagerIsConfigured() throws Exception {
        TrackingEventProcessorConfiguration tepConfig =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing().andBatchSize(100);
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .trackingEventProcessorConfiguration(tepConfig)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(() -> mock(Transaction.class))
                                            .build();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        AtomicInteger invocationsInUnitOfWork = new AtomicInteger();
        doAnswer(i -> {
            if (CurrentUnitOfWork.isStarted()) {
                invocationsInUnitOfWork.incrementAndGet();
                fail("Did not expect an invocation in a Unit of Work");
            }
            return i.callRealMethod();
        }).when(tokenStore).extendClaim(anyString(), anyInt());

        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        eventBus.publish(createEvents(2));
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Unit of Work to have reached clean up phase for 2 messages")
                .isTrue();

        verify(tokenStore, times(1)).storeToken(any(), any(), anyInt());
        assertThat(tokenStore.fetchToken(testSubject.getName(), 0)).isNotNull();

        assertThat(invocationsInUnitOfWork.get())
                .as("Unexpected number of invocations of token extension in unit of work")
                .isZero();
    }

    @Test
    void tokenStoredAtEndOfEventBatchAndExtendedWhenTokenClaimIntervalExceeded() throws Exception {
        TrackingEventProcessorConfiguration tepConfig =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                   .andEventAvailabilityTimeout(10, TimeUnit.MILLISECONDS);
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .trackingEventProcessorConfiguration(tepConfig)
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            Thread.sleep(50);
            return interceptorChain.proceed();
        }));
        testSubject.start();

        eventBus.publish(createEvents(2));
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Unit of Work to have reached clean up phase for 2 messages")
                .isTrue();

        InOrder inOrder = inOrder(tokenStore);
        inOrder.verify(tokenStore, times(1)).storeToken(any(), any(), anyInt());
        inOrder.verify(tokenStore, times(1)).extendClaim(eq(testSubject.getName()), anyInt());

        assertThat(tokenStore.fetchToken(testSubject.getName(), 0)).isNotNull();
    }

    @Test
    void tokenIsNotStoredWhenUnitOfWorkIsRolledBack() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                throw new MockException();
            });
            return interceptorChain.proceed();
        }));
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();

        eventBus.publish(createEvent());
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Unit of Work to have reached clean up phase")
                .isTrue();
        assertThat(tokenStore.fetchToken(testSubject.getName(), 0))
                .satisfiesAnyOf(
                        arg -> assertThat(arg).isNull(),
                        arg -> assertThat(arg).isEqualTo(
                                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                                   .getInitialTrackingToken()
                                                                   .apply(eventBus)
                        )
                );
    }

    @Test
    void continueFromPreviousToken() throws Exception {
        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(10));
        //noinspection resource
        TrackedEventMessage<?> firstEvent = eventBus.openStream(null).nextAvailable();
        tokenStore.storeToken(firstEvent.trackingToken(), testSubject.getName(), 0);
        assertThat(tokenStore.fetchToken(testSubject.getName(), 0)).isEqualTo(firstEvent.trackingToken());

        List<EventMessage<?>> acknowledgedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(9);
        doAnswer(invocation -> {
            acknowledgedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();
        testSubject.start();

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected 9 invocations on Event Handler by now")
                .isTrue();
        assertThat(acknowledgedEvents).hasSize(9);
    }

    @Test
    @Timeout(value = 10)
    @DirtiesContext
    void continueAfterPause() throws Exception {
        List<EventMessage<?>> acknowledgedEvents = new CopyOnWriteArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            acknowledgedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());
        testSubject.start();

        eventBus.publish(createEvents(2));

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected 2 invocations on Event Handler by now")
                .isTrue();
        assertThat(acknowledgedEvents).hasSize(2);

        testSubject.shutDown();
        // The thread may block for 1 second waiting for a next event to pop up
        while (testSubject.activeProcessorThreads() > 0) {
            Thread.sleep(1);
            // Wait...
        }

        CountDownLatch countDownLatch2 = new CountDownLatch(2);
        doAnswer(invocation -> {
            acknowledgedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch2.countDown();
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(2));

        assertThat(countDownLatch2.getCount()).isEqualTo(2);

        testSubject.start();

        assertThat(countDownLatch2.await(5, TimeUnit.SECONDS))
                .as("Expected 4 invocations on Event Handler by now")
                .isTrue();
        assertThat(acknowledgedEvents).hasSize(4);
    }

    @Test
    @DirtiesContext
    void processorGoesToRetryModeWhenOpenStreamFails() throws Exception {
        eventBus = spy(eventBus);

        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(5));
        when(eventBus.openStream(any())).thenThrow(new MockException()).thenCallRealMethod();

        List<EventMessage<?>> acknowledgedEvents = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(5);
        doAnswer(invocation -> {
            acknowledgedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();
        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);
        assertThat(countDownLatch.await(10, TimeUnit.SECONDS))
                .as("Expected 5 invocations on Event Handler by now")
                .isTrue();
        assertThat(acknowledgedEvents).hasSize(5);
        //noinspection resource
        verify(eventBus, times(2)).openStream(any());
    }

    @Test
    @DirtiesContext
    void processorSetsAndUnsetsErrorState() throws Exception {
        eventBus = spy(eventBus);

        tokenStore = new InMemoryTokenStore();
        eventBus.publish(createEvents(5));
        AtomicBoolean fail = new AtomicBoolean(true);
        when(eventBus.openStream(any())).then(invocationOnMock -> {
            if (fail.get()) {
                throw new MockException();
            }
            return invocationOnMock.callRealMethod();
        });

        List<EventMessage<?>> acknowledgedEvents = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(5);
        doAnswer(invocation -> {
            acknowledgedEvents.add((EventMessage<?>) invocation.getArguments()[0]);
            countDownLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();
        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);
        Optional<EventTrackerStatus> healthy = testSubject.processingStatus().values().stream().filter(s -> !s
                .isErrorState()).findFirst();
        assertThat(healthy.isEmpty()).as("no healthy processor when open stream fails").isTrue();
        fail.set(false);
        assertThat(countDownLatch.await(10, TimeUnit.SECONDS))
                .as("Expected 5 invocations on Event Handler by now")
                .isTrue();
        assertThat(acknowledgedEvents).hasSize(5);
        Optional<EventTrackerStatus> inErrorState = testSubject.processingStatus()
                                                               .values()
                                                               .stream()
                                                               .filter(EventTrackerStatus::isErrorState)
                                                               .findFirst();
        assertThat(inErrorState.isEmpty())
                .as("no processor in error state when open stream succeeds again")
                .isTrue();
    }

    @Test
    void firstTokenIsStoredWhenUnitOfWorkIsRolledBackOnSecondEvent() throws Exception {
        List<? extends EventMessage<?>> events = createEvents(2);
        CountDownLatch countDownLatch = new CountDownLatch(2);
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                if (uow.getMessage().equals(events.get(1))) {
                    throw new MockException();
                }
            });
            return interceptorChain.proceed();
        }));
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);

        eventBus.publish(events);
        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Unit of Work to have reached clean up phase")
                .isTrue();
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertThat(tokenStore.fetchToken(testSubject.getName(), 0)).isNotNull();
    }

    @Test
    @DirtiesContext
    void eventsWithTheSameTokenAreProcessedInTheSameBatch() throws Exception {
        eventBus.shutDown();

        eventBus = mock(EmbeddedEventStore.class);
        TrackingToken trackingToken = new GlobalSequenceTrackingToken(0);
        List<TrackedEventMessage<?>> events =
                createEvents(2).stream().map(event -> asTrackedEventMessage(event, trackingToken)).collect(toList());
        when(eventBus.openStream(null)).thenReturn(trackingEventStreamOf(events.iterator()));
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(eventBus)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();

        //noinspection Duplicates,resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCommit(uow -> {
                if (uow.getMessage().equals(events.get(1))) {
                    throw new MockException();
                }
            });
            return interceptorChain.proceed();
        }));

        CountDownLatch countDownLatch = new CountDownLatch(2);

        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));

        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Unit of Work to have reached clean up phase")
                .isTrue();
        verify(tokenStore, atLeastOnce()).storeToken(any(), any(), anyInt());
        assertThat(tokenStore.fetchToken(testSubject.getName(), 0)).isNull();
    }

    @Test
    void resetCausesEventsToBeReplayedWithCorrectContext() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        final List<Object> contextInRedelivery = new CopyOnWriteArrayList<>();
        int segmentId = 0;

        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
                contextInRedelivery.add(ReplayToken.replayContext(message, MyResetContext.class).orElse(null));
            }
            handled.add(message.getIdentifier());
            return null;
        }).when(mockHandler).handle(any());

        testSubject.start();
        awaitProcessorStarted();

        int numberOfEvents = 4;
        eventBus.publish(createEvents(numberOfEvents));
        await("Handle Events - Initial").pollDelay(Duration.ofMillis(50))
                                        .atMost(Duration.ofMillis(4000))
                                        .untilAsserted(() -> assertThat(handled)
                                                .as(() -> "Actually handled [" + handled.size() +
                                                        "] instead of expected [" + numberOfEvents + "]")
                                                .hasSize(numberOfEvents));

        testSubject.shutDown();
        MyResetContext one = new MyResetContext("one");
        testSubject.resetTokens(one);
        // Resetting twice caused problems (see issue #559)
        MyResetContext two = new MyResetContext("two");
        testSubject.resetTokens(two);
        testSubject.start();

        await("Handle Events - Replay")
                .atMost(Duration.ofSeconds(2))
                .pollDelay(Duration.ofMillis(50))
                .until(() -> handled.size() == 8);

        assertThat(handled.subList(4, 8)).containsExactlyElementsOf(handled.subList(0, 4));
        assertThat(handledInRedelivery).containsExactlyElementsOf(handled.subList(4, 8));
        assertThat(contextInRedelivery).hasSize(4);
        assertThat(contextInRedelivery.stream().allMatch(two::equals)).isTrue();
        assertThat(testSubject.processingStatus().get(segmentId).isReplaying()).isTrue();
        assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent()).isTrue();
        assertThat(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent()).isTrue();

        long resetPositionAtReplay = testSubject.processingStatus().get(segmentId).getCurrentPosition().getAsLong();
        eventBus.publish(createEvents(1));

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).isReplaying()).isFalse()
        );
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent()).isFalse()
        );
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent())
                        .isTrue()
        );
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().getAsLong())
                        .isGreaterThan(resetPositionAtReplay)
        );

        verify(eventHandlerInvoker, times(1)).performReset(one);
        verify(eventHandlerInvoker, times(1)).performReset(two);
    }

    @Test
    void resetTokensPassesOnResetContextToResetHandlers() throws Exception {
        String resetContext = "reset-context";
        final List<String> handled = new CopyOnWriteArrayList<>();

        when(mockHandler.supportsReset()).thenReturn(true);

        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            handled.add(message.getIdentifier());
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(4));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(handled).hasSize(4));

        testSubject.shutDown();
        testSubject.resetTokens(resetContext);
        testSubject.start();

        verify(eventHandlerInvoker).performReset(resetContext);
    }

    @Test
    void resetToPositionCausesCertainEventsToBeReplayed() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        int segmentId = 0;

        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            handled.add(message.getIdentifier());
            return null;
        }).when(mockHandler).handle(any());

        testSubject.start();
        awaitProcessorStarted();

        int numberOfEvents = 4;
        eventBus.publish(createEvents(numberOfEvents));
        await("Handle Events")
                .atMost(Duration.ofSeconds(5))
                .pollDelay(Duration.ofMillis(50))
                .until(() -> handled.size() == 4);

        testSubject.shutDown();
        testSubject.resetTokens(source -> new GlobalSequenceTrackingToken(1L));
        testSubject.start();

        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(handled).hasSize(6));
        assertThat(handledInRedelivery)
                .doesNotContain(handled.get(0))
                .doesNotContain(handled.get(1));
        assertThat(handled.subList(4, 6)).containsExactlyElementsOf(handled.subList(2, 4));
        assertThat(handledInRedelivery).containsExactlyElementsOf(handled.subList(4, 6));
        assertThat(testSubject.processingStatus().get(segmentId).isReplaying()).isTrue();
        assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent()).isTrue();
        assertThat(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent()).isTrue();

        long resetPositionAtReplay = testSubject.processingStatus().get(segmentId).getResetPosition().getAsLong();
        eventBus.publish(createEvents(1));

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).isReplaying()).isFalse()
        );
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent()).isFalse()
        );
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent())
                        .isTrue()
        );
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().getAsLong())
                        .isGreaterThan(resetPositionAtReplay)
        );

        verify(eventHandlerInvoker).performReset(NO_RESET_PAYLOAD);
    }

    @Test
    void resetOnInitializeWithTokenResetToThatToken() throws Exception {
        TrackingEventProcessorConfiguration config =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                   .andInitialTrackingToken(ms -> new GlobalSequenceTrackingToken(1L));
        TrackingEventProcessor.Builder eventProcessorBuilder =
                TrackingEventProcessor.builder()
                                      .name("test")
                                      .eventHandlerInvoker(eventHandlerInvoker)
                                      .messageSource(eventBus)
                                      .tokenStore(tokenStore)
                                      .transactionManager(NoTransactionManager.INSTANCE)
                                      .trackingEventProcessorConfiguration(config);
        testSubject = new TrackingEventProcessor(eventProcessorBuilder) {
            @Override
            protected void doSleepFor(long millisToSleep) {
                if (isRunning()) {
                    sleepInstructions.add(millisToSleep);
                }
            }

            @Override
            protected void doSleepFor(long millisToSleep, AtomicBoolean interruptFlag) {
                if (isRunning()) {
                    sleepInstructions.add(millisToSleep);
                }
            }
        };
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        int segmentId = 0;

        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            handled.add(message.getIdentifier());
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(4));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(handled).hasSize(2));
        testSubject.shutDown();
        testSubject.resetTokens();
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(handled).hasSize(4));
        assertThat(handled.subList(2, 4)).containsExactlyElementsOf(handled.subList(0, 2));
        assertThat(handledInRedelivery).containsExactlyElementsOf(handled.subList(2, 4));
        assertThat(testSubject.processingStatus().get(segmentId).isReplaying()).isTrue();
        assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent()).isTrue();
        assertThat(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent()).isTrue();

        long resetPositionAtReplay = testSubject.processingStatus().get(segmentId).getResetPosition().getAsLong();
        eventBus.publish(createEvents(1));

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).isReplaying()).isFalse()
        );
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent()).isFalse()
        );
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent())
                        .isTrue()
        );
        //noinspection OptionalGetWithoutIsPresent
        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().getAsLong())
                        .isGreaterThan(resetPositionAtReplay)
        );

        verify(eventHandlerInvoker).performReset(NO_RESET_PAYLOAD);
    }

    @Test
    void resetBeforeStartingPerformsANormalRun() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);
        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        int segmentId = 0;
        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            handled.add(message.getIdentifier());
            return null;
        }).when(mockHandler).handle(any());

        testSubject.resetTokens();
        testSubject.start();
        awaitProcessorStarted();

        int numberOfEvents = 4;
        eventBus.publish(createEvents(numberOfEvents));
        await("Handle Events").pollDelay(Duration.ofMillis(50))
                              .atMost(Duration.ofMillis(2500))
                              .untilAsserted(() -> assertThat(handled)
                                      .as(() -> "Actually handled [" + handled.size() +
                                              "] instead of expected [" + numberOfEvents + "]")
                                      .hasSize(numberOfEvents));

        assertThat(handledInRedelivery).isEmpty();
        assertThat(testSubject.processingStatus().get(segmentId).isReplaying()).isFalse();
        assertThat(testSubject.processingStatus().get(segmentId).getResetPosition().isPresent()).isFalse();
        assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().isPresent()).isTrue();
        assertThat(testSubject.processingStatus().get(segmentId).getCurrentPosition().getAsLong()).isGreaterThan(0);

        verify(eventHandlerInvoker).performReset(NO_RESET_PAYLOAD);
    }

    private void awaitProcessorStarted() {
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(testSubject.activeProcessorThreads()).isOne());
    }

    @SuppressWarnings("unchecked")
    @Test
    void replayFlagAvailableWhenReplayInDifferentOrder() throws Exception {
        StreamableMessageSource<TrackedEventMessage<?>> stubSource = mock(StreamableMessageSource.class);
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(stubSource)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE)
                                            .build();

        when(stubSource.openStream(any())).thenReturn(new StubTrackingEventStream(0, 1, 2, 5))
                                          .thenReturn(new StubTrackingEventStream(0, 1, 2, 3, 4, 5, 6, 7));


        when(eventHandlerInvoker.supportsReset()).thenReturn(true);
        doReturn(true).when(eventHandlerInvoker).canHandle(any(), any());
        List<TrackingToken> firstRun = new CopyOnWriteArrayList<>();
        List<TrackingToken> replayRun = new CopyOnWriteArrayList<>();
        doAnswer(i -> {
            firstRun.add(i.<TrackedEventMessage<?>>getArgument(0).trackingToken());
            return null;
        }).when(eventHandlerInvoker).handle(any(), any());

        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(firstRun).hasSize(4));
        testSubject.shutDown();

        doAnswer(i -> {
            replayRun.add(i.<TrackedEventMessage<?>>getArgument(0).trackingToken());
            return null;
        }).when(eventHandlerInvoker).handle(any(), any());

        testSubject.resetTokens();
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(replayRun).hasSize(8));

        assertThat(firstRun.get(3)).isEqualTo(GapAwareTrackingToken.newInstance(5, asList(3L, 4L)));
        assertThat(replayRun.get(0)).isInstanceOf(ReplayToken.class);
        assertThat(replayRun.get(5)).isInstanceOf(ReplayToken.class);
        assertThat(replayRun.get(6)).isEqualTo(GapAwareTrackingToken.newInstance(6, emptySortedSet()));

        verify(eventHandlerInvoker).performReset(NO_RESET_PAYLOAD);
    }

    @Test
    void resetRejectedWhileRunning() {
        testSubject.start();

        assertThatThrownBy(testSubject::resetTokens).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resetNotSupportedWhenInvokerDoesNotSupportReset() {
        when(mockHandler.supportsReset()).thenReturn(false);
        assertThat(testSubject.supportsReset()).isFalse();
    }

    @Test
    void resetRejectedWhenInvokerDoesNotSupportReset() {
        when(mockHandler.supportsReset()).thenReturn(false);

        assertThatThrownBy(testSubject::resetTokens).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resetRejectedIfNotAllTokensCanBeClaimed() {
        tokenStore.initializeTokenSegments("test", 4);
        when(tokenStore.fetchToken("test", 3)).thenThrow(new UnableToClaimTokenException("Mock"));

        assertThatThrownBy(testSubject::resetTokens).isInstanceOf(UnableToClaimTokenException.class);
        verify(tokenStore, never()).storeToken(isNull(), anyString(), anyInt());
    }

    @Test
    void resetTokensPassesOnResetContextToTrackingToken() throws Exception {
        String resetContext = "reset-context";
        final AtomicInteger count = new AtomicInteger();

        when(mockHandler.supportsReset()).thenReturn(true);

        //noinspection Duplicates
        doAnswer(i -> {
            count.incrementAndGet();
            return null;
        }).when(mockHandler).handle(any());

        eventBus.publish(createEvents(4));
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(count.get()).isEqualTo(4));
        testSubject.shutDown();
        testSubject.resetTokens(resetContext);
        testSubject.start();

        TrackingToken test = tokenStore.fetchToken("test", 0);
        assertThat(ReplayToken.replayContext(test, String.class).orElse(null)).isEqualTo(resetContext);
    }

    @Test
    void whenFailureDuringInit() throws InterruptedException {
        doThrow(new RuntimeException("Faking issue during fetchSegments"))
                .doCallRealMethod()
                .when(tokenStore).fetchSegments(anyString());

        doThrow(new RuntimeException("Faking issue during initializeTokenSegments"))
                // And on further calls
                .doNothing()
                .when(tokenStore).initializeTokenSegments(anyString(), anyInt());

        testSubject.start();

        for (int i = 0; i < 250 && testSubject.activeProcessorThreads() < 1; i++) {
            Thread.sleep(10);
        }

        assertThat(testSubject.activeProcessorThreads()).isOne();
    }

    @Test
    void updateActiveSegmentsWhenBatchIsEmpty() throws Exception {
        int segmentId = 0;
        //noinspection unchecked
        StreamableMessageSource<TrackedEventMessage<?>> stubSource = mock(StreamableMessageSource.class);
        testSubject = TrackingEventProcessor.builder()
                                            .name("test")
                                            .eventHandlerInvoker(eventHandlerInvoker)
                                            .messageSource(stubSource)
                                            .tokenStore(tokenStore)
                                            .transactionManager(NoTransactionManager.INSTANCE).build();

        when(stubSource.openStream(any())).thenReturn(new StubTrackingEventStream(0, 1, 2, 5));
        doReturn(true, false).when(eventHandlerInvoker).canHandle(any(), any());

        testSubject.start();
        // Give it a bit of time to start
        waitForStatus("processor thread started", 200, TimeUnit.MILLISECONDS, status -> status.containsKey(0));

        waitForStatus("Segment 0 caught up", 5, TimeUnit.SECONDS, status -> status.get(0).isCaughtUp());

        EventTrackerStatus eventTrackerStatus = testSubject.processingStatus().get(segmentId);
        GapAwareTrackingToken expectedToken = GapAwareTrackingToken.newInstance(5, asList(3L, 4L));
        TrackingToken lastToken = eventTrackerStatus.getTrackingToken();
        assertThat(lastToken.covers(expectedToken)).isTrue();
    }

    @Test
    void releaseSegment() {
        testSubject.start();
        assertWithin(5, TimeUnit.SECONDS, () -> assertThat(testSubject.activeProcessorThreads()).isOne());
        testSubject.releaseSegment(0, 2, TimeUnit.SECONDS);
        assertWithin(2, TimeUnit.SECONDS, () -> assertThat(testSubject.activeProcessorThreads()).isZero());
        assertWithin(5, TimeUnit.SECONDS, () -> assertThat(testSubject.activeProcessorThreads()).isOne());
    }

    @Test
    void releaseAndClaimSegmentWillOverrideReleaseDuration() {
        testSubject.start();
        assertWithin(5, TimeUnit.SECONDS, () -> assertThat(testSubject.activeProcessorThreads()).isOne());
        testSubject.releaseSegment(0, 180, TimeUnit.SECONDS);
        assertWithin(2, TimeUnit.SECONDS, () -> assertThat(testSubject.activeProcessorThreads()).isZero());
        testSubject.claimSegment(0).join();
        assertWithin(5, TimeUnit.SECONDS, () -> assertThat(testSubject.activeProcessorThreads()).isOne());
    }

    @Test
    void hasAvailableSegments() {
        assertThat(testSubject.availableProcessorThreads()).isOne();
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(testSubject.availableProcessorThreads()).isZero());
        testSubject.releaseSegment(0);
        assertWithin(2, TimeUnit.SECONDS, () -> assertThat(testSubject.availableProcessorThreads()).isOne());
    }

    @Test
    void processorRestartsAfterShutdownWhileClaimingSegment() throws Exception {
        AtomicReference<Thread> shutdownThread = new AtomicReference<>();
        // Shut down while the worker launcher claims the segment, so the launcher never starts the segment worker
        doAnswer(invocation -> {
            shutdownThread.set(new Thread(testSubject::shutDown));
            shutdownThread.get().start();
            assertWithin(1, TimeUnit.SECONDS, () -> assertThat(testSubject.isRunning()).isFalse());
            return invocation.callRealMethod();
        }).doCallRealMethod().when(tokenStore).fetchToken(eq("test"), any(Segment.class));

        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(shutdownThread.get()).isNotNull());
        shutdownThread.get().join(5000);
        verify(tokenStore).releaseClaim("test", 0);
        assertThat(testSubject.availableProcessorThreads()).isOne();

        testSubject.start();
        assertWithin(2, TimeUnit.SECONDS, () -> assertThat(testSubject.activeProcessorThreads()).isOne());
    }

    @Test
    @Timeout(value = 10)
    void splitSegments() throws InterruptedException {
        tokenStore.initializeTokenSegments(testSubject.getName(), 1);
        testSubject.start();
        waitForSegmentStart(0);
        assertThat(testSubject.splitSegment(0).join()).as("Expected split to succeed").isTrue();
        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(0, 1);
        waitForSegmentStart(0);
    }

    @Test
    @Timeout(value = 10)
    void mergeSegments() throws InterruptedException {
        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        testSubject.start();
        while (testSubject.processingStatus().isEmpty()) {
            Thread.sleep(10);
        }
        int segmentId = 0;

        assertThat(testSubject.mergeSegment(segmentId).join()).as("Expected merge to succeed").isTrue();
        waitForProcessingStatus(segmentId, EventTrackerStatus::isMerging);

        waitForSegmentStart(segmentId);

        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(0);

        publishEvents(1);
        waitForProcessingStatus(segmentId, s -> !s.isMerging());
    }

    @Test
    @Timeout(value = 10)
    void mergeSegments_BothClaimedByProcessor() throws Exception {
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                         .andEventAvailabilityTimeout(10, TimeUnit.MILLISECONDS)
                                                         .andBatchSize(100));
        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        int segmentId = 0;

        when(mockHandler.handle(any())).thenAnswer(i -> handledEvents.add(i.getArgument(0)));

        publishEvents(10);

        testSubject.start();
        waitForActiveThreads(2);

        assertWithin(
                5, TimeUnit.SECONDS,
                () -> assertThat(handledEvents.stream().map(EventMessage::getIdentifier).distinct().count())
                        .as("Expected message to be handled")
                        .isEqualTo(10)
        );

        assertThat(testSubject.processingStatus().get(segmentId).isMerging()).isFalse();
        assertThat(testSubject.processingStatus().get(segmentId).mergeCompletedPosition().isPresent()).isFalse();

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertThat(testSubject.mergeSegment(segmentId).join()).as("Expected merge to succeed").isTrue()
        );

        EventTrackerStatus status = waitForProcessingStatus(segmentId, EventTrackerStatus::isMerging);
        assertThat(status.mergeCompletedPosition().isPresent()).isTrue();
        long mergeCompletedPosition = status.mergeCompletedPosition().getAsLong();

        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(0);

        publishEvents(1);
        status = waitForProcessingStatus(segmentId, s -> !s.isMerging());
        assertThat(status.mergeCompletedPosition().isPresent()).isFalse();
        assertThat(status.getCurrentPosition().isPresent()).isTrue();
        assertThat(status.getCurrentPosition().getAsLong()).isGreaterThan(mergeCompletedPosition);
    }

    @Test
    @Timeout(value = 10)
    void mergeSegments_WithExplicitReleaseOther() throws Exception {
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2));
        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        List<EventMessage<?>> events = new ArrayList<>();
        int segmentId = 0;
        for (int i = 0; i < 10; i++) {
            events.add(createEvent(UUID.randomUUID().toString(), 0));
        }
        when(mockHandler.handle(any())).thenAnswer(i -> handledEvents.add(i.getArgument(0)));
        eventBus.publish(events);
        testSubject.start();
        waitForActiveThreads(2);

        testSubject.releaseSegment(1);
        waitForSegmentRelease(1);

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertThat(testSubject.mergeSegment(segmentId).join()).as("Expected merge to succeed").isTrue()
        );
        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(0);
        waitForSegmentStart(segmentId);

        while (!Optional.ofNullable(testSubject.processingStatus().get(segmentId))
                        .map(EventTrackerStatus::isCaughtUp)
                        .orElse(false)) {
            Thread.sleep(10);
        }

        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(handledEvents).hasSize(10));
    }

    @Test
    @Timeout(value = 10)
    void doubleSplitAndMerge() throws Exception {
        tokenStore.initializeTokenSegments(testSubject.getName(), 1);
        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        int segmentId = 0;
        when(mockHandler.handle(any())).thenAnswer(i -> handledEvents.add(i.getArgument(0)));

        publishEvents(10);

        testSubject.start();
        waitForActiveThreads(1);

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertThat(testSubject.splitSegment(segmentId).join()).as("Expected split to succeed").isTrue()
        );
        waitForActiveThreads(1);

        assertWithin(
                50, TimeUnit.MILLISECONDS,
                () -> assertThat(testSubject.splitSegment(segmentId).join()).as("Expected split to succeed").isTrue()
        );
        waitForActiveThreads(1);

        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(0, 1, 2);

        publishEvents(20);

        waitForProcessingStatus(segmentId, EventTrackerStatus::isCaughtUp);

        assertThat(testSubject.processingStatus().get(segmentId).isMerging()).isFalse();

        assertThat(testSubject.mergeSegment(segmentId).join()).as("Expected merge to succeed").isTrue();
        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(0, 1);

        publishEvents(10);

        waitForSegmentStart(segmentId);
        waitForProcessingStatus(segmentId, est -> est.getSegment().getMask() == 1 && est.isCaughtUp());

        assertThat(testSubject.mergeSegment(segmentId).join()).as("Expected merge to succeed").isTrue();
        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(0);

        assertWithin(3, TimeUnit.SECONDS, () -> assertThat(handledEvents).hasSize(40));
    }

    @Test
    @Timeout(value = 10)
    void mergeSegmentWithDifferentProcessingGroupsAndSequencingPolicies() throws Exception {
        EventMessageHandler otherHandler = mock(EventMessageHandler.class);
        when(otherHandler.canHandle(any())).thenReturn(true);
        when(otherHandler.supportsReset()).thenReturn(true);
        int segmentId = 0;
        EventHandlerInvoker mockInvoker = SimpleEventHandlerInvoker.builder()
                                                                   .eventHandlers(singleton(otherHandler))
                                                                   .sequencingPolicy(m -> 0)
                                                                   .build();
        initProcessor(
                TrackingEventProcessorConfiguration.forParallelProcessing(2).andBatchSize(5),
                builder -> builder.eventHandlerInvoker(new MultiEventHandlerInvoker(eventHandlerInvoker, mockInvoker))
        );

        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        when(mockHandler.handle(any())).thenAnswer(i -> {
            TrackedEventMessage<?> message = i.getArgument(0);
            return handledEvents.add(message);
        });

        publishEvents(10);
        testSubject.start();

        while (testSubject.processingStatus().size() < 2
                || !testSubject.processingStatus().values().stream().allMatch(EventTrackerStatus::isCaughtUp)) {
            Thread.sleep(10);
        }

        testSubject.releaseSegment(1);

        while (testSubject.processingStatus().size() != 1
                || !testSubject.processingStatus().values().stream().allMatch(EventTrackerStatus::isCaughtUp)) {
            Thread.sleep(10);
        }

        publishEvents(10);

        testSubject.mergeSegment(segmentId);

        publishEvents(10);

        while (testSubject.processingStatus().size() != 1
                || !testSubject.processingStatus().values().stream().allMatch(EventTrackerStatus::isCaughtUp)) {
            Thread.sleep(10);
        }

        int numberOfEvents = 30;
        await("Handled Events - After Merge").pollDelay(Duration.ofMillis(50))
                                             .atMost(Duration.ofSeconds(5))
                                             .untilAsserted(() -> assertThat(handledEvents)
                                                     .as(() -> "Actually handled [" + handledEvents.size() +
                                                             "] instead of expected [" + numberOfEvents + "]")
                                                     .hasSize(numberOfEvents));
    }

    @Test
    @Timeout(value = 20)
    void mergeSegmentsDuringReplay() throws Exception {
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2));
        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        List<EventMessage<?>> replayedEvents = new CopyOnWriteArrayList<>();
        int segmentId = 0;
        when(mockHandler.handle(any())).thenAnswer(i -> {
            TrackedEventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                replayedEvents.add(message);
            } else {
                handledEvents.add(message);
            }
            return null;
        });
        for (int i = 0; i < 10; i++) {
            eventBus.publish(createEvent(UUID.randomUUID().toString(), 0));
        }
        testSubject.start();
        while (testSubject.processingStatus().size() < 2
                || !testSubject.processingStatus().values().stream().allMatch(EventTrackerStatus::isCaughtUp)) {
            Thread.sleep(10);
        }

        testSubject.shutDown();
        testSubject.resetTokens();

        testSubject.releaseSegment(1);
        testSubject.start();
        waitForActiveThreads(1);
        Thread.yield();

        CompletableFuture<Boolean> mergeResult = testSubject.mergeSegment(segmentId);

        publishEvents(20);

        assertThat(mergeResult.join()).as("Expected merge to succeed").isTrue();
        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(0);
        waitForSegmentStart(segmentId);

        assertWithin(10, TimeUnit.SECONDS, () -> assertThat(handledEvents).hasSize(30));
        Thread.sleep(100);
        assertThat(handledEvents).hasSize(30);

        // Make sure replay events are only delivered once.
        assertThat(replayedEvents)
                .size()
                .isEqualTo(replayedEvents.stream().map(EventMessage::getIdentifier).distinct().count());
    }

    @Test
    void replayDuringIncompleteMerge() throws Exception {
        List<EventMessage<?>> handledEvents = new CopyOnWriteArrayList<>();
        List<EventMessage<?>> initialEvents = IntStream.range(0, 10)
                                                       .mapToObj(i -> createEvent(UUID.randomUUID().toString(), 0))
                                                       .collect(toList());
        Duration pollDelay = Duration.ofMillis(25);
        int segmentIdZero = 0;
        int segmentIdOne = 1;

        tokenStore.initializeTokenSegments(testSubject.getName(), 2);
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2));
        when(mockHandler.handle(any())).thenAnswer(i -> {
            TrackedEventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                // Ignore replays
                return null;
            }
            return handledEvents.add(message);
        });

        // Publish first 10 events and start TEP.
        eventBus.publish(initialEvents);
        testSubject.start();
        await().pollDelay(pollDelay)
               .atMost(Duration.ofMillis(250))
               .until(() -> testSubject.processingStatus().size() >= 2);

        // Release segment, publish 10 more events and merge the segments of the TEP.
        testSubject.releaseSegment(segmentIdOne);
        while (testSubject.processingStatus().containsKey(segmentIdOne)) {
            Thread.yield();
        }
        publishEvents(10);

        CompletableFuture<Boolean> mergeResult = testSubject.mergeSegment(segmentIdZero);
        assertThat(mergeResult.join()).as("Expected merge to succeed").isTrue();
        await("Merge segments").pollDelay(pollDelay)
                               .atMost(Duration.ofMillis(1000))
                               .until(() -> testSubject.processingStatus().size() == 1);

        // Initiate the reset, with 10 more events published in the middle.
        testSubject.shutDown();
        testSubject.resetTokens();

        publishEvents(10);
        testSubject.start();
        await().pollDelay(pollDelay)
               .atMost(Duration.ofMillis(250))
               .until(() -> !testSubject.processingStatus().isEmpty());

        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(0);
        await().pollDelay(pollDelay)
               .atMost(Duration.ofMillis(250))
               .until(() -> testSubject.processingStatus().containsKey(segmentIdZero));

        await().pollDelay(pollDelay)
               .atMost(Duration.ofSeconds(1))
               .until(() -> testSubject.processingStatus().get(segmentIdZero).isCaughtUp());

        // Replayed messages aren't counted
        int numberOfEvents = 30;
        await("Handle Events - Replay").pollDelay(pollDelay)
                                       .atMost(Duration.ofMillis(4000))
                                       .untilAsserted(() -> assertThat(handledEvents)
                                               .as(() -> "Actually handled [" + handledEvents.size() +
                                                       "] instead of expected [" + numberOfEvents + "]")
                                               .hasSize(numberOfEvents));
    }

    @Test
    @Timeout(value = 10)
    void mergeWithIncompatibleSegmentRejected() throws InterruptedException {
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(3));

        testSubject.start();
        waitForActiveThreads(3);
        assertThat(testSubject.processingStatus()).containsKey(0);
        assertThat(testSubject.processingStatus()).containsKey(1);
        assertThat(testSubject.processingStatus()).containsKey(2);

        // 1 is not "mergeable" with 0, because 0 itself was already split
        testSubject.releaseSegment(0);
        testSubject.releaseSegment(2);

        testSubject.processingStatus().values().forEach(status -> assertThat(status.isMerging()).isFalse());

        while (testSubject.processingStatus().size() > 1) {
            Thread.sleep(10);
        }

        CompletableFuture<Boolean> actual = testSubject.mergeSegment(1);

        assertThat(actual.join()).as("Expected merge to be rejected").isFalse();
    }

    @Test
    @Timeout(value = 10)
    void mergeWithSingleSegmentRejected() throws InterruptedException {
        int numberOfSegments = 1;
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(numberOfSegments));
        int segmentId = 0;

        testSubject.start();
        waitForActiveThreads(1);

        CompletableFuture<Boolean> actual = testSubject.mergeSegment(segmentId);

        assertThat(actual.join()).as("Expected merge to be rejected").isFalse();
        assertThat(testSubject.processingStatus().get(segmentId).isMerging()).isFalse();
    }

    /**
     * This test is a follow-up from issue
     * <a href="https://github.com/AxonIQ/axon-server-se/issues/135">#135</a>.
     */
    @Test
    @Timeout(value = 10)
    void mergeInvertedSegmentOrder() throws InterruptedException {
        int numberOfSegments = 4;
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(numberOfSegments));
        testSubject.start();
        waitForActiveThreads(4);
        int segmentId = 3;
        CompletableFuture<Boolean> mergeResult = testSubject.mergeSegment(segmentId);
        assertThat(mergeResult.join()).as("Expected merge to succeed").isTrue();
        verify(tokenStore).deleteToken("test", 3);
        verify(tokenStore).storeToken(any(), eq("test"), eq(1));
    }

    /**
     * This test is a follow-up from issue
     * <a href="https://github.com/AxonFramework/AxonFramework/issues/1212">#1212</a>.
     */
    @Test
    void thrownErrorBubblesUp() {
        AtomicReference<Throwable> thrownException = new AtomicReference<>();

        EventHandlerInvoker eventHandlerInvoker = mock(EventHandlerInvoker.class);
        when(eventHandlerInvoker.canHandle(any(), any())).thenThrow(new TestError());

        initProcessor(
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                   .andThreadFactory(name -> runnableForThread -> new Thread(() -> {
                                                       try {
                                                           runnableForThread.run();
                                                       } catch (Throwable t) {
                                                           thrownException.set(t);
                                                       }
                                                   })),
                builder -> builder.eventHandlerInvoker(eventHandlerInvoker)
        );

        eventBus.publish(createEvents(1));
        testSubject.start();

        assertWithin(2, TimeUnit.SECONDS, () -> assertThat(testSubject.isError()).isTrue());
        assertWithin(
                15, TimeUnit.SECONDS,
                () -> assertThat(thrownException.get()).isInstanceOf(TestError.class)
        );
    }

    @Test
    void retrievingStorageIdentifierWillCacheResults() {
        String id = testSubject.getTokenStoreIdentifier();
        InOrder inOrder = inOrder(mockTransactionManager, tokenStore);
        inOrder.verify(mockTransactionManager).fetchInTransaction(any());
        inOrder.verify(tokenStore, times(1)).retrieveStorageIdentifier();

        String id2 = testSubject.getTokenStoreIdentifier();
        // expect no extra invocations
        verify(tokenStore, times(1)).retrieveStorageIdentifier();

        assertThat(id2).isEqualTo(id);
    }

    /**
     * This test can spot three invocations of the {@link EventTrackerStatusChangeListener}, but asserts two:
     * <ol>
     *     <li> First call is when the single active {@link org.sophonhq.sophon.eventhandling.Segment} is added.</li>
     *     <li> Second call is when the status transitions to {@link EventTrackerStatus#isCaughtUp()}.</li>
     *     <li>
     *         The last not asserted call is when the {@link TrackingEventProcessor} is shutting down, which removes the
     *         status. This isn't taking into account as it is part of the {@link #tearDown()}.
     *     </li>
     * </ol>
     * <p>
     * More changes occur on the {@link EventTrackerStatus}, but by default only complete additions, removals and {@code
     * boolean} field updates are included as changes.
     */
    @Test
    void publishedEventsUpdateStatusAndHitChangeListener() throws Exception {
        CountDownLatch eventHandlingLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            eventHandlingLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        CountDownLatch statusChangeLatch = new CountDownLatch(2);
        AtomicInteger addedStatusCounter = new AtomicInteger(0);
        AtomicInteger updatedStatusCounter = new AtomicInteger(0);
        AtomicInteger removedStatusCounter = new AtomicInteger(0);
        EventTrackerStatusChangeListener statusChangeListener = updatedTrackerStatus -> {
            assertThat(updatedTrackerStatus).hasSize(1);
            EventTrackerStatus eventTrackerStatus = updatedTrackerStatus.get(0);
            if (eventTrackerStatus.trackerAdded()) {
                addedStatusCounter.getAndIncrement();
            } else if (eventTrackerStatus.trackerRemoved()) {
                removedStatusCounter.getAndIncrement();
            } else {
                updatedStatusCounter.getAndIncrement();
            }
            statusChangeLatch.countDown();
        };

        TrackingEventProcessorConfiguration tepConfiguration =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                   .andEventTrackerStatusChangeListener(statusChangeListener);
        initProcessor(tepConfiguration);

        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);
        publishEvents(2);

        assertThat(eventHandlingLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(statusChangeLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(addedStatusCounter.get()).isOne();
        assertThat(updatedStatusCounter.get()).isOne();
        assertThat(removedStatusCounter.get()).isZero();
    }

    /**
     * This test can spot five invocations of the {@link EventTrackerStatusChangeListener}, but asserts four:
     * <ol>
     *     <li> First call is when the single active {@link org.sophonhq.sophon.eventhandling.Segment} is added.</li>
     *     <li> Second call is when the status transitions to {@link EventTrackerStatus#isCaughtUp()}.</li>
     *     <li> Third call is when the {@link EventTrackerStatus#getCurrentPosition()} moves to 0.</li>
     *     <li> Fourth call is when the {@link EventTrackerStatus#getCurrentPosition()} moves to 1.</li>
     *     <li>
     *         The last not asserted call is when the {@link TrackingEventProcessor} is shutting down, which removes the
     *         status. This isn't taking into account as it is part of the {@link #tearDown()}.
     *     </li>
     * </ol>
     */
    @Test
    void publishedEventsUpdateStatusAndHitChangeListenerIncludingPositions() throws Exception {
        CountDownLatch eventHandlingLatch = new CountDownLatch(2);
        doAnswer(invocation -> {
            eventHandlingLatch.countDown();
            return null;
        }).when(mockHandler).handle(any());

        CountDownLatch statusChangeLatch = new CountDownLatch(4);
        AtomicInteger addedStatusCounter = new AtomicInteger(0);
        AtomicInteger updatedStatusCounter = new AtomicInteger(0);
        AtomicInteger removedStatusCounter = new AtomicInteger(0);
        EventTrackerStatusChangeListener statusChangeListener = new EventTrackerStatusChangeListener() {
            @Override
            public void onEventTrackerStatusChange(Map<Integer, EventTrackerStatus> updatedTrackerStatus) {
                assertThat(updatedTrackerStatus).hasSize(1);
                EventTrackerStatus eventTrackerStatus = updatedTrackerStatus.get(0);
                if (eventTrackerStatus.trackerAdded()) {
                    addedStatusCounter.getAndIncrement();
                } else if (eventTrackerStatus.trackerRemoved()) {
                    removedStatusCounter.getAndIncrement();
                } else {
                    updatedStatusCounter.getAndIncrement();
                }
                statusChangeLatch.countDown();
            }

            @Override
            public boolean validatePositions() {
                return true;
            }
        };

        TrackingEventProcessorConfiguration tepConfiguration =
                TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                   .andEventTrackerStatusChangeListener(statusChangeListener);
        initProcessor(tepConfiguration);

        testSubject.start();
        // Give it a bit of time to start
        Thread.sleep(200);
        publishEvents(2);

        assertThat(eventHandlingLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(statusChangeLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(addedStatusCounter.get()).isOne();
        assertThat(updatedStatusCounter.get()).isEqualTo(3);
        assertThat(removedStatusCounter.get()).isZero();
    }

    @Test
    @Timeout(value = 15)
    void splitAndMergeInfluenceOnChangeListenerInvocations() throws InterruptedException {
        int firstSegment = 0;
        int secondSegment = 1;

        CountDownLatch addedStatusLatch = new CountDownLatch(4);
        CountDownLatch updatedStatusLatch = new CountDownLatch(1);
        CountDownLatch removedStatusLatch = new CountDownLatch(3);
        EventTrackerStatusChangeListener statusChangeListener = updatedTrackerStatus -> {
            assertThat(updatedTrackerStatus).hasSize(1);
            EventTrackerStatus eventTrackerStatus = updatedTrackerStatus.values().iterator().next();
            if (eventTrackerStatus.trackerAdded()) {
                addedStatusLatch.countDown();
            } else if (eventTrackerStatus.trackerRemoved()) {
                removedStatusLatch.countDown();
            } else {
                updatedStatusLatch.countDown();
            }
        };

        // Provide some spare threads so that assertions don't block the processor.
        // Added, lower the event availability timeout to have the TEP threads pick up the segments faster.
        TrackingEventProcessorConfiguration tepConfiguration =
                TrackingEventProcessorConfiguration.forParallelProcessing(4)
                                                   .andInitialSegmentsCount(2)
                                                   .andEventAvailabilityTimeout(50, TimeUnit.MILLISECONDS)
                                                   .andEventTrackerStatusChangeListener(statusChangeListener);
        initProcessor(tepConfiguration);
        tokenStore.initializeTokenSegments(testSubject.getName(), 1);

        publishEvents(2);
        testSubject.start();
        assertWithin(5, TimeUnit.SECONDS, () -> assertThat(testSubject.processingStatus()).containsKey(firstSegment));

        assertThat(testSubject.splitSegment(firstSegment).join()).as("Expected split to succeed").isTrue();
        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(firstSegment, secondSegment);
        assertWithin(5, TimeUnit.SECONDS, () -> assertThat(testSubject.processingStatus()).containsKey(secondSegment));

        assertWithin(
                500, TimeUnit.MILLISECONDS,
                () -> assertThat(testSubject.mergeSegment(firstSegment).join()).as("Expected merge to succeed").isTrue()
        );
        assertThat(tokenStore.fetchSegments(testSubject.getName())).containsExactly(firstSegment);
        assertWithin(5, TimeUnit.SECONDS, () -> assertThat(testSubject.processingStatus()).containsKey(firstSegment));

        assertThat(addedStatusLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(updatedStatusLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(removedStatusLatch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void caughtUpSetToTrueAfterWaitingForEventAvailabilityTimeout() {
        AtomicBoolean hasNextInvoked = new AtomicBoolean(false);
        AtomicBoolean hasNextAvailableTimedOut = new AtomicBoolean(true);
        // Set up two threads and one segment, to provide more "space" for assertions.
        TrackingEventProcessorConfiguration tepConfiguration =
                TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                   .andInitialSegmentsCount(1)
                                                   .andEventAvailabilityTimeout(100, TimeUnit.MILLISECONDS);
        EventStore enhancedEventStore = new EmbeddedEventStore(
                EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine())
        ) {
            @Override
            public TrackingEventStream openStream(TrackingToken trackingToken) {
                //noinspection resource
                TrackingEventStream trackingEventStream = super.openStream(trackingToken);
                return new TrackingEventStream() {
                    @Override
                    public Optional<TrackedEventMessage<?>> peek() {
                        return trackingEventStream.peek();
                    }

                    @Override
                    public boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException {
                        hasNextInvoked.set(true);
                        boolean result = trackingEventStream.hasNextAvailable(timeout, unit);
                        hasNextAvailableTimedOut.set(false);
                        // Add sleep to ensure switching hasNextAvailableTimedOut, and it's assertion has precedence
                        // over regular execution of the TrackingEventProcessor.
                        Thread.sleep(5);
                        return result;
                    }

                    @Override
                    public TrackedEventMessage<?> nextAvailable() throws InterruptedException {
                        return trackingEventStream.nextAvailable();
                    }

                    @Override
                    public void close() {
                        trackingEventStream.close();
                    }
                };
            }
        };
        initProcessor(tepConfiguration, builder -> builder.messageSource(enhancedEventStore));
        testSubject.start();

        assertWithin(100, TimeUnit.MILLISECONDS, () -> assertThat(hasNextInvoked.get()).isTrue());

        assertUntil(hasNextAvailableTimedOut::get, Duration.ofMillis(10), () -> {
            EventTrackerStatus trackerOneStatus = testSubject.processingStatus().get(0);
            assertThat(trackerOneStatus).isNotNull();
            assertThat(trackerOneStatus.isCaughtUp()).isFalse();
        });

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> assertThat(testSubject.processingStatus().get(0).isCaughtUp()).isTrue()
        );
    }

    @Test
    void refuseStartDuringShutdown() throws Exception {
        initProcessor(TrackingEventProcessorConfiguration.forSingleThreadedProcessing()
                                                         .andEventAvailabilityTimeout(1000, TimeUnit.MILLISECONDS));
        CountDownLatch cdl = new CountDownLatch(1);
        publishEvents(10);
        doAnswer(i -> {
            cdl.countDown();
            Thread.sleep(100);
            return i.callRealMethod();
        }).when(eventHandlerInvoker).handle(any(), any());
        testSubject.start();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(testSubject.processingStatus()).isNotEmpty());

        cdl.await();
        testSubject.shutdownAsync();

        IllegalStateException result = assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> testSubject.start())
                .actual();
        assertThat(result.getMessage()).contains("pending shutdown");
    }

    @Test
    @Timeout(value = 1000)
    void isReplayingWhenNotCaughtUp() throws Exception {
        when(mockHandler.supportsReset()).thenReturn(true);

        final List<String> handled = new CopyOnWriteArrayList<>();
        final List<String> handledInRedelivery = new CopyOnWriteArrayList<>();
        int segmentId = 0;
        int numberOfEvents = 750;

        AtomicInteger delay = new AtomicInteger(0);

        //noinspection Duplicates
        doAnswer(i -> {
            EventMessage<?> message = i.getArgument(0);
            if (ReplayToken.isReplay(message)) {
                handledInRedelivery.add(message.getIdentifier());
            }
            handled.add(message.getIdentifier());
            Thread.sleep(delay.get());
            return null;
        }).when(mockHandler).handle(any());

        testSubject.start();
        awaitProcessorStarted();
        // ensure some events have been handled by the TEP
        eventBus.publish(createEvents(numberOfEvents));
        await("Handled Events")
                .atMost(Duration.ofSeconds(2))
                .pollDelay(Duration.ofMillis(50))
                .until(() -> handled.size() == numberOfEvents);

        assertThat(handledInRedelivery).isEmpty();

        // initiate reset to toggle replay status
        testSubject.shutDown();
        testSubject.resetTokens();
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(testSubject.processingStatus()).isEmpty());

        // Set a processing delay to ensure we see some redelivery events first.
        delay.set(10);

        testSubject.start();

        assertWithin(
                100, TimeUnit.MILLISECONDS,
                () -> {
                    assertThat(testSubject.processingStatus()).isNotEmpty();
                    assertThat(testSubject.processingStatus().get(segmentId).isCaughtUp()).isFalse();
                    assertThat(testSubject.processingStatus().get(segmentId).isReplaying()).isTrue();
                    assertThat(testSubject.isReplaying()).isTrue();
                }
        );

        // remove the processing delay to rush to the end
        delay.set(0);

        assertWithin(
                1, TimeUnit.SECONDS,
                () -> {
                    assertThat(testSubject.processingStatus().get(segmentId).isCaughtUp()).isTrue();
                    assertThat(testSubject.processingStatus().get(segmentId).isReplaying()).isTrue();
                }
        );
    }

    @Test
    void processorOnlyTriesToClaimAvailableSegments() {
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 0);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(2L), "test", 1);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 2);
        tokenStore.storeToken(new GlobalSequenceTrackingToken(1L), "test", 3);
        when(tokenStore.fetchAvailableSegments(testSubject.getName()))
                .thenReturn(Collections.singletonList(Segment.computeSegment(2, 0, 1, 2, 3)));

        testSubject.start();

        eventBus.publish(createEvents(1));

        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(testSubject.processingStatus()).hasSize(1));
        assertWithin(1, TimeUnit.SECONDS, () -> assertThat(testSubject.processingStatus()).containsKey(2));
        verify(tokenStore, never())
                .fetchToken(eq(testSubject.getName()), intThat(i -> Arrays.asList(0, 1, 3).contains(i)));
    }

    @Test
    void shutdownTerminatesWorkersAfterConfiguredWorkerTerminationTimeout() throws Exception {
        int testWorkerTerminationTimeout = 50;
        List<Thread> createdThreads = new CopyOnWriteArrayList<>();
        // A higher event availability timeout will block a worker thread that should shut down
        initProcessor(TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                         .andInitialSegmentsCount(2)
                                                         .andBatchSize(100)
                                                         .andThreadFactory(n -> r -> {
                                                             Thread thread = new Thread(r, n);
                                                             createdThreads.add(thread);
                                                             return thread;
                                                         })
                                                         .andWorkerTerminationTimeout(
                                                                 testWorkerTerminationTimeout, TimeUnit.MILLISECONDS
                                                         )
                                                         .andEventAvailabilityTimeout(20, TimeUnit.SECONDS));

        testSubject.start();

        // sleep a little to reach the EventAvailabilityTimeout usage on the Event Stream
        Thread.sleep(500);

        assertThat(createdThreads).hasSize(2);

        CompletableFuture<Void> result = testSubject.shutdownAsync();
        await().pollDelay(Duration.ofMillis(25))
               .atMost(Duration.ofMillis(testWorkerTerminationTimeout * 4))
               .until(result::isDone);
        assertThat(createdThreads.get(0).isAlive()).isFalse();
        assertThat(createdThreads.get(1).isAlive()).isFalse();
    }

    @Test
    void existingEventsBeforeProcessorStartAreConsideredReplayed() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> countDownLatch.countDown());
            return interceptorChain.proceed();
        }));
        eventBus.publish(createEvent(0));
        eventBus.publish(createEvent(1));
        eventBus.publish(createEvent(2));

        testSubject.start();

        assertThat(countDownLatch.await(5, TimeUnit.SECONDS))
                .as("Expected Unit of Work to have reached clean up phase")
                .isTrue();
        TrackingToken trackingToken = tokenStore.fetchToken(testSubject.getName(), 0);
        assertThat(ReplayToken.isReplay(trackingToken)).as("Not a replay token: " + trackingToken).isTrue();
    }

    @Test
    void eventsPublishedAfterProcessorStartAreNotConsideredReplayed() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        //noinspection resource
        testSubject.registerHandlerInterceptor(((unitOfWork, interceptorChain) -> {
            unitOfWork.onCleanup(uow -> started.countDown());
            unitOfWork.onCleanup(uow -> finished.countDown());
            return interceptorChain.proceed();
        }));
        eventBus.publish(createEvent(0));

        doAnswer(i -> i.callRealMethod()).when(tokenStore).storeToken(any(), anyString(), anyInt());
        doAnswer(i -> i.callRealMethod()).when(tokenStore).initializeTokenSegments(anyString(), anyInt(), any());

        testSubject.start();

        started.await();

        eventBus.publish(createEvent(2));

        assertThat(finished.await(5, TimeUnit.SECONDS))
                .as("Expected Unit of Work to have reached clean up phase")
                .isTrue();
        TrackingToken trackingToken = tokenStore.fetchToken(testSubject.getName(), 0);
        assertThat(ReplayToken.isReplay(trackingToken)).as("Not a replay token: " + trackingToken).isFalse();
    }

    @Test
    @Timeout(value = 15)
    void timeoutInterceptorRaceWithNonTimeoutExceptionDoesNotShutDownProcessor() throws Exception {
        // Regression test for: https://github.com/AxonFramework/AxonFramework/issues/4456
        // SophonTimeLimitedTask's scheduled interrupt lambda can fire after detectInterruptionInsteadOfException
        // has already determined that the exception was NOT a timeout. The fix is that complete() is called
        // early in the catch block and is synchronized with the lambda — so either the lambda never fires,
        // or complete() clears the interrupt the lambda set before it can reach doSleepFor.
        AtomicInteger handlerInvocations = new AtomicInteger();
        CountDownLatch processedAfterError = new CountDownLatch(2);

        doAnswer(invocation -> {
            if (handlerInvocations.getAndIncrement() == 0) {
                throw new MockException("Simulated handler failure");
            }
            processedAfterError.countDown();
            return null;
        }).when(mockHandler).handle(any());

        TrackingEventProcessor processorWithRealSleep =
                TrackingEventProcessor.builder()
                                      .name("timeoutRaceTest")
                                      .eventHandlerInvoker(eventHandlerInvoker)
                                      .messageSource(eventBus)
                                      .tokenStore(new InMemoryTokenStore())
                                      .transactionManager(NoTransactionManager.INSTANCE)
                                      .build();
        // A 1ms timeout maximises the chance that the interrupt lambda fires concurrently with the
        // exception-handling path, exercising the synchronized complete() + early-complete fix.
        processorWithRealSleep.registerHandlerInterceptor(
                new UnitOfWorkTimeoutInterceptor("timeoutRaceTest", 1, Integer.MAX_VALUE, 1)
        );
        processorWithRealSleep.start();

        eventBus.publish(createEvents(2));

        assertThat(processedAfterError.await(10, TimeUnit.SECONDS))
                .as("Processor should continue running and handle events despite timeout/exception race")
                .isTrue();
        assertThat(processorWithRealSleep.isRunning()).as("Processor should still be running").isTrue();
        processorWithRealSleep.shutDown();
    }

    private void waitForStatus(String description,
                               long time,
                               TimeUnit unit,
                               Predicate<Map<Integer, EventTrackerStatus>> status) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(time);
        while (!status.test(testSubject.processingStatus())) {
            if (deadline < System.currentTimeMillis()) {
                fail("Expected state '" + description + "'' within " + time + " " + unit.name());
            }
            Thread.sleep(10);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void waitForSegmentStart(int segmentId) throws InterruptedException {
        while (!testSubject.processingStatus().containsKey(segmentId)) {
            Thread.sleep(10);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void waitForSegmentRelease(int segmentId) throws InterruptedException {
        while (testSubject.processingStatus().containsKey(segmentId)) {
            Thread.sleep(10);
        }
    }

    private void waitForActiveThreads(int minimalThreadCount) throws InterruptedException {
        while (testSubject.processingStatus().size() < minimalThreadCount) {
            Thread.sleep(10);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private EventTrackerStatus waitForProcessingStatus(int segmentId,
                                                       Predicate<EventTrackerStatus> expectedStatus)
            throws InterruptedException {
        EventTrackerStatus status = testSubject.processingStatus().get(segmentId);
        while (!Optional.ofNullable(status)
                        .map(expectedStatus::test)
                        .orElse(false)) {
            Thread.sleep(1);
            status = testSubject.processingStatus().get(segmentId);
        }
        return status;
    }

    private void publishEvents(int nrOfEvents) {
        for (int i = 0; i < nrOfEvents; i++) {
            eventBus.publish(createEvent(UUID.randomUUID().toString(), 0));
        }
    }

    private static class StubTrackingEventStream implements TrackingEventStream {

        private final Queue<TrackedEventMessage<?>> eventMessages;

        public StubTrackingEventStream(long... tokens) {
            GapAwareTrackingToken lastToken = GapAwareTrackingToken.newInstance(-1, emptySortedSet());
            eventMessages = new LinkedList<>();
            for (Long seq : tokens) {
                lastToken = lastToken.advanceTo(seq, 1000);
                eventMessages.add(new GenericTrackedEventMessage<>(lastToken, createEvent(seq)));
            }
        }

        @Override
        public Optional<TrackedEventMessage<?>> peek() {
            if (eventMessages.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(eventMessages.peek());
        }

        @Override
        public boolean hasNextAvailable(int timeout, TimeUnit unit) {
            return !eventMessages.isEmpty();
        }

        @Override
        public TrackedEventMessage<?> nextAvailable() {
            return eventMessages.poll();
        }

        @Override
        public void close() {

        }
    }

    private static class TestError extends Error {

        private static final long serialVersionUID = -5579826202840099704L;
    }

    @SuppressWarnings("unused")
    private static class MyResetContext {

        private String property;

        private MyResetContext(String property) {
            this.property = property;
        }

        public String getProperty() {
            return property;
        }

        public void setProperty(String property) {
            this.property = property;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MyResetContext that = (MyResetContext) o;

            return Objects.equals(property, that.property);
        }

        @Override
        public int hashCode() {
            return property != null ? property.hashCode() : 0;
        }
    }
}
