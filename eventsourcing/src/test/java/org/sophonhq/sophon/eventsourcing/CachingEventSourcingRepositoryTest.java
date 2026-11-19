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

package org.sophonhq.sophon.eventsourcing;

import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.core.Ehcache;
import org.ehcache.core.EhcacheManager;
import org.ehcache.core.config.DefaultConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.caching.Cache;
import org.sophonhq.sophon.common.caching.EhCache3Adapter;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventsourcing.eventstore.DomainEventStream;
import org.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.sophonhq.sophon.eventsourcing.utils.MockException;
import org.sophonhq.sophon.eventsourcing.utils.StubAggregate;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.UnitOfWork;
import org.sophonhq.sophon.modelling.command.Aggregate;
import org.sophonhq.sophon.modelling.command.AggregateLifecycle;
import org.sophonhq.sophon.modelling.command.LockAwareAggregate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class CachingEventSourcingRepositoryTest {

    private CachingEventSourcingRepository<StubAggregate> testSubject;
    private EventStore mockEventStore;
    private Cache cache;
    private org.ehcache.core.Ehcache ehCache;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        mockEventStore = spy(EmbeddedEventStore.builder().storageEngine(new InMemoryEventStorageEngine()).build());
        Map<String, CacheConfiguration<?, ?>> caches = new HashMap<>();
        DefaultConfiguration config = new DefaultConfiguration(caches, null);
        cacheManager = new EhcacheManager(config);
        cacheManager.init();
        ehCache = (Ehcache) cacheManager
                .createCache(
                        "testCache",
                        CacheConfigurationBuilder
                                .newCacheConfigurationBuilder(
                                        Object.class,
                                        Object.class,
                                        ResourcePoolsBuilder.heap(100L).build())
                                .build());
        cache = spy(new EhCache3Adapter(ehCache));

        testSubject = CachingEventSourcingRepository.builder(StubAggregate.class)
                                                    .aggregateFactory(new StubAggregateFactory())
                                                    .eventStore(mockEventStore)
                                                    .cache(cache)
                                                    .build();
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
        cacheManager.close();
    }

    @Test
    void aggregatesRetrievedFromCache() throws Exception {
        startAndGetUnitOfWork();

        LockAwareAggregate<StubAggregate, EventSourcedAggregate<StubAggregate>> aggregate1 =
                testSubject.newInstance(() -> new StubAggregate("aggregateId"));
        aggregate1.execute(StubAggregate::doSomething);
        assertThat(aggregate1.getWrappedAggregate().lastSequence()).isZero();
        CurrentUnitOfWork.commit();

        startAndGetUnitOfWork();
        LockAwareAggregate<StubAggregate, EventSourcedAggregate<StubAggregate>> reloadedAggregate1 =
                testSubject.load("aggregateId", null);
        assertThat(reloadedAggregate1.getWrappedAggregate().lastSequence()).isZero();
        aggregate1.execute(StubAggregate::doSomething);
        aggregate1.execute(StubAggregate::doSomething);
        assertThat(aggregate1.getWrappedAggregate().lastSequence()).isEqualTo(2);
        CurrentUnitOfWork.commit();

        DefaultUnitOfWork.startAndGet(null);
        DomainEventStream events = mockEventStore.readEvents("aggregateId");
        List<EventMessage> eventList = new ArrayList<>();
        while (events.hasNext()) {
            eventList.add(events.next());
        }
        assertThat(eventList).hasSize(3);
        ehCache.clear();

        reloadedAggregate1 = testSubject.load(aggregate1.identifierAsString(), null);

        assertThat(reloadedAggregate1.getWrappedAggregate()).isNotSameAs(aggregate1.getWrappedAggregate());
        assertThat(reloadedAggregate1.version()).isEqualTo(aggregate1.version());
        assertThat(reloadedAggregate1.getWrappedAggregate().lastSequence()).isEqualTo(2);
    }

    @Test
    void loadOrCreateNewAggregate() {
        startAndGetUnitOfWork();
        Aggregate<StubAggregate> aggregate = testSubject.loadOrCreate("id1", StubAggregate::new);
        aggregate.execute(s -> s.setIdentifier("id1"));

        CurrentUnitOfWork.commit();

        assertThatObject(cache.get("id1")).isNotNull();
        verify(cache, never()).put(isNull(), any());
    }

    @Test
    void loadDeletedAggregate() throws Exception {
        String identifier = "aggregateId";

        startAndGetUnitOfWork();
        Aggregate<StubAggregate> aggregate1 = testSubject.newInstance(() -> new StubAggregate(identifier));
        CurrentUnitOfWork.commit();


        startAndGetUnitOfWork();
        testSubject.load(identifier).execute((r) -> AggregateLifecycle.markDeleted());
        CurrentUnitOfWork.commit();

        startAndGetUnitOfWork();
        try {
            testSubject.load(identifier);
            fail("Expected AggregateDeletedException");
        } catch (AggregateDeletedException e) {
            assertThat(e.getMessage()).contains(identifier);
        } finally {
            CurrentUnitOfWork.commit();
        }
    }

    @Test
    void cacheClearedAfterRollbackOfAddedAggregate() throws Exception {
        UnitOfWork<?> uow = startAndGetUnitOfWork();
        uow.onCommit(c -> { throw new MockException();});
        try {
            testSubject.newInstance(() -> new StubAggregate("id1")).execute(StubAggregate::doSomething);
            uow.commit();
        } catch (MockException e) {
            // great, that's what we expect
        }
        assertThatObject(cache.get("id1")).isNull();
    }

    @Test
    void cacheClearedAfterRollbackOfLoadedAggregate() {

        startAndGetUnitOfWork().executeWithResult(() -> testSubject.newInstance(() -> new StubAggregate("id1")));

        UnitOfWork<?> uow = startAndGetUnitOfWork();
        uow.onCommit(c -> {
            throw new MockException();
        });
        try {
            testSubject.load("id1").execute(StubAggregate::doSomething);
            uow.commit();
        } catch (MockException e) {
            // great, that's what we expect
        }
        assertThatObject(cache.get("id1")).isNull();
    }

    @Test
    void cacheClearedAfterRollbackOfLoadedAggregateUsingLoadOrCreate() throws Exception {

        startAndGetUnitOfWork().executeWithResult(() -> testSubject.newInstance(() -> new StubAggregate("id1")));

        UnitOfWork<?> uow = startAndGetUnitOfWork();
        uow.onCommit(c -> { throw new MockException();});
        try {
            testSubject.loadOrCreate("id1", () -> new StubAggregate("id1")).execute(StubAggregate::doSomething);
            uow.commit();
        } catch (MockException e) {
            // great, that's what we expect
        }
        assertThatObject(cache.get("id1")).isNull();
    }

    @Test
    void cacheClearedAfterRollbackOfCreatedAggregateUsingLoadOrCreate() throws Exception {

        UnitOfWork<?> uow = startAndGetUnitOfWork();
        uow.onCommit(c -> { throw new MockException();});
        try {
            testSubject.loadOrCreate("id1", () -> new StubAggregate("id1")).execute(StubAggregate::doSomething);
            uow.commit();
        } catch (MockException e) {
            // great, that's what we expect
        }
        assertThatObject(cache.get("id1")).isNull();
    }

    @Test
    void committedVersionFromSupplierIsUsedWhenPresent() throws Exception {
        startAndGetUnitOfWork();
        testSubject.newInstance(() -> new StubAggregate("aggregateId")).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        // Simulate a DCB-style event store that registers a corrected sequence supplier (e.g. global token 42).
        startAndGetUnitOfWork();
        CurrentUnitOfWork.get().resources()
                         .put(CachingEventSourcingRepository.COMMITTED_SEQUENCE_SUPPLIER_KEY_PREFIX + "aggregateId",
                              (Supplier<Optional<Long>>) () -> Optional.of(42L));
        testSubject.load("aggregateId").execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        AggregateCacheEntry<?> updated = (AggregateCacheEntry<?>) cache.get("aggregateId");
        assertThat(updated).isNotNull();
        // Version should be 42 (from the supplier), not 1 (the locally-predicted sequence).
        assertThat(updated.getVersion()).isEqualTo(42L);
    }

    @Test
    void aggregateNotCachedWhenSupplierSignalsConcurrentCommit() throws Exception {
        startAndGetUnitOfWork();
        testSubject.newInstance(() -> new StubAggregate("aggregateId")).execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        cache.remove("aggregateId");

        // Supplier returns empty — another node committed for this aggregate after ours.
        startAndGetUnitOfWork();
        CurrentUnitOfWork.get().resources()
                         .put(CachingEventSourcingRepository.COMMITTED_SEQUENCE_SUPPLIER_KEY_PREFIX + "aggregateId",
                              (Supplier<Optional<Long>>) Optional::empty);
        testSubject.load("aggregateId").execute(StubAggregate::doSomething);
        CurrentUnitOfWork.commit();

        assertThatObject(cache.get("aggregateId")).isNull();
    }

    private UnitOfWork<?> startAndGetUnitOfWork() {
        return DefaultUnitOfWork.startAndGet(null);
    }

    private static class StubAggregateFactory extends AbstractAggregateFactory<StubAggregate> {

        public StubAggregateFactory() {
            super(StubAggregate.class);
        }

        @Override
        public StubAggregate doCreateAggregate(String aggregateIdentifier, DomainEventMessage firstEvent) {
            return new StubAggregate();
        }

        @Override
        public Class<StubAggregate> getAggregateType() {
            return StubAggregate.class;
        }
    }
}
