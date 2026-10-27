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

package org.sophonhq.sophon.eventsourcing.eventstore;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.messaging.MetaData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilteringDomainEventStreamTest {

    private DomainEventMessage event1;
    private DomainEventMessage event2;
    private DomainEventMessage event3;

    @BeforeEach
    void setUp() throws Exception {
        event1 = new GenericDomainEventMessage<>("type", "1", (long) 0,
                                                 "Create type 1", MetaData.emptyInstance());
        event2 = new GenericDomainEventMessage<>("type2", "1", (long) 0,
                                                 "Create type 2", MetaData.emptyInstance());
        event3 = new GenericDomainEventMessage<>("type2", "1", (long) 1,
                                                 "Change type 2", MetaData.emptyInstance());
    }

    @Test
    void forEachRemainingType1() {
        List<DomainEventMessage> expectedMessages = Arrays.asList(event1);

        DomainEventStream concat = new FilteringDomainEventStream(
                DomainEventStream.of(event1, event2, event3), // Initial stream - add all elements
                e -> e.getType().equals("type")
        );

        List<DomainEventMessage<?>> actualMessages = new ArrayList<>();
        concat.forEachRemaining(actualMessages::add);

        assertEquals(expectedMessages, actualMessages);
    }
    
    @Test
    void forEachRemainingType2() {
        List<DomainEventMessage> expectedMessages = Arrays.asList(event2, event3);

        DomainEventStream concat = new FilteringDomainEventStream(
                DomainEventStream.of(event1, event2, event3), // Initial stream - add all elements
                e -> e.getType().equals("type2")
        );

        List<DomainEventMessage<?>> actualMessages = new ArrayList<>();
        concat.forEachRemaining(actualMessages::add);

        assertEquals(expectedMessages, actualMessages);
    }
}
