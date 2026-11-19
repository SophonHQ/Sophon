/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.sophonhq.sophon.eventhandling.scheduling.quartz;

import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.quartz.JobDataMap;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.serialization.JavaSerializer;
import org.sophonhq.sophon.serialization.SerializedType;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.SimpleSerializedObject;
import org.sophonhq.sophon.serialization.TestSerializer;
import org.sophonhq.sophon.serialization.json.JacksonSerializer;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.messaging.Headers.*;

class DirectEventJobDataBinderTest {

    private static final String TEST_EVENT_PAYLOAD = "event-payload";

    private final EventMessage<String> testEventMessage;
    private final MetaData testMetaData;

    DirectEventJobDataBinderTest() {
        this.testMetaData = MetaData.with("some-key", "some-value");
        this.testEventMessage = GenericEventMessage.<String>asEventMessage(TEST_EVENT_PAYLOAD)
                .withMetaData(testMetaData);
    }

    static Stream<Arguments> serializerImplementationAndAssertionSpecifics() {
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
    void eventMessageToJobData(
            Serializer serializer,
            Function<Class, String> expectedSerializedClassType,
            Predicate<Object> revisionMatcher
    ) {
        QuartzEventScheduler.DirectEventJobDataBinder testSubject = new QuartzEventScheduler.DirectEventJobDataBinder(serializer);

        JobDataMap result = testSubject.toJobData(testEventMessage);

        assertThat(result)
                .containsEntry(MESSAGE_ID, testEventMessage.getIdentifier())
                .containsEntry(MESSAGE_TIMESTAMP, testEventMessage.getTimestamp().toString());
        String expectedPayloadType = expectedSerializedClassType.apply(testEventMessage.getPayloadType());
        assertThat(result).containsEntry(MESSAGE_TYPE, expectedPayloadType);
        Object resultRevision = result.get(MESSAGE_REVISION);
        assertThat(revisionMatcher).accepts(resultRevision);

        assertThat(result.get(SERIALIZED_MESSAGE_PAYLOAD)).isNotNull();
        assertThat(result.get(MESSAGE_METADATA)).isNotNull();

        verify(serializer).serialize(TEST_EVENT_PAYLOAD, byte[].class);
        verify(serializer).serialize(testMetaData, byte[].class);
    }

    @SuppressWarnings("unchecked")
    @MethodSource("serializerImplementationAndAssertionSpecifics")
    @ParameterizedTest
    void eventMessageFromJobData(
            Serializer serializer,
            Function<Class, String> expectedSerializedClassType,
            Predicate<Object> revisionMatcher
    ) {
        QuartzEventScheduler.DirectEventJobDataBinder testSubject = new QuartzEventScheduler.DirectEventJobDataBinder(serializer);
        JobDataMap testJobDataMap = testSubject.toJobData(testEventMessage);

        Object result = testSubject.fromJobData(testJobDataMap);

        assertThat(result).isInstanceOf(EventMessage.class);

        EventMessage<String> resultEventMessage = (EventMessage<String>) result;

        assertThat(resultEventMessage.getIdentifier()).isEqualTo(testEventMessage.getIdentifier());
        assertThat(resultEventMessage.getTimestamp()).isEqualTo(testEventMessage.getTimestamp());
        assertThat(resultEventMessage.getPayload()).isEqualTo(testEventMessage.getPayload());
        assertThat(resultEventMessage.getPayloadType()).isEqualTo(testEventMessage.getPayloadType());
        assertThat(resultEventMessage.getMetaData()).isEqualTo(testEventMessage.getMetaData());

        verify(serializer, times(2)).deserialize(
                argThat(new MatchEventMessageSerializedObject(expectedSerializedClassType, revisionMatcher))
        );
    }

    private static class MatchEventMessageSerializedObject implements ArgumentMatcher<SimpleSerializedObject<?>> {
        private final Function<Class, String> expectedSerializedClassType;
        private final Predicate<Object> revisionMatcher;

        MatchEventMessageSerializedObject(Function<Class, String> expectedSerializedClassType, Predicate<Object> revisionMatcher) {
            this.expectedSerializedClassType = expectedSerializedClassType;
            this.revisionMatcher = revisionMatcher;
        }

        @Override
        public boolean matches(SimpleSerializedObject<?> serializedObject) {
            String expectedSerializedPayloadType = expectedSerializedClassType.apply(TEST_EVENT_PAYLOAD.getClass());

            SerializedType type = serializedObject.getType();
            String serializedTypeName = type.getName();
            boolean isSerializedMetaData = serializedTypeName.equals(MetaData.class.getName());

            return serializedObject.getData() != null &&
                    serializedObject.getContentType().equals(byte[].class) &&
                    (serializedTypeName.equals(expectedSerializedPayloadType) || isSerializedMetaData) &&
                    (isSerializedMetaData || revisionMatcher.test(type.getRevision()));
        }
    }

}
