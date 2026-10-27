/*
 * Copyright (c) 2010-2022. Axon Framework
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

package io.github.sophonhq.sophon.config;

import io.github.sophonhq.sophon.common.SophonConfigurationException;
import io.github.sophonhq.sophon.eventhandling.GenericEventMessage;
import io.github.sophonhq.sophon.eventhandling.ListenerInvocationErrorHandler;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import io.github.sophonhq.sophon.modelling.saga.AnnotatedSagaManager;
import io.github.sophonhq.sophon.modelling.saga.AssociationValue;
import io.github.sophonhq.sophon.modelling.saga.SagaEventHandler;
import io.github.sophonhq.sophon.modelling.saga.SagaRepository;
import io.github.sophonhq.sophon.modelling.saga.StartSaga;
import io.github.sophonhq.sophon.modelling.saga.repository.SagaStore;
import io.github.sophonhq.sophon.modelling.saga.repository.inmemory.InMemorySagaStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SagaConfigurerTest {

    @AfterEach
    void cleanup() {
        TestSaga.counter.set(0);
    }

    @Test
    void nullChecksOnSagaConfigurer() {
        SagaConfigurer<Object> configurer = SagaConfigurer.forType(Object.class);
        assertConfigurerNullCheck(() -> SagaConfigurer.forType(null), "Saga type should be checked for null");
        assertConfigurerNullCheck(() -> configurer.configureSagaStore(null),
                                  "Saga store builder should be checked for null");
        assertConfigurerNullCheck(() -> configurer.configureSagaManager(null),
                                  "Saga manager should be checked for null");
        assertConfigurerNullCheck(() -> configurer.configureRepository(null),
                                  "Saga repository should be checked for null");
    }

    @Test
    void defaultConfiguration(
            @Mock ListenerInvocationErrorHandler listenerInvocationErrorHandler,
            @Mock SagaStore store) {
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .eventProcessing(ep -> ep.registerSaga(Object.class))
                                                       .registerComponent(ListenerInvocationErrorHandler.class,
                                                                          c -> listenerInvocationErrorHandler)
                                                       .registerComponent(SagaStore.class, c -> store)

                                                       .buildConfiguration();
        SagaConfiguration<Object> sagaConfiguration = configuration.eventProcessingConfiguration().sagaConfiguration(
                Object.class);

        assertEquals("ObjectProcessor", sagaConfiguration.processingGroup());
        assertEquals(Object.class, sagaConfiguration.type());
        assertEquals(store, sagaConfiguration.store());
        assertEquals(listenerInvocationErrorHandler, sagaConfiguration.listenerInvocationErrorHandler());
    }

    @Test
    void customConfiguration(
            @Mock SagaRepository<Object> repository,
            @Mock AnnotatedSagaManager<Object> manager) {
        SagaStore<Object> sagaStore = new InMemorySagaStore();
        String processingGroup = "myProcessingGroup";

        EventProcessingModule eventProcessingModule = new EventProcessingModule();
        eventProcessingModule.registerSaga(Object.class, sc -> sc.configureSagaStore(c -> sagaStore)
                                                                 .configureRepository(c -> repository)
                                                                 .configureSagaManager(c -> manager));
        eventProcessingModule.assignProcessingGroup("ObjectProcessor", processingGroup)
                             .assignHandlerTypesMatching(processingGroup, clazz -> clazz.equals(Object.class));
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .registerModule(eventProcessingModule)
                                                       .buildConfiguration();

        SagaConfiguration<Object> sagaConfiguration = configuration.eventProcessingConfiguration().sagaConfiguration(
                Object.class);

        assertEquals(Object.class, sagaConfiguration.type());
        assertEquals(processingGroup, sagaConfiguration.processingGroup());
        assertEquals(manager, sagaConfiguration.manager());
        assertEquals(repository, sagaConfiguration.repository());
        assertEquals(sagaStore, sagaConfiguration.store());
    }

    @Test
    void deduplicateRegisterSaga() {
        EmbeddedEventStore eventStore = EmbeddedEventStore
                .builder()
                .storageEngine(new InMemoryEventStorageEngine())
                .build();
        SagaStore<Object> sagaStore = new InMemorySagaStore();
        EventProcessingModule eventProcessingModule = new EventProcessingModule();
        eventProcessingModule
                .registerSaga(TestSaga.class)
                .registerSaga(TestSaga.class, sc -> sc.configureSagaStore(c -> sagaStore))
                .registerSubscribingEventProcessor("testsaga", c -> eventStore);
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .configureEventStore(c -> eventStore)
                                                       .registerModule(eventProcessingModule)
                                                       .buildConfiguration();
        configuration.start();
        TestEvent testEvent = new TestEvent();
        eventStore.publish(GenericEventMessage.asEventMessage(testEvent));
        Set<String> sagas = sagaStore.findSagas(TestSaga.class, new AssociationValue("id", testEvent.id.toString()));
        assertEquals(1, sagas.size());
        assertEquals(1, TestSaga.counter.get());
    }

    private void assertConfigurerNullCheck(Runnable r, String message) {
        try {
            r.run();
            fail(message);
        } catch (SophonConfigurationException ace) {
            // we expect this exception
        }
    }

    private static class TestEvent {

        private final UUID id;

        private TestEvent() {
            id = UUID.randomUUID();
        }

        @SuppressWarnings("unused")
        public UUID getId() {
            return id;
        }
    }

    @ProcessingGroup("testsaga")
    public static class TestSaga {

        static final AtomicInteger counter = new AtomicInteger();

        @StartSaga
        @SagaEventHandler(associationProperty = "id")
        public void handleCreated(TestEvent event) {
            counter.incrementAndGet();
        }
    }
}
