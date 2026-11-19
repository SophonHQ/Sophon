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

package org.sophonhq.sophon.eventhandling.pooled;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.sophonhq.sophon.common.transaction.NoTransactionManager;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.eventhandling.GenericTrackedEventMessage;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.ReplayToken;
import org.sophonhq.sophon.eventhandling.Segment;
import org.sophonhq.sophon.eventhandling.TrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackerStatus;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import org.sophonhq.sophon.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.UnitOfWork;
import org.sophonhq.sophon.utils.DelegateScheduledExecutorService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.utils.AssertUtils.assertWithin;

/**
 * Test class validating the {@link WorkPackage}.
 *
 * @author Steven van Beelen
 */
class WorkPackageTest {

    private static final String PROCESSOR_NAME = "test";

    private TokenStore tokenStore;
    private ScheduledExecutorService executorService;
    private TestEventFilter eventFilter;
    private TestBatchProcessor batchProcessor;
    private Segment segment;
    private TrackingToken initialTrackingToken;

    private WorkPackage.Builder testSubjectBuilder;
    private WorkPackage testSubject;

    private TrackerStatus trackerStatus;
    private List<TrackerStatus> trackerStatusUpdates;
    private Predicate<TrackedEventMessage<?>> eventFilterPredicate;
    private Predicate<List<? extends EventMessage<?>>> batchProcessorPredicate;

    @BeforeEach
    void setUp() {
        tokenStore = spy(new InMemoryTokenStore());
        executorService = spy(new DelegateScheduledExecutorService(Executors.newScheduledThreadPool(1)));
        eventFilter = new TestEventFilter();
        batchProcessor = new TestBatchProcessor();
        segment = Segment.ROOT_SEGMENT;
        initialTrackingToken = new GlobalSequenceTrackingToken(0L);

        trackerStatus = new TrackerStatus(segment, initialTrackingToken);
        trackerStatusUpdates = new ArrayList<>();
        eventFilterPredicate = event -> true;
        batchProcessorPredicate = event -> true;

        testSubjectBuilder = WorkPackage.builder()
                                        .name(PROCESSOR_NAME)
                                        .tokenStore(tokenStore)
                                        .transactionManager(NoTransactionManager.instance())
                                        .executorService(executorService)
                                        .eventFilter(eventFilter)
                                        .batchProcessor(batchProcessor)
                                        .segment(segment)
                                        .initialToken(initialTrackingToken)
                                        .batchSize(1)
                                        .claimExtensionThreshold(5000)
                                        .segmentStatusUpdater(op -> {
                                            TrackerStatus update = op.apply(trackerStatus);
                                            trackerStatusUpdates.add(update);
                                            trackerStatus = update;
                                        });
        testSubject = testSubjectBuilder.build();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    /**
     * The "last delivered token" is configured as the initialToken for a fresh WorkPackage.
     */
    @Test
    void scheduleEventDoesNotScheduleIfTheLastDeliveredTokenCoversTheEventsToken() {
        TrackedEventMessage<String> testEvent = new GenericTrackedEventMessage<>(
                new GlobalSequenceTrackingToken(1L), GenericEventMessage.asEventMessage("some-event")
        );

        WorkPackage testSubjectWithCustomInitialToken =
                testSubjectBuilder.initialToken(new GlobalSequenceTrackingToken(2L))
                                  .build();

        testSubjectWithCustomInitialToken.scheduleEvent(testEvent);

        verifyNoInteractions(executorService);
    }

    @Test
    void scheduleEventUpdatesLastDeliveredToken() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));

        testSubject.scheduleEvent(testEvent);

        assertThat(testSubject.lastDeliveredToken()).isEqualTo(expectedToken);
    }

    @Test
    void scheduleEventFailsOnEventValidator() throws ExecutionException, InterruptedException {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEvent =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("some-event"));
        eventFilterPredicate = event -> {
            if (event.equals(testEvent)) {
                throw new IllegalStateException("Some exception");
            }
            return true;
        };

        testSubject.scheduleEvent(testEvent);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(trackerStatus).isNull());
        assertThat(trackerStatusUpdates).hasSize(2);
        assertThat(trackerStatusUpdates.get(0).isErrorState()).isTrue();
        assertThat(trackerStatusUpdates.get(1)).isNull();

        CompletableFuture<Throwable> abortResult = testSubject.abort(null);
        assertThat(abortResult.isDone()).isTrue();
        assertThat(abortResult.get().getClass().isAssignableFrom(IllegalStateException.class)).isTrue();
    }

    @Test
    void scheduleEventFailsOnEventValidatorThrowingError() throws ExecutionException, InterruptedException {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEvent =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("some-event"));
        Error testError = new LinkageError("Some error");
        eventFilterPredicate = event -> {
            if (event.equals(testEvent)) {
                throw testError;
            }
            return true;
        };

        assertThat(testSubject.scheduleEvent(testEvent)).isFalse();

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(trackerStatus).isNull());
        assertThat(trackerStatusUpdates).hasSize(2);
        assertThat(trackerStatusUpdates.get(0).isErrorState()).isTrue();
        assertThat(trackerStatusUpdates.get(0).getError()).isSameAs(testError);
        assertThat(trackerStatusUpdates.get(1)).isNull();

        CompletableFuture<Throwable> abortResult = testSubject.abort(null);
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(abortResult.isDone()).isTrue());
        assertThat(abortResult.get()).isSameAs(testError);
    }

    @Test
    void scheduleEventFailsOnBatchProcessor() throws ExecutionException, InterruptedException {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEvent =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("some-event"));
        batchProcessorPredicate = event -> {
            if (event.stream().anyMatch(e -> ((TrackedEventMessage<?>) e).trackingToken().equals(testToken))) {
                throw new IllegalStateException("Some exception");
            }
            return true;
        };

        testSubject.scheduleEvent(testEvent);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(trackerStatus).isNull());
        assertThat(trackerStatusUpdates).hasSize(2);
        assertThat(trackerStatusUpdates.get(0).isErrorState()).isTrue();
        assertThat(trackerStatusUpdates.get(1)).isNull();

        CompletableFuture<Throwable> abortResult = testSubject.abort(null);
        assertThat(abortResult.isDone()).isTrue();
        assertThat(abortResult.get().getClass().isAssignableFrom(IllegalStateException.class)).isTrue();
    }

    @Test
    void scheduleEventFailsOnBatchProcessorThrowingError() throws ExecutionException, InterruptedException {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEvent =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("some-event"));
        Error testError = new LinkageError("Some error");
        batchProcessorPredicate = event -> {
            if (event.stream().anyMatch(e -> ((TrackedEventMessage<?>) e).trackingToken().equals(testToken))) {
                throw testError;
            }
            return true;
        };

        testSubject.scheduleEvent(testEvent);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(trackerStatus).isNull());
        assertThat(trackerStatusUpdates).hasSize(2);
        assertThat(trackerStatusUpdates.get(0).isErrorState()).isTrue();
        assertThat(trackerStatusUpdates.get(0).getError()).isSameAs(testError);
        assertThat(trackerStatusUpdates.get(1)).isNull();

        CompletableFuture<Throwable> abortResult = testSubject.abort(null);
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(abortResult.isDone()).isTrue());
        assertThat(abortResult.get()).isSameAs(testError);
    }

    /**
     * This means an event was scheduled, was validated to be handled by the EventValidator, processed by the
     * BatchProcessor and the updated token stored.
     */
    @Test
    void scheduleEventRunsSuccessfully() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));

        testSubject.scheduleEvent(expectedEvent);

        List<EventMessage<?>> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(validatedEvents).hasSize(1));
        assertThat(validatedEvents.get(0)).isEqualTo(expectedEvent);

        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(processedEvents).hasSize(1));
        assertThat(((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken())
                .isEqualTo(expectedEvent.trackingToken());

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertThat(tokenCaptor.getValue()).isEqualTo(expectedToken);

        assertThat(trackerStatusUpdates).hasSize(1);
        OptionalLong resultPosition = trackerStatusUpdates.get(0).getCurrentPosition();
        assertThat(resultPosition.isPresent()).isTrue();
        assertThat(resultPosition.getAsLong()).isOne();
    }

    @Test
    void replayTokenIsPropagatedAndAdvancedWithoutCurrent() {
        testSubjectBuilder.initialToken(new ReplayToken(new GlobalSequenceTrackingToken(1L)));
        testSubject = testSubjectBuilder.build();
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));

        testSubject.scheduleEvent(expectedEvent);

        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(processedEvents).hasSize(1));

        ReplayToken expectedAdvancedToken = new ReplayToken(
                new GlobalSequenceTrackingToken(1L),
                new GlobalSequenceTrackingToken(1L)
        );
        assertThat(((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken()).isEqualTo(expectedAdvancedToken);
    }


    @Test
    void replayTokenIsPropagatedAndAdvancedWithCurrent() {
        testSubjectBuilder.initialToken(new ReplayToken(new GlobalSequenceTrackingToken(1L),
                                                        new GlobalSequenceTrackingToken(0L)));
        testSubject = testSubjectBuilder.build();
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));

        testSubject.scheduleEvent(expectedEvent);

        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(processedEvents).hasSize(1));

        ReplayToken expectedAdvancedToken = new ReplayToken(
                new GlobalSequenceTrackingToken(1L),
                new GlobalSequenceTrackingToken(1L)
        );
        assertThat(((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken()).isEqualTo(expectedAdvancedToken);
    }

    @Test
    void scheduleEventExtendsTokenClaimAfterClaimThresholdExtension() {
        // The short threshold ensures the packages assume the token should be reclaimed.
        int extremelyShortClaimExtensionThreshold = 1;
        WorkPackage testSubjectWithShortThreshold =
                testSubjectBuilder.claimExtensionThreshold(extremelyShortClaimExtensionThreshold)
                                  .build();

        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));
        testSubjectWithShortThreshold.scheduleEvent(expectedEvent);

        // Should have handled one event, so a subsequent run of WorkPackage#processEvents will extend the claim.
        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(processedEvents).hasSize(1));
        assertThat(((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken())
                .isEqualTo(expectedEvent.trackingToken());
        // We  need to verify the TokenStore#storeToken operation, otherwise the extendClaim verify will not succeed.
        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertThat(tokenCaptor.getValue()).isEqualTo(expectedToken);

        assertWithin(
                500, TimeUnit.MILLISECONDS,
                () -> {
                    // Consciously trigger the WorkPackage again, to force it through WorkPackage#processEvents.
                    // This should be done inside the assertWithin, as the WorkPackage does not re-trigger itself.
                    // Furthermore, the test could be to fast to incorporate the extremelyShortClaimExtensionThreshold as a reason to extend the claim too.
                    testSubjectWithShortThreshold.scheduleWorker();
                    verify(tokenStore, atLeastOnce()).extendClaim(PROCESSOR_NAME, segment.getSegmentId());
                }
        );
    }

    /**
     * This requires the WorkPackage to have received events which it should not handle.
     */
    @Test
    void scheduleEventUpdatesTokenAfterClaimThresholdExtension() {
        // The short threshold ensures the packages assume the token should be reclaimed.
        int extremelyShortClaimExtensionThreshold = 1;
        WorkPackage testSubjectWithShortThreshold =
                testSubjectBuilder.claimExtensionThreshold(extremelyShortClaimExtensionThreshold)
                                  .build();
        // Adjust the EventValidator to reject all events
        eventFilterPredicate = event -> false;

        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("some-event"));
        testSubjectWithShortThreshold.scheduleEvent(expectedEvent);

        List<EventMessage<?>> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(validatedEvents).hasSize(1));
        assertThat(validatedEvents.get(0)).isEqualTo(expectedEvent);

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);

        assertWithin(
                500, TimeUnit.MILLISECONDS,
                () -> {
                    // Consciously trigger the WorkPackage again, to force it through WorkPackage#processEvents.
                    // This should be done inside the assertWithin, as the WorkPackage does not re-trigger itself.
                    // Furthermore, the test could be to fast to incorporate the extremelyShortClaimExtensionThreshold as a reason to extend the claim too.
                    testSubjectWithShortThreshold.scheduleWorker();
                    verify(tokenStore, atLeastOnce())
                            .storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
                }
        );
        assertThat(tokenCaptor.getValue()).isEqualTo(expectedToken);
    }

    @Test
    void scheduleWorkerForAbortedPackage() throws ExecutionException, InterruptedException {
        CompletableFuture<Throwable> result = testSubject.abort(null);

        testSubject.scheduleWorker();

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(trackerStatus).isNull());
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(result.isDone()).isTrue());
        assertThat(result.get()).isNull();
    }

    @Test
    void hasRemainingCapacityReturnsTrueForWorkPackageWithoutScheduledEvents() {
        assertThat(testSubject.hasRemainingCapacity()).isTrue();
    }

    @Test
    void segment() {
        assertThat(testSubject.segment()).isEqualTo(segment);
    }

    @Test
    void lastDeliveredTokenEqualsInitialTokenWhenNoEventsHaveBeenScheduled() {
        assertThat(testSubject.lastDeliveredToken()).isEqualTo(initialTrackingToken);
    }

    @Test
    void isAbortTriggeredReturnsFalseInAbsenceOfAbort() {
        assertThat(testSubject.isAbortTriggered()).isFalse();
    }

    @Test
    void isAbortTriggeredReturnsTrueAfterAbortInvocation() {
        testSubject.abort(null);
        assertThat(testSubject.isAbortTriggered()).isTrue();
    }

    @Test
    void abortReturnsAbortReason() throws ExecutionException, InterruptedException {
        Exception expectedResult = new IllegalStateException();

        CompletableFuture<Throwable> result = testSubject.abort(expectedResult);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(result.isDone()).isTrue());
        assertThat(result.get()).isEqualTo(expectedResult);
    }

    @Test
    void abortReturnsOriginalAbortReason() throws ExecutionException, InterruptedException {
        Exception originalAbortReason = new IllegalStateException();
        Exception otherAbortReason = new IllegalArgumentException();
        testSubject.abort(originalAbortReason);

        CompletableFuture<Throwable> result = testSubject.abort(otherAbortReason);

        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(result.isDone()).isTrue());
        assertThat(result.get()).isEqualTo(originalAbortReason);
    }

    @Test
    void scheduleEventsReturnsFalseForEmptyList() {
        assertThat(testSubject.scheduleEvents(Collections.emptyList())).isFalse();
    }

    @Test
    void scheduleEventsThrowsIllegalArgumentExceptionForNoneMatchingTokens() {
        TrackingToken testTokenOne = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEventOne =
                new GenericTrackedEventMessage<>(testTokenOne, GenericEventMessage.asEventMessage("this-event"));
        TrackingToken testTokenTwo = new GlobalSequenceTrackingToken(2L);
        TrackedEventMessage<String> testEventTwo =
                new GenericTrackedEventMessage<>(testTokenTwo, GenericEventMessage.asEventMessage("that-event"));
        List<TrackedEventMessage<?>> testEvents = new ArrayList<>();
        testEvents.add(testEventOne);
        testEvents.add(testEventTwo);

        assertThatThrownBy(() -> testSubject.scheduleEvents(testEvents))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The "last delivered token" is configured as the initialToken for a fresh WorkPackage.
     */
    @Test
    void scheduleEventsDoesNotScheduleIfTheLastDeliveredTokensCoversTheEventsToken() {
        TrackingToken testToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> testEventOne =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("this-event"));
        TrackedEventMessage<String> testEventTwo =
                new GenericTrackedEventMessage<>(testToken, GenericEventMessage.asEventMessage("that-event"));
        List<TrackedEventMessage<?>> testEvents = new ArrayList<>();
        testEvents.add(testEventOne);
        testEvents.add(testEventTwo);

        WorkPackage testSubjectWithCustomInitialToken =
                testSubjectBuilder.initialToken(new GlobalSequenceTrackingToken(2L))
                                  .build();

        boolean result = testSubjectWithCustomInitialToken.scheduleEvents(testEvents);

        assertThat(result).isFalse();
        verifyNoInteractions(executorService);
    }

    @Test
    void scheduleEventsReturnsTrueIfOnlyOneEventIsAcceptedByTheEventValidator() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> filteredEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("this-event"));
        TrackedEventMessage<String> expectedEvent =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("that-event"));
        List<TrackedEventMessage<?>> testEvents = new ArrayList<>();
        testEvents.add(filteredEvent);
        testEvents.add(expectedEvent);

        eventFilterPredicate = event -> !event.equals(filteredEvent);

        boolean result = testSubject.scheduleEvents(testEvents);

        assertThat(result).isTrue();

        List<EventMessage<?>> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(validatedEvents).hasSize(2));
        assertThat(validatedEvents).containsAll(testEvents);

        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(processedEvents).hasSize(1));
        assertThat(((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken())
                .isEqualTo(expectedEvent.trackingToken());

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertThat(tokenCaptor.getValue()).isEqualTo(expectedToken);

        assertThat(trackerStatusUpdates).hasSize(1);
        OptionalLong resultPosition = trackerStatusUpdates.get(0).getCurrentPosition();
        assertThat(resultPosition.isPresent()).isTrue();
        assertThat(resultPosition.getAsLong()).isOne();
    }

    @Test
    void scheduleEventsHandlesAllEventsInOneTransactionWhenAllEventsCanBeHandled() {
        TrackingToken expectedToken = new GlobalSequenceTrackingToken(1L);
        TrackedEventMessage<String> expectedEventOne =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("this-event"));
        TrackedEventMessage<String> expectedEventTwo =
                new GenericTrackedEventMessage<>(expectedToken, GenericEventMessage.asEventMessage("that-event"));
        List<TrackedEventMessage<?>> expectedEvents = new ArrayList<>();
        expectedEvents.add(expectedEventOne);
        expectedEvents.add(expectedEventTwo);

        boolean result = testSubject.scheduleEvents(expectedEvents);

        assertThat(result).isTrue();

        List<EventMessage<?>> validatedEvents = eventFilter.getValidatedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(validatedEvents).hasSize(2));
        assertThat(validatedEvents).containsAll(expectedEvents);

        List<EventMessage<?>> processedEvents = batchProcessor.getProcessedEvents();
        assertWithin(500, TimeUnit.MILLISECONDS, () -> assertThat(processedEvents).hasSize(2));
        assertThat(((TrackedEventMessage<?>) processedEvents.get(0)).trackingToken())
                .isEqualTo(expectedEventOne.trackingToken());
        assertThat(((TrackedEventMessage<?>) processedEvents.get(1)).trackingToken())
                .isEqualTo(expectedEventTwo.trackingToken());

        ArgumentCaptor<TrackingToken> tokenCaptor = ArgumentCaptor.forClass(TrackingToken.class);
        verify(tokenStore).storeToken(tokenCaptor.capture(), eq(PROCESSOR_NAME), eq(segment.getSegmentId()));
        assertThat(tokenCaptor.getValue()).isEqualTo(expectedToken);

        assertThat(trackerStatusUpdates.size()).isGreaterThanOrEqualTo(1);
        OptionalLong resultPosition = trackerStatusUpdates.get(0).getCurrentPosition();
        assertThat(resultPosition.isPresent()).isTrue();
        assertThat(resultPosition.getAsLong()).isOne();
    }

    private class TestEventFilter implements WorkPackage.EventFilter {

        private final List<EventMessage<?>> validatedEvents = new ArrayList<>();

        @Override
        public boolean canHandle(TrackedEventMessage<?> eventMessage, Segment segment) {
            validatedEvents.add(eventMessage);
            return eventFilterPredicate.test(eventMessage);
        }

        public List<EventMessage<?>> getValidatedEvents() {
            return validatedEvents;
        }
    }

    private class TestBatchProcessor implements WorkPackage.BatchProcessor {

        private final List<EventMessage<?>> processedEvents = new ArrayList<>();

        @Override
        public void processBatch(List<? extends EventMessage<?>> eventMessages,
                                 UnitOfWork<? extends EventMessage<?>> unitOfWork,
                                 Collection<Segment> processingSegments) {
            if (batchProcessorPredicate.test(eventMessages)) {
                unitOfWork.executeWithResult(() -> {
                    unitOfWork.commit();
                    processedEvents.addAll(eventMessages);
                    // We don't care about the result to perform our tests. Just return null.
                    return null;
                });
            }
        }

        public List<EventMessage<?>> getProcessedEvents() {
            return processedEvents;
        }
    }
}
