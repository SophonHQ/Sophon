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

package org.sophonhq.sophon.eventhandling.tokenstore.inmemory;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.Segment;
import org.sophonhq.sophon.eventhandling.tokenstore.UnableToClaimTokenException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTokenStoreTest {

    private InMemoryTokenStore testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new InMemoryTokenStore();
    }

    @Test
    void initializeTokens() {
        testSubject.initializeTokenSegments("test1", 7);

        int[] actual = testSubject.fetchSegments("test1");
        Arrays.sort(actual);
        assertThat(actual).containsExactly(0, 1, 2, 3, 4, 5, 6);
    }

    @Test
    void identifierIsPresent() {
        assertThat(testSubject.retrieveStorageIdentifier()).isPresent();
    }

    @Test
    void initializeTokensAtGivenPosition() {
        testSubject.initializeTokenSegments("test1", 7, new GlobalSequenceTrackingToken(10));

        int[] actual = testSubject.fetchSegments("test1");
        Arrays.sort(actual);
        assertThat(actual).containsExactly(0, 1, 2, 3, 4, 5, 6);

        for (int segment : actual) {
            assertThat(testSubject.fetchToken("test1", segment)).isEqualTo(new GlobalSequenceTrackingToken(10));
        }
    }

    @Test
    void updateToken() {
        testSubject.initializeTokenSegments("test1", 1);
        testSubject.storeToken(new GlobalSequenceTrackingToken(1), "test1", 0);

        assertThat(testSubject.fetchToken("test1", 0)).isEqualTo(new GlobalSequenceTrackingToken(1));
    }

    @Test
    void initializeAtGivenToken() {
        testSubject.initializeTokenSegments("test1", 2, new GlobalSequenceTrackingToken(1));

        assertThat(testSubject.fetchToken("test1", 0)).isEqualTo(new GlobalSequenceTrackingToken(1));
        assertThat(testSubject.fetchToken("test1", 1)).isEqualTo(new GlobalSequenceTrackingToken(1));
    }

    @Test
    void initializeTokensWhileAlreadyPresent() {
        assertThatThrownBy(() -> testSubject.fetchToken("test1", 1))
                .isInstanceOf(UnableToClaimTokenException.class);
    }

    @Test
    void querySegments() {
        testSubject.initializeTokenSegments("test", 1);

        assertThat(testSubject.fetchToken("test", 0)).isNull();

        testSubject.storeToken(new GlobalSequenceTrackingToken(1L), "proc1", 0);
        testSubject.storeToken(new GlobalSequenceTrackingToken(2L), "proc1", 1);
        testSubject.storeToken(new GlobalSequenceTrackingToken(2L), "proc2", 1);

        {
            final int[] segments = testSubject.fetchSegments("proc1");
            assertThat(segments).hasSize(2);
        }
        {
            final int[] segments = testSubject.fetchSegments("proc2");
            assertThat(segments).hasSize(1);
        }
        {
            final int[] segments = testSubject.fetchSegments("proc3");
            assertThat(segments).isEmpty();
        }
    }

    @Test
    void queryAvailableSegments() {
        testSubject.storeToken(new GlobalSequenceTrackingToken(1L), "proc1", 0);
        testSubject.storeToken(new GlobalSequenceTrackingToken(2L), "proc1", 1);
        testSubject.storeToken(new GlobalSequenceTrackingToken(2L), "proc2", 1);

        {
            final List<Segment> segments = testSubject.fetchAvailableSegments("proc1");
            assertThat(segments).hasSize(2);
            assertThat(segments.get(0).getSegmentId()).isEqualTo(0);
            assertThat(segments.get(1).getSegmentId()).isEqualTo(1);
        }
        {
            final List<Segment> segments = testSubject.fetchAvailableSegments("proc2");
            assertThat(segments).hasSize(1);
            assertThat(segments.get(0).getSegmentId()).isEqualTo(1);
        }
        {
            final List<Segment> segments = testSubject.fetchAvailableSegments("proc3");
            assertThat(segments).hasSize(0);
        }
    }

    @Test
    void fetchTokenBySegment() {
        testSubject.initializeTokenSegments("test", 2, new GlobalSequenceTrackingToken(1));

        assertThat(testSubject.fetchToken("test", Segment.computeSegment(1, 0, 1)))
                .isEqualTo(new GlobalSequenceTrackingToken(1));
    }

    @Test
    void fetchTokenBySegmentFailsDuringMerge() {
        testSubject.initializeTokenSegments("test", 1);
        Segment segmentToFetch = Segment.computeSegment(0, 0, 1);

        assertThatThrownBy(() -> testSubject.fetchToken("test", segmentToFetch))
                .isInstanceOf(UnableToClaimTokenException.class);
    }

    @Test
    void fetchTokenBySegmentFailsDuringSplit() {
        testSubject.initializeTokenSegments("test", 2);
        Segment segmentToFetch = Segment.computeSegment(0, 0);

        assertThatThrownBy(() -> testSubject.fetchToken("test", segmentToFetch))
                .isInstanceOf(UnableToClaimTokenException.class);
    }
}
