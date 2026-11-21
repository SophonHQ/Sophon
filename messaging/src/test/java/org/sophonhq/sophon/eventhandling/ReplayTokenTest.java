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

package org.sophonhq.sophon.eventhandling;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link ReplayToken}.
 *
 * @author Allard Buijze
 */
class ReplayTokenTest {

    private TrackingToken innerToken;

    @BeforeEach
    void setUp() {
        innerToken = GapAwareTrackingToken.newInstance(10, Collections.singleton(9L));
    }

    @Test
    void advanceReplayTokenWithinReplaySegment() {
        ReplayToken testSubject = new ReplayToken(innerToken, null, null);
        TrackingToken actual = testSubject.advancedTo(GapAwareTrackingToken.newInstance(8, emptySet()));
        assertThat(actual).isInstanceOf(ReplayToken.class);
        assertThat(ReplayToken.isReplay(actual)).isTrue();
    }

    @Test
    void regularTokenIsProvidedWhenResetBeyondCurrentPosition() {
        TrackingToken token1 = new GlobalSequenceTrackingToken(1);
        TrackingToken token2 = new GlobalSequenceTrackingToken(2);

        TrackingToken actual = ReplayToken.createReplayToken(token1, token2);
        assertThat(actual).isSameAs(token2);
    }

    @Test
    void replayTokenIsProvidedWhenResetAtCurrentPosition() {
        TrackingToken token1 = new GlobalSequenceTrackingToken(2);
        TrackingToken token2 = new GlobalSequenceTrackingToken(2);

        TrackingToken actual = ReplayToken.createReplayToken(token1, token2);
        assertThat(actual).isInstanceOf(ReplayToken.class);
        assertThat(ReplayToken.isReplay(actual)).isTrue();
    }

    @Test
    void serializationDeserialization() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ReplayToken replayToken = new ReplayToken(innerToken, null, null);
        String serializedReplayToken = objectMapper.writer().writeValueAsString(replayToken);
        ReplayToken deserializedReplayToken = objectMapper.readerFor(ReplayToken.class)
                .readValue(serializedReplayToken);
        assertThat(deserializedReplayToken).isEqualTo(replayToken);
    }

    @Test
    void position() {
        GapAwareTrackingToken startPosition = GapAwareTrackingToken.newInstance(11L, Collections.singleton(9L));

        TrackingToken replayToken = ReplayToken.createReplayToken(innerToken, startPosition);

        assertThat(replayToken.position().isPresent()).isTrue();
        assertThat(replayToken.position()).hasValue(11L);
    }

    @Test
    void positionIsZeroAtReset() {
        TrackingToken replayToken = ReplayToken.createReplayToken(innerToken);
        assertThat(replayToken.position().orElse(-1)).isZero();
    }

    @Test
    void getTokenAtReset() {
        ReplayToken testSubject = (ReplayToken) ReplayToken.createReplayToken(innerToken);
        TrackingToken actual = testSubject.advancedTo(GapAwareTrackingToken.newInstance(6, emptySet()));
        assertThat(actual).isInstanceOf(ReplayToken.class);
        assertThat(testSubject.getTokenAtReset()).isEqualTo(innerToken);
    }

    @Test
    void createReplayTokenReturnsStartPositionIfTokenAtResetIsNull() {
        TrackingToken tokenAtReset = null;
        TrackingToken startPosition = new GlobalSequenceTrackingToken(1);

        //noinspection ConstantConditions
        TrackingToken result = ReplayToken.createReplayToken(tokenAtReset, startPosition);

        assertThat(result).isEqualTo(startPosition);
    }

    @Test
    void createReplayTokenReturnsStartPositionIfStartPositionCoversTokenAtReset() {
        TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(1);
        TrackingToken startPosition = new GlobalSequenceTrackingToken(2);

        TrackingToken result = ReplayToken.createReplayToken(tokenAtReset, startPosition);

        assertThat(result).isEqualTo(startPosition);
    }

    @Test
    void createReplayTokenReturnsWrappedReplayTokenIfTokenAtResetIsReplayToken() {
        TrackingToken tokenAtReset = ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(1));
        TrackingToken startPosition = new GlobalSequenceTrackingToken(2);

        TrackingToken result = ReplayToken.createReplayToken(tokenAtReset, startPosition);

        assertThat(result).isEqualTo(startPosition);
    }

    @Test
    void createReplayTokenReturnsReplayToken() {
        TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(2);
        TrackingToken startPosition = new GlobalSequenceTrackingToken(1);

        TrackingToken result = ReplayToken.createReplayToken(tokenAtReset, startPosition);

        assertThat(result).isInstanceOf(ReplayToken.class);
        assertThat(((ReplayToken) result).getTokenAtReset()).isEqualTo(tokenAtReset);
        assertThat(((ReplayToken) result).getCurrentToken()).isEqualTo(startPosition);
    }

    /**
     * Tests for {@link ReplayToken#advancedTo(TrackingToken)} with {@link GlobalSequenceTrackingToken}.
     * These tests verify replay detection behavior with simple sequential tokens.
     */
    @Nested
    class AdvancedToWithGlobalSequenceTrackingToken {

        @Test
        void eventBeforeResetPositionIsReplay() {
            // tokenAtReset at index 7 - processor saw events 0-7 before reset
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(7);
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at index 5 - clearly before reset position
            TrackingToken newToken = new GlobalSequenceTrackingToken(5);
            TrackingToken result = replayToken.advancedTo(newToken);

            assertThat(result).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result)).as("Event before reset position should be a replay").isTrue();
        }

        @Test
        void eventAtExactResetPositionIsReplay() {
            // tokenAtReset at index 7
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(7);
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Advance to position 6 first
            replayToken = (ReplayToken) replayToken.advancedTo(new GlobalSequenceTrackingToken(6));

            // Event at index 7 - exactly at reset position (was already processed before reset)
            TrackingToken newToken = new GlobalSequenceTrackingToken(7);
            TrackingToken result = replayToken.advancedTo(newToken);

            assertThat(result).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result)).as("Event at exact reset position should be a replay").isTrue();
        }

        @Test
        void eventAfterResetPositionEndsReplay() {
            // tokenAtReset at index 7
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(7);
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);

            // Event at index 8 - after reset position (new event, never seen before)
            TrackingToken newToken = new GlobalSequenceTrackingToken(8);
            TrackingToken result = replayToken.advancedTo(newToken);

            // Should exit replay mode entirely
            assertThat(result).isInstanceOf(GlobalSequenceTrackingToken.class);
            assertThat(ReplayToken.isReplay(result)).as("Event after reset position should not be a replay").isFalse();
        }

        @Test
        void replayProgressesThroughMultipleEvents() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(5);
            TrackingToken currentToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Process events 0 through 5 - all should be replays
            for (int i = 0; i <= 5; i++) {
                currentToken = ((ReplayToken) currentToken).advancedTo(new GlobalSequenceTrackingToken(i));
                assertThat(currentToken).isInstanceOf(ReplayToken.class);
                assertThat(ReplayToken.isReplay(currentToken)).as("Event " + i + " should be a replay").isTrue();
            }

            // Event 6 should end the replay
            currentToken = ((ReplayToken) currentToken).advancedTo(new GlobalSequenceTrackingToken(6));
            assertThat(ReplayToken.isReplay(currentToken)).as("Event 6 should not be a replay").isFalse();
        }

        @Test
        void advancedToShouldKeepReplayWhenTokenReachesSameIndex() {
            GlobalSequenceTrackingToken tokenAtReset = new GlobalSequenceTrackingToken(6);

            TrackingToken replayToken = ReplayToken.createReplayToken(tokenAtReset, null);

            // Advance to the same index as tokenAtReset
            GlobalSequenceTrackingToken newToken = new GlobalSequenceTrackingToken(6);
            TrackingToken advancedToken = ((ReplayToken) replayToken).advancedTo(newToken);

            assertThat(ReplayToken.isReplay(advancedToken))
                    .as("Should have remained in replay mode since newToken (index 6) does not have tokenAtReset beyond its positio")
                    .isTrue();
        }
    }

    /**
     * Tests for {@link ReplayToken#advancedTo(TrackingToken)} with a windowed token whose {@code lowerBound()}
     * computes a set-intersection (like {@code MongoTrackingToken}). When two tokens track disjoint event sets —
     * e.g. a recent reset token vs. an old replayed event — the intersection is empty and
     * {@code samePositionAs(empty, old)} returns {@code false}. Without the {@code covers()} fallback introduced
     * in <a href="https://github.com/AxonFramework/AxonFramework/issues/4453">issue #4453</a>, every replayed event
     * would be misclassified as "new", causing {@code tokenAtReset.upperBound(newToken)} to be called on every step
     * and the stored token to grow without bound.
     */
    @Nested
    class AdvancedToWithWindowedTrackingToken {

        @Test
        void replayedEventFromEarlierPeriodIsClassifiedAsReplay() {
            // tokenAtReset tracks events from "now" (high position, recent event IDs)
            WindowedTrackingToken tokenAtReset = new WindowedTrackingToken(1000, Set.of(900L, 950L, 1000L));
            // old event: low position, event IDs fully disjoint from tokenAtReset's window
            WindowedTrackingToken oldEvent = new WindowedTrackingToken(10, Set.of(5L, 8L, 10L));

            // lowerBound(tokenAtReset, oldEvent) = intersection = {} — triggers the bug without the fix
            ReplayToken replayToken = (ReplayToken) ReplayToken.createReplayToken(tokenAtReset, null);
            TrackingToken result = replayToken.advancedTo(oldEvent);

            assertThat(result).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(result))
                    .as("Old event processed before reset must be classified as replay, not as new")
                    .isTrue();
        }

        @Test
        void tokenAtResetDoesNotGrowDuringReplay() {
            // Without the fix every misclassified event triggers tokenAtReset.upperBound(newToken),
            // merging ALL new event IDs into tokenAtReset on each step — unbounded growth.
            WindowedTrackingToken tokenAtReset = new WindowedTrackingToken(1000, Set.of(900L, 950L, 1000L));

            TrackingToken current = ReplayToken.createReplayToken(tokenAtReset, null);
            for (long i = 1; i <= 50; i++) {
                current = ((ReplayToken) current).advancedTo(new WindowedTrackingToken(i, Set.of(i)));
            }

            assertThat(current).isInstanceOf(ReplayToken.class);
            assertThat(ReplayToken.isReplay(current)).isTrue();
            assertThat(((ReplayToken) current).getTokenAtReset())
                    .as("tokenAtReset must not grow during replay")
                    .isEqualTo(tokenAtReset);
        }
    }

    @Nested
    class SamePositionAs {

        @Test
        void samePositionAsWithReplayTokenComparesCurrentTokens() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            TrackingToken currentToken1 = new GlobalSequenceTrackingToken(5);
            TrackingToken currentToken2 = new GlobalSequenceTrackingToken(5);

            ReplayToken replayToken1 = new ReplayToken(tokenAtReset, currentToken1, null);
            ReplayToken replayToken2 = new ReplayToken(tokenAtReset, currentToken2, null);

            assertThat(replayToken1.samePositionAs(replayToken2)).isTrue();
            assertThat(replayToken2.samePositionAs(replayToken1)).isTrue();
        }

        @Test
        void samePositionAsWithReplayTokenReturnsFalseWhenCurrentTokensDiffer() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            TrackingToken currentToken1 = new GlobalSequenceTrackingToken(5);
            TrackingToken currentToken2 = new GlobalSequenceTrackingToken(6);

            ReplayToken replayToken1 = new ReplayToken(tokenAtReset, currentToken1, null);
            ReplayToken replayToken2 = new ReplayToken(tokenAtReset, currentToken2, null);

            assertThat(replayToken1.samePositionAs(replayToken2)).isFalse();
            assertThat(replayToken2.samePositionAs(replayToken1)).isFalse();
        }

        @Test
        void samePositionAsWithNonReplayTokenDelegatesToCurrentToken() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            TrackingToken currentToken = new GlobalSequenceTrackingToken(5);
            TrackingToken otherToken = new GlobalSequenceTrackingToken(5);

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            assertThat(replayToken.samePositionAs(otherToken)).isTrue();
        }

        @Test
        void samePositionAsWithNonReplayTokenReturnsFalseWhenDifferent() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            TrackingToken currentToken = new GlobalSequenceTrackingToken(5);
            TrackingToken otherToken = new GlobalSequenceTrackingToken(6);

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            assertThat(replayToken.samePositionAs(otherToken)).isFalse();
        }

        @Test
        void samePositionAsWithNullCurrentTokenReturnsFalse() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            ReplayToken replayToken = new ReplayToken(tokenAtReset, null, null);

            TrackingToken otherToken = new GlobalSequenceTrackingToken(5);

            assertThat(replayToken.samePositionAs(otherToken)).isFalse();
        }

        @Test
        void samePositionAsWithBothNullCurrentTokens() {
            TrackingToken tokenAtReset = new GlobalSequenceTrackingToken(10);
            ReplayToken replayToken1 = new ReplayToken(tokenAtReset, null, null);
            ReplayToken replayToken2 = new ReplayToken(tokenAtReset, null, null);

            // When both have null currentToken, samePositionAs() returns false
            // because currentToken != null check fails
            assertThat(replayToken1.samePositionAs(replayToken2)).isFalse();
        }

        @Test
        void samePositionAsWithGapAwareTrackingToken() {
            TrackingToken tokenAtReset = GapAwareTrackingToken.newInstance(10, emptySet());
            TrackingToken currentToken = GapAwareTrackingToken.newInstance(5, emptySet());
            TrackingToken sameCurrentToken = GapAwareTrackingToken.newInstance(5, emptySet());

            ReplayToken replayToken = new ReplayToken(tokenAtReset, currentToken, null);

            assertThat(replayToken.samePositionAs(sameCurrentToken)).isTrue();
        }
    }

    /**
     * A {@link TrackingToken} whose {@code lowerBound()} returns the intersection of two event-ID sets (mirroring
     * {@code MongoTrackingToken}). The intersection is empty when the two tokens track disjoint event sets, which
     * is the scenario that triggers issue #4453.
     * <p>
     * {@code covers()} uses time/position semantics: {@code other} is covered if it is not newer than {@code this}
     * and all its tracked events are either present in this token's window or predate the oldest event in it.
     */
    private static class WindowedTrackingToken implements TrackingToken {

        private final long position;
        private final Set<Long> trackedEvents;

        WindowedTrackingToken(long position, Set<Long> trackedEvents) {
            this.position = position;
            this.trackedEvents = Collections.unmodifiableSet(new HashSet<>(trackedEvents));
        }

        @Override
        public TrackingToken lowerBound(TrackingToken other) {
            WindowedTrackingToken o = (WindowedTrackingToken) other;
            Set<Long> intersection = new HashSet<>(trackedEvents);
            intersection.retainAll(o.trackedEvents);
            return new WindowedTrackingToken(Math.min(position, o.position), intersection);
        }

        @Override
        public TrackingToken upperBound(TrackingToken other) {
            WindowedTrackingToken o = (WindowedTrackingToken) other;
            Set<Long> union = new HashSet<>(trackedEvents);
            union.addAll(o.trackedEvents);
            return new WindowedTrackingToken(Math.max(position, o.position), union);
        }

        @Override
        public boolean covers(TrackingToken other) {
            WindowedTrackingToken o = (WindowedTrackingToken) other;
            long oldest = trackedEvents.stream().min(Comparator.naturalOrder()).orElse(0L);
            return o.position <= this.position
                    && o.trackedEvents.stream().allMatch(e -> trackedEvents.contains(e) || e < oldest);
        }

        @Override
        public OptionalLong position() {
            return OptionalLong.of(position);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WindowedTrackingToken)) {
                return false;
            }
            WindowedTrackingToken other = (WindowedTrackingToken) obj;
            return position == other.position && trackedEvents.equals(other.trackedEvents);
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, trackedEvents);
        }
    }
}
