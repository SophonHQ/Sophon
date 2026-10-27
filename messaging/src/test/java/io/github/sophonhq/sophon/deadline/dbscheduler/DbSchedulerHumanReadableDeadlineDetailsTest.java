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

package io.github.sophonhq.sophon.deadline.dbscheduler;

import com.github.kagkarlsson.scheduler.serializer.GsonSerializer;
import com.github.kagkarlsson.scheduler.serializer.JacksonSerializer;
import com.github.kagkarlsson.scheduler.serializer.JavaSerializer;
import com.github.kagkarlsson.scheduler.serializer.Serializer;
import io.github.sophonhq.sophon.deadline.DeadlineMessage;
import io.github.sophonhq.sophon.deadline.GenericDeadlineMessage;
import io.github.sophonhq.sophon.deadline.TestScopeDescriptor;
import io.github.sophonhq.sophon.messaging.MetaData;
import io.github.sophonhq.sophon.messaging.ScopeDescriptor;
import io.github.sophonhq.sophon.serialization.TestSerializer;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DbSchedulerHumanReadableDeadlineDetailsTest {

    private static final String TEST_DEADLINE_NAME = "deadline-name";
    private static final String TEST_DEADLINE_PAYLOAD = "deadline-payload";
    private static final MetaData META_DATA = getMetaData();
    private static final DeadlineMessage<?> MESSAGE = getMessage();

    @MethodSource("dbSchedulerSerializers")
    @ParameterizedTest
    void shouldBeSerializableWithDbSchedulerSerializers(Serializer serializer) {
        DbSchedulerHumanReadableDeadlineDetails expected = new DbSchedulerHumanReadableDeadlineDetails(
                "deadlinename",
                "someScope",
                "io.github.sophonhq.sophon.modelling.command.AggregateScopeDescriptor",
                "{\"foo\":\"bar\"}",
                "com.someCompany.api.ImportantEvent",
                "1",
                "{\"traceId\":\"1acc25e2-58a1-4dec-8b43-55388188500a\"}"
        );
        byte[] serialized = serializer.serialize(expected);
        DbSchedulerHumanReadableDeadlineDetails result = serializer.deserialize(DbSchedulerHumanReadableDeadlineDetails.class,
                                                                                serialized);
        assertEquals(expected, result);
    }

    @MethodSource("sophonSerializers")
    @ParameterizedTest
    void whenDataInPojoIsSerializedAndDeserializedItShouldBeTheSame(TestSerializer testSerializer) {
        io.github.sophonhq.sophon.serialization.Serializer serializer = testSerializer.getSerializer();
        String expectedType = "aggregateType";
        String expectedIdentifier = "identifier";
        ScopeDescriptor descriptor = new TestScopeDescriptor(expectedType, expectedIdentifier);
        DbSchedulerHumanReadableDeadlineDetails result = DbSchedulerHumanReadableDeadlineDetails.serialized(
                TEST_DEADLINE_NAME, descriptor, MESSAGE, serializer);

        assertEquals(TEST_DEADLINE_NAME, result.getDeadlineName());
        assertEquals(descriptor, result.getDeserializedScopeDescriptor(serializer));
        DeadlineMessage<?> resultMessage = result.asDeadLineMessage(serializer);

        assertNotNull(resultMessage);
        assertEquals(TEST_DEADLINE_PAYLOAD, resultMessage.getPayload());
        assertEquals(META_DATA, resultMessage.getMetaData());
    }

    public static Collection<Serializer> dbSchedulerSerializers() {
        List<Serializer> serializers = new ArrayList<>();
        serializers.add(new JavaSerializer());
        serializers.add(new JacksonSerializer());
        serializers.add(new GsonSerializer());
        return serializers;
    }

    public static Collection<TestSerializer> sophonSerializers() {
        List<TestSerializer> testSerializerList = new ArrayList<>();
        testSerializerList.add(TestSerializer.JACKSON);
        testSerializerList.add(TestSerializer.XSTREAM);
        return testSerializerList;
    }

    private static MetaData getMetaData() {
        Map<String, Object> map = new HashMap<>();
        map.put("someStringValue", "foo");
        map.put("someIntValue", 2);
        return new MetaData(map);
    }

    private static DeadlineMessage<?> getMessage() {
        return GenericDeadlineMessage.asDeadlineMessage(TEST_DEADLINE_NAME, TEST_DEADLINE_PAYLOAD, Instant.now())
                                     .withMetaData(getMetaData());
    }
}
