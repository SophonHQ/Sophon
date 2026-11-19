/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.sophonhq.sophon.eventhandling.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.messaging.MessageDispatchInterceptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * This class validating the {@link DefaultEventGateway}.
 *
 * @author Bert Laverman
 */
class DefaultEventGatewayTest {

    private DefaultEventGateway testSubject;
    private EventBus mockEventBus;
    private MessageDispatchInterceptor<EventMessage<?>> mockEventMessageTransformer;

    @BeforeEach
    void setUp() {
        mockEventBus = mock(EventBus.class);
        //noinspection unchecked
        mockEventMessageTransformer = mock(MessageDispatchInterceptor.class);

        when(mockEventMessageTransformer.handle(isA(EventMessage.class)))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        //noinspection unchecked
        testSubject = DefaultEventGateway.builder()
                                         .eventBus(mockEventBus)
                                         .dispatchInterceptors(mockEventMessageTransformer)
                                         .build();
    }

    @Test
    void publishSingleEvent() {
        // Given
        //noinspection unchecked
        ArgumentCaptor<EventMessage<?>> eventCaptor = ArgumentCaptor.forClass(EventMessage.class);

        // When
        testSubject.publish("Event1");

        // Then
        verify(mockEventBus).publish(eventCaptor.capture());
        EventMessage<?> result = eventCaptor.getValue();
        assertThat(result.getPayload()).isEqualTo("Event1");

        verify(mockEventMessageTransformer).handle(eventCaptor.capture());
        EventMessage<?> interceptedResult = eventCaptor.getValue();
        assertThat(interceptedResult.getPayload()).isEqualTo("Event1");
    }

    @Test
    void publishMultipleEvents() {
        //Given
        //noinspection unchecked
        ArgumentCaptor<List<EventMessage<?>>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        //noinspection unchecked
        ArgumentCaptor<EventMessage<?>> interceptedCaptor = ArgumentCaptor.forClass(EventMessage.class);

        //When
        testSubject.publish("Event2", "Event3");

        //Then
        verify(mockEventBus).publish(eventsCaptor.capture());
        List<EventMessage<?>> result = eventsCaptor.getValue();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPayload()).isEqualTo("Event2");
        assertThat(result.get(1).getPayload()).isEqualTo("Event3");

        verify(mockEventMessageTransformer, times(2)).handle(interceptedCaptor.capture());
        List<EventMessage<?>> interceptedResult = interceptedCaptor.getAllValues();
        assertThat(interceptedResult).hasSize(2);
        assertThat(interceptedResult.get(0).getPayload()).isEqualTo("Event2");
        assertThat(interceptedResult.get(1).getPayload()).isEqualTo("Event3");
    }

    @Test
    void publishNoEvents() {
        //Given
        ArrayList<Object> noEvents = new ArrayList<>();

        //When
        testSubject.publish(noEvents);

        //Then
        verifyNoInteractions(mockEventBus);
        verifyNoInteractions(mockEventMessageTransformer);
    }
}