/*
 * Copyright (c) 2010-2023. Axon Framework
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

import io.github.sophonhq.sophon.eventhandling.EventBus;
import io.github.sophonhq.sophon.modelling.command.Repository;
import io.github.sophonhq.sophon.modelling.command.RepositoryProvider;
import io.github.sophonhq.sophon.modelling.command.legacyjpa.GenericJpaRepository;
import org.jspecify.annotations.NonNull;

import javax.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;

/**
 * Tests JPA aggregate polymorphism.
 *
 * @author Milan Savic
 */
public class PolymorphicJpaAggregateAnnotationCommandHandlerTest
        extends AbstractPolymorphicAggregateAnnotationCommandHandlerTestSuite {

    private static final Map<Class<?>, Repository<?>> repositories = new HashMap<>();

    @Override
    public <T> Repository<T> repository(Class<T> aggregateType,
                                        Set<Class<? extends T>> subTypes,
                                        EntityManager entityManager) {
        GenericJpaRepository<T> repository = GenericJpaRepository
                .builder(aggregateType)
                .subtypes(subTypes)
                .entityManagerProvider(() -> entityManager)
                .repositoryProvider(new RepositoryProvider() {
                    @Override
                    public <R> Repository<R> repositoryFor(@NonNull Class<R> aggregateType) {
                        //noinspection unchecked
                        return (Repository<R>) repositories.get(aggregateType);
                    }
                })
                .eventBus(mock(EventBus.class))
                .build();
        repositories.put(aggregateType, repository);
        return repository;
    }
}
