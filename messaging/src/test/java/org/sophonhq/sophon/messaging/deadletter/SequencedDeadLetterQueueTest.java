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

package org.sophonhq.sophon.messaging.deadletter;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.MetaData;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abstract class providing a generic test suite every {@link SequencedDeadLetterQueue} implementation should comply
 * with.
 *
 * @param <M> The {@link DeadLetter} implementation enqueued by this test class.
 * @author Steven van Beelen
 */
public abstract class SequencedDeadLetterQueueTest<M extends Message<?>> {

    private SequencedDeadLetterQueue<M> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = buildTestSubject();
    }

    /**
     * Constructs the {@link SequencedDeadLetterQueue} implementation under test.
     *
     * @return A {@link SequencedDeadLetterQueue} implementation under test.
     */
    protected abstract SequencedDeadLetterQueue<M> buildTestSubject();

    /**
     * Return the configured maximum amount of sequences for the {@link #buildTestSubject() test subject}.
     *
     * @return The configured maximum amount of sequences for the {@link #buildTestSubject() test subject}.
     */
    protected abstract long maxSequences();

    /**
     * Return the configured maximum size of a sequence for the {@link #buildTestSubject() test subject}.
     *
     * @return The configured maximum size of a sequence for the {@link #buildTestSubject() test subject}.
     */
    protected abstract long maxSequenceSize();

    @Test
    void enqueueAddsDeadLetter() {
        Object testId = generateId();
        DeadLetter<? extends M> testLetter = generateInitialLetter();

        testSubject.enqueue(testId, testLetter);

        assertThat(testSubject.contains(testId)).isTrue();
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        assertLetter(testLetter, resultLetters.next());
        assertThat(resultLetters).isExhausted();
    }

    @Test
    void enqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequencesIsReached() {
        long maxSequences = this.maxSequences();
        assertThat(maxSequences).isGreaterThan(0);

        for (int i = 0; i < maxSequences; i++) {
            testSubject.enqueue(generateId(), generateInitialLetter());
        }

        Object oneSequenceToMany = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        assertThatThrownBy(() -> testSubject.enqueue(oneSequenceToMany, testLetter))
                .isInstanceOf(DeadLetterQueueOverflowException.class);
    }

    @Test
    void enqueueThrowsDeadLetterQueueOverflowExceptionWhenMaxSequenceSizeIsReached() {
        Object testId = generateId();

        long maxSequenceSize = this.maxSequenceSize();
        assertThat(maxSequenceSize).isGreaterThan(0);

        for (int i = 0; i < maxSequenceSize; i++) {
            testSubject.enqueue(testId, generateInitialLetter());
        }

        DeadLetter<M> oneLetterToMany = generateInitialLetter();
        assertThatThrownBy(() -> testSubject.enqueue(testId, oneLetterToMany))
                .isInstanceOf(DeadLetterQueueOverflowException.class);
    }

    @Test
    void enqueueIfPresentThrowsDeadLetterQueueOverflowExceptionForFullQueue() {
        Object testId = generateId();

        long maxSequenceSize = this.maxSequenceSize();
        assertThat(maxSequenceSize).isGreaterThan(0);

        for (int i = 0; i < maxSequenceSize; i++) {
            testSubject.enqueue(testId, generateInitialLetter());
        }

        assertThatThrownBy(() -> testSubject.enqueueIfPresent(testId, this::generateFollowUpLetter))
                .isInstanceOf(DeadLetterQueueOverflowException.class);
    }

    @Test
    void enqueueIfPresentDoesNotEnqueueForEmptyQueue() {
        Object testId = generateId();

        boolean result = testSubject.enqueueIfPresent(testId, this::generateFollowUpLetter);

        assertThat(result).isFalse();
        assertThat(testSubject.contains(testId)).isFalse();
    }

    @Test
    void enqueueIfPresentDoesNotEnqueueForNonExistentSequenceIdentifier() {
        Object testFirstId = generateId();

        testSubject.enqueue(testFirstId, generateInitialLetter());

        Object testSecondId = generateId();

        boolean result = testSubject.enqueueIfPresent(testSecondId, this::generateFollowUpLetter);

        assertThat(result).isFalse();
        assertThat(testSubject.contains(testFirstId)).isTrue();
        assertThat(testSubject.contains(testSecondId)).isFalse();
    }

    @Test
    void enqueueIfPresentEnqueuesForExistingSequenceIdentifier() {
        Object testId = generateId();
        DeadLetter<M> testFirstLetter = generateInitialLetter();
        DeadLetter<M> testSecondLetter = generateFollowUpLetter();

        testSubject.enqueue(testId, testFirstLetter);
        testSubject.enqueueIfPresent(testId, () -> testSecondLetter);

        assertThat(testSubject.contains(testId)).isTrue();
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        assertLetter(testFirstLetter, resultLetters.next());
        assertThat(resultLetters).hasNext();
        assertLetter(testSecondLetter, resultLetters.next());
        assertThat(resultLetters).isExhausted();
    }

    @Test
    void evictDoesNotChangeTheQueueForNonExistentSequenceIdentifier() {
        Object testId = generateId();
        DeadLetter<? extends M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        assertThat(testSubject.contains(testId)).isTrue();
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        assertLetter(testLetter, resultLetters.next());
        assertThat(resultLetters).isExhausted();

        testSubject.evict(mapToQueueImplementation(generateInitialLetter()));

        assertThat(testSubject.contains(testId)).isTrue();
        resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        assertLetter(testLetter, resultLetters.next());
        assertThat(resultLetters).isExhausted();
    }

    @Test
    void evictDoesNotChangeTheQueueForNonExistentLetterIdentifier() {
        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        assertThat(testSubject.contains(testId)).isTrue();
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        assertLetter(testLetter, resultLetters.next());
        assertThat(resultLetters).isExhausted();

        testSubject.evict(mapToQueueImplementation(generateInitialLetter()));

        assertThat(testSubject.contains(testId)).isTrue();
        resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        assertLetter(testLetter, resultLetters.next());
        assertThat(resultLetters).isExhausted();
    }

    @Test
    void evictRemovesLetterFromQueue() {
        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();

        testSubject.enqueue(testId, testLetter);

        assertThat(testSubject.contains(testId)).isTrue();
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        DeadLetter<? extends M> resultLetter = resultLetters.next();
        assertLetter(testLetter, resultLetter);
        assertThat(resultLetters).isExhausted();

        testSubject.evict(resultLetter);

        assertThat(testSubject.contains(testId)).isFalse();
        assertThat(testSubject.deadLetters().iterator()).isExhausted();
    }

    @Test
    void requeueThrowsNoSuchDeadLetterExceptionForNonExistentSequenceIdentifier() {
        DeadLetter<M> testLetter = generateInitialLetter();

        assertThatThrownBy(() -> testSubject.requeue(mapToQueueImplementation(testLetter), l -> l))
                .isInstanceOf(NoSuchDeadLetterException.class);
    }

    @Test
    void requeueThrowsNoSuchDeadLetterExceptionForNonExistentLetterIdentifier() {
        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        DeadLetter<M> otherTestLetter = generateInitialLetter();

        testSubject.enqueue(testId, testLetter);

        assertThatThrownBy(() -> testSubject.requeue(mapToQueueImplementation(otherTestLetter), l -> l))
                .isInstanceOf(NoSuchDeadLetterException.class);
    }

    @Test
    void requeueReentersLetterToQueueWithUpdatedLastTouchedAndCause() {
        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        Throwable testCause = generateThrowable();

        testSubject.enqueue(testId, testLetter);

        assertThat(testSubject.contains(testId)).isTrue();
        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        DeadLetter<? extends M> resultLetter = resultLetters.next();
        assertLetter(testLetter, resultLetter);
        assertThat(resultLetters).isExhausted();

        DeadLetter<M> expectedLetter = generateRequeuedLetter(testLetter, testCause);

        testSubject.requeue(resultLetter, l -> l.withCause(testCause));

        assertThat(testSubject.contains(testId)).isTrue();
        resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        assertLetter(expectedLetter, resultLetters.next());
        assertThat(resultLetters).isExhausted();
    }

    @Test
    void containsReturnsTrueForContainedLetter() {
        Object testId = generateId();
        Object otherTestId = generateId();

        assertThat(testSubject.contains(testId)).isFalse();

        testSubject.enqueue(testId, generateInitialLetter());

        assertThat(testSubject.contains(testId)).isTrue();
        assertThat(testSubject.contains(otherTestId)).isFalse();
    }

    @Test
    void deadLetterSequenceReturnsEnqueuedLettersMatchingGivenSequenceIdentifier() {
        Object testId = generateId();
        DeadLetter<M> expected = generateInitialLetter();

        Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultIterator).isExhausted();

        testSubject.enqueue(testId, expected);

        resultIterator = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultIterator).hasNext();
        assertLetter(expected, resultIterator.next());
        assertThat(resultIterator).isExhausted();
    }

    @Test
    void deadLetterSequenceReturnsMatchingEnqueuedLettersInInsertOrder() {
        Object testId = generateId();

        Iterator<DeadLetter<? extends M>> resultIterator = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultIterator).isExhausted();

        LinkedHashMap<Integer, DeadLetter<M>> enqueuedLetters = new LinkedHashMap<>();
        DeadLetter<M> initial = generateInitialLetter();
        testSubject.enqueue(testId, initial);
        enqueuedLetters.put(0, initial);

        IntStream.range(1, Long.valueOf(maxSequenceSize()).intValue())
                 .forEach(i -> {
                     DeadLetter<M> followUp = generateFollowUpLetter();
                     testSubject.enqueue(testId, followUp);
                     enqueuedLetters.put(i, followUp);
                 });

        resultIterator = testSubject.deadLetterSequence(testId).iterator();

        for (Map.Entry<Integer, DeadLetter<M>> entry : enqueuedLetters.entrySet()) {
            assertThat(resultIterator).hasNext();
            assertLetter(entry.getValue(), resultIterator.next());
        }
    }

    @Test
    void deadLettersReturnsAllEnqueuedDeadLetters() {
        Object thisTestId = generateId();
        Object thatTestId = generateId();

        Iterator<Iterable<DeadLetter<? extends M>>> result = testSubject.deadLetters().iterator();
        assertThat(result).isExhausted();

        DeadLetter<? extends M> thisFirstExpected = generateInitialLetter();
        DeadLetter<? extends M> thisSecondExpected = generateInitialLetter();
        DeadLetter<? extends M> thatFirstExpected = generateInitialLetter();
        DeadLetter<? extends M> thatSecondExpected = generateInitialLetter();

        testSubject.enqueue(thisTestId, thisFirstExpected);
        testSubject.enqueue(thatTestId, thatFirstExpected);
        testSubject.enqueue(thisTestId, thisSecondExpected);
        testSubject.enqueue(thatTestId, thatSecondExpected);

        result = testSubject.deadLetters().iterator();

        int count = 0;
        while (result.hasNext()) {
            Iterable<DeadLetter<? extends M>> sequenceIterator = result.next();
            Iterator<DeadLetter<? extends M>> resultLetters = sequenceIterator.iterator();
            while (resultLetters.hasNext()) {
                count += 1;
                DeadLetter<? extends M> resultLetter = resultLetters.next();
                if (equals(thisFirstExpected).test(resultLetter)) {
                    assertLetter(thisFirstExpected, resultLetter);
                    assertThat(resultLetters).hasNext();
                    assertLetter(thisSecondExpected, resultLetters.next());
                    assertThat(resultLetters).isExhausted();
                } else {
                    assertLetter(thatFirstExpected, resultLetter);
                    assertThat(resultLetters).hasNext();
                    assertLetter(thatSecondExpected, resultLetters.next());
                    assertThat(resultLetters).isExhausted();
                }
            }
        }
        // Two sequences
        assertThat(count).isEqualTo(2);
    }

    @Test
    void isFullReturnsTrueAfterMaximumAmountOfSequencesIsReached() {
        assertThat(testSubject.isFull(generateId())).isFalse();

        long maxSequences = this.maxSequences();
        assertThat(maxSequences).isGreaterThan(0);

        for (int i = 0; i < maxSequences; i++) {
            testSubject.enqueue(generateId(), generateInitialLetter());
        }

        assertThat(testSubject.isFull(generateId())).isTrue();
    }

    @Test
    void isFullReturnsTrueAfterMaximumSequenceSizeIsReached() {
        Object testId = generateId();

        assertThat(testSubject.isFull(testId)).isFalse();

        long maxSequenceSize = this.maxSequenceSize();
        assertThat(maxSequenceSize).isGreaterThan(0);

        for (int i = 0; i < maxSequenceSize; i++) {
            testSubject.enqueue(testId, generateInitialLetter());
        }

        assertThat(testSubject.isFull(testId)).isTrue();
    }

    @Test
    void sizeReturnsOverallNumberOfContainedDeadLetters() {
        assertThat(testSubject.size()).isZero();

        Object testId = generateId();
        testSubject.enqueue(testId, generateInitialLetter());
        assertThat(testSubject.size()).isOne();
        testSubject.enqueue(testId, generateInitialLetter());
        assertThat(testSubject.size()).isEqualTo(2);

        // This generates a new sequence, increasing the size.
        testSubject.enqueue(generateId(), generateInitialLetter());
        assertThat(testSubject.size()).isEqualTo(3);
    }

    @Test
    void sequenceSizeForSequenceIdentifierReturnsTheNumberOfContainedLettersForGivenSequenceIdentifier() {
        assertThat(testSubject.sequenceSize("some-id")).isZero();

        Object testId = generateId();
        testSubject.enqueue(testId, generateInitialLetter());
        assertThat(testSubject.sequenceSize("some-id")).isZero();
        assertThat(testSubject.sequenceSize(testId)).isOne();
        testSubject.enqueue(testId, generateInitialLetter());
        assertThat(testSubject.sequenceSize(testId)).isEqualTo(2);

        // This generates a new sequence, so shouldn't increase the sequence size.
        testSubject.enqueue(generateId(), generateInitialLetter());
        assertThat(testSubject.sequenceSize("some-id")).isZero();
        assertThat(testSubject.sequenceSize(testId)).isEqualTo(2);
    }

    @Test
    void amountOfSequencesReturnsTheNumberOfUniqueSequences() {
        assertThat(testSubject.amountOfSequences()).isZero();

        testSubject.enqueue(generateId(), generateInitialLetter());
        assertThat(testSubject.amountOfSequences()).isOne();

        testSubject.enqueue(generateId(), generateInitialLetter());
        assertThat(testSubject.amountOfSequences()).isEqualTo(2);

        Object testId = generateId();
        testSubject.enqueue(testId, generateInitialLetter());
        testSubject.enqueue(testId, generateInitialLetter());
        testSubject.enqueue(testId, generateInitialLetter());
        assertThat(testSubject.amountOfSequences()).isEqualTo(3);
    }

    @Test
    void processInvocationReturnsFalseIfThereAreNoLetters() {
        AtomicBoolean taskInvoked = new AtomicBoolean(false);
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            taskInvoked.set(true);
            return Decisions.evict();
        };

        boolean result = testSubject.process(testTask);

        assertThat(result).isFalse();
        assertThat(taskInvoked.get()).isFalse();
    }

    @Test
    void processInvocationReturnsTrueAndEvictsTheLetter() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        boolean result = testSubject.process(testTask);
        assertThat(result).isTrue();
        assertLetter(testLetter, resultLetter.get());

        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).isExhausted();
    }

    @Test
    void processInvocationReturnsFalseAndRequeuesTheLetter() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Throwable testThrowable = generateThrowable();
        MetaData testDiagnostics = MetaData.with("custom-key", "custom-value");
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.requeue(testThrowable, l -> testDiagnostics);
        };

        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        Instant expectedLastTouched = setAndGetTime();
        DeadLetter<M> expectedRequeuedLetter =
                generateRequeuedLetter(testLetter, expectedLastTouched, testThrowable, testDiagnostics);

        boolean result = testSubject.process(testTask);
        assertThat(result).isFalse();
        assertLetter(testLetter, resultLetter.get());

        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        assertLetter(expectedRequeuedLetter, resultLetters.next());
        assertThat(resultLetters).isExhausted();
    }

    @Test
    void processInvocationInvokesProcessingTaskInLastTouchedOrderOfLetters() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        Object testThisId = generateId();
        DeadLetter<? extends M> testThisLetter = generateInitialLetter();
        testSubject.enqueue(testThisId, testThisLetter);

        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime(Instant.now().plus(5, ChronoUnit.SECONDS));
        Object testThatId = generateId();
        DeadLetter<? extends M> testThatLetter = generateInitialLetter();
        testSubject.enqueue(testThatId, testThatLetter);

        boolean result = testSubject.process(testTask);
        assertThat(result).isTrue();
        assertLetter(testThisLetter, resultLetter.get());

        result = testSubject.process(testTask);
        assertThat(result).isTrue();
        assertLetter(testThatLetter, resultLetter.get());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void processInvocationHandlesAllLettersInSequence() {
        AtomicReference<Deque<DeadLetter<? extends M>>> resultLetters = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            Deque<DeadLetter<? extends M>> sequence = resultLetters.get();
            if (sequence == null) {
                sequence = new LinkedList<>();
            }
            sequence.addLast(letter);
            resultLetters.set(sequence);
            return Decisions.evict();
        };

        Object testId = generateId();
        DeadLetter<? extends M> firstTestLetter = generateInitialLetter();
        testSubject.enqueue(testId, firstTestLetter);
        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime(Instant.now());
        DeadLetter<? extends M> secondTestLetter = generateFollowUpLetter();
        testSubject.enqueueIfPresent(testId, () -> secondTestLetter);
        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime(Instant.now());
        DeadLetter<? extends M> thirdTestLetter = generateFollowUpLetter();
        testSubject.enqueueIfPresent(testId, () -> thirdTestLetter);

        // Add another letter in a different sequence that we do not expect to receive.
        testSubject.enqueue(generateId(), generateInitialLetter());

        boolean result = testSubject.process(testTask);
        assertThat(result).isTrue();
        Deque<DeadLetter<? extends M>> resultSequence = resultLetters.get();

        assertLetter(firstTestLetter, resultSequence.pollFirst());
        assertLetter(secondTestLetter, resultSequence.pollFirst());
        assertLetter(thirdTestLetter, resultSequence.pollFirst());
    }

    @Test
    void processHandlesMassiveAmountOfLettersInSequence() {
        AtomicReference<Deque<DeadLetter<? extends M>>> resultLetters = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            Deque<DeadLetter<? extends M>> sequence = resultLetters.get();
            if (sequence == null) {
                sequence = new LinkedList<>();
            }
            sequence.addLast(letter);
            resultLetters.set(sequence);
            return Decisions.evict();
        };

        Object testId = generateId();
        DeadLetter<? extends M> firstTestLetter = generateInitialLetter();
        testSubject.enqueue(testId, firstTestLetter);

        List<DeadLetter<M>> expectedOrderList = new LinkedList<>();
        long loopSize = maxSequences() - 5;
        for (int i = 0; i < loopSize; i++) {
            DeadLetter<M> deadLetter = generateFollowUpLetter();
            expectedOrderList.add(deadLetter);
            testSubject.enqueueIfPresent(testId, () -> deadLetter);
        }

        boolean result = testSubject.process(testTask);
        assertThat(result).isTrue();
        Deque<DeadLetter<? extends M>> resultSequence = resultLetters.get();

        DeadLetter<? extends M> resultLetter = resultSequence.pollFirst();
        assertThat(resultLetter).isNotNull();
        assertLetter(firstTestLetter, resultLetter);

        for (int i = 0; i < loopSize; i++) {
            resultLetter = resultSequence.pollFirst();
            assertThat(resultLetter).isNotNull();
            assertLetter(expectedOrderList.get(i), resultLetter);
        }
    }

    /**
     * A "claimed sequence" in this case means that a process task for a given "sequence identifier" is still processing
     * the sequence. Furthermore, if it's the sole sequence, the processing task will not be invoked. This approach
     * ensure the events for a given sequence identifier are handled in the order they've been dead lettered (a.k.a., in
     * sequence).
     */
    @Test
    void processInvocationReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
        CountDownLatch isBlocking = new CountDownLatch(1);
        CountDownLatch hasProcessed = new CountDownLatch(1);
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        Function<DeadLetter<? extends M>, EnqueueDecision<M>> blockingTask = letter -> {
            try {
                isBlocking.countDown();
                //noinspection ResultOfMethodCallIgnored
                hasProcessed.await(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resultLetter.set(letter);
            return Decisions.evict();
        };
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> nonBlockingTask = letter -> {
            invoked.set(true);
            return Decisions.evict();
        };

        Object testId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);

        Thread blockingProcess = new Thread(() -> testSubject.process(blockingTask));
        blockingProcess.start();
        assertThat(isBlocking.await(100, TimeUnit.MILLISECONDS)).isTrue();

        boolean result = testSubject.process(nonBlockingTask);
        assertThat(result).isFalse();
        assertThat(invoked.get()).isFalse();

        hasProcessed.countDown();
        blockingProcess.join();
        assertLetter(testLetter, resultLetter.get());
    }

    @Test
    void processWithLetterPredicateReturnsFalseIfThereAreNoMatchingLetters() {
        AtomicBoolean releasedLetter = new AtomicBoolean(false);
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            releasedLetter.set(true);
            return Decisions.evict();
        };

        testSubject.enqueue(generateId(), generateInitialLetter());
        testSubject.enqueue(generateId(), generateInitialLetter());

        boolean result = testSubject.process(letter -> false, testTask);
        assertThat(result).isFalse();
        assertThat(releasedLetter.get()).isFalse();
    }

    @Test
    void processWithLetterPredicateInvokesProcessingTaskWithMatchingLetter() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        Object testThisId = generateId();
        DeadLetter<? extends M> testThisLetter = generateInitialLetter();
        testSubject.enqueue(testThisId, testThisLetter);

        Object testThatId = generateId();
        DeadLetter<? extends M> testThatLetter = generateInitialLetter();
        testSubject.enqueue(testThatId, testThatLetter);

        boolean result = testSubject.process(equals(testThisLetter), testTask);
        assertThat(result).isTrue();
        assertLetter(testThisLetter, resultLetter.get());

        result = testSubject.process(equals(testThatLetter), testTask);
        assertThat(result).isTrue();
        assertLetter(testThatLetter, resultLetter.get());
    }

    private Predicate<DeadLetter<? extends M>> equals(DeadLetter<? extends M> expected) {
        return actual -> expected.message().getIdentifier().equals(actual.message().getIdentifier());
    }

    @Test
    void processWithLetterPredicateReturnsTrueAndEvictsTheLetter() {
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.evict();
        };

        Object testId = generateId();
        Object nonMatchingId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(nonMatchingId, generateInitialLetter());

        boolean result = testSubject.process(letter -> letter.message().getPayload()
                                                             .equals(testLetter.message().getPayload()), testTask);
        assertThat(result).isTrue();
        assertLetter(testLetter, resultLetter.get());

        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).isExhausted();
        assertThat(testSubject.deadLetters().iterator()).hasNext();
    }

    @Test
    void processWithLetterPredicateReturnsFalseAndRequeuesTheLetter() {
        setAndGetTime();
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        Throwable testThrowable = generateThrowable();
        MetaData testDiagnostics = MetaData.with("custom-key", "custom-value");
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            resultLetter.set(letter);
            return Decisions.requeue(testThrowable, l -> testDiagnostics);
        };

        Object testId = generateId();
        Object nonMatchingId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(nonMatchingId, generateInitialLetter());

        Instant expectedLastTouched = setAndGetTime();
        DeadLetter<M> expectedRequeuedLetter =
                generateRequeuedLetter(testLetter, expectedLastTouched, testThrowable, testDiagnostics);

        boolean result = testSubject.process(equals(testLetter), testTask);
        assertThat(result).isFalse();
        assertLetter(testLetter, resultLetter.get());

        Iterator<DeadLetter<? extends M>> resultLetters = testSubject.deadLetterSequence(testId).iterator();
        assertThat(resultLetters).hasNext();
        assertLetter(expectedRequeuedLetter, resultLetters.next());
        assertThat(resultLetters).isExhausted();
        assertThat(testSubject.deadLetters().iterator()).hasNext();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void processWithLetterPredicateHandlesAllLettersInSequence() {
        setAndGetTime();
        AtomicReference<Deque<DeadLetter<? extends M>>> resultLetters = new AtomicReference<>();
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> testTask = letter -> {
            Deque<DeadLetter<? extends M>> sequence = resultLetters.get();
            if (sequence == null) {
                sequence = new LinkedList<>();
            }
            sequence.addLast(letter);
            resultLetters.set(sequence);
            return Decisions.evict();
        };

        Object testId = generateId();
        DeadLetter<? extends M> firstTestLetter = generateInitialLetter();
        testSubject.enqueue(testId, firstTestLetter);
        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime(Instant.now());
        DeadLetter<? extends M> secondTestLetter = generateFollowUpLetter();
        testSubject.enqueueIfPresent(testId, () -> secondTestLetter);
        // Move time to impose changes to enqueuedAt and lastTouched.
        setAndGetTime(Instant.now());
        DeadLetter<? extends M> thirdTestLetter = generateFollowUpLetter();
        testSubject.enqueueIfPresent(testId, () -> thirdTestLetter);

        // Add another letter in a different sequence that we do not expect to receive.
        testSubject.enqueue(generateId(), generateInitialLetter());

        boolean result = testSubject.process(equals(firstTestLetter), testTask);
        assertThat(result).isTrue();
        Deque<DeadLetter<? extends M>> resultSequence = resultLetters.get();

        assertLetter(firstTestLetter, resultSequence.pollFirst());
        assertLetter(secondTestLetter, resultSequence.pollFirst());
        assertLetter(thirdTestLetter, resultSequence.pollFirst());
    }

    /**
     * A "claimed sequence" in this case means that a process task for a given "sequence identifier" is still processing
     * the sequence. Furthermore, if it's the sole sequence, the processing task will not be invoked. This approach
     * ensure the events for a given sequence identifier are handled in the order they've been dead lettered (a.k.a., in
     * sequence).
     */
    @Test
    void processWithLetterPredicateReturnsFalseIfAllLetterSequencesAreClaimed() throws InterruptedException {
        CountDownLatch isBlocking = new CountDownLatch(1);
        CountDownLatch hasProcessed = new CountDownLatch(1);
        AtomicReference<DeadLetter<? extends M>> resultLetter = new AtomicReference<>();
        AtomicBoolean invoked = new AtomicBoolean(false);

        Function<DeadLetter<? extends M>, EnqueueDecision<M>> blockingTask = letter -> {
            try {
                isBlocking.countDown();
                //noinspection ResultOfMethodCallIgnored
                hasProcessed.await(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resultLetter.set(letter);
            return Decisions.evict();
        };
        Function<DeadLetter<? extends M>, EnqueueDecision<M>> nonBlockingTask = letter -> {
            invoked.set(true);
            return Decisions.evict();
        };

        Object testId = generateId();
        Object nonMatchingId = generateId();
        DeadLetter<M> testLetter = generateInitialLetter();
        testSubject.enqueue(testId, testLetter);
        // Add non-matching-id letter
        testSubject.enqueue(nonMatchingId, generateInitialLetter());

        Thread blockingProcess = new Thread(() -> testSubject.process(
                equals(testLetter),
                blockingTask
        ));
        blockingProcess.start();
        assertThat(isBlocking.await(100, TimeUnit.MILLISECONDS)).isTrue();

        boolean result = testSubject.process(equals(testLetter), nonBlockingTask);
        assertThat(result).isFalse();
        assertThat(invoked.get()).isFalse();

        hasProcessed.countDown();
        blockingProcess.join();
        assertLetter(testLetter, resultLetter.get());
    }

    @Test
    void clearInvocationRemovesAllEntries() {
        Object idOne = generateId();
        Object idTwo = generateId();
        Object idThree = generateId();

        assertThat(testSubject.contains(idOne)).isFalse();
        assertThat(testSubject.contains(idTwo)).isFalse();
        assertThat(testSubject.contains(idThree)).isFalse();

        testSubject.enqueue(idOne, generateInitialLetter());
        testSubject.enqueue(idTwo, generateInitialLetter());
        testSubject.enqueue(idThree, generateInitialLetter());

        assertThat(testSubject.contains(idOne)).isTrue();
        assertThat(testSubject.contains(idTwo)).isTrue();
        assertThat(testSubject.contains(idThree)).isTrue();

        testSubject.clear();

        assertThat(testSubject.contains(idOne)).isFalse();
        assertThat(testSubject.contains(idTwo)).isFalse();
        assertThat(testSubject.contains(idThree)).isFalse();
    }

    /**
     * Generate a unique {@link Object} based on {@link UUID#randomUUID()}.
     *
     * @return A unique {@link Object}, based on {@link UUID#randomUUID()}.
     */
    protected static Object generateId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a unique {@link EventMessage} to serves as the {@link DeadLetter#message()} contents.
     *
     * @return A unique {@link EventMessage} to serves as the {@link DeadLetter#message()} contents.
     */
    protected static EventMessage<String> generateEvent() {
        return GenericEventMessage.asEventMessage("Then this happened..." + UUID.randomUUID());
    }

    /**
     * Generate a unique {@link Throwable} by using {@link #generateId()} in the cause description.
     *
     * @return A unique {@link Throwable} by using {@link #generateId()} in the cause description.
     */
    protected static Throwable generateThrowable() {
        return new RuntimeException("Because..." + generateId());
    }

    /**
     * Generate an initial {@link DeadLetter} implementation expected by the test subject. This means the dead letter is
     * the first in a potential sequence of letters.
     *
     * @return A {@link DeadLetter} implementation expected by the test subject.
     */
    protected abstract DeadLetter<M> generateInitialLetter();

    /**
     * Generate a follow-up {@link DeadLetter} implementation expected by the test subject with the given
     * {@code sequenceIdentifier}. This means the dead letter is part of a sequence of letters.
     *
     * @return A follow-up {@link DeadLetter} implementation expected by the test subject with the given
     * {@code sequenceIdentifier}.
     */
    protected abstract DeadLetter<M> generateFollowUpLetter();

    /**
     * Generates a {@link DeadLetter} implementation specific to the {@link SequencedDeadLetterQueue} tested. Should use
     * the original properties set on the provided {@code deadLetter}.
     * <p>
     * By default, it keeps the original the way it is.
     *
     * @param deadLetter The dead letter to convert.
     * @return The converted dead letter.
     */
    protected DeadLetter<M> mapToQueueImplementation(DeadLetter<M> deadLetter) {
        return deadLetter;
    }

    /**
     * Generate a {@link DeadLetter} implementation expected by the test subject based on the given {@code original}
     * that's requeued. The result is expected to have an adjusted {@link DeadLetter#lastTouched()} and
     * {@link DeadLetter#cause()}. The latter should resemble the given {@code requeueCause}.
     *
     * @param original     The original {@link DeadLetter} to base the requeued dead letter on.
     * @param requeueCause The cause for requeueing the {@code original}.
     * @return A {@link DeadLetter} implementation expected by the test subject based on the given {@code original}
     * that's requeued.
     */
    protected DeadLetter<M> generateRequeuedLetter(DeadLetter<M> original, Throwable requeueCause) {
        // Move time to impose changes to lastTouched.
        Instant lastTouched = setAndGetTime();
        return generateRequeuedLetter(original, lastTouched, requeueCause, MetaData.emptyInstance());
    }

    /**
     * Generate a {@link DeadLetter} implementation expected by the test subject based on the given {@code original}
     * that's requeued. The result is expected to have an adjusted {@link DeadLetter#lastTouched()} and
     * {@link DeadLetter#cause()}. The former should resemble the given {@code lastTouched} and the latter the given
     * {@code requeueCause}.
     *
     * @param original     The original {@link DeadLetter} to base the requeued dead letter on.
     * @param lastTouched  The {@link DeadLetter#lastTouched()} of the generated {@link DeadLetter} implementation.
     * @param requeueCause The cause for requeueing the {@code original}.
     * @param diagnostics  The diagnostics {@link MetaData} added to the requeued letter for monitoring.
     * @return A {@link DeadLetter} implementation expected by the test subject based on the given {@code original}
     * that's requeued.
     */
    protected DeadLetter<M> generateRequeuedLetter(DeadLetter<M> original,
                                                   Instant lastTouched,
                                                   Throwable requeueCause,
                                                   MetaData diagnostics) {
        setAndGetTime(lastTouched);
        return original.withCause(requeueCause)
                       .withDiagnostics(diagnostics)
                       .markTouched();
    }

    /**
     * Set the current time for testing to {@link Instant#now()} and return this {@code Instant}.
     *
     * @return {@link Instant#now()}, the current time for the invoker of this method.
     */
    protected Instant setAndGetTime() {
        return setAndGetTime(Instant.now());
    }

    /**
     * Set the current time for testing to given {@code time} and return this {@code Instant}.
     *
     * @param time The time to test under.
     * @return The given {@code time}.
     */
    protected Instant setAndGetTime(Instant time) {
        setClock(Clock.fixed(time, ZoneId.systemDefault()));
        return time;
    }

    /**
     * Set the {@link Clock} used by this test. Use this to influence the {@link DeadLetter#enqueuedAt()} and
     * {@link DeadLetter#lastTouched()} times.
     *
     * @param clock The clock to use during testing.
     */
    protected abstract void setClock(Clock clock);

    /**
     * Assert whether the {@code expected} {@link DeadLetter} matches the {@code actual} {@code DeadLetter} of type
     * {@code D}.
     *
     * @param expected The expected format of the {@link DeadLetter}.
     * @param actual   The actual format of the {@link DeadLetter}.
     */
    protected void assertLetter(DeadLetter<? extends M> expected, DeadLetter<? extends M> actual) {
        assertThat(actual.message()).isEqualTo(expected.message());
        assertThat(actual.cause()).isEqualTo(expected.cause());
        assertThat(actual.enqueuedAt()).isEqualTo(expected.enqueuedAt());
        assertThat(actual.lastTouched()).isEqualTo(expected.lastTouched());
        assertThat(actual.diagnostics()).isEqualTo(expected.diagnostics());
    }
}
