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

package org.sophonhq.sophon.test.saga;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.EventMessageHandler;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.eventhandling.ListenerInvocationErrorHandler;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link RecordingListenerInvocationErrorHandler}.
 *
 * @author Christian Vermorken
 */
class RecordingListenerInvocationErrorHandlerTest {

    private static final EventMessage<String> TEST_EVENT = new GenericEventMessage<>("test");
    private static final Exception TEST_EXCEPTION = new IllegalArgumentException("This argument is illegal");

    private ListenerInvocationErrorHandler wrappedErrorHandler;
    private EventMessageHandler eventHandler;

    private RecordingListenerInvocationErrorHandler testSubject;

    @BeforeEach
    void setUp() {
        eventHandler = mock(EventMessageHandler.class);
        doReturn(StubSaga.class).when(eventHandler)
                                .getTargetType();

        wrappedErrorHandler = spy(new NoOpListenerInvocationErrorHandler());
        testSubject = new RecordingListenerInvocationErrorHandler(wrappedErrorHandler);
    }

    @Test
    void emptyOnCreation() {
        assertThat(testSubject.getException()).isEmpty();
    }

    @Test
    void wrappedHandlerCannotBeNull() {
        assertThatThrownBy(() -> new RecordingListenerInvocationErrorHandler(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotSetWrappedHandlerToNull() {
        assertThatThrownBy(() -> testSubject.setListenerInvocationErrorHandler(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delegatesExceptionToWrappedErrorHandler() throws Exception {
        testSubject.startRecording();

        testSubject.onError(TEST_EXCEPTION, TEST_EVENT, eventHandler);

        Optional<Exception> result = testSubject.getException();
        assertThat(result).isPresent();
        assertThat(result).hasValue(TEST_EXCEPTION);
        verify(wrappedErrorHandler).onError(TEST_EXCEPTION, TEST_EVENT, eventHandler);
    }

    @Test
    void clearExceptionOnStartRecording() throws Exception {
        testSubject.startRecording();
        testSubject.onError(TEST_EXCEPTION, TEST_EVENT, eventHandler);
        testSubject.startRecording();

        assertThat(testSubject.getException()).isEmpty();
        verify(wrappedErrorHandler).onError(TEST_EXCEPTION, TEST_EVENT, eventHandler);
    }

    @Test
    void byDefaultRethrowsExceptionsIfRecordingHasNotStartedYet() {
        assertThatThrownBy(() -> testSubject.onError(TEST_EXCEPTION, TEST_EVENT, eventHandler))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(wrappedErrorHandler);
    }

    @Test
    void doesNotRethrowExceptionIfRethrowErrorsWhenNotStartedIsDisabled() throws Exception {
        testSubject.failOnErrorInPreparation(false);

        testSubject.onError(TEST_EXCEPTION, TEST_EVENT, eventHandler);

        Optional<Exception> result = testSubject.getException();
        assertThat(result).isPresent();
        assertThat(result).hasValue(TEST_EXCEPTION);
        verify(wrappedErrorHandler).onError(TEST_EXCEPTION, TEST_EVENT, eventHandler);
    }

    private static class NoOpListenerInvocationErrorHandler implements ListenerInvocationErrorHandler {

        @Override
        public void onError(@NonNull Exception exception, @NonNull EventMessage<?> event,
                            @NonNull EventMessageHandler eventHandler) throws Exception {
            // No-op
        }
    }
}