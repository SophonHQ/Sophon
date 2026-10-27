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

package io.github.sophonhq.sophon.springboot.autoconfig;

import io.github.sophonhq.sophon.config.Configurer;
import io.github.sophonhq.sophon.config.ConfigurerModule;
import io.github.sophonhq.sophon.config.ModuleConfiguration;
import io.github.sophonhq.sophon.lifecycle.Lifecycle;
import io.github.sophonhq.sophon.messaging.annotation.HandlerDefinition;
import io.github.sophonhq.sophon.messaging.annotation.HandlerEnhancerDefinition;
import io.github.sophonhq.sophon.messaging.annotation.ParameterResolverFactory;
import io.github.sophonhq.sophon.messaging.correlation.CorrelationDataProvider;
import io.github.sophonhq.sophon.modelling.saga.ResourceInjector;
import io.github.sophonhq.sophon.serialization.upcasting.event.EventUpcaster;
import io.github.sophonhq.sophon.spring.config.MessageHandlerLookup;
import io.github.sophonhq.sophon.spring.config.SpringAggregateLookup;
import io.github.sophonhq.sophon.spring.config.SpringConfigurer;
import io.github.sophonhq.sophon.spring.config.SpringSagaLookup;
import io.github.sophonhq.sophon.spring.config.SpringSophonConfiguration;
import io.github.sophonhq.sophon.spring.config.annotation.HandlerDefinitionFactoryBean;
import io.github.sophonhq.sophon.spring.config.annotation.SpringParameterResolverFactoryBean;
import io.github.sophonhq.sophon.spring.saga.SpringResourceInjector;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Infrastructure autoconfiguration class for Sophon application. Constructs the look-up components, like the
 * {@link MessageHandlerLookup} to find Sophon components and register them with the {@link SpringConfigurer}.
 *
 * @author Allard Buijze
 * @since axon-3.0.4
 */
@AutoConfiguration
@ConditionalOnClass(SpringConfigurer.class)
@AutoConfigureAfter({
        SophonAutoConfiguration.class,
        JpaAutoConfiguration.class,
        JpaEventStoreAutoConfiguration.class,
        NoOpTransactionAutoConfiguration.class,
        TransactionAutoConfiguration.class
})
public class InfraConfiguration {

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static MessageHandlerLookup messageHandlerLookup() {
        return new MessageHandlerLookup();
    }

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static SpringAggregateLookup springAggregateLookup() {
        return new SpringAggregateLookup();
    }

    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @Bean
    public static SpringSagaLookup springSagaLookup() {
        return new SpringSagaLookup();
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringSophonConfiguration springSophonConfiguration(Configurer configurer) {
        return new SpringSophonConfiguration(configurer);
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringConfigurer springSophonConfigurer(ConfigurableListableBeanFactory beanFactory,
                                                 List<ConfigurerModule> configurerModules,
                                                 List<ModuleConfiguration> moduleConfigurations) {
        SpringConfigurer configurer = new SpringConfigurer(beanFactory);
        moduleConfigurations.forEach(configurer::registerModule);

        List<ConfigurerModule> sortedList = new ArrayList<>(configurerModules);
        sortedList.sort(Comparator.comparing(ConfigurerModule::order)
                .thenComparing(AnnotationAwareOrderComparator.INSTANCE));
        sortedList.forEach(c -> c.configureModule(configurer));
        return configurer;
    }

    @Bean
    public InitializingBean lifecycleInitializer(Configurer configurer,
                                                 List<Lifecycle> lifecycleBeans) {
        return () -> configurer.onInitialize(
                config -> lifecycleBeans.forEach(bean -> bean.registerLifecycleHandlers(config.lifecycleRegistry()))
        );
    }

    @Primary
    @Bean
    public HandlerDefinitionFactoryBean handlerDefinition(List<HandlerDefinition> handlerDefinitions,
                                                          List<HandlerEnhancerDefinition> handlerEnhancerDefinitions) {
        return new HandlerDefinitionFactoryBean(handlerDefinitions, handlerEnhancerDefinitions);
    }

    @Primary
    @Bean
    public SpringParameterResolverFactoryBean parameterResolverFactory(
            List<ParameterResolverFactory> parameterResolverFactories
    ) {
        SpringParameterResolverFactoryBean springParameterResolverFactoryBean = new SpringParameterResolverFactoryBean();
        springParameterResolverFactoryBean.setAdditionalFactories(parameterResolverFactories);
        return springParameterResolverFactoryBean;
    }

    @ConditionalOnClass(CorrelationDataProvider.class)
    @Bean
    public ConfigurerModule correlationDataProvidersConfigurer(List<CorrelationDataProvider> correlationDataProviders) {
        return configurer -> configurer.configureCorrelationDataProviders(c -> correlationDataProviders);
    }

    @ConditionalOnClass(EventUpcaster.class)
    @Bean
    public ConfigurerModule eventUpcastersConfigurer(List<EventUpcaster> upcasters) {
        return configurer -> upcasters.forEach(u -> configurer.registerEventUpcaster(c -> u));
    }

    @ConditionalOnMissingBean
    @Bean
    public ResourceInjector resourceInjector() {
        return new SpringResourceInjector();
    }
}
