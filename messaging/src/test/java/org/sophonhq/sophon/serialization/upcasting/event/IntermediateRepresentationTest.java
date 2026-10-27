/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.sophonhq.sophon.serialization.upcasting.event;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.EventData;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.serialization.Converter;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.TestSerializer;
import org.sophonhq.sophon.utils.TestDomainEventEntry;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Test for intermediate representation.
 *
 * @author Simon Zambrovski
 */
class IntermediateRepresentationTest {

    private final static Serializer serializer = TestSerializer.XSTREAM.getSerializer();

    @Test
    public void canConvertDataTo() {
        EventData<?> eventData = new TestDomainEventEntry(
            new GenericDomainEventMessage<>("test", "aggregateId", 0, "someString"), serializer
        );
        Serializer serializer = mock(Serializer.class);
        Converter converter = mock(Converter.class);
        when(serializer.getConverter()).thenReturn(converter);
        when(converter.canConvert(any(), eq(String.class))).thenReturn(true);

        IntermediateEventRepresentation input = new InitialEventRepresentation(eventData, serializer);
        EventUpcasterChain eventUpcasterChain = new EventUpcasterChain(
            new IntermediateRepresentationTest.MyEventUpcaster()
        );
        List<IntermediateEventRepresentation> result = eventUpcasterChain.upcast(Stream.of(input)).collect(toList());
        assertEquals(1, result.size());


        assertTrue(input.canConvertDataTo(String.class));
        assertTrue(result.get(0).canConvertDataTo(String.class));

        verify(converter).canConvert(String.class, String.class);
    }

    private static class MyEventUpcaster extends SingleEventUpcaster {

        @Override
        protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return true;
        }

        @Override
        protected IntermediateEventRepresentation doUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return new UpcastedEventRepresentation<>(
                intermediateRepresentation.getType(),
                intermediateRepresentation,
                Function.identity(),
                Function.identity(),
                Object.class,
                serializer.getConverter()
            );
        }
    }
}
