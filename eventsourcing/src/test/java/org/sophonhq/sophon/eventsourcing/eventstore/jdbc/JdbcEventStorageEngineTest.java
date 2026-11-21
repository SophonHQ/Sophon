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

package org.sophonhq.sophon.eventsourcing.eventstore.jdbc;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.common.jdbc.PersistenceExceptionResolver;
import org.sophonhq.sophon.common.transaction.NoTransactionManager;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.GapAwareTrackingToken;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.eventhandling.TrackedEventData;
import org.sophonhq.sophon.eventhandling.TrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackingEventStream;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.eventsourcing.eventstore.BatchingEventStorageEngineTest;
import org.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.jdbc.statements.JdbcEventStorageEngineStatements;
import org.sophonhq.sophon.eventsourcing.eventstore.jdbc.statements.ReadEventDataForAggregateStatementBuilder;
import org.sophonhq.sophon.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.sophonhq.sophon.eventsourcing.utils.TestSerializer;
import org.sophonhq.sophon.serialization.UnknownSerializedType;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.sophonhq.sophon.eventsourcing.utils.EventStoreTestUtils.AGGREGATE;
import static org.sophonhq.sophon.eventsourcing.utils.EventStoreTestUtils.createEvent;
import static org.sophonhq.sophon.eventsourcing.utils.EventStoreTestUtils.createEvents;

/**
 * Test class validating the {@link JdbcEventStorageEngine}.
 *
 * @author Rene de Waele
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
class JdbcEventStorageEngineTest
        extends BatchingEventStorageEngineTest<JdbcEventStorageEngine, JdbcEventStorageEngine.Builder> {

    private JdbcDataSource dataSource;
    private PersistenceExceptionResolver defaultPersistenceExceptionResolver;
    private JdbcEventStorageEngine testSubject;
    private ReadEventDataForAggregateStatementBuilder readForAggregateStatementBuilder;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        defaultPersistenceExceptionResolver = new SQLErrorCodesResolver(dataSource);
        //noinspection Convert2Lambda,Anonymous2MethodRef
        readForAggregateStatementBuilder = spy(new ReadEventDataForAggregateStatementBuilder() {
            @Override
            public PreparedStatement build(Connection connection, EventSchema schema, String identifier, long firstSequenceNumber, int batchSize) throws SQLException {
                return JdbcEventStorageEngineStatements.readEventDataForAggregate(connection, schema, identifier, firstSequenceNumber, batchSize);
            }
        });
        setTestSubject(testSubject = createEngine(b -> b.readEventDataForAggregate(readForAggregateStatementBuilder)));
    }

    @Test
    void testLoadLargeAmountOfEventsFromAggregateStream() {
        super.loadLargeAmountOfEventsFromAggregateStream();

        try {
            verify(readForAggregateStatementBuilder, times(6)).build(any(), any(), eq(AGGREGATE), anyLong(), anyInt());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void loadLargeAmountOfEventsFromAggregateStream_WithCustomFinalBatchPredicate() throws SQLException {
        setTestSubject(testSubject = createEngine(b -> b.finalAggregateBatchPredicate(l -> l.size() < 50)
                                                        .readEventDataForAggregate(readForAggregateStatementBuilder)));

        super.loadLargeAmountOfEventsFromAggregateStream();

        verify(readForAggregateStatementBuilder, times(4)).build(any(), any(), eq(AGGREGATE), anyLong(), anyInt());
    }

    @Test
    void storeTwoExactSameSnapshots() {
        testSubject.storeSnapshot(createEvent(1));
        testSubject.storeSnapshot(createEvent(1));
    }

    @Test
    void loadLastSequenceNumber() {
        String aggregateId = UUID.randomUUID().toString();
        testSubject.appendEvents(createEvent(aggregateId, 0), createEvent(aggregateId, 1));
        assertThat((long) testSubject.lastSequenceNumberFor(aggregateId).orElse(-1L)).isOne();
        assertThat(testSubject.lastSequenceNumberFor("inexistent")).isEmpty();
    }

    @Test
    @DirtiesContext
    void customSchemaConfig() {
        EventSchema testSchema = EventSchema.builder()
                                            .eventTable("CustomDomainEvent")
                                            .payloadColumn("eventData")
                                            .build();
        setTestSubject(testSubject = createEngine(
                engineBuilder -> engineBuilder.schema(testSchema).dataType(String.class),
                new H2EventTableFactory() {
                    @Override
                    protected String payloadType() {
                        return "LONGVARCHAR";
                    }
                }
        ));

        storeAndLoadEvents();
    }

    @Test
    @DirtiesContext
    void customSchemaConfigTimestampColumn() {
        setTestSubject(testSubject = createTimestampEngine(new H2EventTableFactory() {
            @Override
            protected String timestampType() {
                return "timestamp";
            }
        }));
        storeAndLoadEvents();
    }

    @Test
    void gapsForVeryOldEventsAreNotIncluded() throws SQLException {
        GenericEventMessage.clock =
                Clock.fixed(Clock.systemUTC().instant().minus(1, ChronoUnit.HOURS), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-1), createEvent(0));

        GenericEventMessage.clock =
                Clock.fixed(Clock.systemUTC().instant().minus(2, ChronoUnit.MINUTES), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-2), createEvent(1));

        GenericEventMessage.clock =
                Clock.fixed(Clock.systemUTC().instant().minus(50, ChronoUnit.SECONDS), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-3), createEvent(2));

        GenericEventMessage.clock = Clock.fixed(Clock.systemUTC().instant(), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-4), createEvent(3));

        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM DomainEventEntry WHERE sequenceNumber < 0").executeUpdate();
        }

        testSubject.fetchTrackedEvents((TrackingToken) null, 100).stream()
                   .map(i -> (GapAwareTrackingToken) i.trackingToken())
                   .forEach(i -> assertThat(i.getGaps().size()).isLessThanOrEqualTo(2));
    }

    @DirtiesContext
    @Test
    void oldGapsAreRemovedFromProvidedTrackingToken() throws SQLException {
        testSubject = createEngine(engineBuilder -> engineBuilder.gapTimeout(50001).gapCleaningThreshold(50));

        Instant now = Clock.systemUTC().instant();
        GenericEventMessage.clock = Clock.fixed(now.minus(1, ChronoUnit.HOURS), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-1), createEvent(0)); // index 0 and 1
        GenericEventMessage.clock = Clock.fixed(now.minus(2, ChronoUnit.MINUTES), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-2), createEvent(1)); // index 2 and 3
        GenericEventMessage.clock = Clock.fixed(now.minus(50, ChronoUnit.SECONDS), Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-3), createEvent(2)); // index 4 and 5
        GenericEventMessage.clock = Clock.fixed(now, Clock.systemUTC().getZone());
        testSubject.appendEvents(createEvent(-4), createEvent(3)); // index 6 and 7

        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM DomainEventEntry WHERE sequenceNumber < 0").executeUpdate();
        }

        List<Long> gaps = LongStream.range(-50, 6)
                                    .filter(i -> i != 1L && i != 3L && i != 5)
                                    .boxed()
                                    .collect(Collectors.toList());
        List<? extends TrackedEventData<?>> events =
                testSubject.fetchTrackedEvents(GapAwareTrackingToken.newInstance(6, gaps), 100);
        assertThat(events.size()).isOne();
        assertThat((long) ((GapAwareTrackingToken) events.get(0).trackingToken()).getGaps().first()).isEqualTo(4L);
    }

    @Test
    void eventsWithUnknownPayloadTypeDoNotResultInError() throws SQLException, InterruptedException {
        String expectedPayloadOne = "Payload3";
        String expectedPayloadTwo = "Payload4";

        int testBatchSize = 2;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));
        EmbeddedEventStore testEventStore = EmbeddedEventStore.builder().storageEngine(testSubject).build();

        testSubject.appendEvents(createEvent(AGGREGATE, 1, "Payload1"),
                                 createEvent(AGGREGATE, 2, "Payload2"));
        // Update events which will be part of the first batch to an unknown payload type
        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("UPDATE DomainEventEntry e SET e.payloadType = 'unknown'")
                .executeUpdate();
        }
        testSubject.appendEvents(createEvent(AGGREGATE, 3, expectedPayloadOne),
                                 createEvent(AGGREGATE, 4, expectedPayloadTwo));

        List<String> eventStorageEngineResult = testSubject.readEvents(null, false)
                                                           .filter(m -> m.getPayload() instanceof String)
                                                           .map(m -> (String) m.getPayload())
                                                           .collect(toList());
        assertThat(eventStorageEngineResult).containsExactlyElementsOf(asList(expectedPayloadOne, expectedPayloadTwo));

        TrackingEventStream eventStoreResult = testEventStore.openStream(null);
        assertThat(eventStoreResult.hasNextAvailable()).isTrue();
        assertThat(eventStoreResult.nextAvailable().getPayloadType()).isEqualTo(UnknownSerializedType.class);
        assertThat(eventStoreResult.nextAvailable().getPayloadType()).isEqualTo(UnknownSerializedType.class);
        assertThat(eventStoreResult.nextAvailable().getPayload()).isEqualTo(expectedPayloadOne);
        assertThat(eventStoreResult.nextAvailable().getPayload()).isEqualTo(expectedPayloadTwo);
    }

    @Test
    void streamCrossesConsecutiveGapsOfMoreThanBatchSuccessfully() throws SQLException {
        int testBatchSize = 10;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));
        testSubject.appendEvents(createEvents(100));

        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM DomainEventEntry WHERE globalIndex >= 20 and globalIndex < 40")
                .executeUpdate();
        }

        Stream<? extends TrackedEventMessage<?>> actual = testSubject.readEvents(null, false);
        List<? extends TrackedEventMessage<?>> actualEvents = actual.collect(toList());
        assertThat(actualEvents.size()).isEqualTo(80);
    }

    @Test
    void streamDoesNotCrossExtendedGapWhenDisabled() throws SQLException {
        int testBatchSize = 10;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize)
                                                                 .extendedGapCheckEnabled(false));

        try {
            Connection connection = dataSource.getConnection();
            connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
            connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
            testSubject.createSchema(H2EventTableFactory.INSTANCE);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        testSubject.appendEvents(createEvents(100));

        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM DomainEventEntry WHERE globalIndex >= 20 and globalIndex < 40")
                .executeUpdate();
        }

        Stream<? extends TrackedEventMessage<?>> actual = testSubject.readEvents(null, false);
        List<? extends TrackedEventMessage<?>> actualEvents = actual.collect(toList());
        assertThat(actualEvents.size()).isEqualTo(20);
    }

    @Test
    void streamCrossesInitialConsecutiveGapsOfMoreThanBatchSuccessfully() throws SQLException {
        int testBatchSize = 10;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));

        testSubject.appendEvents(createEvents(100));

        try (Connection conn = dataSource.getConnection()) {
            conn.prepareStatement("DELETE FROM DomainEventEntry WHERE globalIndex < 20")
                .executeUpdate();
        }

        Stream<? extends TrackedEventMessage<?>> actual = testSubject.readEvents(null, false);
        List<? extends TrackedEventMessage<?>> actualEvents = actual.collect(toList());
        assertThat(actualEvents.size()).isEqualTo(80);
    }

    @Test
    void readEventsForAggregateReturnsTheCompleteStream() {
        int testBatchSize = 10;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));

        DomainEventMessage<String> testEventOne = createEvent(0);
        DomainEventMessage<String> testEventTwo = createEvent(1);
        DomainEventMessage<String> testEventThree = createEvent(2);
        DomainEventMessage<String> testEventFour = createEvent(3);
        DomainEventMessage<String> testEventFive = createEvent(4);

        testSubject.appendEvents(testEventOne, testEventTwo, testEventThree, testEventFour, testEventFive);

        List<? extends DomainEventMessage<?>> result = testSubject.readEvents(AGGREGATE, 0L).asStream()
                                                                  .collect(toList());

        assertThat(result.size()).isEqualTo(5);
        assertThat(result.get(0).getSequenceNumber()).isZero();
        assertThat(result.get(1).getSequenceNumber()).isOne();
        assertThat(result.get(2).getSequenceNumber()).isEqualTo(2);
        assertThat(result.get(3).getSequenceNumber()).isEqualTo(3);
        assertThat(result.get(4).getSequenceNumber()).isEqualTo(4);
    }

    @Test
    void readEventsForAggregateWithGapsReturnsTheCompleteStream() {
        int testBatchSize = 10;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));

        DomainEventMessage<String> testEventOne = createEvent(0);
        DomainEventMessage<String> testEventTwo = createEvent(1);
        // Event with sequence number 2 is missing -> the gap
        DomainEventMessage<String> testEventFour = createEvent(3);
        DomainEventMessage<String> testEventFive = createEvent(4);

        testSubject.appendEvents(testEventOne, testEventTwo, testEventFour, testEventFive);

        List<? extends DomainEventMessage<?>> result = testSubject.readEvents(AGGREGATE, 0L).asStream()
                                                                  .collect(toList());

        assertThat(result.size()).isEqualTo(4);
        assertThat(result.get(0).getSequenceNumber()).isZero();
        assertThat(result.get(1).getSequenceNumber()).isOne();
        assertThat(result.get(2).getSequenceNumber()).isEqualTo(3);
        assertThat(result.get(3).getSequenceNumber()).isEqualTo(4);
    }

    @Test
    void readEventsForAggregateWithEventsExceedingOneBatchReturnsTheCompleteStream() {
        // Set batch size to 5, so that the number of events exceeds at least one batch
        int testBatchSize = 5;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));

        DomainEventMessage<String> testEventOne = createEvent(0);
        DomainEventMessage<String> testEventTwo = createEvent(1);
        DomainEventMessage<String> testEventThree = createEvent(2);
        DomainEventMessage<String> testEventFour = createEvent(3);
        DomainEventMessage<String> testEventFive = createEvent(4);
        DomainEventMessage<String> testEventSix = createEvent(5);
        DomainEventMessage<String> testEventSeven = createEvent(6);
        DomainEventMessage<String> testEventEight = createEvent(7);

        testSubject.appendEvents(
                testEventOne, testEventTwo, testEventThree, testEventFour, testEventFive, testEventSix, testEventSeven,
                testEventEight
        );

        List<? extends DomainEventMessage<?>> result = testSubject.readEvents(AGGREGATE, 0L).asStream()
                                                                  .collect(toList());

        assertThat(result.size()).isEqualTo(8);
        assertThat(result.get(0).getSequenceNumber()).isZero();
        assertThat(result.get(1).getSequenceNumber()).isOne();
        assertThat(result.get(2).getSequenceNumber()).isEqualTo(2);
        assertThat(result.get(3).getSequenceNumber()).isEqualTo(3);
        assertThat(result.get(4).getSequenceNumber()).isEqualTo(4);
        assertThat(result.get(5).getSequenceNumber()).isEqualTo(5);
        assertThat(result.get(6).getSequenceNumber()).isEqualTo(6);
        assertThat(result.get(7).getSequenceNumber()).isEqualTo(7);
    }

    @Test
    void readEventsForAggregateWithEventsExceedingOneBatchAndGapsReturnsTheCompleteStream() {
        // Set batch size to 5, so that the number of events exceeds at least one batch
        int testBatchSize = 5;
        testSubject = createEngine(engineBuilder -> engineBuilder.batchSize(testBatchSize));

        DomainEventMessage<String> testEventOne = createEvent(0);
        DomainEventMessage<String> testEventTwo = createEvent(1);
        // Event with sequence number 2 is missing -> the gap
        DomainEventMessage<String> testEventFour = createEvent(3);
        DomainEventMessage<String> testEventFive = createEvent(4);
        DomainEventMessage<String> testEventSix = createEvent(5);
        DomainEventMessage<String> testEventSeven = createEvent(6);
        DomainEventMessage<String> testEventEight = createEvent(7);

        testSubject.appendEvents(
                testEventOne, testEventTwo, testEventFour, testEventFive, testEventSix, testEventSeven,
                testEventEight
        );

        List<? extends DomainEventMessage<?>> result = testSubject.readEvents(AGGREGATE, 0L).asStream()
                                                                  .collect(toList());

        assertThat(result.size()).isEqualTo(7);
        assertThat(result.get(0).getSequenceNumber()).isZero();
        assertThat(result.get(1).getSequenceNumber()).isOne();
        assertThat(result.get(2).getSequenceNumber()).isEqualTo(3);
        assertThat(result.get(3).getSequenceNumber()).isEqualTo(4);
        assertThat(result.get(4).getSequenceNumber()).isEqualTo(5);
        assertThat(result.get(5).getSequenceNumber()).isEqualTo(6);
        assertThat(result.get(6).getSequenceNumber()).isEqualTo(7);
    }

    @Override
    protected JdbcEventStorageEngine createEngine(UnaryOperator<JdbcEventStorageEngine.Builder> customization) {
        return createEngine(customization, H2EventTableFactory.INSTANCE);
    }

    private JdbcEventStorageEngine createEngine(UnaryOperator<JdbcEventStorageEngine.Builder> customization,
                                                EventTableFactory eventTableFactory) {
        JdbcEventStorageEngine.Builder engineBuilder =
                JdbcEventStorageEngine.builder()
                                      .eventSerializer(TestSerializer.xStreamSerializer())
                                      .persistenceExceptionResolver(defaultPersistenceExceptionResolver)
                                      .snapshotSerializer(TestSerializer.xStreamSerializer())
                                      .batchSize(100)
                                      .connectionProvider(dataSource::getConnection)
                                      .transactionManager(NoTransactionManager.INSTANCE);
        return doCreateTables(
                eventTableFactory,
                new JdbcEventStorageEngine(customization.apply(engineBuilder))
        );
    }

    private JdbcEventStorageEngine createTimestampEngine(EventTableFactory eventTableFactory) {
        JdbcEventStorageEngine.Builder builder =
                JdbcEventStorageEngine.builder()
                                      .eventSerializer(TestSerializer.xStreamSerializer())
                                      .snapshotSerializer(TestSerializer.xStreamSerializer())
                                      .connectionProvider(dataSource::getConnection)
                                      .transactionManager(NoTransactionManager.INSTANCE);

        JdbcEventStorageEngine result = new JdbcEventStorageEngine(builder) {
            @Override
            protected Object readTimeStamp(ResultSet resultSet, String columnName) throws SQLException {
                Timestamp ts = resultSet.getTimestamp(columnName);
                return ts.toInstant();
            }

            @Override
            protected void writeTimestamp(PreparedStatement preparedStatement, int position, Instant timestamp)
                    throws SQLException {
                preparedStatement.setTimestamp(position, new Timestamp(timestamp.toEpochMilli()));
            }
        };

        return doCreateTables(eventTableFactory, result);
    }

    private JdbcEventStorageEngine doCreateTables(EventTableFactory eventTableFactory, JdbcEventStorageEngine result) {
        try {
            Connection connection = dataSource.getConnection();
            connection.prepareStatement("DROP TABLE IF EXISTS DomainEventEntry").executeUpdate();
            connection.prepareStatement("DROP TABLE IF EXISTS SnapshotEventEntry").executeUpdate();
            result.createSchema(eventTableFactory);
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void buildWithoutEventSerializerThrowsSophonConfigurationException() {
        JdbcEventStorageEngine.Builder builderTestSubject =
                JdbcEventStorageEngine.builder()
                                      .snapshotSerializer(TestSerializer.xStreamSerializer())
                                      .connectionProvider(dataSource::getConnection)
                                      .transactionManager(NoTransactionManager.INSTANCE);

        assertThatThrownBy(builderTestSubject::build).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithoutSnapshotSerializerThrowsSophonConfigurationException() {
        JdbcEventStorageEngine.Builder builderTestSubject =
                JdbcEventStorageEngine.builder()
                                      .eventSerializer(TestSerializer.xStreamSerializer())
                                      .connectionProvider(dataSource::getConnection)
                                      .transactionManager(NoTransactionManager.INSTANCE);

        assertThatThrownBy(builderTestSubject::build).isInstanceOf(SophonConfigurationException.class);
    }
}
