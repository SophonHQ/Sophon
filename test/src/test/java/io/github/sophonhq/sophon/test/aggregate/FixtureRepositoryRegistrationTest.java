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

package io.github.sophonhq.sophon.test.aggregate;

import io.github.sophonhq.sophon.commandhandling.CommandHandler;
import io.github.sophonhq.sophon.common.jpa.SimpleEntityManagerProvider;
import io.github.sophonhq.sophon.eventhandling.SimpleEventBus;
import io.github.sophonhq.sophon.eventhandling.TimestampParameterResolverFactory;
import io.github.sophonhq.sophon.eventsourcing.EventSourcingHandler;
import io.github.sophonhq.sophon.eventsourcing.EventSourcingRepository;
import io.github.sophonhq.sophon.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import io.github.sophonhq.sophon.modelling.command.AggregateIdentifier;
import io.github.sophonhq.sophon.modelling.command.GenericJpaRepository;
import io.github.sophonhq.sophon.modelling.command.Repository;
import io.github.sophonhq.sophon.modelling.command.RepositoryProvider;
import io.github.sophonhq.sophon.modelling.saga.SagaMethodMessageHandlerDefinition;
import io.github.sophonhq.sophon.test.FixtureExecutionException;
import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Test class validating the following behavior around the fixture's {@link Repository}:
 * <ul>
 *     <li>The default {@code Repository} of the fixture can be wired in message handling methods.</li>
 *     <li>A registered {@code Repository} in the fixture can be wired in message handling methods.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link RepositoryProvider} is registered after the {@code Repository} is defined.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link io.github.sophonhq.sophon.messaging.annotation.ParameterResolverFactory} is registered after the {@code Repository} is defined.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link io.github.sophonhq.sophon.messaging.annotation.HandlerDefinition} is registered after the {@code Repository} is defined.</li>
 *     <li>A {@link FixtureExecutionException} is thrown whenever a {@link io.github.sophonhq.sophon.messaging.annotation.HandlerEnhancerDefinition} is registered after the {@code Repository} is defined.</li>
 * </ul>
 *
 * @author Steven van Beelen
 */
class FixtureRepositoryRegistrationTest {

    private AggregateTestFixture<MyAggregate> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AggregateTestFixture<>(MyAggregate.class);
    }

    @Test
    void fixturesDefaultRepositoryCanBeWiredAsMessageHandlingParameter() {
        testSubject.givenNoPriorActivity()
                   .when("some-command")
                   .expectEvents("some-command_" + EventSourcingRepository.class.getSimpleName());
    }

    @Test
    void customRepositoryCanBeWiredAsMessageHandlingParameter() {
        Repository<MyAggregate> testRepository =
                CustomRepository.builder()
                                .eventStore(testSubject.getEventStore())
                                .build();

        testSubject.registerRepository(testRepository)
                   .givenNoPriorActivity()
                   .when("some-command")
                   .expectEvents("some-command_" + testRepository.getClass().getSimpleName());
    }

    @Test
    void registeringTheRepositoryProviderThrowsFixtureExecutionExceptionAfterRegisteringTheRepository() {
        Repository<MyAggregate> testRepository =
                GenericJpaRepository.builder(MyAggregate.class)
                                    .entityManagerProvider(new SimpleEntityManagerProvider(mock(EntityManager.class)))
                                    .eventBus(SimpleEventBus.builder().build())
                                    .build();
        testSubject.registerRepository(testRepository);

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerRepositoryProvider(new RepositoryProvider() {
                         @Override
                         public <T> Repository<T> repositoryFor(@NonNull Class<T> aggregateType) {
                             return null;
                         }
                     }));
    }

    @Test
    void registeringTheRepositoryProviderThrowsFixtureExecutionExceptionAfterRetrievingTheRepository() {
        testSubject.getRepository();

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerRepositoryProvider(new RepositoryProvider() {
                         @Override
                         public <T> Repository<T> repositoryFor(@NonNull Class<T> aggregateType) {
                             return null;
                         }
                     }));
    }

    @Test
    void registeringParameterResolversThrowsFixtureExecutionExceptionAfterRegisteringTheRepository() {
        Repository<MyAggregate> testRepository =
                GenericJpaRepository.builder(MyAggregate.class)
                                    .entityManagerProvider(new SimpleEntityManagerProvider(mock(EntityManager.class)))
                                    .eventBus(SimpleEventBus.builder().build())
                                    .build();
        testSubject.registerRepository(testRepository);

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerParameterResolverFactory(new TimestampParameterResolverFactory()));
    }

    @Test
    void registeringParameterResolversThrowsFixtureExecutionExceptionAfterRetrievingTheRepository() {
        testSubject.getRepository();

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerParameterResolverFactory(new TimestampParameterResolverFactory()));
    }

    @Test
    void registeringHandlerDefinitionThrowsFixtureExecutionExceptionAfterRegisteringTheRepository() {
        Repository<MyAggregate> testRepository =
                GenericJpaRepository.builder(MyAggregate.class)
                                    .entityManagerProvider(new SimpleEntityManagerProvider(mock(EntityManager.class)))
                                    .eventBus(SimpleEventBus.builder().build())
                                    .build();
        testSubject.registerRepository(testRepository);

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerHandlerDefinition(new AnnotatedMessageHandlingMemberDefinition()));
    }

    @Test
    void registeringHandlerDefinitionThrowsFixtureExecutionExceptionAfterRetrievingTheRepository() {
        testSubject.getRepository();

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerHandlerDefinition(new AnnotatedMessageHandlingMemberDefinition()));
    }

    @Test
    void registeringHandlerEnhancersThrowsFixtureExecutionExceptionAfterRegisteringTheRepository() {
        Repository<MyAggregate> testRepository =
                GenericJpaRepository.builder(MyAggregate.class)
                                    .entityManagerProvider(new SimpleEntityManagerProvider(mock(EntityManager.class)))
                                    .eventBus(SimpleEventBus.builder().build())
                                    .build();
        testSubject.registerRepository(testRepository);

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerHandlerEnhancerDefinition(new SagaMethodMessageHandlerDefinition()));
    }

    @Test
    void registeringHandlerEnhancersThrowsFixtureExecutionExceptionAfterRetrievingTheRepository() {
        testSubject.getRepository();

        assertThrows(FixtureExecutionException.class,
                     () -> testSubject.registerHandlerEnhancerDefinition(new SagaMethodMessageHandlerDefinition()));
    }

    private static class MyAggregate {

        @AggregateIdentifier
        private String aggregateId;

        @CommandHandler
        public MyAggregate(String command, Repository<MyAggregate> repository) {
            apply(command + "_" + repository.getClass().getSimpleName());
        }

        @EventSourcingHandler
        public void on(String event) {
            aggregateId = event;
        }

        @SuppressWarnings("unused")
        public MyAggregate() {
            // Required by Sophon
        }
    }

    private static class CustomRepository extends EventSourcingRepository<MyAggregate> {

        protected CustomRepository(Builder builder) {
            super(builder);
        }

        public static Builder builder() {
            return new Builder(MyAggregate.class);
        }

        private static class Builder extends EventSourcingRepository.Builder<MyAggregate> {

            protected Builder(Class<MyAggregate> aggregateType) {
                super(aggregateType);
            }

            @SuppressWarnings("unchecked")
            public CustomRepository build() {
                return new CustomRepository(this);
            }
        }
    }
}
