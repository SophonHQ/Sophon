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

package org.sophonhq.sophon.eventhandling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.sophonhq.sophon.common.SophonConfigurationException;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.utils.EventTestUtils.createEvent;
import static org.sophonhq.sophon.utils.EventTestUtils.createEvents;

/**
 * Test class validating the {@link SimpleEventHandlerInvoker}.
 *
 * @author Rene de Waele
 */
class SimpleEventHandlerInvokerTest {

    private static final Object NO_RESET_PAYLOAD = null;

    private EventMessageHandler mockHandler1;
    private EventMessageHandler mockHandler2;

    private SimpleEventHandlerInvoker testSubject;

    @BeforeEach
    void setUp() {
        mockHandler1 = mock(EventMessageHandler.class);
        mockHandler2 = mock(EventMessageHandler.class);
        testSubject = SimpleEventHandlerInvoker.builder()
                                               .eventHandlers("test", mockHandler1, mockHandler2)
                                               .build();
    }

    @Test
    void singleEventPublication() throws Exception {
        EventMessage<?> event = createEvent();

        testSubject.handle(event, Segment.ROOT_SEGMENT);

        InOrder inOrder = inOrder(mockHandler1, mockHandler2);
        inOrder.verify(mockHandler1).handle(event);
        inOrder.verify(mockHandler2).handle(event);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void repeatedEventPublication() throws Exception {
        List<? extends EventMessage<?>> events = createEvents(2);

        for (EventMessage<?> event : events) {
            testSubject.handle(event, Segment.ROOT_SEGMENT);
        }

        InOrder inOrder = inOrder(mockHandler1, mockHandler2);
        inOrder.verify(mockHandler1).handle(events.get(0));
        inOrder.verify(mockHandler2).handle(events.get(0));
        inOrder.verify(mockHandler1).handle(events.get(1));
        inOrder.verify(mockHandler2).handle(events.get(1));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void performReset() {
        testSubject.performReset();

        verify(mockHandler1).prepareReset(NO_RESET_PAYLOAD);
        verify(mockHandler2).prepareReset(NO_RESET_PAYLOAD);
    }

    @Test
    void performResetWithResetContext() {
        String resetContext = "reset-context";

        testSubject.performReset(resetContext);

        verify(mockHandler1).prepareReset(resetContext);
        verify(mockHandler2).prepareReset(resetContext);
    }

    @Test
    void buildWithNullEventHandlersListThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThatThrownBy(() -> builderTestSubject.eventHandlers((List<?>) null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyEventHandlersListThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        List<Object> testEventHandlers = Collections.emptyList();

        assertThatThrownBy(() -> builderTestSubject.eventHandlers(testEventHandlers))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithoutEventHandlersListThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();

        assertThatThrownBy(builderTestSubject::build).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNullParameterResolverFactoryThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThatThrownBy(() -> builderTestSubject.parameterResolverFactory(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNullHandlerDefinitionThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThatThrownBy(() -> builderTestSubject.handlerDefinition(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNullListenerInvocationErrorHandlerThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThatThrownBy(() -> builderTestSubject.listenerInvocationErrorHandler(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNullSequencingPolicyThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThatThrownBy(() -> builderTestSubject.sequencingPolicy(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void shouldSupportResetWhenAtLeastOneEventMessageHandlerSupportsReset() {
        doReturn(true).when(mockHandler1).supportsReset();
        doReturn(false).when(mockHandler2).supportsReset();

        assertThat(testSubject.supportsReset()).isTrue();
    }

    @Test
    void shouldNotSupportResetWhenNoEventMessageHandlerSupportsReset() {
        doReturn(false).when(mockHandler1).supportsReset();
        doReturn(false).when(mockHandler2).supportsReset();

        assertThat(testSubject.supportsReset()).isFalse();
    }
}
