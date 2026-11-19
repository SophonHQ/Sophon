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

import org.h2.jdbcx.JdbcDataSource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:sophontest;DB_CLOSE_DELAY=-1");
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
        assertThat(actualMessage.getPayload()).isEqualTo(expectedMessage.getPayload());
        assertThat(actualMessage.getPayloadType()).isEqualTo(expectedMessage.getPayloadType());
        assertThat(actualMessage.getMetaData()).isEqualTo(expectedMessage.getMetaData());
        assertThat(actualMessage.getIdentifier()).isEqualTo(expectedMessage.getIdentifier());
        assertThat(actual.cause()).isEqualTo(expected.cause());
        assertThat(actual.enqueuedAt()).isEqualTo(formatExpected(expected.enqueuedAt()));
        assertThat(actual.lastTouched()).isEqualTo(formatExpected(expected.lastTouched()));
        assertThat(actual.diagnostics()).isEqualTo(expected.diagnostics());
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
        assertThatThrownBy(() -> jdbcDeadLetterQueue.evict(testLetter))
                .isInstanceOf(WrongDeadLetterTypeException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullProcessingGroupThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.processingGroup(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithEmptyProcessingGroupThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.processingGroup("")).isInstanceOf(SophonConfigurationException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullConnectionProviderThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.connectionProvider(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullTransactionManagerThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.transactionManager(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullSchemaThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.schema(null)).isInstanceOf(SophonConfigurationException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullStatementFactoryThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.statementFactory(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullConverterThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.converter(null)).isInstanceOf(SophonConfigurationException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullGenericSerializerThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.genericSerializer(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void buildWithNullEventSerializerThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.eventSerializer(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithZeroMaxSequencesThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.maxSequences(0)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNegativeMaxSequencesThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.maxSequences(-1)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithZeroMaxSequenceSizeThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.maxSequenceSize(0)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNegativeMaxSequenceSizeThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.maxSequenceSize(-1)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNullClaimDurationThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.claimDuration(null)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithZeroPageSizeThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.pageSize(0)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNegativePageSizeThrowsSophonConfigurationException() {
        JdbcSequencedDeadLetterQueue.Builder<EventMessage<?>> testBuilder = JdbcSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> testBuilder.pageSize(-1)).isInstanceOf(SophonConfigurationException.class);
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

        assertThatThrownBy(testBuilder::build).isInstanceOf(SophonConfigurationException.class);
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

        assertThatThrownBy(testBuilder::build).isInstanceOf(SophonConfigurationException.class);
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

        assertThatThrownBy(testBuilder::build).isInstanceOf(SophonConfigurationException.class);
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

        assertThatThrownBy(testBuilder::build).isInstanceOf(SophonConfigurationException.class);
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

        assertThatThrownBy(testBuilder::build).isInstanceOf(SophonConfigurationException.class);
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

        assertThatThrownBy(testBuilder::build).isInstanceOf(SophonConfigurationException.class);
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

        assertThatThrownBy(testBuilder::build).isInstanceOf(SophonConfigurationException.class);
    }
}
