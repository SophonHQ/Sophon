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

package org.sophonhq.sophon.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.messaging.correlation.ThrowingCorrelationDataProvider;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.UnitOfWork;
import org.sophonhq.sophon.serialization.CannotConvertBetweenTypesException;
import org.sophonhq.sophon.serialization.SerializedObject;
import org.sophonhq.sophon.serialization.json.JacksonSerializer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test correct operations of the {@link GenericMessage} class.
 *
 * @author Rene de Waele
 */
class GenericMessageTest {

    private final Map<String, ?> correlationData = MetaData.from(Collections.singletonMap("foo", "bar"));
    private UnitOfWork<?> unitOfWork;

    @BeforeEach
    void setUp() {
        unitOfWork = mock(UnitOfWork.class);
        when(unitOfWork.getCorrelationData()).thenAnswer(invocation -> correlationData);
        CurrentUnitOfWork.set(unitOfWork);
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.clear(CurrentUnitOfWork.get());
        }
    }

    @Test
    void correlationDataAddedToNewMessage() {
        assertThat(new HashMap<>(new GenericMessage<>(new Object()).getMetaData())).isEqualTo(correlationData);

        MetaData newMetaData = MetaData.from(Collections.singletonMap("whatever", new Object()));
        assertThat(new GenericMessage<>(new Object(), newMetaData).getMetaData())
                .isEqualTo(newMetaData.mergedWith(correlationData));
    }

    @Test
    void messageSerialization() throws IOException {
        Map<String, String> metaDataMap = Collections.singletonMap("key", "value");

        GenericMessage<String> message = new GenericMessage<>("payload", metaDataMap);

        JacksonSerializer jacksonSerializer = JacksonSerializer.builder().build();


        SerializedObject<String> serializedPayload = message.serializePayload(jacksonSerializer, String.class);
        SerializedObject<String> serializedMetaData = message.serializeMetaData(jacksonSerializer, String.class);

        assertThat(serializedPayload.getData()).isEqualTo("\"payload\"");


        ObjectMapper objectMapper = jacksonSerializer.getObjectMapper();
        Map<String, String> actualMetaData = objectMapper.readValue(serializedMetaData.getData(), Map.class);

        assertThat(actualMetaData.entrySet()).containsAll(metaDataMap.entrySet());
    }

    @Test
    void asMessageReturnsProvidedMessageAsIs() {
        GenericMessage<String> testMessage = new GenericMessage<>("payload");

        Message<?> result = GenericMessage.asMessage(testMessage);

        assertThat(result).isEqualTo(testMessage);
    }

    @Test
    void asMessageWrapsProvidedObjectsInMessage() {
        String testPayload = "payload";

        Message<?> result = GenericMessage.asMessage(testPayload);

        assertThat(result).isNotEqualTo(testPayload);
        assertThat(result.getPayload()).isEqualTo(testPayload);
    }

    @Test
    void whenCorrelationDataProviderThrowsException_thenCatchException() {
        unitOfWork = new DefaultUnitOfWork<>(new GenericEventMessage<>("Input 1"));
        CurrentUnitOfWork.set(unitOfWork);
        unitOfWork.registerCorrelationDataProvider(new ThrowingCorrelationDataProvider());
        CannotConvertBetweenTypesException exception = new CannotConvertBetweenTypesException("foo");

        Message<?> result = GenericMessage.asMessage(exception);

        assertThat(result).isNotNull();
    }
}
