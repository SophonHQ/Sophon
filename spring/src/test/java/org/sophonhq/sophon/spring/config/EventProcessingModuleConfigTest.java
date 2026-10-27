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

package org.sophonhq.sophon.spring.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.sophonhq.sophon.config.EventProcessingModule;
import org.sophonhq.sophon.config.ProcessingGroup;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.EventProcessor;
import org.sophonhq.sophon.messaging.MessageHandlerInterceptor;
import org.sophonhq.sophon.messaging.interceptors.CorrelationDataInterceptor;
import org.sophonhq.sophon.messaging.interceptors.LoggingInterceptor;
import org.sophonhq.sophon.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Import;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests configuration of {@link EventProcessingModule}.
 *
 * @author Milan Savic
 */
@ExtendWith(SpringExtension.class)
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
public class EventProcessingModuleConfigTest {

    @Autowired
    private EventProcessingModule eventProcessingConfiguration;

    @Test
    void eventProcessingConfiguration() {
        assertEquals(3, eventProcessingConfiguration.eventProcessors().size());
        assertTrue(eventProcessingConfiguration.eventProcessor("processor2").isPresent());
        assertTrue(eventProcessingConfiguration.eventProcessor("subscribingProcessor").isPresent());

        EventProcessor processor2 = eventProcessingConfiguration.eventProcessorByProcessingGroup("processor1").get();
        assertEquals("processor2", processor2.getName());
        List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor = eventProcessingConfiguration
                .interceptorsFor("processor2");
        assertEquals(2, interceptorsFor.size());
        assertTrue(interceptorsFor.stream().anyMatch(i -> i instanceof CorrelationDataInterceptor));
        assertTrue(interceptorsFor.stream().anyMatch(i -> i instanceof LoggingInterceptor));

        assertEquals("processor2",
                     eventProcessingConfiguration.eventProcessorByProcessingGroup("processor2").get().getName());
        assertEquals("subscribingProcessor",
                     eventProcessingConfiguration.eventProcessorByProcessingGroup("processor3").get().getName());
        assertEquals("subscribingProcessor",
                     eventProcessingConfiguration.eventProcessorByProcessingGroup("Saga3Processor").get().getName());
        assertEquals("processor4",
                     eventProcessingConfiguration.eventProcessorByProcessingGroup("processor4").get().getName());
    }

    @Import(SpringSophonAutoConfigurer.ImportSelector.class)
    @Configuration
    public static class Context {

        @Bean
        public EventProcessingModule eventProcessingConfiguration() {
            EventProcessingModule config = new EventProcessingModule();
            config.assignProcessingGroup("processor1", "processor2");
            config.assignProcessingGroup(group -> group.contains("3") ? "subscribingProcessor" : group);
            config.registerSubscribingEventProcessor("subscribingProcessor");
            config.registerDefaultHandlerInterceptor((configuration, name) -> new LoggingInterceptor<>());
            return config;
        }

        @Saga
        @ProcessingGroup("processor1")
        public static class Saga1 {

        }

        @Saga
        @ProcessingGroup("processor2")
        public static class Saga2 {

        }

        @Saga
        public static class Saga3 {

        }

        @Component
        @ProcessingGroup("processor3")
        public static class EventHandler1 {

            @EventHandler
            public void on(String evt) {
                // nothing to do
            }
        }

        @Saga
        @ProcessingGroup("processor4")
        public static class Saga4 {

        }

        @Saga
        @ProcessingGroup("processor4")
        public static class Saga5 {

        }
    }
}
