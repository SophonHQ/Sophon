/*
 * Copyright (c) 2010-2022. Axon Framework
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

package io.github.sophonhq.sophon.eventhandling;

import io.github.sophonhq.sophon.common.SophonConfigurationException;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Collections;
import java.util.List;

import static io.github.sophonhq.sophon.utils.EventTestUtils.createEvent;
import static io.github.sophonhq.sophon.utils.EventTestUtils.createEvents;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.eventHandlers((List<?>) null));
    }

    @Test
    void buildWithEmptyEventHandlersListThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        List<Object> testEventHandlers = Collections.emptyList();

        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.eventHandlers(testEventHandlers));
    }

    @Test
    void buildWithoutEventHandlersListThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();

        assertThrows(SophonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithNullParameterResolverFactoryThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.parameterResolverFactory(null));
    }

    @Test
    void buildWithNullHandlerDefinitionThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.handlerDefinition(null));
    }

    @Test
    void buildWithNullListenerInvocationErrorHandlerThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.listenerInvocationErrorHandler(null));
    }

    @Test
    void buildWithNullSequencingPolicyThrowsSophonConfigurationException() {
        SimpleEventHandlerInvoker.Builder<?> builderTestSubject = SimpleEventHandlerInvoker.builder();
        //noinspection ConstantConditions
        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.sequencingPolicy(null));
    }

    @Test
    void shouldSupportResetWhenAtLeastOneEventMessageHandlerSupportsReset() {
        doReturn(true).when(mockHandler1).supportsReset();
        doReturn(false).when(mockHandler2).supportsReset();

        assertTrue(testSubject.supportsReset());
    }

    @Test
    void shouldNotSupportResetWhenNoEventMessageHandlerSupportsReset() {
        doReturn(false).when(mockHandler1).supportsReset();
        doReturn(false).when(mockHandler2).supportsReset();

        assertFalse(testSubject.supportsReset());
    }
}
