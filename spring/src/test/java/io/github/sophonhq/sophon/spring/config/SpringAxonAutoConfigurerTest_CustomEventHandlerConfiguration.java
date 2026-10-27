/*
 * Copyright (c) 2010-2018. Axon Framework
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

import io.github.sophonhq.sophon.commandhandling.AsynchronousCommandBus;
import io.github.sophonhq.sophon.commandhandling.CommandBus;
import io.github.sophonhq.sophon.commandhandling.CommandHandler;
import io.github.sophonhq.sophon.commandhandling.SimpleCommandBus;
import io.github.sophonhq.sophon.config.EventProcessingModule;
import io.github.sophonhq.sophon.eventhandling.EventBus;
import io.github.sophonhq.sophon.eventhandling.EventHandler;
import io.github.sophonhq.sophon.eventhandling.SubscribingEventProcessor;
import io.github.sophonhq.sophon.eventsourcing.EventSourcingHandler;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import io.github.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import io.github.sophonhq.sophon.messaging.annotation.MetaDataValue;
import io.github.sophonhq.sophon.modelling.command.AggregateIdentifier;
import io.github.sophonhq.sophon.modelling.saga.SagaEventHandler;
import io.github.sophonhq.sophon.modelling.saga.StartSaga;
import io.github.sophonhq.sophon.modelling.saga.repository.SagaStore;
import io.github.sophonhq.sophon.modelling.saga.repository.inmemory.InMemorySagaStore;
import io.github.sophonhq.sophon.spring.stereotype.Aggregate;
import io.github.sophonhq.sophon.spring.stereotype.Saga;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonMap;
import static io.github.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@ContextConfiguration
public class SpringAxonAutoConfigurerTest_CustomEventHandlerConfiguration {

    @Autowired(required = false)
    private EventBus eventBus;

    @Autowired
    private Context.MyEventHandler myEventHandler;

    @Autowired
    private Context.MyOtherEventHandler myOtherEventHandler;

    @Test
    void eventHandlerIsRegisteredWithCustomProcessor() {
        eventBus.publish(asEventMessage("Testing 123"));

        assertNotNull(myEventHandler.eventBus, "Expected EventBus to be wired");
        assertTrue(myEventHandler.received.contains("value"));
        assertTrue(myOtherEventHandler.received.contains("Testing 123"));
    }

    @Import(SpringAxonAutoConfigurer.ImportSelector.class)
    @Scope
    @Configuration
    public static class Context {

        @Bean
        public EventProcessingModule eventProcessingConfiguration() {
            EventProcessingModule eventProcessingModule = new EventProcessingModule();
            eventProcessingModule.usingSubscribingEventProcessors();
            eventProcessingModule.byDefaultAssignTo("test")
                                 .registerEventProcessor("test", (name, config, eventHandlerInvoker) -> {
                                     SubscribingEventProcessor processor =
                                             SubscribingEventProcessor.builder()
                                                                      .name(name)
                                                                      .eventHandlerInvoker(eventHandlerInvoker)
                                                                      .messageSource(config.eventBus())
                                                                      .build();
                                     processor.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
                                         unitOfWork.transformMessage(m -> m.andMetaData(singletonMap("key", "value")));
                                         return interceptorChain.proceed();
                                     });
                                     return processor;
                                 });
            return eventProcessingModule;
        }

        @Primary
        @Bean(destroyMethod = "shutdown")
        public CommandBus commandBus() {
            return AsynchronousCommandBus.builder().build();
        }

        @Bean
        public CommandBus simpleCommandBus() {
            return SimpleCommandBus.builder().build();
        }

        @Bean
        public EventStorageEngine eventStorageEngine() {
            return new InMemoryEventStorageEngine();
        }

        @Bean
        public SagaStore sagaStore() {
            return new InMemorySagaStore();
        }

        @Bean
        public SagaStore customSagaStore() {
            return new InMemorySagaStore();
        }

        @Aggregate
        public static class MyAggregate {

            @AggregateIdentifier
            private String id;

            @EventSourcingHandler
            public void on(String event) {
                fail("Event Handler on aggregate shouldn't be invoked");
            }
        }

        @Component
        public static class MyCommandHandler {

            private List<String> commands = new ArrayList<>();

            @CommandHandler
            public void handle(String command) {
                commands.add(command);
            }

            public List<String> getCommands() {
                return commands;
            }
        }

        @Saga(sagaStore = "customSagaStore")
        public static class MySaga {

            private static List<String> events = new ArrayList<>();

            @StartSaga
            @SagaEventHandler(associationProperty = "id")
            public void handle(SomeEvent event) {
                events.add(event.getId());
            }
        }

        @Component
        public static class MyEventHandler {

            public List<String> received = new ArrayList<>();
            private EventBus eventBus;

            public MyEventHandler(EventBus eventBus) {
                this.eventBus = eventBus;
            }

            @EventHandler(payloadType = String.class)
            public void handle(@MetaDataValue("key") String event) {
                assertNotNull(eventBus);
                received.add(event);
            }
        }

        @Component
        public static class MyOtherEventHandler {

            public List<String> received = new ArrayList<>();

            @EventHandler
            public void handle(String event) {
                received.add(event);
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
