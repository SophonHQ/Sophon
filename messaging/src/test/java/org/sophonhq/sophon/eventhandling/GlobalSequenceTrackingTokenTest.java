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

class GlobalSequenceTrackingTokenTest {

    @Test
    void upperBound() {
        GlobalSequenceTrackingToken token1 = new GlobalSequenceTrackingToken(1L);
        GlobalSequenceTrackingToken token2 = new GlobalSequenceTrackingToken(2L);

        assertThat(token1.upperBound(token2)).isSameAs(token2);
        assertThat(token2.upperBound(token1)).isSameAs(token2);
        assertThat(token2.upperBound(token2)).isSameAs(token2);
    }

    @Test
    void lowerBound() {
        GlobalSequenceTrackingToken token1 = new GlobalSequenceTrackingToken(1L);
        GlobalSequenceTrackingToken token2 = new GlobalSequenceTrackingToken(2L);

        assertThat(token1.lowerBound(token2)).isSameAs(token1);
        assertThat(token2.lowerBound(token1)).isSameAs(token1);
        assertThat(token2.lowerBound(token2)).isSameAs(token2);
    }

    @Test
    void covers() {
        GlobalSequenceTrackingToken token1 = new GlobalSequenceTrackingToken(1L);
        GlobalSequenceTrackingToken token2 = new GlobalSequenceTrackingToken(2L);

        assertThat(token1.covers(token2)).isFalse();
        assertThat(token2.covers(token1)).isTrue();
        assertThat(token2.covers(token2)).isTrue();
        assertThat(token2.covers(null)).isTrue();
    }

    @Test
    void position() {
        GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(1L);

        assertThat(token.position().getAsLong()).isOne();
    }

    @Test
    void samePositionAs() {
        GlobalSequenceTrackingToken token1 = new GlobalSequenceTrackingToken(1L);
        GlobalSequenceTrackingToken token2 = new GlobalSequenceTrackingToken(2L);
        GlobalSequenceTrackingToken token1Copy = new GlobalSequenceTrackingToken(1L);

        // Same index means same position
        assertThat(token1.samePositionAs(token1)).isTrue();
        assertThat(token1.samePositionAs(token1Copy)).isTrue();
        assertThat(token2.samePositionAs(token2)).isTrue();

        // Different index means different position
        assertThat(token1.samePositionAs(token2)).isFalse();
        assertThat(token2.samePositionAs(token1)).isFalse();

        // Null is never the same
        assertThat(token1.samePositionAs(null)).isFalse();
        assertThat(token2.samePositionAs(null)).isFalse();
    }
}
