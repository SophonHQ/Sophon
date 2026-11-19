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

package org.sophonhq.sophon.eventhandling.deadletter.jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.DateTimeUtils;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.eventhandling.GenericTrackedDomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericTrackedEventMessage;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.messaging.deadletter.Cause;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.TestSerializer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        assertThat(resultMessage).isInstanceOf(GenericTrackedDomainEventMessage.class);
        GenericTrackedDomainEventMessage<?> castedResultMessage = (GenericTrackedDomainEventMessage<?>) resultMessage;
        TrackingToken resultToken = castedResultMessage.trackingToken();
        assertThat(resultToken).isEqualTo(TRACKING_TOKEN);
        assertThat(castedResultMessage.getAggregateIdentifier()).isEqualTo(AGGREGATE_ID);
        assertThat(castedResultMessage.getType()).isEqualTo(AGGREGATE_TYPE);
        assertThat(castedResultMessage.getSequenceNumber()).isEqualTo(SEQ_NO);
    }

    @Test
    void convertToLetterConstructsGenericTrackedEventMessage() throws SQLException {
        boolean withToken = true;
        boolean isDomainEvent = false;
        boolean withCause = true;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        EventMessage<?> resultMessage = result.message();
        assertThat(resultMessage).isInstanceOf(GenericTrackedEventMessage.class);
        assertThat(((GenericTrackedEventMessage<?>) resultMessage).trackingToken()).isEqualTo(TRACKING_TOKEN);
    }

    @Test
    void convertToLetterConstructsGenericDomainEventMessage() throws SQLException {
        boolean withToken = false;
        boolean isDomainEvent = true;
        boolean withCause = true;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        EventMessage<?> resultMessage = result.message();
        assertThat(resultMessage).isInstanceOf(GenericDomainEventMessage.class);
        GenericDomainEventMessage<?> castedResultMessage = (GenericDomainEventMessage<?>) resultMessage;
        assertThat(castedResultMessage.getAggregateIdentifier()).isEqualTo(AGGREGATE_ID);
        assertThat(castedResultMessage.getType()).isEqualTo(AGGREGATE_TYPE);
        assertThat(castedResultMessage.getSequenceNumber()).isEqualTo(SEQ_NO);
    }

    @Test
    void convertToLetterConstructsGenericEventMessage() throws SQLException {
        boolean withToken = false;
        boolean isDomainEvent = false;
        boolean withCause = true;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        assertThat(result.message()).isInstanceOf(GenericEventMessage.class);
    }

    @Test
    void convertToLetterConstructsWithCause() throws SQLException {
        boolean withToken = true;
        boolean isDomainEvent = true;
        boolean withCause = true;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        Optional<Cause> optionalCause = result.cause();
        assertThat(optionalCause).isPresent();
        Cause resultCause = optionalCause.get();
        assertThat(resultCause.type()).isEqualTo(CAUSE_TYPE);
        assertThat(resultCause.message()).isEqualTo(CAUSE_MESSAGE);
    }

    @Test
    void convertToLetterConstructsWithoutCause() throws SQLException {
        boolean withToken = true;
        boolean isDomainEvent = true;
        boolean withCause = false;
        ResultSet resultSet = mockedResultSet(withToken, isDomainEvent, withCause);

        JdbcDeadLetter<?> result = testSubject.convertToLetter(resultSet);

        assertThat(result.cause()).isNotPresent();
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

        assertThatThrownBy(() -> testBuilder.schema(null)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNullGenericSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterJdbcConverter.Builder<?> testBuilder = DefaultDeadLetterJdbcConverter.builder();

        assertThatThrownBy(() -> testBuilder.genericSerializer(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNullEventSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterJdbcConverter.Builder<?> testBuilder = DefaultDeadLetterJdbcConverter.builder();

        assertThatThrownBy(() -> testBuilder.eventSerializer(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithoutTheGenericSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterJdbcConverter.Builder<?> testBuilder =
                DefaultDeadLetterJdbcConverter.builder()
                                              .eventSerializer(TestSerializer.JACKSON.getSerializer());

        assertThatThrownBy(testBuilder::build).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithoutTheEventSerializerThrowsSophonConfigurationException() {
        DefaultDeadLetterJdbcConverter.Builder<?> testBuilder =
                DefaultDeadLetterJdbcConverter.builder()
                                              .genericSerializer(TestSerializer.JACKSON.getSerializer());

        assertThatThrownBy(testBuilder::build).isInstanceOf(SophonConfigurationException.class);
    }
}