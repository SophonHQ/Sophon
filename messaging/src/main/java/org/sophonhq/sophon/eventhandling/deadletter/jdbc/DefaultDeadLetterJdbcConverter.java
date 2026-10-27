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

import org.sophonhq.sophon.common.DateTimeUtils;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.eventhandling.GenericTrackedDomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericTrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.messaging.deadletter.Cause;
import org.sophonhq.sophon.messaging.deadletter.DeadLetter;
import org.sophonhq.sophon.messaging.deadletter.ThrowableCause;
import org.sophonhq.sophon.serialization.SerializedMessage;
import org.sophonhq.sophon.serialization.SerializedObject;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.SimpleSerializedObject;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.function.Supplier;

import static org.sophonhq.sophon.common.BuilderUtils.assertNonNull;

/**
 * Default implementation of the {@link DeadLetterJdbcConverter}, converting {@link ResultSet ResultSets} into
 * {@link JdbcDeadLetter} instances.
 * <p>
 * This converter expects a {@link DeadLetterSchema} to define the column names / labels used to retrieve the fields
 * from the {@link ResultSet}. Furthermore, it uses the configurable {@code genericSerializer} to deserialize
 * {@link TrackingToken TrackingTokens} for {@link TrackedEventMessage} instances. Lastly, this factory uses the
 * {@code eventSerializer} to deserialize the {@link EventMessage#getPayload() event payload},
 * {@link EventMessage#getMetaData() MetaData}, and {@link DeadLetter#diagnostics() diagnostics} for the
 * {@code JdbcDeadLetter} to return.
 *
 * @param <E> An implementation of {@link EventMessage} contained within the {@link JdbcDeadLetter} implementation this
 *            converter converts.
 * @author Steven van Beelen
 * @since axon-4.8.0
 */
public class DefaultDeadLetterJdbcConverter<E extends EventMessage<?>>
        implements DeadLetterJdbcConverter<E, JdbcDeadLetter<E>> {

    private final DeadLetterSchema schema;
    private final Serializer genericSerializer;
    private final Serializer eventSerializer;

    /**
     * Instantiate a default {@link DeadLetterJdbcConverter} based on the given {@code builder}.
     * <p>
     * Will validate whether the {@link Builder#genericSerializer(Serializer) generic Serializer} and
     * {@link Builder#eventSerializer(Serializer) event Serializer} are set. If for either this is not the case an
     * {@link SophonConfigurationException} is thrown.
     *
     * @param builder The {@link Builder} used to instantiate a {@link DefaultDeadLetterJdbcConverter} instance.
     */
    protected DefaultDeadLetterJdbcConverter(Builder<E> builder) {
        builder.validate();
        schema = builder.schema;
        genericSerializer = builder.genericSerializer;
        eventSerializer = builder.eventSerializer;
    }

    /**
     * Instantiate a builder to construct a {@link DefaultDeadLetterJdbcConverter}.
     * <p>
     * The {@link Builder#schema(DeadLetterSchema) schema} is defaulted to a {@link DeadLetterSchema#defaultSchema()}.
     * The {@link Builder#genericSerializer(Serializer) generic Serializer} and
     * {@link Builder#eventSerializer(Serializer) event Serializer} are hard requirements and should be provided.
     *
     * @param <E> An implementation of {@link EventMessage} contained within the {@link JdbcDeadLetter} implementation
     *            this converter converts.
     * @return A builder that con construct a {@link DefaultDeadLetterJdbcConverter}.
     */
    public static <E extends EventMessage<?>> Builder<E> builder() {
        return new Builder<>();
    }

    @Override
    public JdbcDeadLetter<E> convertToLetter(ResultSet resultSet) throws SQLException {
        EventMessage<?> eventMessage;
        Message<?> serializedMessage = convertToSerializedMessage(resultSet);
        String eventTimestampString = resultSet.getString(schema.timestampColumn());
        Supplier<Instant> timestampSupplier = () -> DateTimeUtils.parseInstant(eventTimestampString);

        if (resultSet.getString(schema.tokenTypeColumn()) != null) {
            TrackingToken trackingToken = convertToTrackingToken(resultSet);
            if (resultSet.getString(schema.aggregateIdentifierColumn()) != null) {
                eventMessage = new GenericTrackedDomainEventMessage<>(
                        trackingToken,
                        resultSet.getString(schema.aggregateTypeColumn()),
                        resultSet.getString(schema.aggregateIdentifierColumn()),
                        resultSet.getLong(schema.sequenceNumberColumn()),
                        serializedMessage,
                        timestampSupplier
                );
            } else {
                eventMessage = new GenericTrackedEventMessage<>(trackingToken, serializedMessage, timestampSupplier);
            }
        } else if (resultSet.getString(schema.aggregateIdentifierColumn()) != null) {
            eventMessage = new GenericDomainEventMessage<>(resultSet.getString(schema.aggregateTypeColumn()),
                                                           resultSet.getString(schema.aggregateIdentifierColumn()),
                                                           resultSet.getLong(schema.sequenceNumberColumn()),
                                                           serializedMessage.getPayload(),
                                                           serializedMessage.getMetaData(),
                                                           serializedMessage.getIdentifier(),
                                                           timestampSupplier.get());
        } else {
            eventMessage = new GenericEventMessage<>(serializedMessage, timestampSupplier);
        }

        String deadLetterIdentifier = resultSet.getString(schema.deadLetterIdentifierColumn());
        long sequenceIndex = resultSet.getLong(schema.sequenceIndexColumn());
        String sequenceIdentifier = resultSet.getString(schema.sequenceIdentifierColumn());
        Instant enqueuedAt = DateTimeUtils.parseInstant(resultSet.getString(schema.enqueuedAtColumn()));
        Instant lastTouched = DateTimeUtils.parseInstant(resultSet.getString(schema.lastTouchedColumn()));
        Cause cause = null;
        String causeType = resultSet.getString(schema.causeTypeColumn());
        if (causeType != null) {
            cause = new ThrowableCause(causeType, resultSet.getString(schema.causeMessageColumn()));
        }
        MetaData diagnostics = convertToDiagnostics(resultSet);

        //noinspection unchecked
        return new JdbcDeadLetter<>(deadLetterIdentifier,
                                    sequenceIndex,
                                    sequenceIdentifier,
                                    enqueuedAt,
                                    lastTouched,
                                    cause,
                                    diagnostics,
                                    (E) eventMessage);
    }

    private SerializedMessage<?> convertToSerializedMessage(ResultSet resultSet) throws SQLException {
        SerializedObject<byte[]> serializedPayload = convertToSerializedPayload(resultSet);
        SerializedObject<byte[]> serializedMetaData = convertToSerializedMetaData(resultSet);
        return new SerializedMessage<>(resultSet.getString(schema.eventIdentifierColumn()),
                                       serializedPayload,
                                       serializedMetaData,
                                       eventSerializer);
    }

    private SerializedObject<byte[]> convertToSerializedPayload(ResultSet resultSet) throws SQLException {
        return new SimpleSerializedObject<>(resultSet.getBytes(schema.payloadColumn()),
                                            byte[].class,
                                            resultSet.getString(schema.payloadTypeColumn()),
                                            resultSet.getString(schema.payloadRevisionColumn()));
    }

    private SerializedObject<byte[]> convertToSerializedMetaData(ResultSet resultSet) throws SQLException {
        return new SimpleSerializedObject<>(resultSet.getBytes(schema.metaDataColumn()),
                                            byte[].class,
                                            MetaData.class.getName(),
                                            null);
    }

    private TrackingToken convertToTrackingToken(ResultSet resultSet) throws SQLException {
        SerializedObject<byte[]> serializedToken =
                new SimpleSerializedObject<>(resultSet.getBytes(schema.tokenColumn()),
                                             byte[].class,
                                             resultSet.getString(schema.tokenTypeColumn()),
                                             null);
        return genericSerializer.deserialize(serializedToken);
    }

    private MetaData convertToDiagnostics(ResultSet resultSet) throws SQLException {
        SerializedObject<byte[]> serializedDiagnostics =
                new SimpleSerializedObject<>(resultSet.getBytes(schema.diagnosticsColumn()),
                                             byte[].class,
                                             MetaData.class.getName(),
                                             null);
        return eventSerializer.deserialize(serializedDiagnostics);
    }

    /**
     * Builder class to instantiate a {@link DefaultDeadLetterJdbcConverter}.
     * <p>
     * The {@link Builder#schema(DeadLetterSchema) schema} is defaulted to a {@link DeadLetterSchema#defaultSchema()}.
     * The {@link Builder#genericSerializer(Serializer) generic Serializer} and
     * {@link Builder#eventSerializer(Serializer) event Serializer} are hard requirements and should be provided.
     *
     * @param <E> An implementation of {@link EventMessage} contained within the {@link JdbcDeadLetter} implementation
     *            this converter converts.
     */
    protected static class Builder<E extends EventMessage<?>> {

        private DeadLetterSchema schema = DeadLetterSchema.defaultSchema();
        private Serializer genericSerializer;
        private Serializer eventSerializer;

        /**
         * Sets the given {@code schema} used to define the column names / labels with to return fields from the
         * {@link ResultSet}. Defaults to a {@link DeadLetterSchema#defaultSchema()}.
         *
         * @param schema The {@link DeadLetterSchema} used to define the table and column names used when constructing
         *               <b>all</b> {@link PreparedStatement PreparedStatements}.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> schema(DeadLetterSchema schema) {
            assertNonNull(schema, "DeadLetterSchema may not be null");
            this.schema = schema;
            return this;
        }

        /**
         * Sets the {@link Serializer} to deserialize the {@link TrackingToken} of a {@link TrackedEventMessage}
         * instance.
         *
         * @param genericSerializer The serializer used to deserialize {@link TrackingToken TrackingTokens} with
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> genericSerializer(Serializer genericSerializer) {
            assertNonNull(genericSerializer, "The generic Serializer may not be null");
            this.genericSerializer = genericSerializer;
            return this;
        }

        /**
         * Sets the {@link Serializer} to deserialize {@link EventMessage#getPayload() event payloads},
         * {@link EventMessage#getMetaData() MetaData} instances, and {@link DeadLetter#diagnostics() diagnostics}
         * with.
         *
         * @param eventSerializer The serializer used to deserialize {@link EventMessage#getPayload() event payloads},
         *                        {@link EventMessage#getMetaData() MetaData} instances, and
         *                        {@link DeadLetter#diagnostics() diagnostics} with.
         * @return The current Builder, for fluent interfacing.
         */
        public Builder<E> eventSerializer(Serializer eventSerializer) {
            assertNonNull(eventSerializer, "The event Serializer may not be null");
            this.eventSerializer = eventSerializer;
            return this;
        }

        /**
         * Initializes a {@link DefaultDeadLetterJdbcConverter} as specified through this Builder.
         *
         * @return A {@link DefaultDeadLetterJdbcConverter} as specified through this Builder.
         */
        public DefaultDeadLetterJdbcConverter<E> build() {
            return new DefaultDeadLetterJdbcConverter<>(this);
        }

        /**
         * Validate whether the fields contained in this Builder are set accordingly.
         *
         * @throws SophonConfigurationException When one field asserts to be incorrect according to the Builder's
         *                                    specifications.
         */
        protected void validate() {
            assertNonNull(genericSerializer, "The generic Serializer is a hard requirement and should be provided");
            assertNonNull(eventSerializer, "The event Serializer is a hard requirement and should be provided");
        }
    }
}
