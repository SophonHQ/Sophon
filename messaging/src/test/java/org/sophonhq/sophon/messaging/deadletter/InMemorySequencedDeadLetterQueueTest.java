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

package org.sophonhq.sophon.messaging.deadletter;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.messaging.MetaData;

import java.time.Clock;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link InMemorySequencedDeadLetterQueue}.
 *
 * @author Steven van Beelen
 */
class InMemorySequencedDeadLetterQueueTest extends SequencedDeadLetterQueueTest<EventMessage<?>> {

    private static final int MAX_SEQUENCES_AND_SEQUENCE_SIZE = 128;

    @Override
    protected SequencedDeadLetterQueue<EventMessage<?>> buildTestSubject() {
        return InMemorySequencedDeadLetterQueue.<EventMessage<?>>builder()
                                               .maxSequences(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                               .maxSequenceSize(MAX_SEQUENCES_AND_SEQUENCE_SIZE)
                                               .build();
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
    protected DeadLetter<EventMessage<?>> generateRequeuedLetter(DeadLetter<EventMessage<?>> original,
                                                                 Instant lastTouched,
                                                                 Throwable requeueCause,
                                                                 MetaData diagnostics) {
        setAndGetTime(lastTouched);
        return original.withCause(requeueCause)
                       .withDiagnostics(diagnostics)
                       .markTouched();
    }

    @Override
    protected void setClock(Clock clock) {
        GenericDeadLetter.clock = clock;
    }

    @Test
    void buildDefaultQueue() {
        assertDoesNotThrow(() -> InMemorySequencedDeadLetterQueue.defaultQueue());
    }

    @Test
    void buildWithNegativeMaxSequencesThrowsSophonConfigurationException() {
        InMemorySequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject =
                InMemorySequencedDeadLetterQueue.builder();

        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.maxSequences(-1));
    }

    @Test
    void buildWithZeroMaxSequencesThrowsSophonConfigurationException() {
        InMemorySequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject =
                InMemorySequencedDeadLetterQueue.builder();

        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.maxSequences(0));
    }

    @Test
    void buildWithNegativeMaxSequenceSizeThrowsSophonConfigurationException() {
        InMemorySequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject =
                InMemorySequencedDeadLetterQueue.builder();

        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(-1));
    }

    @Test
    void buildWithZeroMaxSequenceSizeThrowsSophonConfigurationException() {
        InMemorySequencedDeadLetterQueue.Builder<EventMessage<?>> builderTestSubject =
                InMemorySequencedDeadLetterQueue.builder();

        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.maxSequenceSize(0));
    }

    @Test
    void processDoesNotFailWhileAnotherThreadEnqueuesNewSequences() {
        int sequences = 10_000;
        var queue = InMemorySequencedDeadLetterQueue.<EventMessage<?>>builder().maxSequences(sequences).build();

        CompletableFuture<Void> enqueuing = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < sequences; i++) {
                String sequenceId = "sequence-" + i;
                queue.enqueue(sequenceId, new GenericDeadLetter<>(sequenceId, generateEvent()));
            }
        });

        while (!enqueuing.isDone()) {
            assertDoesNotThrow(() -> queue.process(letter -> false, letter -> Decisions.evict()));
        }
        assertDoesNotThrow(enqueuing::join);
    }

    @Test
    void processReturnsFalseWhenAnotherProcessTakesTheOnlyMatchingSequence() {
        var queue = InMemorySequencedDeadLetterQueue.<EventMessage<?>>defaultQueue();
        queue.enqueue("sequence", new GenericDeadLetter<>("sequence", generateEvent(), generateThrowable()));
        CompletableFuture<Void> taken = new CompletableFuture<>();
        CompletableFuture<Void> release = new CompletableFuture<>();

        boolean result;
        try {
            result = queue.process(letter -> {
                CompletableFuture.runAsync(() -> queue.process(l -> true, l -> {
                    taken.complete(null);
                    release.join();
                    return Decisions.evict();
                }));
                taken.orTimeout(5, TimeUnit.SECONDS).join();
                return true;
            }, letter -> Decisions.evict());
        } finally {
            release.complete(null);
        }

        assertFalse(result);
    }

    @Test
    void sequenceSizeDoesNotFailWhenAnotherThreadRemovesTheSequence() {
        assertDoesNotFailWhenTheSequenceIsRemovedBeforeAnyRead((queue, letter) -> queue.sequenceSize("sequence"));
    }

    @Test
    void deadLetterSequenceDoesNotFailWhenAnotherThreadRemovesTheSequence() {
        assertDoesNotFailWhenTheSequenceIsRemovedBeforeAnyRead((queue, letter) -> queue.deadLetterSequence("sequence"));
    }

    @Test
    void clearDoesNotFailWhenAnotherThreadRemovesTheSequence() {
        assertDoesNotFailWhenTheSequenceIsRemovedBeforeAnyRead((queue, letter) -> queue.clear());
    }

    @Test
    void evictDoesNotFailWhenAnotherThreadRemovesTheSequence() {
        assertDoesNotFailWhenTheSequenceIsRemovedBeforeAnyRead(InMemorySequencedDeadLetterQueue::evict);
    }

    @Test
    void requeueKeepsTheLetterWhenAnotherThreadRemovesTheSequence() {
        assertDoesNotFailWhenTheSequenceIsRemovedBeforeAnyRead((queue, letter) -> {
            queue.requeue(letter, l -> l);
            assertEquals(1, queue.size());
        });
    }

    @Test
    void processDoesNotFailWhenAnotherThreadRemovesTheSequence() {
        assertDoesNotFailWhenTheSequenceIsRemovedBeforeAnyRead(
                (queue, letter) -> queue.process(l -> true, l -> Decisions.evict())
        );
    }
    
    private void assertDoesNotFailWhenTheSequenceIsRemovedBeforeAnyRead(
            BiConsumer<InMemorySequencedDeadLetterQueue<EventMessage<?>>, DeadLetter<EventMessage<?>>> action
    ) {
        for (int read = 1; ; read++) {
            var deadLetters = new RemovingMap<Deque<DeadLetter<? extends EventMessage<?>>>>();
            var queue = new InMemorySequencedDeadLetterQueue<>(InMemorySequencedDeadLetterQueue.builder(), deadLetters);
            DeadLetter<EventMessage<?>> letter = new GenericDeadLetter<>("sequence", generateEvent());
            queue.enqueue("sequence", letter);
            deadLetters.removeBeforeRead(read);

            int removedBeforeRead = read;
            assertDoesNotThrow(() -> action.accept(queue, letter),
                               () -> "The sequence was removed before read " + removedBeforeRead);
            if (!deadLetters.removed) {
                assertTrue(read > 1, "The action does not read the sequence");
                return;
            }
        }
    }

    private static class RemovingMap<V> extends ConcurrentHashMap<String, V> {

        private int readsLeft;
        private boolean removed;

        void removeBeforeRead(int read) {
            readsLeft = read;
        }

        private void beforeRead(Object key) {
            if (--readsLeft == 0) {
                super.remove(key);
                removed = true;
            }
        }

        @Override
        public V get(Object key) {
            beforeRead(key);
            return super.get(key);
        }

        @Override
        public V remove(Object key) {
            beforeRead(key);
            return super.remove(key);
        }

        @Override
        public V computeIfAbsent(String key, Function<? super String, ? extends V> mappingFunction) {
            beforeRead(key);
            return super.computeIfAbsent(key, mappingFunction);
        }
    }
}
