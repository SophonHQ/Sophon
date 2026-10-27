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

package io.github.sophonhq.sophon.deadline.jobrunr;

import io.github.sophonhq.sophon.deadline.DeadlineMessage;
import io.github.sophonhq.sophon.deadline.GenericDeadlineMessage;
import io.github.sophonhq.sophon.deadline.TestScopeDescriptor;
import io.github.sophonhq.sophon.messaging.MetaData;
import io.github.sophonhq.sophon.messaging.ScopeDescriptor;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.serialization.SimpleSerializedObject;
import io.github.sophonhq.sophon.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeadlineDetailsSerializationTest {

    private static final String TEST_DEADLINE_NAME = "deadline-name";
    private static final String TEST_DEADLINE_PAYLOAD = "deadline-payload";
    private static MetaData metaData;
    @SuppressWarnings("rawtypes")
    private static DeadlineMessage message;

    @BeforeAll
    static void setUp() {
        Map<String, Object> map = new HashMap<>();
        map.put("someStringValue", "foo");
        map.put("someIntValue", 2);
        metaData = new MetaData(map);
        message = GenericDeadlineMessage.asDeadlineMessage(TEST_DEADLINE_NAME, TEST_DEADLINE_PAYLOAD, Instant.now())
                                        .withMetaData(metaData);
    }

    @Test
    void whenSerializedAndDeserializedAllPropertiesShouldBeTheSameUsingXStream() {
        Serializer serializer = TestSerializer.XSTREAM.getSerializer();
        testSerialisationWithSpecificSerializer(serializer);
    }

    @Test
    void whenSerializedAndDeserializedAllPropertiesShouldBeTheSameUsingJackson() {
        Serializer serializer = TestSerializer.JACKSON.getSerializer();
        testSerialisationWithSpecificSerializer(serializer);
    }

    @SuppressWarnings("rawtypes")
    private void testSerialisationWithSpecificSerializer(Serializer serializer) {
        String expectedType = "aggregateType";
        String expectedIdentifier = "identifier";
        ScopeDescriptor descriptor = new TestScopeDescriptor(expectedType, expectedIdentifier);
        String serializedDeadlineDetails = DeadlineDetails.serialized(
                TEST_DEADLINE_NAME, descriptor, message, serializer);
        SimpleSerializedObject<String> serializedDeadlineMetaData = new SimpleSerializedObject<>(
                serializedDeadlineDetails, String.class, DeadlineDetails.class.getName(), null
        );
        DeadlineDetails result = serializer.deserialize(serializedDeadlineMetaData);

        assertEquals(TEST_DEADLINE_NAME, result.getDeadlineName());
        assertEquals(descriptor, result.getDeserializedScopeDescriptor(serializer));
        DeadlineMessage resultMessage = result.asDeadLineMessage(serializer);

        assertNotNull(resultMessage);
        assertEquals(TEST_DEADLINE_PAYLOAD, resultMessage.getPayload());
        assertEquals(metaData, resultMessage.getMetaData());
    }
}