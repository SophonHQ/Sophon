/*
 * Copyright (c) 2010-2021. Axon Framework
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

package io.github.sophonhq.sophon.eventsourcing.eventstore.jpa;

import io.github.sophonhq.sophon.common.DateTimeUtils;
import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import io.github.sophonhq.sophon.eventsourcing.utils.TestSerializer;
import io.github.sophonhq.sophon.messaging.MetaData;
import io.github.sophonhq.sophon.serialization.Serializer;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Collections;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DomainEventEntry}.
 *
 * @author Allard Buijze
 */
class DomainEventEntryTest {

    private final Serializer serializer = TestSerializer.xStreamSerializer();

    @Test
    void domainEventEntryWrapEventsCorrectly() {
        Instant testTimestamp = Instant.now();

        String expectedType = "type";
        String expectedAggregateId = randomUUID().toString();
        long expectedSequenceNumber = 2L;
        String expectedPayload = "Payload";
        MetaData expectedMetaData = new MetaData(Collections.singletonMap("Key", "Value"));
        Instant expectedTimestamp = DateTimeUtils.parseInstant(DateTimeUtils.formatInstant(testTimestamp));
        String expectedEventIdentifier = randomUUID().toString();

        DomainEventMessage<String> testEvent = new GenericDomainEventMessage<>(
                expectedType, expectedAggregateId, expectedSequenceNumber, expectedPayload, expectedMetaData,
                expectedEventIdentifier, testTimestamp
        );

        DomainEventEntry result = new DomainEventEntry(testEvent, serializer);

        assertEquals(expectedType, result.getType());
        assertEquals(expectedAggregateId, result.getAggregateIdentifier());
        assertEquals(expectedSequenceNumber, result.getSequenceNumber());
        assertEquals(expectedEventIdentifier, result.getEventIdentifier());
        assertEquals(expectedTimestamp, result.getTimestamp());
        assertEquals(expectedPayload, serializer.deserialize(result.getPayload()));
        assertEquals(byte[].class, result.getPayload().getContentType());
        assertEquals(expectedMetaData, serializer.deserialize(result.getMetaData()));
    }
}
