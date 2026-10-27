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

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.*;
import org.sophonhq.sophon.common.IdentifierFactory;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.common.jdbc.ConnectionProvider;
import org.sophonhq.sophon.common.transaction.NoTransactionManager;
import org.sophonhq.sophon.common.transaction.Transaction;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.messaging.deadletter.DeadLetter;
import org.sophonhq.sophon.messaging.deadletter.GenericDeadLetter;
import org.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueue;
import org.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueueTest;
import org.sophonhq.sophon.messaging.deadletter.WrongDeadLetterTypeException;
import org.sophonhq.sophon.serialization.TestSerializer;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.common.DateTimeUtils.formatInstant;
import static org.sophonhq.sophon.common.DateTimeUtils.parseInstant;
import static org.sophonhq.sophon.common.jdbc.JdbcUtils.closeQuietly;

/**
 * Implementation of the {@link SequencedDeadLetterQueueTest}, validating the {@link JdbcSequencedDeadLetterQueue}.
 *
 * @author Steven van Beelen
 */
class JdbcSequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage<?>> {

    private static final int MAX_SEQUENCES_AND_SEQUENCE_SIZE = 64;
    private static final String TEST_PROCESSING_GROUP = "some-processing-group";

    private DataSource dataSource;
    private TransactionManager transactionManager;
    private JdbcSequencedDeadLetterQueue<EventMessage<?>> jdbcDeadLetterQueue;

    private final DeadLetterSchema schema = DeadLetterSchema.defaultSchema();

    @Override
    protected SequencedDeadLetterQueue<EventMessage<?>> buildTestSubject() {
        dataSource = dataSource();
        transactionManager = transactionManager(dataSource);
        jdbcDeadLetterQueue = JdbcSequencedDeadLetterQueue.builder()
                                                          .processingGroup(TEST_PROCESSING_GROUP)
                                                          .maxSequences(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                                          .maxSequenceSize(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                                          .connectionProvider(dataSource::getConnection)
                                                          .schema(schema)
                                                          .transactionManager(transactionManager)
                                                          .genericSerializer(TestSerializer.JACKSON.getSerializer())
                                                          .eventSerializer(TestSerializer.JACKSON.getSerializer())
                                                          .build();
        return jdbcDeadLetterQueue;
    }

    private DataSource dataSource() {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl("jdbc:hsqldb:mem:sophontest");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private TransactionManager transactionManager(DataSource dataSource) {
        PlatformTransactionManager platformTransactionManager = new DataSourceTransactionManager(dataSource);
        return () -> {
            TransactionStatus transaction =
                    platformTransactionManager.getTransaction(new DefaultTransactionDefinition());
            return new Transaction() {
                @Override
                public void commit() {
                    platformTransactionManager.commit(transaction);
                }

                @Override
                public void rollback() {
                    platformTransactionManager.rollback(transaction);
                }
            };
        };
    }

    @BeforeEach
    void setUpJdbc() {
        transactionManager.executeInTransaction(() -> {
            // Clear current DLQ
            Connection connection = null;
            try {
                connection = dataSource.getConnection();
                //noinspection SqlDialectInspection,SqlNoDataSourceInspection
                connection.prepareStatement("DROP TABLE IF EXISTS " + schema.deadLetterTable())
                          .executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Enable to retrieve a Connection to drop the dead-letter queue", e);
            } finally {
                closeQuietly(connection);
            }
            // Construct new DLQ
            jdbcDeadLetterQueue.createSchema(new GenericDeadLetterTableFactory());
        });
    }

    @Override
    protected long maxSequences() {
        return MAX_SEQUENCES_AND_SEQUENCE_SIZE;
    }

    @Override
    protected long maxSequenceSize() {
        return MAX_SEQUENCES_AND_SEQUENCE_SIZE;
    }

    @Override
    public DeadLetter<EventMessage<?>> generateInitialLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent(), generateThrowable());
    }

    @Override
    protected DeadLetter<EventMessage<?>> generateFollowUpLetter() {
        return new GenericDeadLetter<>("sequenceIdentifier", generateEvent());
    }

    @Override
    protected DeadLetter<EventMessage<?>> mapToQueueImplementation(DeadLetter<EventMessage<?>> deadLetter) {
        if (deadLetter instanceof org.sophonhq.sophon.eventhandling.deadletter.jdbc.JdbcDeadLetter) {
            return deadLetter;
        }
        if (deadLetter instanceof GenericDeadLetter) {
            return new JdbcDeadLetter<>(
                    IdentifierFactory.getInstance().generateIdentifier(),
                    0L,
                    ((GenericDeadLetter<EventMessage<?>>) deadLetter).getSequenceIdentifier().toString(),
                    deadLetter.enqueuedAt(),
                    deadLetter.lastTouched(),
                    deadLetter.cause().orElse(null),
                    deadLetter.diagnostics(),
                    deadLetter.message()
            );
        }
        throw new IllegalArgumentException("Can not map dead letter of type " + deadLetter.getClass().getName());
    }

    @Override
    protected void setClock(Clock clock) {
        GenericDeadLetter.clock = clock;
    }

    @Override
    protected void assertLetter(DeadLetter<? extends EventMessage<?>> expected,
                                DeadLetter<? extends EventMessage<?>> actual) {
        EventMessage<?> expectedMessage = expected.message();
        EventMessage<?> actualMessage = actual.message();
        assertEquals(expectedMessage.getPayload(), actualMessage.getPayload());
        assertEquals(expectedMessage.getPayloadType(), actualMessage.getPayloadType());
        assertEquals(expectedMessage.getMetaData(), actualMessage.getMetaData());
        assertEquals(expectedMessage.getIdentifier(), actualMessage.getIdentifier());
        assertEquals(expected.cause(), actual.cause());
        assertEquals(formatExpected(expected.enqueuedAt()), actual.enqueuedAt());
        assertEquals(formatExpected(expected.lastTouched()), actual.lastTouched());
        assertEquals(expected.diagnostics(), actual.diagnostics());
    }

    /**
     * Format the expected {@link Instant} to align with the precision as dictated by the
     * {@link org.sophonhq.sophon.common.DateTimeUtils}. Required as the actual {@code Instants} underwent formatting by
     * the {@link JdbcSequencedDeadLetterQueue} as well, whereas the {@code expected} value did not.
     *
     * @param expected The {@link Instant} to format according to the {@link org.sophonhq.sophon.common.DateTimeUtils}.
     * @return A formatted {@link Instant} according to the {@link org.sophonhq.sophon.common.DateTimeUtils}.
     */
    private static Instant formatExpected(Instant expected) {
        return parseInstant(formatInstant(expected));
    }

    @Test
    void invokingEvictWithNonJdbcDeadLetterThrowsWrongDeadLetterTypeException() {
        DeadLetter<EventMessage<?>> testLetter = generateInitialLetter();
        assertThrows(WrongDeadLetterTypeException.class, () -> jdbcDeadLetterQueue.evict(testLetter));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullProcessingGroupThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.processingGroup(null));
    }

    @Test
    void buildWithEmptyProcessingGroupThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.processingGroup(""));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullConnectionProviderThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.connectionProvider(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullTransactionManagerThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.transactionManager(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullSchemaThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.schema(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullStatementFactoryThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.statementFactory(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullConverterThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.converter(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullGenericSerializerThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.genericSerializer(null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullEventSerializerThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.eventSerializer(null));
    }

    @Test
    void buildWithZeroMaxSequencesThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.maxSequences(0));
    }

    @Test
    void buildWithNegativeMaxSequencesThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.maxSequences(-1));
    }

    @Test
    void buildWithZeroMaxSequenceSizeThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.maxSequenceSize(0));
    }

    @Test
    void buildWithNegativeMaxSequenceSizeThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.maxSequenceSize(-1));
    }

    @Test
    void buildWithNullClaimDurationThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.claimDuration(null));
    }

    @Test
    void buildWithZeroPageSizeThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.pageSize(0));
    }

    @Test
    void buildWithNegativePageSizeThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> testBuilder.pageSize(-1));
    }

    @Test
    void buildWithoutProcessingGroupThrowsSophonConfigurationException() {
        @SuppressWarnings("unchecked")
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .connectionProvider(mock(ConnectionProvider.class))
                                            .transactionManager(NoTransactionManager.instance())
                                            .statementFactory(mock(DeadLetterStatementFactory.class))
                                            .converter(mock(DeadLetterJdbcConverter.class));

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }

    @Test
    void buildWithoutConnectionProviderThrowsSophonConfigurationException() {
        @SuppressWarnings("unchecked")
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .transactionManager(NoTransactionManager.instance())
                                            .statementFactory(mock(DeadLetterStatementFactory.class))
                                            .converter(mock(DeadLetterJdbcConverter.class));

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }

    @Test
    void buildWithoutTransactionManagerThrowsSophonConfigurationException() {
        @SuppressWarnings("unchecked")
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .connectionProvider(mock(ConnectionProvider.class))
                                            .statementFactory(mock(DeadLetterStatementFactory.class))
                                            .converter(mock(DeadLetterJdbcConverter.class));

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }

    @Test
    void buildWithoutStatementFactoryAndGenericSerializersThrowsSophonConfigurationException() {
        @SuppressWarnings("unchecked")
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .connectionProvider(mock(ConnectionProvider.class))
                                            .converter(mock(DeadLetterJdbcConverter.class))
                                            .eventSerializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }

    @Test
    void buildWithoutStatementFactoryAndEventSerializersThrowsSophonConfigurationException() {
        @SuppressWarnings("unchecked")
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .connectionProvider(mock(ConnectionProvider.class))
                                            .converter(mock(DeadLetterJdbcConverter.class))
                                            .genericSerializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }

    @Test
    void buildWithoutConverterAndGenericSerializersThrowsSophonConfigurationException() {
        @SuppressWarnings("unchecked")
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .connectionProvider(mock(ConnectionProvider.class))
                                            .statementFactory(mock(DeadLetterStatementFactory.class))
                                            .eventSerializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }

    @Test
    void buildWithoutConverterAndEventSerializersThrowsSophonConfigurationException() {
        @SuppressWarnings("unchecked")
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder =
                JdbcSequencedDeadLetterQueue.builder()
                                            .processingGroup(TEST_PROCESSING_GROUP)
                                            .connectionProvider(mock(ConnectionProvider.class))
                                            .statementFactory(mock(DeadLetterStatementFactory.class))
                                            .genericSerializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, testBuilder::build);
    }
}
