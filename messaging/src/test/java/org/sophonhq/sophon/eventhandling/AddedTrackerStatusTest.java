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

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AddedTrackerStatus}.
 *
 * @author Steven van Beelen
 */
class AddedTrackerStatusTest {

    @Test
    void getSegment() {
        Segment expectedSegment = Segment.ROOT_SEGMENT;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.getSegment()).thenReturn(expectedSegment);

        AddedTrackerStatus testSubject = new AddedTrackerStatus(delegate);

        assertThat(testSubject.getSegment()).isEqualTo(expectedSegment);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void isCaughtUp() {
        boolean expectedIsCaughtUp = true;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.isCaughtUp()).thenReturn(expectedIsCaughtUp);

        AddedTrackerStatus testSubject = new AddedTrackerStatus(delegate);

        assertThat(testSubject.isCaughtUp()).isEqualTo(expectedIsCaughtUp);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void isReplaying() {
        boolean expectedIsReplaying = true;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.isReplaying()).thenReturn(expectedIsReplaying);

        AddedTrackerStatus testSubject = new AddedTrackerStatus(delegate);

        assertThat(testSubject.isReplaying()).isEqualTo(expectedIsReplaying);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void isMerging() {
        boolean expectedIsMerging = true;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.isMerging()).thenReturn(expectedIsMerging);

        AddedTrackerStatus testSubject = new AddedTrackerStatus(delegate);

        assertThat(testSubject.isMerging()).isEqualTo(expectedIsMerging);
    }

    @Test
    void getTrackingToken() {
        TrackingToken expectedTrackingToken = new GlobalSequenceTrackingToken(0);
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.getTrackingToken()).thenReturn(expectedTrackingToken);

        AddedTrackerStatus testSubject = new AddedTrackerStatus(delegate);

        assertThat(testSubject.getTrackingToken()).isEqualTo(expectedTrackingToken);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void isErrorState() {
        boolean expectedIsErrorState = true;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.isErrorState()).thenReturn(expectedIsErrorState);

        AddedTrackerStatus testSubject = new AddedTrackerStatus(delegate);

        assertThat(testSubject.isErrorState()).isEqualTo(expectedIsErrorState);
    }

    @Test
    void getError() {
        Exception expectedException = new IllegalArgumentException("some-exception");
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.getError()).thenReturn(expectedException);

        AddedTrackerStatus testSubject = new AddedTrackerStatus(delegate);

        assertThat(testSubject.getError()).isEqualTo(expectedException);
    }

    @Test
    void getCurrentPosition() {
        long expectedCurrentPosition = 0L;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.getCurrentPosition()).thenReturn(OptionalLong.of(expectedCurrentPosition));

        AddedTrackerStatus testSubject = new AddedTrackerStatus(delegate);

        OptionalLong result = testSubject.getCurrentPosition();
        assertThat(result.isPresent()).isTrue();
        assertThat(result).hasValue(expectedCurrentPosition);
    }

    @Test
    void getResetPosition() {
        long expectedResetPosition = 0L;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.getResetPosition()).thenReturn(OptionalLong.of(expectedResetPosition));

        AddedTrackerStatus testSubject = new AddedTrackerStatus(delegate);

        OptionalLong result = testSubject.getResetPosition();
        assertThat(result.isPresent()).isTrue();
        assertThat(result).hasValue(expectedResetPosition);
    }

    @Test
    void mergeCompletedPosition() {
        long expectedMergeCompletedPosition = 0L;
        EventTrackerStatus delegate = mock(EventTrackerStatus.class);
        when(delegate.mergeCompletedPosition()).thenReturn(OptionalLong.of(expectedMergeCompletedPosition));

        AddedTrackerStatus testSubject = new AddedTrackerStatus(delegate);

        OptionalLong result = testSubject.mergeCompletedPosition();
        assertThat(result.isPresent()).isTrue();
        assertThat(result).hasValue(expectedMergeCompletedPosition);
    }

    @Test
    void trackerAdded() {
        AddedTrackerStatus testSubject = new AddedTrackerStatus(mock(EventTrackerStatus.class));

        assertThat(testSubject.trackerAdded()).isTrue();
    }

    @Test
    void trackerRemoved() {
        AddedTrackerStatus testSubject = new AddedTrackerStatus(mock(EventTrackerStatus.class));

        assertThat(testSubject.trackerRemoved()).isFalse();
    }
}
