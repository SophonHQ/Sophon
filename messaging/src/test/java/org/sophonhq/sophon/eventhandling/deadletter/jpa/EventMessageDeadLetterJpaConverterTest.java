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

package org.sophonhq.sophon.eventhandling.deadletter.jpa;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GapAwareTrackingToken;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.eventhandling.GenericTrackedDomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericTrackedEventMessage;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.TrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.serialization.Revision;
import org.sophonhq.sophon.serialization.SerializedMessage;
import org.sophonhq.sophon.serialization.SerializedType;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.SimpleSerializedObject;
import org.sophonhq.sophon.serialization.SimpleSerializedType;
import org.sophonhq.sophon.serialization.TestSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class EventMessageDeadLetterJpaConverterTest {

    private static final String PAYLOAD_REVISION = "23.0";
    private final EventMessageDeadLetterJpaConverter converter = new EventMessageDeadLetterJpaConverter();
    private final Serializer eventSerializer = TestSerializer.JACKSON.getSerializer();
    private final Serializer genericSerializer = TestSerializer.XSTREAM.getSerializer();
    private final ConverterTestEvent event = new ConverterTestEvent("myValue");
    private final MetaData metaData = MetaData.from(Collections.singletonMap("myMetadataKey", "myMetadataValue"));

    @Test
    void canConvertGenericEventMessageAndBackCorrectly() {
        testConversion(GenericEventMessage.asEventMessage(event).andMetaData(metaData));
    }

    @Test
    void canConvertDomainEventMessageAndBackCorrectly() {
        testConversion(new GenericDomainEventMessage<>("MyType", "8239081092", 25L, event, metaData));
    }

    @Test
    void canConvertTrackedDomainEventMessageWithGlobalSequenceTokenAndBackCorrectly() {
        testConversion(new GenericTrackedDomainEventMessage<>(new GlobalSequenceTrackingToken(232323L),
                                                              "MyType",
                                                              "8239081092",
                                                              25L,
                                                              new GenericEventMessage<>(event, metaData),
                                                              Instant::now));
    }

    @Test
    void canConvertMessagesWithSerializationErrors() {
        SerializedType eventType = new SimpleSerializedType(
                "org.sophonhq.sophon.eventhandling.deadletter.jpa.EventMessageDeadLetterJpaConverterTest$SerializationErrorClass",
                null
        );
        EventMessage<Object> message = new GenericEventMessage<>(new SerializedMessage<>(
                "my-identifier",
                new SimpleSerializedObject<>(
                        "{'my-wrong-payload':'wadawd'}".getBytes(StandardCharsets.UTF_8),
                        byte[].class,
                        eventType
                ),
                new SimpleSerializedObject<>(
                        "{}".getBytes(StandardCharsets.UTF_8),
                        byte[].class,
                        new SimpleSerializedType("org.sophonhq.sophon.messaging.MetaData", null)
                ),
                eventSerializer
        ), Instant::now);
        DeadLetterEventEntry deadLetterEventEntry = converter.convert(message, eventSerializer, genericSerializer);
        assertThat(deadLetterEventEntry).isNotNull();
        assertThat(new String(deadLetterEventEntry.getPayload().getData())).isEqualTo("{'my-wrong-payload':'wadawd'}");
    }

    @Test
    void canConvertTrackedDomainEventMessageWithGapAwareTokenAndBackCorrectly() {
        TrackingToken testToken = new GapAwareTrackingToken(232323L, Arrays.asList(24L, 255L, 2225L));
        testConversion(new GenericTrackedDomainEventMessage<>(testToken,
                                                              "MyType",
                                                              "8239081092",
                                                              25L,
                                                              new GenericEventMessage<>(event, metaData),
                                                              Instant::now));
    }

    @Test
    void canConvertTrackedEventMessageWithGlobalSequenceTokenAndBackCorrectly() {
        testConversion(new GenericTrackedEventMessage<>(new GlobalSequenceTrackingToken(232323L),
                                                        new GenericEventMessage<>(event, metaData),
                                                        Instant::now));
    }


    @Test
    void canConvertTrackedEventMessageWithGapAwareTokenAndBackCorrectly() {
        TrackingToken testToken = new GapAwareTrackingToken(232323L, Arrays.asList(24L, 255L, 2225L));
        testConversion(new GenericTrackedEventMessage<>(testToken,
                                                        new GenericEventMessage<>(event, metaData),
                                                        Instant::now));
    }

    @Test
    void canConvertDeadLettersStoredWithAxonFrameworkMessageTypes() {
        DeadLetterEventEntry legacyEntry = new DeadLetterEventEntry(
                "org.axonframework.eventhandling.GenericTrackedDomainEventMessage", "identifier", Instant.now().toString(),
                null, null, null, null, null, null, null, null, null
        );

        assertThat(converter.canConvert(legacyEntry)).isTrue();
    }

    private void testConversion(EventMessage<?> message) {
        assertThat(converter.canConvert(message)).isTrue();
        DeadLetterEventEntry deadLetterEventEntry = converter.convert(message, eventSerializer, genericSerializer);

        assertCorrectlyMapped(message, deadLetterEventEntry);
        assertThat(converter.canConvert(deadLetterEventEntry)).isTrue();

        EventMessage<?> restoredEventMessage =
                converter.convert(deadLetterEventEntry, eventSerializer, genericSerializer);
        assertCorrectlyRestored(message, restoredEventMessage);
    }

    private void assertCorrectlyRestored(EventMessage<?> expected, EventMessage<?> actual) {
        assertThat(actual.getIdentifier()).isEqualTo(expected.getIdentifier());
        assertThat(actual.getTimestamp()).isEqualTo(expected.getTimestamp());
        assertThat(actual.getPayload()).isEqualTo(expected.getPayload());
        assertThat(actual.getPayloadType()).isEqualTo(expected.getPayloadType());
        assertThat(actual.getMetaData()).isEqualTo(expected.getMetaData());

        assertThat(actual.getClass()).isEqualTo(expected.getClass());
        if (expected instanceof DomainEventMessage) {
            DomainEventMessage<?> domainExpected = (DomainEventMessage<?>) expected;
            DomainEventMessage<?> domainActual = (DomainEventMessage<?>) actual;

            assertThat(domainActual.getType()).isEqualTo(domainExpected.getType());
            assertThat(domainActual.getAggregateIdentifier()).isEqualTo(domainExpected.getAggregateIdentifier());
            assertThat(domainActual.getSequenceNumber()).isEqualTo(domainExpected.getSequenceNumber());
        }
        if (expected instanceof TrackedEventMessage) {
            TrackedEventMessage<?> trackedExpected = (TrackedEventMessage<?>) expected;
            TrackedEventMessage<?> trackedActual = (TrackedEventMessage<?>) actual;

            assertThat(trackedActual.trackingToken()).isEqualTo(trackedExpected.trackingToken());
        }
    }

    private void assertCorrectlyMapped(EventMessage<?> eventMessage, DeadLetterEventEntry deadLetterEventEntry) {
        assertThat(deadLetterEventEntry.getEventIdentifier()).isEqualTo(eventMessage.getIdentifier());
        assertThat(deadLetterEventEntry.getTimeStamp()).isEqualTo(eventMessage.getTimestamp().toString());
        assertThat(deadLetterEventEntry.getPayload().getType().getName())
                .isEqualTo(eventMessage.getPayload().getClass().getName());
        assertThat(deadLetterEventEntry.getPayload().getType().getRevision()).isEqualTo(PAYLOAD_REVISION);
        assertThat(new String(deadLetterEventEntry.getPayload().getData()))
                .isEqualTo(eventSerializer.serialize(event, String.class).getData());
        assertThat(deadLetterEventEntry.getMetaData().getType().getName()).isEqualTo(MetaData.class.getName());
        assertThat(new String(deadLetterEventEntry.getMetaData().getData()))
                .isEqualTo(eventSerializer.serialize(metaData, String.class).getData());

        if (eventMessage instanceof DomainEventMessage) {
            DomainEventMessage<?> domainEventMessage = (DomainEventMessage<?>) eventMessage;
            assertThat(deadLetterEventEntry.getType()).isEqualTo(domainEventMessage.getType());
            assertThat(deadLetterEventEntry.getAggregateIdentifier())
                    .isEqualTo(domainEventMessage.getAggregateIdentifier());
            assertThat(deadLetterEventEntry.getSequenceNumber()).isEqualTo(domainEventMessage.getSequenceNumber());
        } else {
            assertThat(deadLetterEventEntry.getType()).isNull();
            assertThat(deadLetterEventEntry.getAggregateIdentifier()).isNull();
            assertThat(deadLetterEventEntry.getSequenceNumber()).isNull();
        }
        if (eventMessage instanceof TrackedEventMessage) {
            TrackedEventMessage<?> trackedEventMessage = (TrackedEventMessage<?>) eventMessage;
            assertThat(deadLetterEventEntry.getTrackingToken().getType().getName())
                    .isEqualTo(trackedEventMessage.trackingToken().getClass().getName());
            assertThat(new String(deadLetterEventEntry.getTrackingToken().getData()))
                    .isEqualTo(genericSerializer.serialize(trackedEventMessage.trackingToken(), String.class)
                                                .getData());
        } else {
            assertThat(deadLetterEventEntry.getTrackingToken()).isNull();
        }
    }

    @Revision(EventMessageDeadLetterJpaConverterTest.PAYLOAD_REVISION)
    public static class ConverterTestEvent {

        private final String myProperty;

        @JsonCreator
        public ConverterTestEvent(@JsonProperty("myProperty") String myProperty) {
            this.myProperty = myProperty;
        }

        public String getMyProperty() {
            return myProperty;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ConverterTestEvent that = (ConverterTestEvent) o;

            return Objects.equals(myProperty, that.myProperty);
        }

        @Override
        public int hashCode() {
            return myProperty != null ? myProperty.hashCode() : 0;
        }
    }

    // Suppressed since it's used for test 'canConvertMessagesWithSerializationErrors'
    @SuppressWarnings("unused")
    static class SerializationErrorClass {
        String myValue;
    }
}
