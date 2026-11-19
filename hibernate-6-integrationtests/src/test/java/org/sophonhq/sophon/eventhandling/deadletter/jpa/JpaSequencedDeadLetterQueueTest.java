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

package org.sophonhq.sophon.eventhandling.deadletter.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.IdentifierFactory;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.common.jpa.EntityManagerProvider;
import org.sophonhq.sophon.common.jpa.SimpleEntityManagerProvider;
import org.sophonhq.sophon.common.transaction.NoOpTransactionManager;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.messaging.deadletter.DeadLetter;
import org.sophonhq.sophon.messaging.deadletter.GenericDeadLetter;
import org.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueue;
import org.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueueTest;
import org.sophonhq.sophon.messaging.deadletter.WrongDeadLetterTypeException;
import org.sophonhq.sophon.serialization.TestSerializer;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JpaSequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage<?>> {

    private static final int MAX_SEQUENCES_AND_SEQUENCE_SIZE = 64;

    private final TransactionManager transactionManager = spy(new NoOpTransactionManager());
    private final EntityManagerFactory emf = Persistence.createEntityManagerFactory("h6dlq");
    private final EntityManager entityManager = emf.createEntityManager();
    private EntityTransaction transaction;

    @BeforeEach
    public void setUpJpa() {
        transaction = entityManager.getTransaction();
        transaction.begin();
    }

    @AfterEach
    public void rollback() {
        transaction.rollback();
    }

    @Override
    protected void setClock(Clock clock) {
        GenericDeadLetter.clock = clock;
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
        if (deadLetter instanceof JpaDeadLetter) {
            return deadLetter;
        }
        if (deadLetter instanceof GenericDeadLetter) {
            return new JpaDeadLetter<>(
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
    protected DeadLetter<EventMessage<?>> generateRequeuedLetter(DeadLetter<EventMessage<?>> original,
                                                                 Instant lastTouched,
                                                                 Throwable requeueCause,
                                                                 MetaData diagnostics) {
        setAndGetTime(lastTouched);
        return original.withCause(requeueCause).withDiagnostics(diagnostics).markTouched();
    }

    @Override
    protected void assertLetter(DeadLetter<? extends EventMessage<?>> expected,
                                DeadLetter<? extends EventMessage<?>> actual) {

        assertThat(actual.message().getPayload()).isEqualTo(expected.message().getPayload());
        assertThat(actual.message().getPayloadType()).isEqualTo(expected.message().getPayloadType());
        assertThat(actual.message().getMetaData()).isEqualTo(expected.message().getMetaData());
        assertThat(actual.message().getIdentifier()).isEqualTo(expected.message().getIdentifier());
        assertThat(actual.cause()).isEqualTo(expected.cause());
        assertThat(actual.enqueuedAt()).isEqualTo(expected.enqueuedAt());
        assertThat(actual.lastTouched()).isEqualTo(expected.lastTouched());
        assertThat(actual.diagnostics()).isEqualTo(expected.diagnostics());
    }

    @Override
    public SequencedDeadLetterQueue<EventMessage<?>> buildTestSubject() {
        EntityManagerProvider entityManagerProvider = new SimpleEntityManagerProvider(entityManager);
        return JpaSequencedDeadLetterQueue
                .builder()
                .transactionManager(transactionManager)
                .entityManagerProvider(entityManagerProvider)
                .maxSequences(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                .maxSequenceSize(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                .processingGroup("my_processing_group")
                .eventSerializer(TestSerializer.JACKSON.getSerializer())
                .genericSerializer(TestSerializer.XSTREAM.getSerializer())
                .build();
    }

    @Test
    void buildWithNegativeMaxQueuesThrowsSophonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThatThrownBy(() -> builderTestSubject.maxSequences(-1))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithZeroMaxQueuesThrowsSophonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThatThrownBy(() -> builderTestSubject.maxSequences(0))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNegativeMaxQueueSizeThrowsSophonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThatThrownBy(() -> builderTestSubject.maxSequenceSize(-1))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithZeroMaxQueueSizeThrowsSophonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThatThrownBy(() -> builderTestSubject.maxSequenceSize(0))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void canNotSetNegativeQueryPageSize() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> builder.queryPageSize(-1)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void canNotSetZeroQueryPageSize() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> builder.queryPageSize(0)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void cannotRequeueGenericDeadLetter() {
        SequencedDeadLetterQueue<EventMessage<?>> queue = buildTestSubject();
        DeadLetter<EventMessage<?>> letter = generateInitialLetter();
        assertThatThrownBy(() -> queue.requeue(letter, d -> d)).isInstanceOf(WrongDeadLetterTypeException.class);
    }

    @Test
    void cannotEvictGenericDeadLetter() {
        SequencedDeadLetterQueue<EventMessage<?>> queue = buildTestSubject();
        DeadLetter<EventMessage<?>> letter = generateInitialLetter();
        assertThatThrownBy(() -> queue.evict(letter)).isInstanceOf(WrongDeadLetterTypeException.class);
    }

    @Test
    void canNotSetProcessingGroupToEmpty() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> builder.processingGroup("")).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void canNotSetProcessingGroupToNull() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThatThrownBy(() -> builder.processingGroup("")).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void canNotBuildWithoutProcessingGroup() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue
                .builder()
                .transactionManager(transactionManager)
                .entityManagerProvider(() -> entityManager)
                .serializer(TestSerializer.JACKSON.getSerializer());
        assertThatThrownBy(builder::build).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void canNotBuildWithoutTransactionManager() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue
                .builder()
                .processingGroup("my_processing_Group")
                .entityManagerProvider(() -> entityManager)
                .serializer(TestSerializer.JACKSON.getSerializer());

        assertThatThrownBy(builder::build).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void canNotBuildWithoutEntityManagerProvider() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue
                .builder()
                .processingGroup("my_processing_Group")
                .transactionManager(transactionManager)
                .serializer(TestSerializer.JACKSON.getSerializer());

        assertThatThrownBy(builder::build).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void canNotAddNullConverterWhileBuilding() {
        assertThatThrownBy(() -> JpaSequencedDeadLetterQueue.builder().addConverter(null))
                .isInstanceOf(SophonConfigurationException.class);
    }
}
