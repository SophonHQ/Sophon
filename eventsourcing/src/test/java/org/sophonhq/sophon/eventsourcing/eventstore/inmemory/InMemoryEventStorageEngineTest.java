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

package org.sophonhq.sophon.eventsourcing.eventstore.inmemory;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.eventhandling.TrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngineTest;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.UnitOfWork;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link InMemoryEventStorageEngine}.
 *
 * @author Rene de Waele
 */
class InMemoryEventStorageEngineTest extends EventStorageEngineTest {

    private static final EventMessage<Object> TEST_EVENT = GenericEventMessage.asEventMessage("test");

    private InMemoryEventStorageEngine testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new InMemoryEventStorageEngine();
        setTestSubject(testSubject);
    }

    @Test
    void publishedEventsEmittedToExistingStreams() {
        Stream<? extends TrackedEventMessage<?>> stream = testSubject.readEvents(null, true);
        testSubject.appendEvents(TEST_EVENT);

        assertTrue(stream.findFirst().isPresent());
    }

    @Test
    void publishedEventsEmittedToExistingStreams_WithOffset() {
        testSubject = new InMemoryEventStorageEngine(1);
        Stream<? extends TrackedEventMessage<?>> stream = testSubject.readEvents(null, true);
        testSubject.appendEvents(TEST_EVENT);

        Optional<? extends TrackedEventMessage<?>> optionalResult = stream.findFirst();
        assertTrue(optionalResult.isPresent());
        OptionalLong optionalResultPosition = optionalResult.get().trackingToken().position();
        assertTrue(optionalResultPosition.isPresent());
        assertEquals(1, optionalResultPosition.getAsLong());
    }

    @Test
    void eventsAreStoredOnCommitIfCurrentUnitOfWorkIsActive() {
        UnitOfWork<EventMessage<Object>> unitOfWork = DefaultUnitOfWork.startAndGet(TEST_EVENT);

        // when _only_ publishing...
        testSubject.appendEvents(TEST_EVENT);

        // then there are no events in the storage engine, since the UnitOfWork is not committed yet.
        Stream<? extends TrackedEventMessage<?>> eventStream = testSubject.readEvents(null, true);
        assertEquals(0L, eventStream.count());

        // When rolling back the UnitOfWork...
        unitOfWork.commit();

        // then there are *still* no events in the storage engine.
        eventStream = testSubject.readEvents(null, true);
        assertEquals(1L, eventStream.count());
    }

    @Test
    void eventsAreNotStoredWhenTheUnitOfWorkIsRolledBackIfCurrentUnitOfWorkIsActive() {
        UnitOfWork<EventMessage<Object>> unitOfWork = DefaultUnitOfWork.startAndGet(TEST_EVENT);

        // when _only_ publishing...
        testSubject.appendEvents(TEST_EVENT);

        // then there are no events in the storage engine, since the UnitOfWork is not committed yet.
        Stream<? extends TrackedEventMessage<?>> eventStream = testSubject.readEvents(null, true);
        assertEquals(0L, eventStream.count());

        // When rolling back the UnitOfWork...
        unitOfWork.rollback();

        // then there are *still* no events in the storage engine.
        eventStream = testSubject.readEvents(null, true);
        assertEquals(0L, eventStream.count());
    }

    @Test
    void concurrentReaderDoesNotSkipEventsAppendedInBatches() throws InterruptedException {
        int numberOfBatches = 500;
        int batchSize = 10;
        long lastPosition = (long) numberOfBatches * batchSize - 1;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        List<Long> readPositions = new ArrayList<>();

        Thread reader = startReaderThread(lastPosition, deadline, readPositions);
        for (int batch = 0; batch < numberOfBatches; batch++) {
            testSubject.appendEvents(IntStream.range(0, batchSize)
                                              .mapToObj(i -> GenericEventMessage.asEventMessage("event-" + i))
                                              .collect(Collectors.toList()));
        }
        reader.join();

        List<Long> expectedPositions = LongStream.rangeClosed(0, lastPosition).boxed().collect(Collectors.toList());
        assertEquals(expectedPositions, readPositions);
    }

    private Thread startReaderThread(long lastPosition, long deadline, List<Long> readPositions) {
        Thread reader = new Thread(() -> {
            TrackingToken lastToken = null;
            while ((lastToken == null || lastToken.position().orElse(-1) < lastPosition)
                    && System.nanoTime() < deadline) {
                Iterator<? extends TrackedEventMessage<?>> events =
                        testSubject.readEvents(lastToken, false).iterator();
                while (events.hasNext()) {
                    lastToken = events.next().trackingToken();
                    readPositions.add(lastToken.position().orElse(-1));
                }
            }
        });
        reader.start();
        return reader;
    }
}
