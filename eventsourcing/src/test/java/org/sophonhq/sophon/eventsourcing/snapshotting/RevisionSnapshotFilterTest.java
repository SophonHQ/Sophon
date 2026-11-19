/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.sophonhq.sophon.eventsourcing.snapshotting;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.eventhandling.DomainEventData;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import org.sophonhq.sophon.eventsourcing.utils.TestSerializer;
import org.sophonhq.sophon.modelling.command.AggregateRoot;
import org.sophonhq.sophon.serialization.Revision;
import org.sophonhq.sophon.serialization.Serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating the {@link RevisionSnapshotFilter}.
 * <p>
 * Snapshots are stored under an aggregate's <em>declared type</em> (its {@link DomainEventData#getType()}), which
 * defaults to the simple class name and can be overridden through {@link AggregateRoot#type()}. The fixtures below
 * therefore store snapshots under that declared type, mirroring how snapshots are physically stored.
 *
 * @author Steven van Beelen
 */
class RevisionSnapshotFilterTest {

    private static final String EXPECTED_REVISION = "LET ME IN";
    private static final String OTHER_REVISION = "some-other-revision";

    private final Serializer serializer = TestSerializer.xStreamSerializer();

    @Test
    void allowsSnapshotMatchingDeclaredTypeAndRevision() {
        RevisionSnapshotFilter testSubject = filterFor(AggregateWithExpectedRevision.class, EXPECTED_REVISION);

        assertThat(testSubject.allow(snapshotOf(AggregateWithExpectedRevision.class,
                                                new AggregateWithExpectedRevision()))).isTrue();
    }

    /**
     * A filter only votes on its own aggregate type; snapshots of other aggregates must be allowed through, as filters
     * are combined in an AND operation across all aggregates.
     */
    @Test
    void allowsSnapshotOfAnotherAggregateType() {
        RevisionSnapshotFilter testSubject = filterFor(AggregateWithExpectedRevision.class, EXPECTED_REVISION);

        assertThat(testSubject.allow(snapshotOf(AggregateWithOtherRevision.class,
                                                new AggregateWithOtherRevision()))).isTrue();
    }

    @Test
    void disallowsSnapshotMatchingDeclaredTypeButWithWrongRevision() {
        RevisionSnapshotFilter testSubject = filterFor(AggregateWithOtherRevision.class, EXPECTED_REVISION);

        assertThat(testSubject.allow(snapshotOf(AggregateWithOtherRevision.class,
                                                new AggregateWithOtherRevision()))).isFalse();
    }

    /**
     * {@code type(Class)} must resolve to the declared type - the simple name here - to match the stored snapshot.
     */
    @Test
    void typeByClassResolvesToSimpleNameWhenNoAggregateRootTypeOverride() {
        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(AggregateWithExpectedRevision.class)
                                      .revision(EXPECTED_REVISION)
                                      .build();

        assertThat(testSubject.allow(snapshotOf(AggregateWithExpectedRevision.class,
                                                new AggregateWithExpectedRevision()))).isTrue();
    }

    /**
     * The snapshot is stored under the {@link AggregateRoot#type()} value, which is what {@code type(Class)} must
     * resolve to.
     */
    @Test
    void typeByClassHonorsAggregateRootTypeOverride() {
        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(CustomTypedAggregate.class)
                                      .revision(EXPECTED_REVISION)
                                      .build();

        assertThat(testSubject.allow(snapshotOf("custom-type", new CustomTypedAggregate()))).isTrue();
    }

    /**
     * Configuring the filter with a fully-qualified class name while snapshots are stored under the declared (simple)
     * type is a misconfiguration: the types never match, so the filter abstains (returns {@code true}) without
     * evaluating the revision, and an outdated snapshot is kept.
     */
    @Test
    void fullyQualifiedTypeDoesNotMatchDeclaredTypeAndAllowsSnapshot() {
        String fullyQualifiedType = AggregateWithOtherRevision.class.getName();
        assertThat(AggregateWithOtherRevision.class.getSimpleName()).isNotEqualTo(fullyQualifiedType);

        RevisionSnapshotFilter testSubject =
                RevisionSnapshotFilter.builder()
                                      .type(fullyQualifiedType)
                                      .revision(EXPECTED_REVISION)
                                      .build();

        DomainEventData<byte[]> outdatedSnapshot =
                snapshotOf(AggregateWithOtherRevision.class, new AggregateWithOtherRevision());

        assertThat(testSubject.allow(outdatedSnapshot))
                .as("A fully-qualified filter type never matches the stored simple-name type, "
                            + "so the filter keeps the outdated snapshot")
                .isTrue();
    }

    @Test
    void buildWithNullOrEmptyTypeThrowsSophonConfigurationException() {
        RevisionSnapshotFilter.Builder builderTestSubject = RevisionSnapshotFilter.builder();

        assertThatThrownBy(() -> builderTestSubject.type("")).isInstanceOf(SophonConfigurationException.class);
        assertThatThrownBy(() -> builderTestSubject.type((String) null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNullOrEmptyRevisionThrowsSophonConfigurationException() {
        RevisionSnapshotFilter.Builder builderTestSubject = RevisionSnapshotFilter.builder();

        assertThatThrownBy(() -> builderTestSubject.revision("")).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithoutTypeThrowsSophonConfigurationException() {
        RevisionSnapshotFilter.Builder builderTestSubject = RevisionSnapshotFilter.builder()
                                                                                  .revision(EXPECTED_REVISION);

        assertThatThrownBy(builderTestSubject::build).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithBlankRevisionThrowsSophonConfigurationException() {
        assertThatThrownBy(() -> RevisionSnapshotFilter.builder()
                                                       .type(AggregateWithExpectedRevision.class)
                                                       .revision("")
                                                       .build())
                .isInstanceOf(SophonConfigurationException.class);
    }

    private RevisionSnapshotFilter filterFor(Class<?> aggregateType, String revision) {
        return RevisionSnapshotFilter.builder()
                                     .type(aggregateType.getSimpleName())
                                     .revision(revision)
                                     .build();
    }

    private DomainEventData<byte[]> snapshotOf(Class<?> aggregateType, Object payload) {
        return snapshotOf(aggregateType.getSimpleName(), payload);
    }

    private DomainEventData<byte[]> snapshotOf(String storedAggregateType, Object payload) {
        DomainEventMessage<Object> snapshot =
                new GenericDomainEventMessage<>(storedAggregateType, "aggregate-id", 0, payload);
        return new SnapshotEventEntry(snapshot, serializer);
    }

    @Revision(EXPECTED_REVISION)
    private static class AggregateWithExpectedRevision {

    }

    @Revision(OTHER_REVISION)
    private static class AggregateWithOtherRevision {

    }

    @Revision(EXPECTED_REVISION)
    @AggregateRoot(type = "custom-type")
    private static class CustomTypedAggregate {

    }
}
