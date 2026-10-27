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

package io.github.sophonhq.sophon.eventhandling.deadletter.jdbc;

import io.github.sophonhq.sophon.common.DateTimeUtils;
import io.github.sophonhq.sophon.common.SophonConfigurationException;
import io.github.sophonhq.sophon.eventhandling.EventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericEventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericTrackedDomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericTrackedEventMessage;
import io.github.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import io.github.sophonhq.sophon.eventhandling.TrackingToken;
import io.github.sophonhq.sophon.messaging.MetaData;
import io.github.sophonhq.sophon.messaging.deadletter.Cause;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DefaultDeadLetterJdbcConverter}.
 *
 * @author Steven van Beelen
 */
class DefaultDeadLetterJdbcConverterTest {

    private static final String AGGREGATE_ID = "aggregateId";
    private static final String AGGREGATE_TYPE = "aggregateType";
    private static final long SEQ_NO = 42;
    private static final TrackingToken TRACKING_TOKEN = new GlobalSequenceTrackingToken(1);
    private static final String CAUSE_TYPE = "causeType";
    private static final String CAUSE_MESSAGE = "causeMessage";

    private DeadLetterSchema schema;
    private Serializer genericSerializer;
    private Serializer eventSerializer;

    private DefaultDeadLetterJdbcConverter<?> testSubject;

    @BeforeEach
    void setUp() {
        schema = DeadLetterSchema.defaultSchema();
        genericSerializer = TestSerializer.JACKSON.getSerializer();
        eventSerializer = genericSerializer;

        testSubject = DefaultDeadLetterJdbcConverter.builder()
                                                    .schema(schema)
                                                    .genericSerializer(genericSerializer)
                                                    .eventSerializer(eventSerializer)
                                                    .build();
    }

    @Test
    void convertToLetterConstructsGenericTrackedDomainEventMessage() throws SQLException {
        boolean withToken = true;
        boolean isDomainEvent = true;
        boolean withCause = true;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        EventMessage<?> resultMessage = result.message();
        assertTrue(resultMessage instanceof GenericTrackedDomainEventMessage);
        GenericTrackedDomainEventMessage<?> castedResultMessage = (GenericTrackedDomainEventMessage<?>) resultMessage;
        TrackingToken resultToken = castedResultMessage.trackingToken();
        assertEquals(TRACKING_TOKEN, resultToken);
        assertEquals(AGGREGATE_ID, castedResultMessage.getAggregateIdentifier());
        assertEquals(AGGREGATE_TYPE, castedResultMessage.getType());
        assertEquals(SEQ_NO, castedResultMessage.getSequenceNumber());
    }

    @Test
    void convertToLetterConstructsGenericTrackedEventMessage() throws SQLException {
        boolean withToken = true;
        boolean isDomainEvent = false;
        boolean withCause = true;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        EventMessage<?> resultMessage = result.message();
        assertTrue(resultMessage instanceof GenericTrackedEventMessage);
        assertEquals(TRACKING_TOKEN, ((GenericTrackedEventMessage<?>) resultMessage).trackingToken());
    }

    @Test
    void convertToLetterConstructsGenericDomainEventMessage() throws SQLException {
        boolean withToken = false;
        boolean isDomainEvent = true;
        boolean withCause = true;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        EventMessage<?> resultMessage = result.message();
        assertTrue(resultMessage instanceof GenericDomainEventMessage);
        GenericDomainEventMessage<?> castedResultMessage = (GenericDomainEventMessage<?>) resultMessage;
        assertEquals(AGGREGATE_ID, castedResultMessage.getAggregateIdentifier());
        assertEquals(AGGREGATE_TYPE, castedResultMessage.getType());
        assertEquals(SEQ_NO, castedResultMessage.getSequenceNumber());
    }

    @Test
    void convertToLetterConstructsGenericEventMessage() throws SQLException {
        boolean withToken = false;
        boolean isDomainEvent = false;
        boolean withCause = true;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        assertTrue(result.message() instanceof GenericEventMessage);
    }

    @Test
    void convertToLetterConstructsWithCause() throws SQLException {
        boolean withToken = true;
        boolean isDomainEvent = true;
        boolean withCause = true;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        Optional<Cause> optionalCause = result.cause();
        assertTrue(optionalCause.isPresent());
        Cause resultCause = optionalCause.get();
        assertEquals(CAUSE_TYPE, resultCause.type());
        assertEquals(CAUSE_MESSAGE, resultCause.message());
    }

    @Test
    void convertToLetterConstructsWithoutCause() throws SQLException {
        boolean withToken = true;
        boolean isDomainEvent = true;
        boolean withCause = false;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        assertFalse(result.cause().isPresent());
    }

    private ResultSet mockedResultSet(boolean withToken, boolean isDomainEvent, boolean withCause) throws SQLException {
        ResultSet mock = mock(ResultSet.class);
        String timestamp = DateTimeUtils.formatInstant(Instant.now());
        byte[] serializedPayload = eventSerializer.serialize("some-payload", byte[].class).getData();
        byte[] serializedMetaData = eventSerializer.serialize(MetaData.emptyInstance(), byte[].class).getData();

        // Payload mocking
        when(mock.getBytes(schema.payloadColumn())).thenReturn(serializedPayload);
        when(mock.getString(schema.payloadTypeColumn())).thenReturn(String.class.getName());
        // MetaData mocking
        when(mock.getBytes(schema.metaDataColumn())).thenReturn(serializedMetaData);
        // Event Message mocking
        when(mock.getString(schema.eventIdentifierColumn())).thenReturn(UUID.randomUUID().toString());
        when(mock.getString(schema.timestampColumn())).thenReturn(timestamp);
        // Token mocking
        if (withToken) {
            when(mock.getString(schema.tokenTypeColumn())).thenReturn(GlobalSequenceTrackingToken.class.getName());
            when(mock.getBytes(schema.tokenColumn()))
                    .thenReturn(genericSerializer.serialize(TRACKING_TOKEN, byte[].class).getData());
        } else {
            when(mock.getString(schema.tokenTypeColumn())).thenReturn(null);
        }
        // Domain Event mocking
        if (isDomainEvent) {
            when(mock.getString(schema.aggregateIdentifierColumn())).thenReturn(AGGREGATE_ID);
            when(mock.getString(schema.aggregateTypeColumn())).thenReturn(AGGREGATE_TYPE);
            when(mock.getLong(schema.sequenceNumberColumn())).thenReturn(SEQ_NO);
        } else {
            when(mock.getString(schema.aggregateIdentifierColumn())).thenReturn(null);
        }
        // Dead Letter mocking
        when(mock.getString(schema.deadLetterIdentifierColumn())).thenReturn(UUID.randomUUID().toString());
        when(mock.getLong(schema.sequenceIndexColumn())).thenReturn(1337L);
        when(mock.getString(schema.sequenceIdentifierColumn())).thenReturn(UUID.randomUUID().toString());
        when(mock.getString(schema.enqueuedAtColumn())).thenReturn(timestamp);
        when(mock.getString(schema.lastTouchedColumn())).thenReturn(timestamp);
        when(mock.getBytes(schema.diagnosticsColumn())).thenReturn(serializedMetaData);
        // Cause mocking
        if (withCause) {
            when(mock.getString(schema.causeTypeColumn())).thenReturn(CAUSE_TYPE);
            when(mock.getString(schema.causeMessageColumn())).thenReturn(CAUSE_MESSAGE);

        } else {
            when(mock.getString(schema.causeTypeColumn())).thenReturn(null);
        }

        return mock;
    }

    @Test
    void buildWithNullSchemaThrowsSophonConfigurationException() {
        DefaultDeadLetterJdbcConverter.Builder<?> testBuilder = DefaultDeadLetterJdbcConverter.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.schema(null));
    }

    @Test
    void buildWithNullGenericSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterJdbcConverter.Builder<?> testBuilder = DefaultDeadLetterJdbcConverter.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.genericSerializer(null));
    }

    @Test
    void buildWithNullEventSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterJdbcConverter.Builder<?> testBuilder = DefaultDeadLetterJdbcConverter.builder();

        assertThrows(SophonConfigurationException.class, () -> testBuilder.eventSerializer(null));
    }

    @Test
    void buildWithoutTheGenericSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterJdbcConverter.Builder<?> testBuilder =
                DefaultDeadLetterJdbcConverter.builder()
                                              .eventSerializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }

    @Test
    void buildWithoutTheEventSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterJdbcConverter.Builder<?> testBuilder =
                DefaultDeadLetterJdbcConverter.builder()
                                              .genericSerializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }
}