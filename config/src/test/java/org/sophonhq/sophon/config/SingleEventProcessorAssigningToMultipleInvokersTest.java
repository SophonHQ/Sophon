/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.sophonhq.sophon.config;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.EventProcessor;
import org.sophonhq.sophon.eventhandling.SubscribingEventProcessor;
import org.sophonhq.sophon.modelling.saga.repository.inmemory.InMemorySagaStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testing functionality of assigning different invokers (saga or event handlers) to the same event processor.
 *
 * @author Milan Savic
 */
class SingleEventProcessorAssigningToMultipleInvokersTest {

    @Test
    void multipleAssignmentsToTrackingProcessor() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .registerEventHandler(config -> new EventHandler1())
                  .registerSaga(Saga1.class, sc -> sc.configureSagaStore(c -> new InMemorySagaStore()))
                  .registerSaga(Saga2.class)
                  .registerSaga(Saga3.class);

        Configuration configuration = configurer.buildConfiguration();

        EventProcessor saga1Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga1.class).orElse(null);
        EventProcessor saga2Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga2.class).orElse(null);
        EventProcessor saga3Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga3.class).orElse(null);

        assertThat(saga1Processor).isNotNull();
        assertThat(saga2Processor).isNotNull();
        assertThat(saga3Processor).isNotNull();
        assertThat(configuration.eventProcessingConfiguration().eventProcessor("processor1").get()).isNotNull();
        assertThat(saga2Processor).isEqualTo(saga1Processor);
        assertThat(configuration.eventProcessingConfiguration().eventProcessor("processor1").get())
                .isEqualTo(saga1Processor);
        assertThat(saga3Processor).isNotEqualTo(saga1Processor);
        assertThat(saga3Processor).isNotEqualTo(saga2Processor);
        assertThat(configuration.eventProcessingConfiguration().eventProcessor("processor1").get())
                .isNotEqualTo(saga3Processor);
    }

    @Test
    void multipleAssignmentsToSubscribingProcessor() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .usingSubscribingEventProcessors()
                  .registerEventHandler(config -> new EventHandler1())
                  .registerSaga(Saga1.class)
                  .registerSaga(Saga2.class)
                  .registerSaga(Saga3.class);
        Configuration configuration = configurer.buildConfiguration();

        EventProcessor saga1Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga1.class).orElse(null);
        EventProcessor saga2Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga2.class).orElse(null);
        EventProcessor saga3Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga3.class).orElse(null);

        assertThat(saga1Processor).isNotNull();
        assertThat(saga2Processor).isNotNull();
        assertThat(saga3Processor).isNotNull();
        assertThat(configuration.eventProcessingConfiguration().eventProcessor("processor1").get()).isNotNull();
        assertThat(saga2Processor).isEqualTo(saga1Processor);
        assertThat(configuration.eventProcessingConfiguration().eventProcessor("processor1").get())
                .isEqualTo(saga1Processor);
        assertThat(saga3Processor).isNotEqualTo(saga1Processor);
        assertThat(saga3Processor).isNotEqualTo(saga2Processor);
        assertThat(saga3Processor)
                .isNotEqualTo(configuration.eventProcessingConfiguration().eventProcessor("processor1").get());
    }

    @Test
    void multipleAssignmentsWithProvidedProcessorName() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("processor1", clazz -> clazz.equals(Saga3.class))
                  .assignHandlerTypesMatching("processor1", clazz -> clazz.equals(Saga1.class))
                  .assignHandlerTypesMatching("someOtherProcessor", clazz -> clazz.equals(Saga2.class))
                  .assignProcessingGroup("processor1", "myProcessor")
                  .registerEventHandler(config -> new EventHandler1())
                  .registerSaga(Saga1.class)
                  .registerSaga(Saga2.class)
                  .registerSaga(Saga3.class)
                  .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                          SubscribingEventProcessor.builder()
                                                   .name(name)
                                                   .eventHandlerInvoker(eventHandlerInvoker)
                                                   .messageSource(conf.eventBus())
                                                   .build()
                  );
        Configuration configuration = configurer.buildConfiguration();

        EventProcessor saga1Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga1.class).orElse(null);
        EventProcessor saga2Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga2.class).orElse(null);
        EventProcessor saga3Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga3.class).orElse(null);
        assertThat(saga1Processor).isNotNull();
        assertThat(saga2Processor).isNotNull();
        assertThat(saga3Processor).isNotNull();
        assertThat(configuration.eventProcessingConfiguration().eventProcessor("myProcessor").get()).isNotNull();
        assertThat(saga3Processor).isEqualTo(saga1Processor);
        assertThat(configuration.eventProcessingConfiguration().eventProcessor("someOtherProcessor").get())
                .isEqualTo(saga2Processor);
        assertThat(saga3Processor).isNotEqualTo(saga2Processor);
        assertThat(configuration.eventProcessingConfiguration().eventProcessor("myProcessor").get())
                .isEqualTo(saga3Processor);
        assertThat(configuration.eventProcessingConfiguration().eventProcessor("someOtherProcessor").get())
                .isNotEqualTo(saga3Processor);
    }

    @Test
    void processorGroupAssignment() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                          SubscribingEventProcessor.builder()
                                                   .name(name)
                                                   .eventHandlerInvoker(eventHandlerInvoker)
                                                   .messageSource(conf.eventBus())
                                                   .build())
                  .assignProcessingGroup("processor1", "myProcessor")
                  .registerSaga(Saga1.class)
                  .registerSaga(Saga2.class)
                  .registerSaga(Saga3.class);
        Configuration configuration = configurer.buildConfiguration();

        EventProcessor saga1Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga1.class).orElse(null);
        EventProcessor saga2Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga2.class).orElse(null);
        EventProcessor saga3Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga3.class).orElse(null);

        assertThat(saga1Processor.getName()).isEqualTo("myProcessor");
        assertThat(saga2Processor.getName()).isEqualTo("myProcessor");
        assertThat(saga3Processor.getName()).isEqualTo("Saga3Processor");
    }

    @Test
    void processorGroupAssignmentByRule() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration();
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("myProcessor", clazz -> clazz.equals(Saga3.class))
                  .registerSaga(Saga1.class)
                  .registerSaga(Saga2.class)
                  .registerSaga(Saga3.class)
                  .registerEventProcessor("myProcessor", (name, conf, eventHandlerInvoker) ->
                          SubscribingEventProcessor.builder()
                                                   .name(name)
                                                   .eventHandlerInvoker(eventHandlerInvoker)
                                                   .messageSource(conf.eventBus())
                                                   .build())
                  .assignProcessingGroup(group -> "myProcessor");

        Configuration configuration = configurer.buildConfiguration();

        EventProcessor saga1Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga1.class).orElse(null);
        EventProcessor saga2Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga2.class).orElse(null);
        EventProcessor saga3Processor = configuration.eventProcessingConfiguration().sagaEventProcessor(Saga3.class).orElse(null);

        assertThat(saga1Processor.getName()).isEqualTo("myProcessor");
        assertThat(saga2Processor.getName()).isEqualTo("myProcessor");
        assertThat(saga3Processor.getName()).isEqualTo("myProcessor");
    }

    @ProcessingGroup("processor1")
    private static class Saga1 {

    }

    @ProcessingGroup("processor1")
    private static class Saga2 {

    }

    private static class Saga3 {

    }

    @ProcessingGroup("processor1")
    private static class EventHandler1 {

    }
}
