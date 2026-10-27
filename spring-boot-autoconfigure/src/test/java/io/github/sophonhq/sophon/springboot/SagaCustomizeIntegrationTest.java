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

package io.github.sophonhq.sophon.springboot;

import io.github.sophonhq.sophon.common.transaction.Transaction;
import io.github.sophonhq.sophon.common.transaction.TransactionManager;
import io.github.sophonhq.sophon.config.EventProcessingModule;
import io.github.sophonhq.sophon.eventhandling.EventBus;
import io.github.sophonhq.sophon.eventhandling.TrackingEventProcessorConfiguration;
import io.github.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import io.github.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import io.github.sophonhq.sophon.modelling.saga.SagaEventHandler;
import io.github.sophonhq.sophon.modelling.saga.StartSaga;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.spring.stereotype.Saga;
import io.github.sophonhq.sophon.springboot.autoconfig.SophonAutoConfiguration;
import io.github.sophonhq.sophon.springboot.utils.TestSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jmx.support.RegistrationPolicy;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static io.github.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests customization of the event handlers on a Saga, updating the configuration through an autowired method. Ensures
 * that if there are multiple threads and a logging interceptor events are still received once.
 *
 * @author Marc Gathier
 */
@SpringBootTest(properties = {
        "spring.main.banner-mode=off"
})
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class
})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class SagaCustomizeIntegrationTest {

    @Autowired
    private EventBus eventBus;
    @Autowired
    private TransactionManager transactionManager;
    @Autowired
    private AtomicInteger eventsReceived;
    @Autowired
    private EventProcessingModule eventProcessingModule;

    @Test
    void publishSomeEvents() {
        publishEvent(new EchoEvent(UUID.randomUUID().toString()));
        eventProcessingModule.eventProcessors()
                             .forEach((name, ep) -> assertTrue(ep.isRunning()));

        eventProcessingModule.eventProcessors()
                             .forEach((name, ep) -> assertFalse(ep.isError(), "Processor ended with error"));

        await().atMost(Duration.ofSeconds(1)).until(() -> eventsReceived.get() == 1);
        publishEvent(new EchoEvent(UUID.randomUUID().toString()));
        await().atMost(Duration.ofSeconds(1)).until(() -> eventsReceived.get() == 2);
    }

    private void publishEvent(EchoEvent... events) {
        DefaultUnitOfWork.startAndGet(null).execute(
                () -> {
                    Transaction tx = transactionManager.startTransaction();
                    CurrentUnitOfWork.get().onRollback(u -> tx.rollback());
                    CurrentUnitOfWork.get().onCommit(u -> tx.commit());
                    for (EchoEvent event : events) {
                        eventBus.publish(asEventMessage(event));
                    }
                });
    }

    @AutoConfigureBefore(SophonAutoConfiguration.class)
    @Configuration
    public static class Context {

        @Bean
        public AtomicInteger eventsReceived() {
            return new AtomicInteger();
        }

        @Bean
        @Primary
        public Serializer serializer() {
            return TestSerializer.xStreamSerializer();
        }

        @Autowired
        private void registerEventHandlers(EventProcessingModule eventProcessingConfiguration) throws ClassNotFoundException {
            Set<String> registeredProcessingGroups = new HashSet<>();
            ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Saga.class));
            for (BeanDefinition bd : scanner.findCandidateComponents("io.github.sophonhq.sophon.springboot")) {
                Class<?> aClass = Class.forName(bd.getBeanClassName());
                String processorGroupName = eventProcessingConfiguration.sagaProcessingGroup(aClass);
                if (!registeredProcessingGroups.contains(processorGroupName)) {
                    eventProcessingConfiguration.registerTrackingEventProcessor(
                            processorGroupName,
                            io.github.sophonhq.sophon.config.Configuration::eventStore,
                            c -> TrackingEventProcessorConfiguration.forParallelProcessing(2)
                                                                    .andInitialSegmentsCount(2)
                    );

                    registeredProcessingGroups.add(processorGroupName);
                }
            }
        }
    }

    @Saga
    @SuppressWarnings("unused")
    public static class SimpleSaga {

        @SagaEventHandler(associationProperty = "id")
        @StartSaga
        public void on(EchoEvent echoEvent, AtomicInteger eventsReceived) {
            eventsReceived.getAndIncrement();
        }
    }

    @SuppressWarnings("unused")
    public static class EchoEvent {

        private String id;

        public EchoEvent() {
        }

        public EchoEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }
}
