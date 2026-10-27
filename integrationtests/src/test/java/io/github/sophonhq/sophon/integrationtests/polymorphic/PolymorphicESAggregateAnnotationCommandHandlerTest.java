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

package io.github.sophonhq.sophon.integrationtests.polymorphic;

import io.github.sophonhq.sophon.eventsourcing.EventSourcingRepository;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import io.github.sophonhq.sophon.modelling.command.Repository;
import io.github.sophonhq.sophon.modelling.command.RepositoryProvider;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.persistence.EntityManager;

/**
 * Tests for ES aggregate polymorphism.
 *
 * @author Milan Savic
 */
public class PolymorphicESAggregateAnnotationCommandHandlerTest
        extends AbstractPolymorphicAggregateAnnotationCommandHandlerTestSuite {

    private static final Map<Class<?>, Repository<?>> repositories = new HashMap<>();

    @Override
    public <T> Repository<T> repository(Class<T> aggregateType,
                                        Set<Class<? extends T>> subTypes,
                                        EntityManager entityManager) {
        EventSourcingRepository<T> repository = EventSourcingRepository
                .builder(aggregateType)
                .subtypes(subTypes)
                .eventStore(EmbeddedEventStore.builder()
                                              .storageEngine(new InMemoryEventStorageEngine())
                                              .build())
                .repositoryProvider(new RepositoryProvider() {
                    @Override
                    public <R> Repository<R> repositoryFor(@NonNull Class<R> aggregateType) {
                        //noinspection unchecked
                        return (Repository<R>) repositories.get(aggregateType);
                    }
                })
                .build();
        repositories.put(aggregateType, repository);
        return repository;
    }
}
