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

package org.sophonhq.sophon.springboot;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.commandhandling.CommandBus;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import org.sophonhq.sophon.config.Configurer;
import org.sophonhq.sophon.disruptor.commandhandling.DisruptorCommandBus;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.eventhandling.gateway.EventGateway;
import org.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import org.sophonhq.sophon.eventsourcing.AggregateFactory;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.sophonhq.sophon.messaging.correlation.CorrelationDataProvider;
import org.sophonhq.sophon.messaging.correlation.SimpleCorrelationDataProvider;
import org.sophonhq.sophon.modelling.command.Repository;
import org.sophonhq.sophon.modelling.saga.SagaEventHandler;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.spring.stereotype.Aggregate;
import org.sophonhq.sophon.spring.stereotype.Saga;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.*;

class SophonAutoConfigurationWithDisruptorTest {

    @Test
    void contextInitialization() {
        new ApplicationContextRunner()
                .withUserConfiguration(SophonAutoConfigurationWithDisruptorTest.Context.class)
                .run(applicationContext -> {
                    assertNotNull(applicationContext);
                    assertNotNull(applicationContext.getBean(Configurer.class));
                    assertNotNull(applicationContext.getBean(CommandBus.class));
                    assertEquals(DisruptorCommandBus.class, applicationContext.getBean(CommandBus.class).getClass());
                    assertNotNull(applicationContext.getBean(EventBus.class));
                    assertNotNull(applicationContext.getBean(CommandGateway.class));
                    assertNotNull(applicationContext.getBean(EventGateway.class));
                    assertNotNull(applicationContext.getBean(Serializer.class));
                    assertEquals(1, applicationContext.getBeansOfType(EventStorageEngine.class).size());
                    assertEquals(0, applicationContext.getBeansOfType(TokenStore.class).size());
                    assertNotNull(applicationContext.getBean(Context.MySaga.class));
                    assertNotNull(applicationContext.getBean(Context.MyAggregate.class));
                    assertNotNull(applicationContext.getBean(Repository.class));
                    assertNotNull(applicationContext.getBean(AggregateFactory.class));
                    org.sophonhq.sophon.config.Configuration configuration = applicationContext.getBean(org.sophonhq.sophon.config.Configuration.class);
                    assertEquals(2, configuration.correlationDataProviders().size());
                });
    }

    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    @Configuration
    public static class Context {

        @Bean
        public EventStorageEngine storageEngine() {
            return new InMemoryEventStorageEngine();
        }

        @Bean
        public CorrelationDataProvider correlationData1() {
            return new SimpleCorrelationDataProvider("key1");
        }

        @Bean
        public CorrelationDataProvider correlationData2() {
            return new SimpleCorrelationDataProvider("key2");
        }


        @Bean
        public DisruptorCommandBus commandBus() {
            return DisruptorCommandBus.builder().build();
        }

        @Aggregate
        public static class MyAggregate {

            @CommandHandler
            public void handle(String type, SomeComponent test) {

            }

            @EventHandler
            public void on(String type, SomeComponent test) {

            }
        }

        @Saga
        public static class MySaga {

            @SagaEventHandler(associationProperty = "toString")
            public void handle(String type, SomeComponent test) {

            }
        }

        @Component
        public static class SomeComponent {

            @EventHandler
            public void handle(String event, SomeOtherComponent test) {

            }
        }

        @Component
        public static class SomeOtherComponent {

        }
    }
}
