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

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.eventsourcing.utils.EventStoreTestUtils.AGGREGATE;
import static org.sophonhq.sophon.eventsourcing.utils.EventStoreTestUtils.createEvents;

/**
 * Test class validating the specifics around a {@link BatchingEventStorageEngine}.
 *
 * @author Rene de Waele
 */
@Transactional
public abstract class BatchingEventStorageEngineTest<E extends BatchingEventStorageEngine, EB extends BatchingEventStorageEngine.Builder>
        extends AbstractEventStorageEngineTest<E, EB> {

    private BatchingEventStorageEngine testSubject;

    @Test
    protected void loadLargeAmountOfEventsFromAggregateStream() {
        int eventCount = testSubject.batchSize() + 10;
        testSubject.appendEvents(createEvents(eventCount));
        testSubject.appendEvents(new GenericEventMessage<>("test"));
        assertThat(testSubject.readEvents(AGGREGATE).asStream().count()).isEqualTo(eventCount);
        Optional<? extends DomainEventMessage<?>> resultEventMessage =
                testSubject.readEvents(AGGREGATE).asStream().reduce((a, b) -> b);
        assertThat(resultEventMessage).isPresent();
        assertThat(resultEventMessage.get().getSequenceNumber()).isEqualTo(eventCount - 1);
    }

    @Test
    void loadLargeAmountFromOpenStream() {
        int eventCount = testSubject.batchSize() + 10;
        testSubject.appendEvents(createEvents(eventCount));
        GenericEventMessage<String> last = new GenericEventMessage<>("test");
        testSubject.appendEvents(last);

        Optional<? extends EventMessage<?>> resultEventMessage =
                testSubject.readEvents(null, false).reduce((a, b) -> b);
        assertThat(testSubject.readEvents(null, false).count()).isEqualTo(testSubject.batchSize() + 11);
        assertThat(resultEventMessage).isPresent();
        assertThat(resultEventMessage.get().getIdentifier()).isEqualTo(last.getIdentifier());
    }

    protected void setTestSubject(BatchingEventStorageEngine testSubject) {
        super.setTestSubject(this.testSubject = testSubject);
    }
}
