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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@code default} methods on the {@link EventTrackerStatus}.
 *
 * @author Steven van Beelen
 */
class EventTrackerStatusTest {

    private static final boolean DO_NOT_VALIDATE_POSITIONS = false;
    private static final boolean VALIDATE_POSITIONS = true;

    private FakeEventTrackerStatus thisStatus;
    private FakeEventTrackerStatus thatStatus;

    @BeforeEach
    void setUp() {
        thisStatus = new FakeEventTrackerStatus();
        thatStatus = new FakeEventTrackerStatus();
    }

    @Test
    void isDifferentReturnsFalseForIdenticalObjects() {
        assertThat(thisStatus.isDifferent(thisStatus, DO_NOT_VALIDATE_POSITIONS)).isFalse();
        assertThat(thisStatus.isDifferent(thisStatus)).isFalse();
    }

    @Test
    void isDifferentReturnsTrueForDifferentEventTrackerStatusImplementations() {
        AddedTrackerStatus otherStatus = new AddedTrackerStatus(thatStatus);
        assertThat(thisStatus.isDifferent(otherStatus, DO_NOT_VALIDATE_POSITIONS)).isTrue();
        assertThat(thisStatus.isDifferent(otherStatus)).isTrue();
    }

    @Test
    void isDifferentReturnsFalseForSimilarStatusObjects() {
        assertThat(thisStatus.isDifferent(thatStatus, DO_NOT_VALIDATE_POSITIONS)).isFalse();
        assertThat(thisStatus.isDifferent(thatStatus, VALIDATE_POSITIONS)).isFalse();
    }

    @Test
    void isDifferentReturnsFalseForSimilarStatusObjectsWhenDisregardingPositions() {
        assertThat(thisStatus.isDifferent(thatStatus, DO_NOT_VALIDATE_POSITIONS)).isFalse();
        assertThat(thisStatus.isDifferent(thatStatus, VALIDATE_POSITIONS)).isFalse();

        thisStatus.setMerging(true);
        thatStatus.setMerging(false);
        assertThat(thisStatus.isDifferent(thatStatus, DO_NOT_VALIDATE_POSITIONS)).isTrue();
        assertThat(thisStatus.isDifferent(thatStatus, VALIDATE_POSITIONS)).isTrue();

        thisStatus.setMerging(false);
        thisStatus.setCurrentPosition(10L);
        thatStatus.setCurrentPosition(42L);
        assertThat(thisStatus.isDifferent(thatStatus, DO_NOT_VALIDATE_POSITIONS)).isFalse();
        assertThat(thisStatus.isDifferent(thatStatus, VALIDATE_POSITIONS)).isTrue();
    }

    @Test
    void matchStates() {
        assertThat(thisStatus.matchStates(thatStatus)).isTrue();

        thisStatus.setSegment(Segment.ROOT_SEGMENT);
        thisStatus.setTrackingToken(new GlobalSequenceTrackingToken(0L));
        thisStatus.setCaughtUp(true);
        thisStatus.setReplaying(true);
        thisStatus.setMerging(true);
        thisStatus.setErrorState(true);
        thisStatus.setError(new IllegalArgumentException("some-exception"));
        assertThat(thisStatus.matchStates(thatStatus)).isFalse();

        thatStatus.setSegment(Segment.ROOT_SEGMENT);
        thatStatus.setTrackingToken(new GlobalSequenceTrackingToken(0L));
        thatStatus.setCaughtUp(true);
        thatStatus.setReplaying(true);
        thatStatus.setMerging(true);
        thatStatus.setErrorState(true);
        thatStatus.setError(new IllegalArgumentException("some-exception"));
        assertThat(thisStatus.matchStates(thatStatus)).isTrue();
    }

    @Test
    void matchPositions() {
        assertThat(thisStatus.matchPositions(thatStatus)).isTrue();

        thisStatus.setCurrentPosition(10L);
        thisStatus.setResetPosition(42L);
        thisStatus.setMergeCompletedPosition(1337L);
        assertThat(thisStatus.matchPositions(thatStatus)).isFalse();

        thatStatus.setCurrentPosition(10L);
        thatStatus.setResetPosition(42L);
        thatStatus.setMergeCompletedPosition(1337L);
        assertThat(thisStatus.matchPositions(thatStatus)).isTrue();
    }

    @Test
    void trackerAdded() {
        assertThat(thisStatus.trackerAdded()).isFalse();
    }

    @Test
    void trackerRemoved() {
        assertThat(thisStatus.trackerRemoved()).isFalse();
    }
}