/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.sophonhq.sophon.eventhandling;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GapAwareTrackingTokenTest {

    @Test
    void gapAwareTokenConcurrency() throws InterruptedException {
        AtomicLong counter = new AtomicLong();
        AtomicReference<GapAwareTrackingToken> currentToken = new AtomicReference<>(GapAwareTrackingToken.newInstance(-1,
                emptySortedSet()));

        ExecutorService executorService = Executors.newCachedThreadPool();

        // we need more threads than available processors, for a high likelihood to trigger this concurrency issue
        int threadCount = Runtime.getRuntime().availableProcessors() + 1;
        Future<?>[] results = new Future<?>[threadCount];
        for (int i = 0; i < threadCount; i++) {
            results[i] = executorService.submit(() -> {
                long deadline = System.currentTimeMillis() + 1000;
                while (System.currentTimeMillis() < deadline) {
                    long next = counter.getAndIncrement();
                    currentToken.getAndUpdate(t -> t.advanceTo(next, Integer.MAX_VALUE));
                }
            });
        }
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS))
                .as("ExecutorService not stopped within expected reasonable time frame")
                .isTrue();

        for (Future<?> result : results) {
            assertThatNoException().isThrownBy(() -> result.get(1, TimeUnit.SECONDS));
        }

        assertThat(counter.get() > 0).as("The test did not seem to have generated any tokens").isTrue();
        assertThat(currentToken.get().getIndex()).isEqualTo(counter.get() - 1);
        assertThat(currentToken.get().getGaps()).isEqualTo(emptySortedSet());
    }

    @Test
    void whenAdvancingToPositionGapsAheadOfThatPositionAreNeverCleanedUp() {
        List<Long> gaps = asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
        GapAwareTrackingToken token = GapAwareTrackingToken.newInstance(100, gaps);

        GapAwareTrackingToken actual = token.advanceTo(2L, 0);
        assertThat(actual.hasGaps()).isTrue();
        assertThat(actual.getGaps()).containsExactlyElementsOf(Arrays.asList(3L, 4L, 5L, 6L, 7L, 8L, 9L));
        assertThat(actual.getIndex()).isEqualTo(100);
    }

    @Test
    void gapAwareTokenConcurrency_HighConcurrency() throws InterruptedException {
        long counter = 0;
        AtomicReference<GapAwareTrackingToken> currentToken = new AtomicReference<>(GapAwareTrackingToken.newInstance(-1,
                emptySortedSet()));

        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        Future<?>[] results = new Future<?>[1_000_000];
        for (int i = 0; i < 1_000_000; i++) {
            long next = counter++;
            results[i] = executorService.submit(() -> {
                currentToken.getAndUpdate(t -> t.advanceTo(next, Integer.MAX_VALUE));
            });
        }
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS))
                .as("ExecutorService not stopped within expected reasonable time frame")
                .isTrue();

        for (Future<?> result : results) {
            assertThatNoException().isThrownBy(() -> result.get(1, TimeUnit.SECONDS));
        }

        assertThat(counter > 0).as("The test did not seem to have generated any tokens").isTrue();
        assertThat(currentToken.get().getIndex()).isEqualTo(counter - 1);
        assertThat(currentToken.get().getGaps()).isEqualTo(emptySortedSet());
    }

    @Test
    void initializationWithNullGaps() {
        GapAwareTrackingToken token = new GapAwareTrackingToken(10, null);
        assertThat(token.getGaps()).isNotNull();
        assertThat(token.getGaps()).isEmpty();
    }

    @Test
    void advanceToWithoutGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(0L, Collections.emptyList());
        subject = subject.advanceTo(1L, 10);
        assertThat(subject.getIndex()).isOne();
        assertThat(subject.getGaps()).isEqualTo(emptySortedSet());
    }

    @Test
    void advanceToWithInitialGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(10L, asList(1L, 5L, 6L));
        subject = subject.advanceTo(5L, 10);
        assertThat(subject.getIndex()).isEqualTo(10L);
        assertThat(subject.getGaps()).isEqualTo(Stream.of(1L, 6L).collect(Collectors.toCollection(TreeSet::new)));
    }

    @Test
    void advanceToWithNewGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(10L, Collections.emptyList());
        subject = subject.advanceTo(13L, 10);
        assertThat(subject.getIndex()).isEqualTo(13L);
        assertThat(subject.getGaps()).isEqualTo(Stream.of(11L, 12L).collect(Collectors.toCollection(TreeSet::new)));
    }

    @Test
    void advanceToGapClearsOldGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, asList(1L, 5L, 12L));
        subject = subject.advanceTo(12L, 10);
        assertThat(subject.getIndex()).isEqualTo(15L);
        assertThat(subject.getGaps()).isEqualTo(Stream.of(5L).collect(Collectors.toCollection(TreeSet::new)));
    }

    @Test
    void advanceToOldGapClearsOldGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, asList(1L, 5L, 12L));
        subject = subject.advanceTo(1L, 10);
        assertThat(subject.getIndex()).isEqualTo(15L);
        assertThat(subject.getGaps()).isEqualTo(Stream.of(5L, 12L).collect(Collectors.toCollection(TreeSet::new)));
    }

    @Test
    void advanceToHigherSequenceClearsOldGaps() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, asList(1L, 5L, 12L));
        subject = subject.advanceTo(16L, 10);
        assertThat(subject.getIndex()).isEqualTo(16L);
        assertThat(subject.getGaps()).isEqualTo(Stream.of(12L).collect(Collectors.toCollection(TreeSet::new)));
    }

    @Test
    void advanceToLowerSequenceThatIsNotAGapNotAllowed() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(15L, asList(1L, 5L, 12L));
        assertThatThrownBy(() -> subject.advanceTo(4L, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newInstanceWithGapHigherThanSequenceNotAllowed() {
        assertThatThrownBy(() -> GapAwareTrackingToken.newInstance(9L, asList(1L, 5L, 12L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tokenCoversOther() {
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(3L, singleton(1L));
        GapAwareTrackingToken token2 = GapAwareTrackingToken.newInstance(4L, singleton(2L));
        GapAwareTrackingToken token3 = GapAwareTrackingToken.newInstance(2L, asList(0L, 1L));
        GapAwareTrackingToken token4 = GapAwareTrackingToken.newInstance(3L, emptySortedSet());
        GapAwareTrackingToken token5 = GapAwareTrackingToken.newInstance(3L, singleton(2L));
        GapAwareTrackingToken token6 = GapAwareTrackingToken.newInstance(1L, emptySortedSet());
        GapAwareTrackingToken token7 = GapAwareTrackingToken.newInstance(2L, singleton(1L));

        assertThat(token1.covers(token2)).isFalse();
        assertThat(token2.covers(token1)).isFalse();
        assertThat(token1.covers(token3)).isTrue();
        assertThat(token3.covers(token1)).isFalse();
        assertThat(token4.covers(token1)).isTrue();
        assertThat(token1.covers(token4)).isFalse();
        assertThat(token2.covers(token4)).isFalse();
        assertThat(token4.covers(token2)).isFalse();
        assertThat(token4.covers(token5)).isTrue();
        assertThat(token5.covers(token4)).isFalse();
        assertThat(token1.covers(token5)).isFalse();
        assertThat(token5.covers(token1)).isFalse();
        assertThat(token1.covers(token6)).isFalse();
        assertThat(token6.covers(token1)).isFalse();

        assertThat(token3.covers(token7)).isFalse();
    }

    @Test
    void samePositionAs() {
        // Same index, same gaps = same position
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(3L, singleton(1L));
        GapAwareTrackingToken token1Copy = GapAwareTrackingToken.newInstance(3L, singleton(1L));
        assertThat(token1.samePositionAs(token1)).isTrue();
        assertThat(token1.samePositionAs(token1Copy)).isTrue();

        // Same index, different gaps = same position (past gaps don't matter)
        GapAwareTrackingToken token2 = GapAwareTrackingToken.newInstance(3L, singleton(2L));
        assertThat(token1.samePositionAs(token2)).isTrue();
        assertThat(token2.samePositionAs(token1)).isTrue();

        // Null is never the same
        assertThat(token1.samePositionAs(null)).isFalse();
        assertThat(token2.samePositionAs(null)).isFalse();

        // Same index, one with gaps one without = same position
        GapAwareTrackingToken token3 = GapAwareTrackingToken.newInstance(3L, emptySortedSet());
        assertThat(token1.samePositionAs(token3)).isTrue();
        assertThat(token3.samePositionAs(token1)).isTrue();

        // Different index = different position
        GapAwareTrackingToken token4 = GapAwareTrackingToken.newInstance(4L, emptySortedSet());
        assertThat(token1.samePositionAs(token4)).isFalse();
        assertThat(token4.samePositionAs(token1)).isFalse();

        // One token's index is in the other's gaps = NOT same
        // token5 is at index 7, token6 has index 10 with gap at 7
        GapAwareTrackingToken token5 = GapAwareTrackingToken.newInstance(7L, emptySortedSet());
        GapAwareTrackingToken token6 = GapAwareTrackingToken.newInstance(10L, singleton(7L));
        assertThat(token5.samePositionAs(token6)).isFalse();  // token6 skipped event 7, token5 is pointing at 7
        assertThat(token6.samePositionAs(token5)).isFalse();  // different indices anyway
    }

    @Test
    void occurrenceOfInconsistentRangeException() {
        // verifies issue 655 (https://github.com/AxonFramework/AxonFramework/issues/655)
        GapAwareTrackingToken.newInstance(10L, asList(0L, 1L, 2L, 8L, 9L))
                .advanceTo(0L, 5)
                .covers(GapAwareTrackingToken.newInstance(0L, emptySet()));
    }

    @Test
    void lowerBound() {
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(3L, singleton(1L));
        GapAwareTrackingToken token2 = GapAwareTrackingToken.newInstance(4L, singleton(2L));
        GapAwareTrackingToken token3 = GapAwareTrackingToken.newInstance(2L, asList(0L, 1L));
        GapAwareTrackingToken token4 = GapAwareTrackingToken.newInstance(3L, emptySortedSet());
        GapAwareTrackingToken token5 = GapAwareTrackingToken.newInstance(3L, singleton(2L));
        GapAwareTrackingToken token6 = GapAwareTrackingToken.newInstance(1L, emptySortedSet());

        assertThat(token1.lowerBound(token2)).isEqualTo(GapAwareTrackingToken.newInstance(3L, asList(1L, 2L)));
        assertThat(token1.lowerBound(token3)).isEqualTo(token3);
        assertThat(token1.lowerBound(token4)).isEqualTo(token1);
        assertThat(token1.lowerBound(token5)).isEqualTo(GapAwareTrackingToken.newInstance(3L, asList(1L, 2L)));
        assertThat(token1.lowerBound(token6)).isEqualTo(GapAwareTrackingToken.newInstance(0L, emptySortedSet()));
        assertThat(token2.lowerBound(token3)).isEqualTo(GapAwareTrackingToken.newInstance(-1L, emptySortedSet()));
    }

    @Test
    void upperBound() {
        GapAwareTrackingToken token0 = GapAwareTrackingToken.newInstance(9, emptyList());
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(10, singleton(9L));
        GapAwareTrackingToken token2 = GapAwareTrackingToken.newInstance(10, asList(9L, 8L));
        GapAwareTrackingToken token3 = GapAwareTrackingToken.newInstance(15, singletonList(14L));
        GapAwareTrackingToken token4 = GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L));
        GapAwareTrackingToken token5 = GapAwareTrackingToken.newInstance(14, emptyList());

        assertThat(token0.upperBound(token1)).isEqualTo(GapAwareTrackingToken.newInstance(10, emptyList()));
        assertThat(token1.upperBound(token2)).isEqualTo(token1);
        assertThat(token1.upperBound(token3)).isEqualTo(token3);
        assertThat(token1.upperBound(token4)).isEqualTo(GapAwareTrackingToken.newInstance(15, asList(14L, 9L)));
        assertThat(token2.upperBound(token4)).isEqualTo(GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L)));
        assertThat(token5.upperBound(token3)).isEqualTo(GapAwareTrackingToken.newInstance(15, emptyList()));
    }

    @Test
    void upperBoundRemovesGapsWhenOtherTokenFillsThem() {
        // upper(T1,T2) = 7, 8, 10+  --> token not changed
        GapAwareTrackingToken token0_0 = GapAwareTrackingToken.newInstance(9, asList(7L, 8L));
        GapAwareTrackingToken token0_1 = GapAwareTrackingToken.newInstance(0, emptyList());
        assertThat(token0_0.upperBound(token0_1)).isEqualTo(token0_0);

        // upper(T1,T2) = 7, 8, 10+  --> token not changed
        GapAwareTrackingToken token1_0 = GapAwareTrackingToken.newInstance(9, asList(7L, 8L));
        GapAwareTrackingToken token1_1 = GapAwareTrackingToken.newInstance(6, emptyList());
        assertThat(token1_0.upperBound(token1_1)).isEqualTo(token1_0);

        // upper(T1,T2) = 8, 10+     --> token changed
        GapAwareTrackingToken token2_0 = GapAwareTrackingToken.newInstance(9, asList(7L, 8L));
        GapAwareTrackingToken token2_1 = GapAwareTrackingToken.newInstance(7, emptyList());
        assertThat(token2_0.upperBound(token2_1)).isEqualTo(GapAwareTrackingToken.newInstance(9, singletonList(8L)));

        // upper(T1,T2) = 10+        --> token changed
        GapAwareTrackingToken token3_0 = GapAwareTrackingToken.newInstance(9, singletonList(8L));
        GapAwareTrackingToken token3_1 = GapAwareTrackingToken.newInstance(8, emptyList());
        assertThat(token3_0.upperBound(token3_1)).isEqualTo(GapAwareTrackingToken.newInstance(9, emptyList()));

        // upper(T1,T2) = 10+        --> token not changed
        GapAwareTrackingToken token4_0 = GapAwareTrackingToken.newInstance(9, emptyList());
        GapAwareTrackingToken token4_1 = GapAwareTrackingToken.newInstance(9, emptyList());
        assertThat(token4_0.upperBound(token4_1)).isEqualTo(GapAwareTrackingToken.newInstance(9, emptyList()));

        // upper(T1,T2) = 11+        --> token changed
        GapAwareTrackingToken token5_0 = GapAwareTrackingToken.newInstance(9, emptyList());
        GapAwareTrackingToken token5_1 = GapAwareTrackingToken.newInstance(10, emptyList());
        assertThat(token5_0.upperBound(token5_1)).isEqualTo(GapAwareTrackingToken.newInstance(10, emptyList()));
    }



    @Test
    void upperBoundGapsKeptIfAfterLowerIndex() {
        GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, asList(3L, 4L));
        GapAwareTrackingToken replayToken = GapAwareTrackingToken.newInstance(2, emptyList());

        assertThat(tokenAtReset.upperBound(replayToken))
                .isEqualTo(GapAwareTrackingToken.newInstance(5, asList(3L, 4L)));
    }

    @Test
    void upperBoundGapsRemovesGapsThatAreAtLowerIndex() {
        GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, asList(3L, 4L));
        GapAwareTrackingToken replayToken = GapAwareTrackingToken.newInstance(3, emptyList());

        assertThat(tokenAtReset.upperBound(replayToken))
                .isEqualTo(GapAwareTrackingToken.newInstance(5, singletonList(4L)));
    }

    @Test
    void upperBoundRemovesGapsAreNotAtLower() {
        GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5, asList(3L, 4L));
        GapAwareTrackingToken replayToken = GapAwareTrackingToken.newInstance(4, singletonList(3L));

        assertThat(tokenAtReset.upperBound(replayToken))
                .isEqualTo(GapAwareTrackingToken.newInstance(5, singletonList(3L)));
    }

    @Test
    void gapTruncationRetainsEquality() {
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L));

        assertThat(token1.withGapsTruncatedAt(8L)).isEqualTo(token1);
        assertThat(token1).isEqualTo(token1.withGapsTruncatedAt(9L));
        assertThat(token1.withGapsTruncatedAt(8L)).isEqualTo(token1.withGapsTruncatedAt(9L));
        assertThat(token1.withGapsTruncatedAt(16L)).isEqualTo(token1.withGapsTruncatedAt(16L));
    }

    @Test
    void truncateGaps() {
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L));

        assertThat(token1.withGapsTruncatedAt(7)).isSameAs(token1);


        assertThat(token1.withGapsTruncatedAt(9L).getGaps()).isEqualTo(asTreeSet(9L, 14L));
        assertThat(token1.withGapsTruncatedAt(10L).getGaps()).isEqualTo(asTreeSet(14L));
        assertThat(token1.withGapsTruncatedAt(15L).getGaps()).isEqualTo(emptySet());
    }

    @Test
    void gapTruncatedTokenCoveredByOriginal() {
        GapAwareTrackingToken token1 = GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L));

        assertThat(token1.covers(token1.withGapsTruncatedAt(10))).isTrue();
        assertThat(token1.withGapsTruncatedAt(10).covers(token1)).isTrue();
    }

    @Test
    void position() {
        GapAwareTrackingToken token = GapAwareTrackingToken.newInstance(15, asList(14L, 9L, 8L));

        assertThat(token.position()).hasValue(15L);
    }

    @Test
    void noMemoryLeakWhenJumpingLargeGaps() {
        GapAwareTrackingToken token = GapAwareTrackingToken.newInstance(0, emptyList());
        GapAwareTrackingToken advancedToken = token.advanceTo(Long.MAX_VALUE, 1_234);
        assertThat(advancedToken.getGaps()).hasSize(1_234);
    }


    private TreeSet<Long> asTreeSet(Long... elements) {
        return new TreeSet<>(asList(elements));
    }

    /**
     * Tests demonstrating how {@code lowerBound()} can be used to detect whether a position
     * was a gap at the time of another token (reference token).
     * <p>
     * Key insight: When computing {@code lowerBound()}, if the minimum index falls on a gap,
     * the algorithm walks backwards to find the first non-gap position. This behavior
     * naturally distinguishes:
     * <ul>
     *   <li>Positions that were gaps (lowerBound walks back → different from the other token)</li>
     *   <li>Positions that were already seen (lowerBound stays → same as the other token)</li>
     * </ul>
     */
    @Nested
    class LowerBoundForGapDetection {

        /**
         * Scenario: Position 7 was a gap in the reference token.
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   Timeline: [0-6 seen] [7 GAP] [8 GAP] [9-10 seen]
         *
         * newToken: index=7, gaps=[]
         *   Represents: position 7
         *
         * lowerBound calculation:
         *   - mergedGaps = [7, 8]
         *   - mergedIndex = min(10, 7) = 7
         *   - 7 IS in gaps → walk back to 6
         *   - Result: index=6
         *
         * lowerBound.samePositionAs(newToken)? → 6 == 7? NO
         * Therefore: Position 7 was a gap in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsGapFilledEvent() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10L, asList(7L, 8L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(7L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound walked back past gap 7 to index 6
            assertThat(lowerBound.getIndex()).isEqualTo(6L);
            assertThat(lowerBound.getGaps()).isEmpty();

            // lowerBound is NOT same as newToken → position was a gap
            assertThat(lowerBound.samePositionAs(newToken)).isFalse();
        }

        /**
         * Scenario: Position 5 was already seen in the reference token.
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   Timeline: [0-6 seen] [7 GAP] [8 GAP] [9-10 seen]
         *
         * newToken: index=5, gaps=[]
         *   Represents: position 5
         *
         * lowerBound calculation:
         *   - mergedGaps = [7, 8]
         *   - mergedIndex = min(10, 5) = 5
         *   - 5 is NOT in gaps → stays 5
         *   - Result: index=5
         *
         * lowerBound.samePositionAs(newToken)? → 5 == 5? YES
         * Therefore: Position 5 was already seen in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsAlreadyProcessedEvent() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10L, asList(7L, 8L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(5L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound stays at index 5 (not a gap)
            assertThat(lowerBound.getIndex()).isEqualTo(5L);

            // lowerBound IS same as newToken → position was already seen
            assertThat(lowerBound.samePositionAs(newToken)).isTrue();
        }

        /**
         * Scenario: Position 8 was also a gap, consecutive with gap at 7.
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   Timeline: [0-6 seen] [7 GAP] [8 GAP] [9-10 seen]
         *
         * newToken: index=8, gaps=[]
         *   Represents: position 8
         *
         * lowerBound calculation:
         *   - mergedGaps = [7, 8]
         *   - mergedIndex = min(10, 8) = 8
         *   - 8 IS in gaps → walk back: 7 also in gaps → walk to 6
         *   - Result: index=6
         *
         * lowerBound.samePositionAs(newToken)? → 6 == 8? NO
         * Therefore: Position 8 was a gap in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsConsecutiveGapFill() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10L, asList(7L, 8L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(8L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound walked back past gaps 8 and 7 to index 6
            assertThat(lowerBound.getIndex()).isEqualTo(6L);

            // lowerBound is NOT same as newToken → position was a gap
            assertThat(lowerBound.samePositionAs(newToken)).isFalse();
        }

        /**
         * Scenario: Position 6 (boundary case - just before the gap).
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   Timeline: [0-6 seen] [7 GAP] [8 GAP] [9-10 seen]
         *
         * newToken: index=6, gaps=[]
         *   Represents: position 6
         *
         * lowerBound calculation:
         *   - mergedGaps = [7, 8]
         *   - mergedIndex = min(10, 6) = 6
         *   - 6 is NOT in gaps → stays 6
         *   - Result: index=6
         *
         * lowerBound.samePositionAs(newToken)? → 6 == 6? YES
         * Therefore: Position 6 was already seen in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsBoundaryBeforeGap() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10L, asList(7L, 8L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(6L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound stays at index 6
            assertThat(lowerBound.getIndex()).isEqualTo(6L);

            // lowerBound IS same as newToken → position was already seen
            assertThat(lowerBound.samePositionAs(newToken)).isTrue();
        }

        /**
         * Scenario: Position 9 (boundary case - just after the gaps).
         * <pre>
         * tokenAtReset: index=10, gaps=[7, 8]
         *   Timeline: [0-6 seen] [7 GAP] [8 GAP] [9-10 seen]
         *
         * newToken: index=9, gaps=[]
         *   Represents: position 9
         *
         * lowerBound calculation:
         *   - mergedGaps = [7, 8]
         *   - mergedIndex = min(10, 9) = 9
         *   - 9 is NOT in gaps → stays 9
         *   - Result: index=9, gaps=[7, 8] (gaps below 9 kept)
         *
         * lowerBound.samePositionAs(newToken)? → 9 == 9? YES
         * Therefore: Position 9 was already seen in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsBoundaryAfterGap() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10L, asList(7L, 8L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(9L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound stays at index 9
            assertThat(lowerBound.getIndex()).isEqualTo(9L);
            // Note: gaps 7, 8 are retained since they're below the merged index
            assertThat(new ArrayList<>(lowerBound.getGaps())).containsExactlyElementsOf(asList(7L, 8L));

            // lowerBound IS same as newToken → position was already seen
            assertThat(lowerBound.samePositionAs(newToken)).isTrue();
        }

        /**
         * Scenario: Gap at the very beginning (position 0).
         * <pre>
         * tokenAtReset: index=5, gaps=[0, 1]
         *   Timeline: [0 GAP] [1 GAP] [2-5 seen]
         *
         * newToken: index=0, gaps=[]
         *   Represents: position 0
         *
         * lowerBound calculation:
         *   - mergedGaps = [0, 1]
         *   - mergedIndex = min(5, 0) = 0
         *   - 0 IS in gaps → walk back to -1
         *   - Result: index=-1
         *
         * lowerBound.samePositionAs(newToken)? → -1 == 0? NO
         * Therefore: Position 0 was a gap in the reference token
         * </pre>
         */
        @Test
        void lowerBoundDetectsGapAtStart() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(5L, asList(0L, 1L));
            GapAwareTrackingToken newToken = GapAwareTrackingToken.newInstance(0L, emptyList());

            GapAwareTrackingToken lowerBound = tokenAtReset.lowerBound(newToken);

            // lowerBound walked back past gap 0 to index -1
            assertThat(lowerBound.getIndex()).isEqualTo(-1L);

            // lowerBound is NOT same as newToken → position was a gap
            assertThat(lowerBound.samePositionAs(newToken)).isFalse();
        }

        /**
         * Scenario: Multiple scattered gaps.
         * <pre>
         * tokenAtReset: index=15, gaps=[3, 7, 8, 12]
         *   Timeline: [0-2 seen] [3 GAP] [4-6 seen] [7 GAP] [8 GAP] [9-11 seen] [12 GAP] [13-15 seen]
         *
         * Various positions tested:
         *   - Position 3: was a gap
         *   - Position 5: was seen
         *   - Position 12: was a gap
         *   - Position 13: was seen
         * </pre>
         */
        @Test
        void lowerBoundWithMultipleScatteredGaps() {
            GapAwareTrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(15L, asList(3L, 7L, 8L, 12L));

            // Position 3 was a gap
            GapAwareTrackingToken event3 = GapAwareTrackingToken.newInstance(3L, emptyList());
            GapAwareTrackingToken lowerBound3 = tokenAtReset.lowerBound(event3);
            assertThat(lowerBound3.getIndex()).isEqualTo(2L); // walked back past gap 3
            assertThat(lowerBound3.samePositionAs(event3)).isFalse();

            // Position 5 was seen
            GapAwareTrackingToken event5 = GapAwareTrackingToken.newInstance(5L, emptyList());
            GapAwareTrackingToken lowerBound5 = tokenAtReset.lowerBound(event5);
            assertThat(lowerBound5.getIndex()).isEqualTo(5L); // stays at 5
            assertThat(lowerBound5.samePositionAs(event5)).isTrue();

            // Position 12 was a gap
            GapAwareTrackingToken event12 = GapAwareTrackingToken.newInstance(12L, emptyList());
            GapAwareTrackingToken lowerBound12 = tokenAtReset.lowerBound(event12);
            assertThat(lowerBound12.getIndex()).isEqualTo(11L); // walked back past gap 12
            assertThat(lowerBound12.samePositionAs(event12)).isFalse();

            // Position 13 was seen
            GapAwareTrackingToken event13 = GapAwareTrackingToken.newInstance(13L, emptyList());
            GapAwareTrackingToken lowerBound13 = tokenAtReset.lowerBound(event13);
            assertThat(lowerBound13.getIndex()).isEqualTo(13L); // stays at 13
            assertThat(lowerBound13.samePositionAs(event13)).isTrue();
        }
    }
}
