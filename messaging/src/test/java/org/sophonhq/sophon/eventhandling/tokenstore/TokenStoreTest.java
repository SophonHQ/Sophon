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

package org.sophonhq.sophon.eventhandling.tokenstore;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.Segment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TokenStoreTest {

    private final TokenStore tokenStore = spy(TokenStore.class);

    @Test
    void fetchAvailableSegments() {
        when(tokenStore.fetchSegments("")).thenReturn(new int[]{0, 1, 2, 3});
        List<Segment> availableSegments = tokenStore.fetchAvailableSegments("");

        assertThat(availableSegments).hasSize(4);

        Segment segment0 = availableSegments.get(0);
        assertThat(segment0.getSegmentId()).isZero();
        assertThat(segment0.getMask()).isEqualTo(3);

        Segment segment1 = availableSegments.get(1);
        assertThat(segment1.getSegmentId()).isOne();
        assertThat(segment1.getMask()).isEqualTo(3);

        Segment segment2 = availableSegments.get(2);
        assertThat(segment2.getSegmentId()).isEqualTo(2);
        assertThat(segment2.getMask()).isEqualTo(3);

        Segment segment3 = availableSegments.get(3);
        assertThat(segment3.getSegmentId()).isEqualTo(3);
        assertThat(segment3.getMask()).isEqualTo(3);
    }
}