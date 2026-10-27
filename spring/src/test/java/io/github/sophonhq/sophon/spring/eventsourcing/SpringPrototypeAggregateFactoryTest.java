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

package io.github.sophonhq.sophon.spring.eventsourcing;

import io.github.sophonhq.sophon.config.Configurer;
import io.github.sophonhq.sophon.config.ConfigurerModule;
import io.github.sophonhq.sophon.config.ModuleConfiguration;
import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import io.github.sophonhq.sophon.eventsourcing.AggregateFactory;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import io.github.sophonhq.sophon.spring.config.SpringAggregateLookup;
import io.github.sophonhq.sophon.spring.config.SpringConfigurer;
import io.github.sophonhq.sophon.spring.config.SpringSophonConfiguration;
import io.github.sophonhq.sophon.spring.eventsourcing.context.SpringWiredAggregate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SpringPrototypeAggregateFactory}.
 *
 * @author Allard Buijze
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SpringPrototypeAggregateFactoryTest.Context.class)
class SpringPrototypeAggregateFactoryTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private AggregateFactory<SpringWiredAggregate> testSubject;

    @Test
    void contextStarts() {
        assertNotNull(testSubject);
    }

    @Test
    void createNewAggregateInstance() {
        GenericDomainEventMessage<String> domainEvent = new GenericDomainEventMessage<>(
                "SpringWiredAggregate", "id2", 0, "FirstEvent"
        );
        SpringWiredAggregate aggregate = testSubject.createAggregateRoot("id2", domainEvent);

        assertNotNull(aggregate.getContext(), "ContextAware method not invoked");
    }

    @Test
    void processSnapshotAggregateInstance() {
        DomainEventMessage<SpringWiredAggregate> snapshotEvent = new GenericDomainEventMessage<>(
                "SpringWiredAggregate", "id2", 5, new SpringWiredAggregate()
        );
        SpringWiredAggregate aggregate = testSubject.createAggregateRoot("id2", snapshotEvent);

        assertNotNull(aggregate.getContext(), "ContextAware method not invoked");
    }

    @Configuration
    @ComponentScan(basePackages = {"io.github.sophonhq.sophon.spring.eventsourcing.context"})
    static class Context {

        // Wired to ensure an EventStore is present for the aggregate to allow event sourcing.
        @Bean
        public EmbeddedEventStore eventStore() {
            return EmbeddedEventStore.builder()
                                     .storageEngine(new InMemoryEventStorageEngine())
                                     .build();
        }

        /**
         * The below three bean methods are a copy of the
         * {@code io.github.sophonhq.sophon.springboot.autoconfig.InfraConfiguration} to set up the basics to auto-wired
         * Aggregates.
         * <p>
         * Copied to keep this test Spring Boot agnostic.
         */
        @Bean
        public static SpringAggregateLookup springAggregateLookup() {
            return new SpringAggregateLookup();
        }

        @Bean
        public SpringSophonConfiguration springSophonConfiguration(Configurer configurer) {
            return new SpringSophonConfiguration(configurer);
        }

        @Bean
        public SpringConfigurer springSophonConfigurer(ConfigurableListableBeanFactory beanFactory,
                                                     List<ConfigurerModule> configurerModules,
                                                     List<ModuleConfiguration> moduleConfigurations) {
            SpringConfigurer configurer = new SpringConfigurer(beanFactory);
            moduleConfigurations.forEach(configurer::registerModule);
            configurerModules.forEach(c -> c.configureModule(configurer));
            return configurer;
        }
    }
}
