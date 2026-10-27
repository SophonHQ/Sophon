/*
 * Copyright (c) 2010-2026. Axon Framework
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

package io.github.sophonhq.sophon.eventsourcing.snapshotting;

import io.github.sophonhq.sophon.eventhandling.DomainEventData;
import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import io.github.sophonhq.sophon.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import io.github.sophonhq.sophon.eventsourcing.utils.TestSerializer;
import io.github.sophonhq.sophon.serialization.Revision;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating that {@link RevisionSnapshotFilter} behaves identically regardless of the {@link Serializer}
 * used for snapshots.
 * <p>
 * A snapshot's revision is determined at serialization time by the serializer's {@code RevisionResolver} (defaulting to
 * the {@code AnnotationRevisionResolver}, which reads {@link Revision @Revision}) and stored as separate metadata - not
 * embedded in the serialized body. The filter reads it back from that metadata, so revision-based filtering works the
 * same for XML (XStream) and JSON (Jackson).
 *
 * @author Mateusz Nowak
 */
class RevisionSnapshotFilterSerializerTest {

    private static final String STORED_REVISION = "1";
    private static final String NEW_REVISION = "2";

    @Test
    void rejectsSnapshotWithOutdatedRevisionUsingXStream() {
        assertOutdatedSnapshotRejected(TestSerializer.xStreamSerializer());
    }

    @Test
    void rejectsSnapshotWithOutdatedRevisionUsingJackson() {
        assertOutdatedSnapshotRejected(JacksonSerializer.defaultSerializer());
    }

    @Test
    void allowsSnapshotWithMatchingRevisionUsingXStream() {
        assertMatchingSnapshotAllowed(TestSerializer.xStreamSerializer());
    }

    @Test
    void allowsSnapshotWithMatchingRevisionUsingJackson() {
        assertMatchingSnapshotAllowed(JacksonSerializer.defaultSerializer());
    }

    private void assertOutdatedSnapshotRejected(Serializer serializer) {
        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(SnapshottedAggregate.class)
                                      .revision(NEW_REVISION) // the stored snapshot still carries STORED_REVISION
                                      .build();

        DomainEventData<?> snapshot = snapshotUsing(serializer);

        assertEquals(STORED_REVISION, snapshot.getPayload().getType().getRevision(),
                     "Revision must be available as metadata regardless of the serializer");
        assertFalse(testSubject.allow(snapshot));
    }

    private void assertMatchingSnapshotAllowed(Serializer serializer) {
        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(SnapshottedAggregate.class)
                                      .revision(STORED_REVISION)
                                      .build();

        assertTrue(testSubject.allow(snapshotUsing(serializer)));
    }

    private DomainEventData<byte[]> snapshotUsing(Serializer serializer) {
        DomainEventMessage<Object> snapshot = new GenericDomainEventMessage<>(
                SnapshottedAggregate.class.getSimpleName(), "aggregate-id", 0, new SnapshottedAggregate());
        return new SnapshotEventEntry(snapshot, serializer);
    }

    @Revision(STORED_REVISION)
    private static class SnapshottedAggregate {

        private String state = "state";

        public String getState() {
            return state;
        }
    }
}
