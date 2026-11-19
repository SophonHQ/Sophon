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

package org.sophonhq.sophon.deadline.quartz;

import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.quartz.JobDataMap;
import org.sophonhq.sophon.deadline.DeadlineMessage;
import org.sophonhq.sophon.deadline.GenericDeadlineMessage;
import org.sophonhq.sophon.deadline.TestScopeDescriptor;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.messaging.ScopeDescriptor;
import org.sophonhq.sophon.serialization.JavaSerializer;
import org.sophonhq.sophon.serialization.SerializedType;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.SimpleSerializedObject;
import org.sophonhq.sophon.serialization.TestSerializer;
import org.sophonhq.sophon.serialization.json.JacksonSerializer;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.deadline.quartz.DeadlineJob.DeadlineJobDataBinder.*;
import static org.sophonhq.sophon.messaging.Headers.*;

class DeadlineJobDataBinderTest {

    private static final String TEST_DEADLINE_NAME = "deadline-name";
    private static final String TEST_DEADLINE_PAYLOAD = "deadline-payload";

    private final DeadlineMessage<String> testDeadlineMessage;
    private final MetaData testMetaData;
    private final ScopeDescriptor testDeadlineScope;

    public DeadlineJobDataBinderTest() {
        DeadlineMessage<String> testDeadlineMessage =
                GenericDeadlineMessage.asDeadlineMessage(TEST_DEADLINE_NAME, TEST_DEADLINE_PAYLOAD, Instant.now());
        testMetaData = MetaData.with("some-key", "some-value");
        this.testDeadlineMessage = testDeadlineMessage.withMetaData(testMetaData);
        testDeadlineScope = new TestScopeDescriptor("aggregate-type", "aggregate-identifier");
    }

    public static Stream<Arguments> serializerImplementationAndAssertionSpecifics() {
        return Stream.of(
                Arguments.arguments(
                        spy(JavaSerializer.builder().build()),
                        (Function<Class, String>) Class::getName,
                        (Predicate<Object>) Objects::nonNull
                ),
                Arguments.arguments(
                        spy(TestSerializer.XSTREAM.getSerializer()),
                        (Function<Class, String>) clazz -> clazz.getSimpleName().toLowerCase(),
                        (Predicate<Object>) Objects::isNull
                ),
                Arguments.arguments(
                        spy(JacksonSerializer.builder().build()),
                        (Function<Class, String>) Class::getName,
                        (Predicate<Object>) Objects::isNull
                )
        );
    }

    @MethodSource("serializerImplementationAndAssertionSpecifics")
    @ParameterizedTest
    void toJobDataTest(
            Serializer serializer,
            Function<Class, String> expectedSerializedClassType,
            Predicate<Object> revisionMatcher
    ) {
        JobDataMap result = toJobData(serializer, testDeadlineMessage, testDeadlineScope);

        assertThat(result)
                .containsEntry(DEADLINE_NAME, TEST_DEADLINE_NAME)
                .containsEntry(MESSAGE_ID, testDeadlineMessage.getIdentifier())
                .containsEntry(MESSAGE_TIMESTAMP, testDeadlineMessage.getTimestamp().toString());
        String expectedPayloadType = expectedSerializedClassType.apply(testDeadlineMessage.getPayloadType());
        assertThat(result).containsEntry(MESSAGE_TYPE, expectedPayloadType);
        Object resultRevision = result.get(MESSAGE_REVISION);
        assertThat(revisionMatcher).accepts(resultRevision);

        assertThat(result.get(SERIALIZED_MESSAGE_PAYLOAD)).isNotNull();
        assertThat(result.get(MESSAGE_METADATA)).isNotNull();
        assertThat(result.get(SERIALIZED_DEADLINE_SCOPE)).isNotNull();
        assertThat(result).containsEntry(SERIALIZED_DEADLINE_SCOPE_CLASS_NAME, testDeadlineScope.getClass().getName());

        verify(serializer).serialize(TEST_DEADLINE_PAYLOAD, byte[].class);
        verify(serializer).serialize(testMetaData, byte[].class);
        verify(serializer).serialize(testDeadlineScope, byte[].class);
    }

    @SuppressWarnings("unchecked")
    @MethodSource("serializerImplementationAndAssertionSpecifics")
    @ParameterizedTest
    void retrievingDeadlineMessage(
            Serializer serializer,
            Function<Class, String> expectedSerializedClassType,
            Predicate<Object> revisionMatcher
    ) {
        JobDataMap testJobDataMap = toJobData(serializer, testDeadlineMessage, testDeadlineScope);

        DeadlineMessage<String> result = deadlineMessage(serializer, testJobDataMap);

        assertThat(result.getDeadlineName()).isEqualTo(testDeadlineMessage.getDeadlineName());
        assertThat(result.getIdentifier()).isEqualTo(testDeadlineMessage.getIdentifier());
        assertThat(result.getTimestamp()).isEqualTo(testDeadlineMessage.getTimestamp());
        assertThat(result.getPayload()).isEqualTo(testDeadlineMessage.getPayload());
        assertThat(result.getPayloadType()).isEqualTo(testDeadlineMessage.getPayloadType());
        assertThat(result.getMetaData()).isEqualTo(testDeadlineMessage.getMetaData());

        verify(serializer, times(2))
                .deserialize(argThat(new DeadlineMessageSerializedObjectMatcher(expectedSerializedClassType, revisionMatcher)));
    }

    private static class DeadlineMessageSerializedObjectMatcher implements ArgumentMatcher<SimpleSerializedObject<?>> {
        private final Function<Class, String> expectedSerializedClassType;
        private final Predicate<Object> revisionMatcher;

        DeadlineMessageSerializedObjectMatcher(Function<Class, String> expectedSerializedClassType, Predicate<Object> revisionMatcher) {
            this.expectedSerializedClassType = expectedSerializedClassType;
            this.revisionMatcher = revisionMatcher;
        }

        @Override
        public boolean matches(SimpleSerializedObject<?> serializedObject) {
            String expectedSerializedPayloadType = expectedSerializedClassType.apply(TEST_DEADLINE_PAYLOAD.getClass());

            SerializedType type = serializedObject.getType();
            String serializedTypeName = type.getName();
            boolean isSerializedMetaData = serializedTypeName.equals(MetaData.class.getName());

            return serializedObject.getData() != null &&
                    serializedObject.getContentType().equals(byte[].class) &&
                    (serializedTypeName.equals(expectedSerializedPayloadType) || isSerializedMetaData) &&
                    (isSerializedMetaData || revisionMatcher.test(type.getRevision()));
        }
    }

    @MethodSource("serializerImplementationAndAssertionSpecifics")
    @ParameterizedTest
    void retrievingDeadlineScope(Serializer serializer) {
        JobDataMap testJobDataMap = toJobData(serializer, testDeadlineMessage, testDeadlineScope);

        ScopeDescriptor result = deadlineScope(serializer, testJobDataMap);

        assertThat(result).isEqualTo(testDeadlineScope);
        verify(serializer).deserialize(
                (SimpleSerializedObject<?>) argThat(this::assertDeadlineScopeSerializedObject)
        );
    }

    private boolean assertDeadlineScopeSerializedObject(SimpleSerializedObject<?> serializedObject) {
        SerializedType type = serializedObject.getType();
        return serializedObject.getData() != null &&
                serializedObject.getContentType().equals(byte[].class) &&
                type.getName().equals(testDeadlineScope.getClass().getName()) &&
                type.getRevision() == null;
    }
}
