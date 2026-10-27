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

package io.github.sophonhq.sophon.eventhandling.deadletter.legacyjpa;

import io.github.sophonhq.sophon.common.IdentifierFactory;
import io.github.sophonhq.sophon.common.SophonConfigurationException;
import io.github.sophonhq.sophon.common.legacyjpa.EntityManagerProvider;
import io.github.sophonhq.sophon.common.legacyjpa.SimpleEntityManagerProvider;
import io.github.sophonhq.sophon.common.transaction.NoOpTransactionManager;
import io.github.sophonhq.sophon.common.transaction.TransactionManager;
import io.github.sophonhq.sophon.eventhandling.EventMessage;
import io.github.sophonhq.sophon.messaging.MetaData;
import io.github.sophonhq.sophon.messaging.deadletter.DeadLetter;
import io.github.sophonhq.sophon.messaging.deadletter.GenericDeadLetter;
import io.github.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueue;
import io.github.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueueTest;
import io.github.sophonhq.sophon.messaging.deadletter.WrongDeadLetterTypeException;
import io.github.sophonhq.sophon.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import java.time.Clock;
import java.time.Instant;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class JpaSequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage<?>> {

    private static final int MAX_SEQUENCES_AND_SEQUENCE_SIZE = 64;

    private final TransactionManager transactionManager = spy(new NoOpTransactionManager());
    private final EntityManagerFactory emf = Persistence.createEntityManagerFactory("dlq");
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
        if (deadLetter instanceof io.github.sophonhq.sophon.eventhandling.deadletter.jpa.JpaDeadLetter) {
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

        assertEquals(expected.message().getPayload(), actual.message().getPayload());
        assertEquals(expected.message().getPayloadType(), actual.message().getPayloadType());
        assertEquals(expected.message().getMetaData(), actual.message().getMetaData());
        assertEquals(expected.message().getIdentifier(), actual.message().getIdentifier());
        assertEquals(expected.cause(), actual.cause());
        assertEquals(expected.enqueuedAt(), actual.enqueuedAt());
        assertEquals(expected.lastTouched(), actual.lastTouched());
        assertEquals(expected.diagnostics(), actual.diagnostics());
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

        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.maxSequences(-1));
    }

    @Test
    void buildWithZeroMaxQueuesThrowsSophonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.maxSequences(0));
    }

    @Test
    void buildWithNegativeMaxQueueSizeThrowsSophonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(-1));
    }

    @Test
    void buildWithZeroMaxQueueSizeThrowsSophonConfigurationException() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();

        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(0));
    }

    @Test
    void canNotSetNegativeQueryPageSize() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> builder.queryPageSize(-1));
    }

    @Test
    void canNotSetZeroQueryPageSize() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> builder.queryPageSize(0));
    }

    @Test
    void cannotRequeueGenericDeadLetter() {
        SequencedDeadLetterQueue<EventMessage<?>> queue = buildTestSubject();
        DeadLetter<EventMessage<?>> letter = generateInitialLetter();
        assertThrows(WrongDeadLetterTypeException.class, () -> queue.requeue(letter, d -> d));
    }

    @Test
    void cannotEvictGenericDeadLetter() {
        SequencedDeadLetterQueue<EventMessage<?>> queue = buildTestSubject();
        DeadLetter<EventMessage<?>> letter = generateInitialLetter();
        assertThrows(WrongDeadLetterTypeException.class, () -> queue.evict(letter));
    }

    @Test
    void canNotSetProcessingGroupToEmpty() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> builder.processingGroup(""));
    }

    @Test
    void canNotSetProcessingGroupToNull() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> builder.processingGroup(""));
    }

    @Test
    void canNotBuildWithoutProcessingGroup() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue
                .builder()
                .transactionManager(transactionManager)
                .entityManagerProvider(() -> entityManager)
                .serializer(TestSerializer.JACKSON.getSerializer());
        assertThrows(SophonConfigurationException.class, builder::build);
    }

    @Test
    void canNotBuildWithoutTransactionManager() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue
                .builder()
                .processingGroup("my_processing_Group")
                .entityManagerProvider(() -> entityManager)
                .serializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, builder::build);
    }

    @Test
    void canNotBuildWithoutEntityManagerProvider() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builder = JpaSequencedDeadLetterQueue
                .builder()
                .processingGroup("my_processing_Group")
                .transactionManager(transactionManager)
                .serializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, builder::build);
    }

    @Test
    void canNotAddNullConverterWhileBuilding() {
        JpaSequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject = JpaSequencedDeadLetterQueue.builder();
        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.addConverter(null));
    }
}
