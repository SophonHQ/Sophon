/*
 * Copyright (c) 2026. SophonHQ
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

import org.sophonhq.sophon.config.Configurer;
import org.sophonhq.sophon.config.ConfigurerModule;
import org.sophonhq.sophon.config.ModuleConfiguration;
import org.sophonhq.sophon.serialization.upcasting.event.EventUpcaster;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Test configuration that sets up Sophon in a plain Spring application context. It copies the beans of the Spring Boot
 * {@code InfraConfiguration} that the tests in this module need, so these tests stay independent of Spring Boot.
 */
@Configuration
public class InfraTestConfiguration {

    @Bean
    public static MessageHandlerLookup messageHandlerLookup() {
        return new MessageHandlerLookup();
    }

    @Bean
    public static SpringAggregateLookup springAggregateLookup() {
        return new SpringAggregateLookup();
    }

    @Bean
    public static SpringSagaLookup springSagaLookup() {
        return new SpringSagaLookup();
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
        List<ConfigurerModule> sortedModules = new ArrayList<>(configurerModules);
        sortedModules.sort(Comparator.comparing(ConfigurerModule::order)
                                     .thenComparing(AnnotationAwareOrderComparator.INSTANCE));
        sortedModules.forEach(module -> module.configureModule(configurer));
        return configurer;
    }

    @Bean
    public ConfigurerModule eventUpcastersConfigurer(List<EventUpcaster> upcasters) {
        return configurer -> upcasters.forEach(upcaster -> configurer.registerEventUpcaster(c -> upcaster));
    }
}
