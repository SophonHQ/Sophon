/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.sophonhq.sophon.eventsourcing.conflictresolution;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.eventsourcing.conflictresolution.Conflicts.payloadMatching;
import static org.sophonhq.sophon.eventsourcing.conflictresolution.Conflicts.payloadTypeOf;
import static org.sophonhq.sophon.eventsourcing.utils.EventStoreTestUtils.*;

class ConflictsTest {

    private final List<DomainEventMessage<?>> events = IntStream.range(0, 10)
                                                                .mapToObj(sequenceNumber -> createEvent(AGGREGATE, sequenceNumber, PAYLOAD + sequenceNumber))
                                                                .collect(toList());

    @Test
    void payload() {
        assertThat(payloadMatching(payload -> Objects.equals(payload, PAYLOAD + 2))).accepts(events);
        assertThat(payloadMatching(String.class, payload -> Objects.equals(payload, PAYLOAD + 5))).accepts(events);
        assertThat(payloadMatching(payload -> Objects.equals(payload, PAYLOAD + 11))).rejects(events);
    }

    @Test
    void payloadType() {
        assertThat(payloadTypeOf(String.class)).accepts(events);
        assertThat(payloadTypeOf(Object.class)).accepts(events);
        assertThat(payloadTypeOf(Long.class)).rejects(events);
    }

}
