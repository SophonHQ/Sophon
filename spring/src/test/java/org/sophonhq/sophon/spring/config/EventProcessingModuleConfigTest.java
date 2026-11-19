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

import org.junit.jupiter.api.Test;
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
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests configuration of {@link EventProcessingModule}.
 *
 * @author Milan Savic
 */
@ExtendWith(SpringExtension.class)
public class EventProcessingModuleConfigTest {

    @Autowired
    private EventProcessingModule eventProcessingConfiguration;

    @Test
    void eventProcessingConfiguration() {
        assertThat(eventProcessingConfiguration.eventProcessors()).hasSize(3);
        assertThat(eventProcessingConfiguration.eventProcessor("processor2")).isPresent();
        assertThat(eventProcessingConfiguration.eventProcessor("subscribingProcessor")).isPresent();

        EventProcessor processor2 = eventProcessingConfiguration.eventProcessorByProcessingGroup("processor1").get();
        assertThat(processor2.getName()).isEqualTo("processor2");
        List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptorsFor = eventProcessingConfiguration
                .interceptorsFor("processor2");
        assertThat(interceptorsFor).hasSize(2);
        assertThat(interceptorsFor.stream().anyMatch(i -> i instanceof CorrelationDataInterceptor)).isTrue();
        assertThat(interceptorsFor.stream().anyMatch(i -> i instanceof LoggingInterceptor)).isTrue();

        assertThat(eventProcessingConfiguration.eventProcessorByProcessingGroup("processor2").get().getName())
                .isEqualTo("processor2");
        assertThat(eventProcessingConfiguration.eventProcessorByProcessingGroup("processor3").get().getName())
                .isEqualTo("subscribingProcessor");
        assertThat(eventProcessingConfiguration.eventProcessorByProcessingGroup("Saga3Processor").get().getName())
                .isEqualTo("subscribingProcessor");
        assertThat(eventProcessingConfiguration.eventProcessorByProcessingGroup("processor4").get().getName())
                .isEqualTo("processor4");
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
