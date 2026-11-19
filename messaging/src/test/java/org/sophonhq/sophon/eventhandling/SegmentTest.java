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

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.messaging.MetaData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SegmentTest {

    private List<DomainEventMessage<?>> domainEventMessages;

    @BeforeEach
    void before() {
        domainEventMessages = produceEvents();
    }

    @Test
    void segmentSplitAddsUp() {

        final List<Long> identifiers = domainEventMessages.stream().map(de -> {
            final String aggregateIdentifier = de.getAggregateIdentifier();
            return UUID.fromString(aggregateIdentifier).getLeastSignificantBits();
        }).toList();

        // segment 0, mask 0;
        final long count = identifiers.stream().filter(Segment.ROOT_SEGMENT::matches).count();
        assertThat(count).isEqualTo(identifiers.size());

        final Segment[] splitSegment = Segment.ROOT_SEGMENT.split();

        final long splitCount1 = identifiers.stream().filter(splitSegment[0]::matches).count();
        final long splitCount2 = identifiers.stream().filter(splitSegment[1]::matches).count();

        assertThat(splitCount1 + splitCount2).isEqualTo(identifiers.size());
    }

    @Test
    void segmentSplit() {

        // Split segment 0
        final Segment[] splitSegment0 = Segment.ROOT_SEGMENT.split();
        assertThat(Segment.ROOT_SEGMENT.splitSegmentId()).isEqualTo(1);

        assertThat(splitSegment0[0].getSegmentId()).isEqualTo(0);
        assertThat(splitSegment0[0].getMask()).isEqualTo(0x01);

        assertThat(splitSegment0[1].getSegmentId()).isEqualTo(1);
        assertThat(splitSegment0[1].getMask()).isEqualTo(0x01);

        // Split segment 0 again
        final Segment[] splitSegment0_1 = splitSegment0[0].split();
        assertThat(splitSegment0[0].splitSegmentId()).isEqualTo(2);

        assertThat(splitSegment0_1[0].getSegmentId()).isEqualTo(0);
        assertThat(splitSegment0_1[0].getMask()).isEqualTo(0x3);

        assertThat(splitSegment0_1[1].getSegmentId()).isEqualTo(2);
        assertThat(splitSegment0_1[1].getMask()).isEqualTo(0x3);

        // Split segment 0 again
        final Segment[] splitSegment0_2 = splitSegment0_1[0].split();
        assertThat(splitSegment0_1[0].splitSegmentId()).isEqualTo(4);

        assertThat(splitSegment0_2[0].getSegmentId()).isEqualTo(0);
        assertThat(splitSegment0_2[0].getMask()).isEqualTo(0x7);

        assertThat(splitSegment0_2[1].getSegmentId()).isEqualTo(4);
        assertThat(splitSegment0_2[1].getMask()).isEqualTo(0x7);


        // Split segment 0 again
        final Segment[] splitSegment0_3 = splitSegment0_2[0].split();
        assertThat(splitSegment0_2[0].splitSegmentId()).isEqualTo(8);

        assertThat(splitSegment0_3[0].getSegmentId()).isEqualTo(0);
        assertThat(splitSegment0_3[0].getMask()).isEqualTo(0xF);

        assertThat(splitSegment0_3[1].getSegmentId()).isEqualTo(8);
        assertThat(splitSegment0_3[1].getMask()).isEqualTo(0xF);

        //////////////////////////////////////////////////////////////

        // Split segment 1
        final Segment[] splitSegment1 = splitSegment0[1].split();
        assertThat(splitSegment0[1].splitSegmentId()).isEqualTo(3);

        assertThat(splitSegment1[0].getSegmentId()).isEqualTo(1);
        assertThat(splitSegment1[0].getMask()).isEqualTo(0x3);

        assertThat(splitSegment1[1].getSegmentId()).isEqualTo(3);
        assertThat(splitSegment1[1].getMask()).isEqualTo(0x3);

        // Split segment 3
        final Segment[] splitSegment3 = splitSegment1[1].split();
        assertThat(splitSegment1[1].splitSegmentId()).isEqualTo(7);

        assertThat(splitSegment3[0].getSegmentId()).isEqualTo(3);
        assertThat(splitSegment3[0].getMask()).isEqualTo(0x7);

        assertThat(splitSegment3[1].getSegmentId()).isEqualTo(7);
        assertThat(splitSegment3[1].getMask()).isEqualTo(0x7);
    }

    @Test
    void segmentSplitNTimes() {
        {
            //
            final List<Segment> segmentMasks = Segment.splitBalanced(Segment.ROOT_SEGMENT, 5);
            assertThat(segmentMasks).hasSize(6);
            assertThat(segmentMasks.get(5)).isEqualTo(new Segment(5, 0x7));
            assertThat(segmentMasks.get(0)).isEqualTo(new Segment(0, 0x7));
            assertThat(segmentMasks.get(1)).isEqualTo(new Segment(1, 0x7));
            assertThat(segmentMasks.get(2)).isEqualTo(new Segment(2, 0x3));
            assertThat(segmentMasks.get(3)).isEqualTo(new Segment(3, 0x3));
            assertThat(segmentMasks.get(4)).isEqualTo(new Segment(4, 0x7));
        }
    }

    @Test
    void splitFromRootSegmentAlwaysYieldsSequentialSegmentIds() {
        for (int i = 0; i < 500; i++) {
            List<Segment> segments = Segment.splitBalanced(Segment.ROOT_SEGMENT, i);
            assertThat(segments).hasSize(i + 1);
            for (int j = 0; j < i; j++) {
                assertThat(segments.get(j).getSegmentId()).isEqualTo(j);
            }
        }
    }

    @Test
    void mergeable() {
        Segment[] segments = Segment.ROOT_SEGMENT.split();
        assertThat(segments[0].isMergeableWith(segments[0])).isFalse();
        assertThat(segments[1].isMergeableWith(segments[1])).isFalse();

        assertThat(segments[0].isMergeableWith(segments[1])).isTrue();
        assertThat(segments[1].isMergeableWith(segments[0])).isTrue();

        // these masks differ
        Segment[] segments2 = segments[0].split();
        assertThat(segments[0].isMergeableWith(segments2[0])).isFalse();
        assertThat(segments[0].isMergeableWith(segments2[1])).isFalse();
        assertThat(segments[1].isMergeableWith(segments2[0])).isFalse();

        // this should still work
        assertThat(segments2[0].isMergeableWith(segments2[1])).isTrue();
    }

    @Test
    void mergeableSegment() {
        Segment[] segments = Segment.ROOT_SEGMENT.split();
        assertThat(segments[0].mergeableSegmentId()).isEqualTo(segments[1].getSegmentId());
        assertThat(segments[1].mergeableSegmentId()).isEqualTo(segments[0].getSegmentId());
        assertThat(Segment.ROOT_SEGMENT.mergeableSegmentId()).isZero();
    }

    @Test
    void mergeSegments() {
        Segment[] segments = Segment.ROOT_SEGMENT.split();
        Segment[] segments2 = segments[0].split();

        assertThat(segments2[0].mergedWith(segments2[1])).isEqualTo(segments[0]);
        assertThat(segments2[1].mergedWith(segments2[0])).isEqualTo(segments[0]);
        assertThat(segments[1].mergedWith(segments[0])).isEqualTo(Segment.ROOT_SEGMENT);
        assertThat(Segment.ROOT_SEGMENT.mergedWith(Segment.ROOT_SEGMENT)).isEqualTo(Segment.ROOT_SEGMENT);
    }

    @Test
    void segmentResolve() {
        {
            final int[] segments = {};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks).isEmpty();
        }

        {
            final int[] segments = {0};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks).hasSize(1);
            assertThat(segmentMasks[0].getMask()).isEqualTo(Segment.ROOT_SEGMENT.getMask());
        }

        {
            // balanced distribution
            final int[] segments = {0, 1};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks).hasSize(2);
            assertThat(segmentMasks[0].getMask()).isEqualTo(0x1);
            assertThat(segmentMasks[1].getMask()).isEqualTo(0x1);
        }

        {
            // un-balanced distribution segment 0 is split.
            final int[] segments = {0, 1, 2};
            final Segment[] segmentMasks = Segment.computeSegments(segments);

            assertThat(segmentMasks).hasSize(3);
            assertThat(segmentMasks[0].getMask()).isEqualTo(0x3);
            assertThat(segmentMasks[1].getMask()).isEqualTo(0x1);
            assertThat(segmentMasks[2].getMask()).isEqualTo(0x3);
        }

        {
            // un-balanced distribution segment 1 is split.
            final int[] segments = {0, 1, 3};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks).hasSize(3);
            assertThat(segmentMasks[0].getMask()).isEqualTo(0x1);
            assertThat(segmentMasks[1].getMask()).isEqualTo(0x3);
            assertThat(segmentMasks[2].getMask()).isEqualTo(0x3);
        }

        {
            // balanced distribution segment 0 and 1 are split.
            final int[] segments = {0, 1, 2, 3};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks).hasSize(4);
            assertThat(segmentMasks[0].getMask()).isEqualTo(0x3);
            assertThat(segmentMasks[1].getMask()).isEqualTo(0x3);
            assertThat(segmentMasks[2].getMask()).isEqualTo(0x3);
            assertThat(segmentMasks[3].getMask()).isEqualTo(0x3);
        }

        {
            // un-balanced distribution segment 1 is split and segment 3 is split.
            final int[] segments = {0, 1, 3, 7};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks).hasSize(4);
            assertThat(segmentMasks[0].getMask()).isEqualTo(0x1);
            assertThat(segmentMasks[1].getMask()).isEqualTo(0x3);
            assertThat(segmentMasks[2].getMask()).isEqualTo(0x7);
            assertThat(segmentMasks[3].getMask()).isEqualTo(0x7);
        }

        {
            // un-balanced distribution segment 0 is split, segment 3 is split.
            final int[] segments = {0, 1, 2, 3, 7};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks).hasSize(5);
            assertThat(segmentMasks[0].getMask()).isEqualTo(0x3);
            assertThat(segmentMasks[1].getMask()).isEqualTo(0x3);
            assertThat(segmentMasks[2].getMask()).isEqualTo(0x3);
            assertThat(segmentMasks[3].getMask()).isEqualTo(0x7);
            assertThat(segmentMasks[4].getMask()).isEqualTo(0x7);
        }

        {
            //
            final int[] segments = {0, 1, 2, 3, 4, 5};
            final Segment[] segmentMasks = Segment.computeSegments(segments);
            assertThat(segmentMasks).hasSize(6);
            assertThat(segmentMasks[0]).isEqualTo(new Segment(0, 0x7));
            assertThat(segmentMasks[1]).isEqualTo(new Segment(1, 0x7));
            assertThat(segmentMasks[2]).isEqualTo(new Segment(2, 0x3));
            assertThat(segmentMasks[3]).isEqualTo(new Segment(3, 0x3));
            assertThat(segmentMasks[4]).isEqualTo(new Segment(4, 0x7));
            assertThat(segmentMasks[5]).isEqualTo(new Segment(5, 0x7));
        }
    }

    @Test
    void computeSegment() {
        for (int segmentCount = 0; segmentCount < 256; segmentCount++) {
            List<Segment> segments = Segment.splitBalanced(Segment.ROOT_SEGMENT, segmentCount);
            int[] segmentIds = new int[segments.size()];
            for (int i = 0; i < segmentIds.length; i++) {
                segmentIds[i] = segments.get(i).getSegmentId();
            }

            for (Segment segment : segments) {
                assertThat(Segment.computeSegment(segment.getSegmentId(), segmentIds))
                        .as("Got wrong segment for " + segmentCount + " number of segments")
                        .isEqualTo(segment);
            }
        }
    }

    @Test
    void computeSegment_Imbalanced() {
        List<Segment> segments = new ArrayList<>();
        Segment initialSegment = Segment.ROOT_SEGMENT;
        for (int i = 0; i < 8; i++) {
            Segment[] split = initialSegment.split();
            initialSegment = split[0];
            segments.add(split[1]);
        }
        segments.add(initialSegment);

        int[] segmentIds = new int[segments.size()];
        for (int i = 0; i < segmentIds.length; i++) {
            segmentIds[i] = segments.get(i).getSegmentId();
        }

        for (Segment segment : segments) {
            assertThat(Segment.computeSegment(segment.getSegmentId(), segmentIds)).isEqualTo(segment);
        }
    }

    @Test
    void segmentSplitBeyondBoundary() {
        final Segment segment = new Segment(0, Integer.MAX_VALUE);
        assertThatThrownBy(segment::split).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void segmentSplitOnBoundary() {

        final Segment segment = new Segment(0, Integer.MAX_VALUE >>> 1);
        final Segment[] splitSegment = segment.split();
        assertThat(splitSegment[0].getSegmentId()).isEqualTo(0);
        assertThat(splitSegment[0].getMask()).isEqualTo(Integer.MAX_VALUE);

        assertThat(splitSegment[1].getSegmentId()).isEqualTo((Integer.MAX_VALUE / 2) + 1);
        assertThat(splitSegment[1].getMask()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void itemsAssignedToOnlyOneSegment() {
        for (int j = 0; j < 10; j++) {
            List<Segment> segments = Segment.splitBalanced(Segment.ROOT_SEGMENT,
                                                           ThreadLocalRandom.current().nextInt(50) + 1);
            for (int i = 0; i < 100_000; i++) {
                String value = UUID.randomUUID().toString();
                assertThat(segments.stream().filter(s -> s.matches(value)).count()).isOne();
            }
        }
    }

    private List<DomainEventMessage<?>> produceEvents() {
        final ArrayList<DomainEventMessage<?>> events = new ArrayList<>();
        // Produce a set of
        for (int i = 0; i < 10000; i++) {
            String aggregateIdentifier = UUID.randomUUID().toString();
            final DomainEventMessage<?> domainEventMessage = newStubDomainEvent(aggregateIdentifier);
            events.add(domainEventMessage);
        }
        return events;
    }

    private DomainEventMessage<?> newStubDomainEvent(String aggregateIdentifier) {
        return new GenericDomainEventMessage<>("type", aggregateIdentifier, 0,
                                               new Object(), MetaData.emptyInstance());
    }
}
