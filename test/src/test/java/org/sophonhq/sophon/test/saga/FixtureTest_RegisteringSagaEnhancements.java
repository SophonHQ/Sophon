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

package org.sophonhq.sophon.test.saga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.ListenerInvocationErrorHandler;
import org.sophonhq.sophon.eventhandling.LoggingErrorHandler;
import org.sophonhq.sophon.modelling.saga.SagaEventHandler;
import org.sophonhq.sophon.modelling.saga.StartSaga;
import org.sophonhq.sophon.test.FixtureExecutionException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Duration.ofSeconds;
import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Test class dedicated to validating custom, saga specific, registered components on {@link SagaTestFixture}.
 *
 * @author Steven van Beelen
 */
class FixtureTest_RegisteringSagaEnhancements {

    private SagaTestFixture<SomeTestSaga> testSubject;

    private AtomicInteger startRecordingCount;

    @BeforeEach
    void setUp() {
        startRecordingCount = new AtomicInteger();

        testSubject = new SagaTestFixture<>(SomeTestSaga.class);
    }

    @Test
    void startRecordingCallbackIsInvokedOnWhenPublishingAnEvent() {
        testSubject.registerStartRecordingCallback(startRecordingCount::getAndIncrement)
                   .givenAPublished(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get()).isZero();

        testSubject.whenPublishingA(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get()).isOne();
    }

    @Test
    void startRecordingCallbackIsInvokedOnWhenTimeAdvances() {
        testSubject.registerStartRecordingCallback(startRecordingCount::getAndIncrement)
                   .givenAPublished(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get()).isZero();

        testSubject.whenTimeAdvancesTo(now());
        assertThat(startRecordingCount.get()).isOne();
    }

    @Test
    void startRecordingCallbackIsInvokedOnWhenTimeElapses() {
        testSubject.registerStartRecordingCallback(startRecordingCount::getAndIncrement)
                   .givenAPublished(new SomeTestSaga.SomeEvent());
        assertThat(startRecordingCount.get()).isZero();

        testSubject.whenTimeElapses(ofSeconds(5));
        assertThat(startRecordingCount.get()).isOne();
    }

    @Test
    void customListenerInvocationErrorHandlerIsUsedInWhenPhase() {
        SomeTestSaga.SomeEvent testEvent = new SomeTestSaga.SomeEvent("some-id", true);
        ListenerInvocationErrorHandler testErrorHandler = (exception, event, eventHandler) ->
                assertThat(exception.getMessage()).isEqualTo(testEvent.getException().getMessage());
        this.testSubject.registerListenerInvocationErrorHandler(testErrorHandler);

        this.testSubject.givenNoPriorActivity()
                        .whenPublishingA(testEvent);
    }

    @Test
    void customListenerInvocationErrorHandlerIsUsedInGivenPhase() {
        SomeTestSaga.SomeEvent testEvent = new SomeTestSaga.SomeEvent("some-id", true);
        ListenerInvocationErrorHandler testErrorHandler = (exception, event, eventHandler) ->
                assertThat(exception.getMessage()).isEqualTo(testEvent.getException().getMessage());

        this.testSubject.registerListenerInvocationErrorHandler(testErrorHandler)
                        .suppressExceptionInGivenPhase(true);

        this.testSubject.givenAPublished(testEvent);
    }

    @Test
    void exceptionsAreRethrownAsFixtureExecutionExceptionDuringGivenPhaseWithoutInvokedCustomErrorHandler() {
        SomeTestSaga.SomeEvent testEvent = new SomeTestSaga.SomeEvent("some-id", true);
        ListenerInvocationErrorHandler testErrorHandler = spy(new LoggingErrorHandler());

        this.testSubject.registerListenerInvocationErrorHandler(testErrorHandler);

        assertThatThrownBy(() -> this.testSubject.givenAPublished(testEvent))
                .isInstanceOf(FixtureExecutionException.class);
    }

    @Test
    void registeredResourceInjectorIsCalledUponFirstEventPublication() {
        AtomicBoolean assertion = new AtomicBoolean(false);
        testSubject.registerResourceInjector(saga -> assertion.set(true))
                   // Publishing a single event should trigger the creation and injection of resources
                   .givenAPublished(new SomeTestSaga.SomeEvent());

        assertThat(assertion.get()).isTrue();
    }

    public static class SomeTestSaga {

        @SuppressWarnings("unused")
        @StartSaga
        @SagaEventHandler(associationProperty = "id")
        public void handle(SomeEvent event) throws Exception {
            if (event.shouldThrowException()) {
                throw event.getException();
            }
        }

        public static class SomeEvent {

            private final String id;
            private final Boolean shouldThrowException;
            private final Exception exception = new IllegalStateException("I was told to throw an exception");

            public SomeEvent() {
                this("42", false);
            }

            public SomeEvent(String id, Boolean shouldThrowException) {
                this.id = id;
                this.shouldThrowException = shouldThrowException;
            }

            public String getId() {
                return id;
            }

            Boolean shouldThrowException() {
                return shouldThrowException;
            }

            public Exception getException() {
                return exception;
            }
        }
    }
}
