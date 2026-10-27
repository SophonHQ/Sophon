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

package org.sophonhq.sophon.eventsourcing;

import org.jspecify.annotations.NonNull;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.TrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.eventsourcing.eventstore.DomainEventStream;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of EventStorageEngine that delegates to another implementation, while filtering
 * events as they are appended. This prevents certain events to be stored in the Event Store.
 *
 * @author Allard Buijze
 * @since axon-3.1
 */
public class FilteringEventStorageEngine implements EventStorageEngine {

    private final EventStorageEngine delegate;
    private final Predicate<? super EventMessage<?>> filter;

    /**
     * Initializes the FilteringEventStorageEngine delegating all event messages matching the given {@code filter} to
     * the given {@code delegate}.
     * <p>
     * Note that this only affects events stored in the StorageEngine. The {@code EventStore} will still publish these
     * events to Subscribed event handlers. Tracking Event Processors take their events from the stored events, and
     * will therefore not receive any events blocked by this instance.
     *
     * @param delegate the EventStorageEngine to store matching messages in
     * @param filter   the predicate that event messages must match against to be stored
     */
    public FilteringEventStorageEngine(EventStorageEngine delegate, Predicate<? super EventMessage<?>> filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public void appendEvents(EventMessage<?> @NonNull ... events) {
        delegate.appendEvents(Arrays.stream(events).filter(filter).collect(Collectors.toList()));
    }

    @Override
    public void appendEvents(@NonNull List<? extends EventMessage<?>> events) {
        delegate.appendEvents(events.stream().filter(filter).collect(Collectors.toList()));
    }

    @Override
    public void storeSnapshot(@NonNull DomainEventMessage<?> snapshot) {
        delegate.storeSnapshot(snapshot);
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> readEvents(TrackingToken trackingToken, boolean mayBlock) {
        return delegate.readEvents(trackingToken, mayBlock);
    }

    @Override
    public DomainEventStream readEvents(@NonNull String aggregateIdentifier) {
        return delegate.readEvents(aggregateIdentifier);
    }

    @Override
    public DomainEventStream readEvents(@NonNull String aggregateIdentifier, long firstSequenceNumber) {
        return delegate.readEvents(aggregateIdentifier, firstSequenceNumber);
    }

    @Override
    public Optional<DomainEventMessage<?>> readSnapshot(@NonNull String aggregateIdentifier) {
        return delegate.readSnapshot(aggregateIdentifier);
    }

    @Override
    public Optional<Long> lastSequenceNumberFor(@NonNull String aggregateIdentifier) {
        return delegate.lastSequenceNumberFor(aggregateIdentifier);
    }

    @Override
    public TrackingToken createTailToken() {
        return delegate.createTailToken();
    }

    @Override
    public TrackingToken createHeadToken() {
        return delegate.createHeadToken();
    }

    @Override
    public TrackingToken createTokenAt(@NonNull Instant dateTime) {
        return delegate.createTokenAt(dateTime);
    }
}
