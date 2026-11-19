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

package org.sophonhq.sophon.springboot;

import com.thoughtworks.xstream.XStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.sophonhq.sophon.eventsourcing.utils.TestSerializer;
import org.sophonhq.sophon.modelling.command.Repository;
import org.sophonhq.sophon.spring.stereotype.Aggregate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.common.StringUtils.lowerCaseFirstCharacterOf;

/**
 * Test class validating {@link Repository} beans are wired as intended to an external command handler.
 *
 * @author Steven van Beelen
 */
class RepositoryWiringTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withUserConfiguration(DefaultContext.class);
    }

    @Test
    void aggregateRepositoryIsWiredToExternalCommandHandler() {
        testApplicationContext.withUserConfiguration(SingleAggregateContext.class).run(context -> {
            SingleAggregateContext.ExternalCommandHandlerForAggregateOne externalHandler =
                    context.getBean(SingleAggregateContext.ExternalCommandHandlerForAggregateOne.class);
            assertThat(externalHandler).isNotNull();

            Repository<SingleAggregateContext.AggregateOne> repositoryFromHandler = externalHandler.getRepository();
            assertThat(repositoryFromHandler).isNotNull();
            Object repositoryFromContext =
                    context.getBean(repositoryBeanName(SingleAggregateContext.AggregateOne.class));
            assertThat(repositoryFromContext).isNotNull();

            assertThat(repositoryFromContext).isEqualTo(repositoryFromHandler);
        });
    }

    @Test
    void aggregateRepositoriesAreWiredToExternalCommandHandlerBasedOnGenerics() {
        testApplicationContext.withUserConfiguration(SeveralAggregatesContext.class).run(context -> {
            SeveralAggregatesContext.ExternalCommandHandlerWiringThroughGenerics externalHandler =
                    context.getBean(SeveralAggregatesContext.ExternalCommandHandlerWiringThroughGenerics.class);
            assertThat(externalHandler).isNotNull();

            Repository<SeveralAggregatesContext.AggregateOne> repositoryOneFromHandler =
                    externalHandler.getRepositoryOne();
            assertThat(repositoryOneFromHandler).isNotNull();
            Object repositoryOneFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateOne.class));
            assertThat(repositoryOneFromContext).isNotNull();
            assertThat(repositoryOneFromContext).isEqualTo(repositoryOneFromHandler);

            Repository<SeveralAggregatesContext.AggregateTwo> repositoryTwoFromHandler =
                    externalHandler.getRepositoryTwo();
            assertThat(repositoryTwoFromHandler).isNotNull();
            Object repositoryTwoFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateTwo.class));
            assertThat(repositoryTwoFromContext).isNotNull();
            assertThat(repositoryTwoFromContext).isEqualTo(repositoryTwoFromHandler);

            Repository<SeveralAggregatesContext.AggregateThree> repositoryThreeFromHandler =
                    externalHandler.getRepositoryThree();
            assertThat(repositoryThreeFromHandler).isNotNull();
            Object repositoryThreeFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateThree.class));
            assertThat(repositoryThreeFromContext).isNotNull();
            assertThat(repositoryThreeFromContext).isEqualTo(repositoryThreeFromHandler);
        });
    }

    @Test
    void aggregateRepositoriesAreWiredToExternalCommandHandlerBasedOnBeanName() {
        testApplicationContext.withUserConfiguration(SeveralAggregatesContext.class).run(context -> {
            SeveralAggregatesContext.ExternalCommandHandlerWiringThroughBeanNames externalHandler =
                    context.getBean(SeveralAggregatesContext.ExternalCommandHandlerWiringThroughBeanNames.class);
            assertThat(externalHandler).isNotNull();

            Repository<?> repositoryOneFromHandler = externalHandler.getRepositoryOne();
            assertThat(repositoryOneFromHandler).isNotNull();
            Object repositoryOneFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateOne.class));
            assertThat(repositoryOneFromContext).isNotNull();
            assertThat(repositoryOneFromContext).isEqualTo(repositoryOneFromHandler);

            Repository<?> repositoryTwoFromHandler = externalHandler.getRepositoryTwo();
            assertThat(repositoryTwoFromHandler).isNotNull();
            Object repositoryTwoFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateTwo.class));
            assertThat(repositoryTwoFromContext).isNotNull();
            assertThat(repositoryTwoFromContext).isEqualTo(repositoryTwoFromHandler);

            Repository<?> repositoryThreeFromHandler = externalHandler.getRepositoryThree();
            assertThat(repositoryThreeFromHandler).isNotNull();
            Object repositoryThreeFromContext =
                    context.getBean(repositoryBeanName(SeveralAggregatesContext.AggregateThree.class));
            assertThat(repositoryThreeFromContext).isNotNull();
            assertThat(repositoryThreeFromContext).isEqualTo(repositoryThreeFromHandler);
        });
    }

    private static String repositoryBeanName(Class<?> aggregateClass) {
        return lowerCaseFirstCharacterOf(aggregateClass.getSimpleName()) + "Repository";
    }

    @Configuration
    @EnableAutoConfiguration
    static class DefaultContext {

        @Bean
        public EventStorageEngine eventStorageEngine() {
            return new InMemoryEventStorageEngine();
        }

        @Bean
        public XStream xStream() {
            return TestSerializer.xStreamSerializer().getXStream();
        }
    }

    @Configuration
    static class SingleAggregateContext {

        @Aggregate
        static class AggregateOne {

            public AggregateOne() {
            }
        }

        @Component
        static class ExternalCommandHandlerForAggregateOne {

            private final Repository<AggregateOne> repository;

            ExternalCommandHandlerForAggregateOne(Repository<AggregateOne> repository) {
                this.repository = repository;
            }

            public Repository<AggregateOne> getRepository() {
                return repository;
            }
        }
    }

    @Configuration
    static class SeveralAggregatesContext {

        @Aggregate
        static class AggregateOne {

            public AggregateOne() {
            }
        }

        @Aggregate
        static class AggregateTwo {

            public AggregateTwo() {
            }
        }

        @Aggregate
        static class AggregateThree {

            public AggregateThree() {
            }
        }

        @Component
        static class ExternalCommandHandlerWiringThroughGenerics {

            private final Repository<AggregateOne> repositoryOne;
            private final Repository<AggregateTwo> repositoryTwo;
            private final Repository<AggregateThree> repositoryThree;

            ExternalCommandHandlerWiringThroughGenerics(Repository<AggregateOne> repositoryOne,
                                                        Repository<AggregateTwo> repositoryTwo,
                                                        Repository<AggregateThree> repositoryThree) {
                this.repositoryOne = repositoryOne;
                this.repositoryTwo = repositoryTwo;
                this.repositoryThree = repositoryThree;
            }

            public Repository<AggregateOne> getRepositoryOne() {
                return repositoryOne;
            }

            public Repository<AggregateTwo> getRepositoryTwo() {
                return repositoryTwo;
            }

            public Repository<AggregateThree> getRepositoryThree() {
                return repositoryThree;
            }
        }

        @Component
        static class ExternalCommandHandlerWiringThroughBeanNames {

            private final Repository<?> repositoryOne;
            private final Repository<?> repositoryTwo;
            private final Repository<?> repositoryThree;

            ExternalCommandHandlerWiringThroughBeanNames(
                    @Qualifier("aggregateOneRepository") Repository<?> repositoryOne,
                    @Qualifier("aggregateTwoRepository") Repository<?> repositoryTwo,
                    @Qualifier("aggregateThreeRepository") Repository<?> repositoryThree
            ) {
                this.repositoryOne = repositoryOne;
                this.repositoryTwo = repositoryTwo;
                this.repositoryThree = repositoryThree;
            }

            public Repository<?> getRepositoryOne() {
                return repositoryOne;
            }

            public Repository<?> getRepositoryTwo() {
                return repositoryTwo;
            }

            public Repository<?> getRepositoryThree() {
                return repositoryThree;
            }
        }
    }
}
