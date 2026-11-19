/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.sophonhq.sophon.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.MetaData;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SerializedMessage}.
 *
 * @author Allard Buijze
 */
class SerializedMessageTest {

    private final SerializedObject<String> serializedPayload =
            new SimpleSerializedObject<>("serializedPayload", String.class, "java.lang.Object", "1");
    private final SerializedObject<String> serializedMetaData =
            new SerializedMetaData<>("serializedMetaData", String.class);
    private final Object deserializedPayload = new Object();
    private final MetaData deserializedMetaData = MetaData.emptyInstance();
    private final Serializer serializer = mock(Serializer.class);
    private final String eventId = "eventId";

    @BeforeEach
    void setUp() {
        when(serializer.deserialize(serializedMetaData)).thenReturn(deserializedMetaData);
        when(serializer.deserialize(serializedPayload)).thenReturn(deserializedPayload);
        when(serializer.classForType(isA(SerializedType.class))).thenReturn(Object.class);
        when(serializer.getConverter()).thenReturn(new ChainingConverter());
    }

    @Test
    void constructorLeavesSerializedObjectsSerialized() {
        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        assertThat(testSubject.getPayloadType()).isEqualTo(Object.class);
        assertThat(testSubject.isPayloadDeserialized()).isFalse();
        assertThat(testSubject.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(testSubject.isPayloadDeserialized()).isTrue();
        assertThat(testSubject.isMetaDataDeserialized()).isFalse();
        assertThat(testSubject.getMetaData()).isSameAs(MetaData.emptyInstance());
        assertThat(testSubject.isMetaDataDeserialized()).isTrue();
    }

    @Test
    void withMetaDataReplacesOriginalMetaData() {
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        when(serializer.deserialize(serializedMetaData)).thenReturn(metaData);

        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        Message<Object> resultOne = testSubject.withMetaData(MetaData.emptyInstance());
        Message<Object> resultTwo =
                testSubject.withMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertThat(resultOne.getMetaData().size()).isZero();
        assertThat(resultTwo.getMetaData().size()).isOne();
    }

    @Test
    void andMetaDataAppendsToOriginalMetaData() {
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        when(serializer.deserialize(serializedMetaData)).thenReturn(metaData);

        Message<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        Message<Object> resultOne = testSubject.andMetaData(MetaData.emptyInstance());
        assertThat(resultOne.getMetaData().size()).isOne();
        assertThat(resultOne.getMetaData()).containsEntry("key", "value");

        Message<Object> resultTwo =
                testSubject.andMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));
        assertThat(resultTwo.getMetaData().size()).isOne();
        assertThat(resultTwo.getMetaData()).containsEntry("key", "otherValue");
    }

    @Test
    void serializePayloadImmediatelyAfterConstructionReturnsOriginalPayload() {
        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        SerializedObject<byte[]> result = testSubject.serializePayload(serializer, byte[].class);
        assertThat(result.getData()).containsExactly("serializedPayload".getBytes(StandardCharsets.UTF_8));
        // this call is allowed
        verify(serializer, atLeast(0)).classForType(isA(SerializedType.class));
        verify(serializer, atLeast(0)).getConverter();
        verifyNoMoreInteractions(serializer);
    }

    @Test
    void serializeMetaDataImmediatelyAfterConstructionReturnsOriginalMetaData() {
        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        SerializedObject<byte[]> result = testSubject.serializeMetaData(serializer, byte[].class);
        assertThat(result.getData()).containsExactly("serializedMetaData".getBytes(StandardCharsets.UTF_8));
        // this call is allowed
        verify(serializer, atLeast(0)).classForType(isA(SerializedType.class));
        verify(serializer, atLeast(0)).getConverter();
        verifyNoMoreInteractions(serializer);
    }

    @Test
    void rethrowSerializationExceptionOnGetPayload() {
        SerializationException serializationException = new SerializationException("test message");
        when(serializer.deserialize(serializedMetaData)).thenThrow(serializationException);
        when(serializer.deserialize(serializedPayload)).thenThrow(serializationException);

        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        SerializationException result = assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(testSubject::getPayload)
                .actual();
        assertThat(result.getMessage()).isEqualTo("Error while deserializing payload of message " + eventId);
        assertThat(result.getCause()).isSameAs(serializationException);
    }

    @Test
    void rethrowSerializationExceptionOnGetMetaData() {
        SerializationException serializationException = new SerializationException("test message");
        when(serializer.deserialize(serializedMetaData)).thenThrow(serializationException);
        when(serializer.deserialize(serializedPayload)).thenThrow(serializationException);

        SerializedMessage<Object> testSubject =
                new SerializedMessage<>(eventId, serializedPayload, serializedMetaData, serializer);

        SerializationException result = assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(testSubject::getMetaData)
                .actual();
        assertThat(result.getMessage()).isEqualTo("Error while deserializing meta data of message " + eventId);
        assertThat(result.getCause()).isSameAs(serializationException);
    }
}
