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

package io.github.sophonhq.sophon.spring.config;

import io.github.sophonhq.sophon.commandhandling.CommandHandler;
import io.github.sophonhq.sophon.common.jpa.EntityManagerProvider;
import io.github.sophonhq.sophon.config.EventProcessingModule;
import io.github.sophonhq.sophon.eventsourcing.EventSourcingHandler;
import io.github.sophonhq.sophon.eventsourcing.EventSourcingRepository;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import io.github.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import io.github.sophonhq.sophon.modelling.command.Repository;
import io.github.sophonhq.sophon.spring.stereotype.Aggregate;
import jakarta.persistence.Id;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static io.github.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration
public class AutoWiredEventSourcedAggregateTest {

    @Autowired
    private Repository<Context.MyAggregate> myAggregateRepository;

    @Test
    void aggregateIsWiredUsingStateStorage() {
        assertEquals(EventSourcingRepository.class, myAggregateRepository.getClass());
    }

    @Import(SpringSophonAutoConfigurer.ImportSelector.class)
    @Scope
    @Configuration
    public static class Context {

        @Bean
        public EventStorageEngine eventStorageEngine() {
            return new InMemoryEventStorageEngine();
        }

        @Bean
        public EntityManagerProvider entityManagerProvider() {
            return mock(EntityManagerProvider.class);
        }

        @Bean
        public EventProcessingModule eventProcessingConfiguration() {
            return new EventProcessingModule();
        }

        @Aggregate
        public static class MyAggregate {

            @Id
            private String id;

            @CommandHandler
            public void handle(Long command) {
                apply(command);
            }

            @CommandHandler
            public void handle(String command) {
            }

            @EventSourcingHandler
            public void on(Long event) {
                this.id = Long.toString(event);
            }

            @EventSourcingHandler
            public void on(String event) {
                fail("Event Handler on aggregate shouldn't be invoked");
            }
        }
    }

    public static class SomeEvent {

        private final String id;

        public SomeEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }


}
