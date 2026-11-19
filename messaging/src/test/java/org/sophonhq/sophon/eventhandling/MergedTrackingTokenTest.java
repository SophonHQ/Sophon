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

package org.sophonhq.sophon.eventhandling;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MergedTrackingTokenTest {

    @Test
    void mergedTokenCoversOriginal() {
        MergedTrackingToken testSubject = new MergedTrackingToken(token(1), token(3));

        assertThat(testSubject.covers(token(1))).isTrue();
        assertThat(testSubject.covers(token(2))).isFalse();
        assertThat(testSubject.covers(token(3))).isFalse();
    }

    @Test
    void upperBound() {
        MergedTrackingToken testSubject = new MergedTrackingToken(token(1), token(3));

        assertThat(testSubject.upperBound(token(2))).isEqualTo(new MergedTrackingToken(token(2), token(3)));
        assertThat(testSubject.upperBound(token(3))).isEqualTo(token(3));
    }

    @Test
    void lowerBound() {
        MergedTrackingToken testSubject = new MergedTrackingToken(token(1), token(3));

        assertThat(testSubject.lowerBound(token(2))).isEqualTo(new MergedTrackingToken(token(1), token(2)));
        assertThat(testSubject.lowerBound(token(1))).isEqualTo(token(1));
    }

    @Test
    void unwrapToLowerBound() {
        assertThat(new MergedTrackingToken(new MergedTrackingToken(token(1), token(5)), token(3)).lowerBound())
                .isEqualTo(token(1));
        assertThat(new MergedTrackingToken(token(1), new MergedTrackingToken(token(5), token(3))).lowerBound())
                .isEqualTo(token(1));
    }

    @Test
    void upperBound_NestedTokens() {
        MergedTrackingToken testSubject = new MergedTrackingToken(new MergedTrackingToken(token(1), token(3)), token(5));

        assertThat(testSubject.upperBound(token(4))).isEqualTo(new MergedTrackingToken(token(4), token(5)));
        assertThat(testSubject.upperBound(token(3)))
                .isEqualTo(new MergedTrackingToken(new MergedTrackingToken(token(3), token(3)), token(5)));
        assertThat(testSubject.upperBound(token(2)))
                .isEqualTo(new MergedTrackingToken(new MergedTrackingToken(token(2), token(3)), token(5)));
    }

    @Test
    void lowerBound_NestedTokens() {
        MergedTrackingToken testSubject = new MergedTrackingToken(new MergedTrackingToken(token(1), token(5)), token(3));

        assertThat(testSubject.lowerBound(token(3)))
                .isEqualTo(new MergedTrackingToken(new MergedTrackingToken(token(1), token(3)), token(3)));
        assertThat(testSubject.lowerBound(token(2)))
                .isEqualTo(new MergedTrackingToken(new MergedTrackingToken(token(1), token(2)), token(2)));
        assertThat(testSubject.lowerBound(token(1))).isEqualTo(token(1));
    }

    @Test
    void advanceWithNestedReplayToken() {
        TrackingToken incomingMessage = new GlobalSequenceTrackingToken(0);

        MergedTrackingToken currentToken = new MergedTrackingToken(
                new ReplayToken(new GlobalSequenceTrackingToken(9), new GlobalSequenceTrackingToken(9)),
                new ReplayToken(new GlobalSequenceTrackingToken(9), new GlobalSequenceTrackingToken(-1))
        );

        TrackingToken advancedToken = currentToken.advancedTo(incomingMessage);

        assertThat(advancedToken).isInstanceOf(MergedTrackingToken.class);
        MergedTrackingToken actual = (MergedTrackingToken) advancedToken;
        assertThat(actual.lowerSegmentToken())
                .isInstanceOf(ReplayToken.class); // this token should not have been modified
        assertThat(actual.upperSegmentToken())
                .as("Wrong upper segment: " + actual.upperSegmentToken())
                .isInstanceOf(ReplayToken.class); // this token should not have been modified
    }

    @Test
    void unwrapPrefersLastAdvancedToken_LowerSegmentAdvanced() {
        TrackingToken merged = new MergedTrackingToken(token(1), token(3)).advancedTo(token(2));
        assertThat(merged).isInstanceOf(MergedTrackingToken.class);
        assertThat(WrappedToken.unwrap(merged, GlobalSequenceTrackingToken.class).orElse(null)).isEqualTo(token(2));
    }

    @Test
    void unwrapPrefersLastAdvancedToken_UpperSegmentAdvanced() {
        TrackingToken merged = new MergedTrackingToken(token(3), token(1)).advancedTo(token(2));
        assertThat(merged).isInstanceOf(MergedTrackingToken.class);
        assertThat(WrappedToken.unwrap(merged, GlobalSequenceTrackingToken.class).orElse(null)).isEqualTo(token(2));
    }

    @Test
    void unwrapPrefersLastAdvancedToken_NeitherSegmentAdvanced() {
        TrackingToken merged = new MergedTrackingToken(token(3), token(3)).advancedTo(token(2));
        assertThat(merged).isInstanceOf(MergedTrackingToken.class);
        assertThat(WrappedToken.unwrap(merged, GlobalSequenceTrackingToken.class).orElse(null)).isEqualTo(token(3));
    }

    @Test
    void unwrapPrefersLastAdvancedToken_NeitherSegmentAdvanced_OnlyLowerIsCandidate() {
        MergedTrackingToken merged = new MergedTrackingToken(token(3), mock(TrackingToken.class));
        assertThat(merged.unwrap(GlobalSequenceTrackingToken.class).orElse(null)).isEqualTo(token(3));
    }

    @Test
    void unwrapPrefersLastAdvancedToken_NeitherSegmentAdvanced_OnlyUpperIsCandidate() {
        MergedTrackingToken merged = new MergedTrackingToken(mock(TrackingToken.class), token(3));
        assertThat(merged.unwrap(GlobalSequenceTrackingToken.class).orElse(null)).isEqualTo(token(3));
    }

    @Test
    void positionReportsLowestSegment() {
        MergedTrackingToken merged = new MergedTrackingToken(token(4), token(3));
        assertThat(merged.position().orElse(0L)).isEqualTo(3L);
    }

    @Test
    void positionIsNotPresent() {
        MergedTrackingToken merged = new MergedTrackingToken(mock(TrackingToken.class), token(3));
        assertThat(merged.position().isPresent()).isFalse();
    }

    @Test
    void isMergeInProgress() {
        MergedTrackingToken testSubject = new MergedTrackingToken(token(1), token(3));
        assertThat(MergedTrackingToken.isMergeInProgress(testSubject)).isTrue();
    }

    @Test
    void mergePosition() {
        MergedTrackingToken testSubject = new MergedTrackingToken(new MergedTrackingToken(token(1), token(3)), token(5));

        assertThat(MergedTrackingToken.mergePosition(testSubject).isPresent()).isTrue();
        assertThat(MergedTrackingToken.mergePosition(testSubject).getAsLong()).isEqualTo(5);
    }

    @Test
    void coversWithNestedMergedNullTokens() {
        MergedTrackingToken testSubject = new MergedTrackingToken(new MergedTrackingToken(null, null), null);

        assertThat(testSubject.covers(token(0))).isFalse();
        assertThat(testSubject.covers(null)).isTrue();

        GlobalSequenceTrackingToken advance = token(1);
        assertThat(testSubject.advancedTo(advance)).isSameAs(advance);
    }

    @Test
    void coversWithNullTokens() {
        MergedTrackingToken testSubject = new MergedTrackingToken(null, null);

        assertThat(testSubject.covers(token(0))).isFalse();
        assertThat(testSubject.covers(null)).isTrue();

        GlobalSequenceTrackingToken advance = token(1);
        assertThat(testSubject.advancedTo(advance)).isSameAs(advance);
    }

    @Test
    void samePositionAs() {
        MergedTrackingToken testSubject = new MergedTrackingToken(token(1), token(3));

        // Both segments must be at the same position
        assertThat(testSubject.samePositionAs(token(1))).isFalse();
        assertThat(testSubject.samePositionAs(token(2))).isFalse();  // Lower segment is at 1, not 2
        assertThat(testSubject.samePositionAs(token(3))).isFalse();  // Lower segment is at 1, not 3

        // When both segments are at the same position
        MergedTrackingToken equalSegments = new MergedTrackingToken(token(5), token(5));
        assertThat(equalSegments.samePositionAs(token(5))).isTrue();
        assertThat(equalSegments.samePositionAs(token(4))).isFalse();
        assertThat(equalSegments.samePositionAs(token(6))).isFalse();
    }

    @Test
    void samePositionAsWithNullTokens() {
        MergedTrackingToken testSubject = new MergedTrackingToken(null, null);

        assertThat(testSubject.samePositionAs(null)).isTrue();
        assertThat(testSubject.samePositionAs(token(0))).isFalse();
    }

    private GlobalSequenceTrackingToken token(int sequence) {
        return new GlobalSequenceTrackingToken(sequence);
    }
}
