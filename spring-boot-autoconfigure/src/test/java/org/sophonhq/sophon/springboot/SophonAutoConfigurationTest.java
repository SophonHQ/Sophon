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

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandBus;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.commandhandling.SimpleCommandBus;
import org.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import org.sophonhq.sophon.commandhandling.gateway.DefaultCommandGateway;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.config.Configurer;
import org.sophonhq.sophon.config.EventProcessingConfiguration;
import org.sophonhq.sophon.config.EventProcessingConfigurer;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.gateway.EventGateway;
import org.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import org.sophonhq.sophon.eventsourcing.AggregateFactory;
import org.sophonhq.sophon.eventsourcing.EventCountSnapshotTriggerDefinition;
import org.sophonhq.sophon.eventsourcing.SnapshotTriggerDefinition;
import org.sophonhq.sophon.eventsourcing.Snapshotter;
import org.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.sophonhq.sophon.lifecycle.Lifecycle;
import org.sophonhq.sophon.messaging.annotation.FixedValueParameterResolver;
import org.sophonhq.sophon.messaging.annotation.MultiParameterResolverFactory;
import org.sophonhq.sophon.messaging.annotation.ParameterResolver;
import org.sophonhq.sophon.messaging.annotation.ParameterResolverFactory;
import org.sophonhq.sophon.messaging.annotation.SimpleResourceParameterResolverFactory;
import org.sophonhq.sophon.messaging.correlation.CorrelationDataProvider;
import org.sophonhq.sophon.messaging.correlation.SimpleCorrelationDataProvider;
import org.sophonhq.sophon.modelling.command.Repository;
import org.sophonhq.sophon.modelling.saga.SagaEventHandler;
import org.sophonhq.sophon.queryhandling.QueryBus;
import org.sophonhq.sophon.serialization.SerializedType;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.SimpleSerializedType;
import org.sophonhq.sophon.serialization.UnknownSerializedType;
import org.sophonhq.sophon.serialization.json.JacksonSerializer;
import org.sophonhq.sophon.serialization.xml.XStreamSerializer;
import org.sophonhq.sophon.spring.stereotype.Aggregate;
import org.sophonhq.sophon.spring.stereotype.Saga;
import org.sophonhq.sophon.springboot.util.LegacyPropertiesDetector;
import org.sophonhq.sophon.tracing.SpanFactory;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;

class SophonAutoConfigurationTest {

    @Test
    void contextInitialization() {
        new ApplicationContextRunner()
                .withUserConfiguration(SophonAutoConfigurationTest.Context.class)
                .run(applicationContext -> {
                    assertThat(applicationContext).isNotNull();
                    assertThat(applicationContext.getBean(Configurer.class)).isNotNull();
                    assertThat(applicationContext.getBean(Snapshotter.class)).isNotNull();

                    assertThat(applicationContext.getBean(CommandBus.class)).isNotNull();
                    assertThat(applicationContext.getBean(EventBus.class)).isNotNull();
                    assertThat(applicationContext.getBean(QueryBus.class)).isNotNull();
                    assertThat(applicationContext.getBean(EventStore.class)).isNotNull();
                    assertThat(applicationContext.getBean(CommandGateway.class)).isNotNull();
                    assertThat(applicationContext.getBean(EventGateway.class)).isNotNull();
                    assertThat(applicationContext.getBean(Serializer.class)).isNotNull();
                    assertThat(applicationContext.getBean(SpanFactory.class)).isNotNull();
                    assertThat(applicationContext.getBean(ParameterResolverFactory.class).getClass())
                            .isEqualTo(MultiParameterResolverFactory.class);
                    assertThat(applicationContext.getBeansOfType(EventStorageEngine.class)).hasSize(1);
                    assertThat(applicationContext.getBeansOfType(TokenStore.class)).hasSize(0);
                    assertThat(applicationContext.getBean(Context.MySaga.class)).isNotNull();
                    assertThat(applicationContext.getBean(Context.MyAggregate.class)).isNotNull();
                    assertThat(applicationContext.getBean(Repository.class)).isNotNull();
                    assertThat(applicationContext.getBean(AggregateFactory.class)).isNotNull();
                    assertThat(applicationContext.getBean(EventProcessingConfiguration.class)).isNotNull();

                    assertThat(applicationContext.getBean(org.sophonhq.sophon.config.Configuration.class)
                            .correlationDataProviders()).hasSize(2);

                    Context.SomeComponent someComponent = applicationContext.getBean(Context.SomeComponent.class);
                    assertThat(someComponent.invocations).isEmpty();
                    applicationContext.getBean(EventBus.class).publish(asEventMessage("testing"));
                    assertThat(someComponent.invocations).hasSize(1);

                    assertThat(applicationContext.getBean(Serializer.class)).isInstanceOf(XStreamSerializer.class);
                    assertThat(applicationContext.getBean("messageSerializer"))
                            .isSameAs(applicationContext.getBean("eventSerializer"));
                    assertThat(applicationContext.getBean("messageSerializer"))
                            .isSameAs(applicationContext.getBean("serializer"));
                });
    }

    @Test
    void ambiguousComponentsThrowExceptionWhenRequestedFromConfiguration() {
        ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean("gatewayOne", CommandGateway.class, () -> DefaultCommandGateway.builder().commandBus(SimpleCommandBus.builder().build()).build())
                .withBean("gatewayTwo", CommandGateway.class, () -> DefaultCommandGateway.builder().commandBus(SimpleCommandBus.builder().build()).build());

        SophonConfigurationException actual = assertThatExceptionOfType(SophonConfigurationException.class)
                .isThrownBy(() -> {
                    applicationContextRunner
                            .run(ctx -> ctx.getBean(org.sophonhq.sophon.config.Configuration.class).commandGateway());
                })
                .actual();
        assertThat(actual.getMessage()).contains("CommandGateway");
        assertThat(actual.getMessage()).contains("gatewayOne");
        assertThat(actual.getMessage()).contains("gatewayTwo");

    }

    @Test
    void beansImplementingLifecycleHaveTheirHandlersRegistered() {
        ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean("lifecycletest", CustomLifecycleBean.class, CustomLifecycleBean::new);

        applicationContextRunner.run(context -> {
            assertThat(context.getBean("lifecycletest", CustomLifecycleBean.class).isInvoked()).isTrue();
        });
    }

    @Test
    void shutDownCalledOnEmbeddedEventStoreEngine() {
        ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(Context.class);

        AtomicReference<EmbeddedEventStore> eventStore = new AtomicReference<>();

        applicationContextRunner.run(context -> {
            eventStore.set(context.getBean(EmbeddedEventStore.class));
            assertThat(eventStore.get()).isNotNull();
        });
        verify(eventStore.get(), atLeastOnce()).shutDown();
    }

    @Test
    void ambiguousPrimaryComponentsThrowExceptionWhenRequestedFromConfiguration() {
        ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean("gatewayOne",
                          CommandGateway.class,
                          () -> DefaultCommandGateway.builder().commandBus(SimpleCommandBus.builder().build()).build(),
                          beanDefinition -> beanDefinition.setPrimary(true))
                .withBean("gatewayTwo",
                          CommandGateway.class,
                          () -> DefaultCommandGateway.builder().commandBus(SimpleCommandBus.builder().build()).build(),
                          beanDefinition -> beanDefinition.setPrimary(true));

        SophonConfigurationException actual = assertThatExceptionOfType(SophonConfigurationException.class)
                .isThrownBy(() -> {
                    applicationContextRunner
                            .run(ctx -> ctx.getBean(org.sophonhq.sophon.config.Configuration.class).commandGateway());
                })
                .actual();
        assertThat(actual.getMessage()).contains("CommandGateway");
        assertThat(actual.getMessage()).contains("gatewayOne");
        assertThat(actual.getMessage()).contains("gatewayTwo");
    }

    @Test
    void ambiguousBeanDependenciesThrowException() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean("gatewayOne", CommandBus.class, () -> SimpleCommandBus.builder().build())
                .withBean("gatewayTwo", CommandBus.class, () -> SimpleCommandBus.builder().build())
                .run(ctx -> {
                    Throwable startupFailure = ctx.getStartupFailure();
                    assertThat(startupFailure).isInstanceOf(UnsatisfiedDependencyException.class);
                    assertThat(startupFailure.getMessage())
                            .as("Expected mention of 'CommandBus' in: " + startupFailure.getMessage())
                            .contains("CommandBus");
                    assertThat(startupFailure.getMessage())
                            .as("Expected mention of 'gatewayOne' in: " + startupFailure.getMessage())
                            .contains("gatewayOne");
                    assertThat(startupFailure.getMessage())
                            .as("Expected mention of 'gatewayTwo' in: " + startupFailure.getMessage())
                            .contains("gatewayTwo");
                });
    }

    @Test
    void embeddedEventStoreDefaultsToOptimizeEventConsumptionEnabled() throws Exception {
        new ApplicationContextRunner()
                .withUserConfiguration(MinimalContext.class)
                .run(context -> {
                    EmbeddedEventStore eventStore = context.getBean(EmbeddedEventStore.class);
                    java.lang.reflect.Field field = EmbeddedEventStore.class.getDeclaredField("optimizeEventConsumption");
                    field.setAccessible(true);
                    assertThat((boolean) field.get(eventStore)).isTrue();
                });
    }

    @Test
    void embeddedEventStoreOptimizeEventConsumptionCanBeDisabledViaProperties() throws Exception {
        new ApplicationContextRunner()
                .withUserConfiguration(MinimalContext.class)
                .withPropertyValues("sophon.eventstore.optimize-event-consumption=false")
                .run(context -> {
                    EmbeddedEventStore eventStore = context.getBean(EmbeddedEventStore.class);
                    java.lang.reflect.Field field = EmbeddedEventStore.class.getDeclaredField("optimizeEventConsumption");
                    field.setAccessible(true);
                    assertThat((boolean) field.get(eventStore)).isFalse();
                });
    }

    @Test
    void cborSerializerUsesDefaultCborMapperBeanWhenMultipleCborMappersExist() {
        CBORMapper defaultCborMapper = new CBORMapper();
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withPropertyValues("sophon.serializer.general=cbor")
                .withBean("otherCborMapper", CBORMapper.class, CBORMapper::new)
                .withBean("defaultSophonCborMapper", CBORMapper.class, () -> defaultCborMapper)
                .run(context -> {
                    Serializer serializer = context.getBean(Serializer.class);

                    assertThat(serializer).isInstanceOf(JacksonSerializer.class);
                    assertThat(((JacksonSerializer) serializer).getObjectMapper()).isSameAs(defaultCborMapper);
                });
    }

    @Test
    void serializersTranslateLegacyClassNamesWhenEnabled() {
        SerializedType legacyType = new SimpleSerializedType(
                "org.axonframework.eventhandling.GlobalSequenceTrackingToken", null
        );
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withPropertyValues("sophon.serializer.translate-legacy-class-names=true")
                .run(context -> assertThat(context.getBean(Serializer.class).classForType(legacyType))
                        .isEqualTo(GlobalSequenceTrackingToken.class));
    }

    @Test
    void serializersDoNotTranslateLegacyClassNamesByDefault() {
        SerializedType legacyType = new SimpleSerializedType(
                "org.axonframework.eventhandling.GlobalSequenceTrackingToken", null
        );
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> assertThat(context.getBean(Serializer.class).classForType(legacyType))
                        .isEqualTo(UnknownSerializedType.class));
    }

    @Test
    void legacyPropertiesDetectorIsRegistered() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> assertThat(context.getBean(LegacyPropertiesDetector.class)).isNotNull());
    }

    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
    @Configuration
    public static class Context {

        @Autowired
        public void configure(EventProcessingConfigurer eventProcessingConfigurer) {
            eventProcessingConfigurer.usingSubscribingEventProcessors();
        }

        @Bean
        public SnapshotTriggerDefinition snapshotTriggerDefinition(Snapshotter snapshotter) {
            return new EventCountSnapshotTriggerDefinition(snapshotter, 2);
        }

        @Bean
        public ParameterResolverFactory customerParameterResolverFactory() {
            return new SimpleResourceParameterResolverFactory(singleton(new CustomResource()));
        }

        @Bean
        public EventStore eventStore() {
            return spy(EmbeddedEventStore.builder().storageEngine(storageEngine()).build());
        }

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

        @Aggregate(snapshotTriggerDefinition = "snapshotTriggerDefinition")
        public static class MyAggregate {

            @CommandHandler
            public void handle(String type, SomeComponent test, CustomResource resource) {

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

        @Saga
        public static class MyDefaultConfigSaga {

            @SagaEventHandler(associationProperty = "toString")
            public void handle(String type, SomeComponent test) {

            }
        }

        @Component
        public static class CustomParameterResolverFactory implements ParameterResolverFactory {

            private final EventBus eventBus;

            public CustomParameterResolverFactory(EventBus eventBus) {
                this.eventBus = eventBus;
            }

            @Override
            public ParameterResolver createInstance(Executable executable, Parameter[] parameters, int parameterIndex) {
                if (Integer.class.isAssignableFrom(parameters[parameterIndex].getType())) {
                    return new FixedValueParameterResolver<>(1);
                }
                return null;
            }
        }

        @Component
        public static class SomeComponent {

            private final List<String> invocations = new ArrayList<>();

            public SomeComponent(org.sophonhq.sophon.config.Configuration sophonConfig) {
                // just to see if wiring the configuration causes circular dependencies.
            }

            @EventHandler
            public void handle(String event, SomeOtherComponent test, Integer testing) {
                invocations.add(event);
            }
        }

        @Component
        public static class SomeOtherComponent {

        }
    }

    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
    @Configuration
    public static class MinimalContext {

        @Bean
        public EventStorageEngine storageEngine() {
            return new InMemoryEventStorageEngine();
        }
    }

    public static class CustomResource {

    }

    private class CustomLifecycleBean implements Lifecycle {
        private boolean invoked;

        @Override
        public void registerLifecycleHandlers(LifecycleRegistry lifecycle) {
            this.invoked = true;
        }

        public boolean isInvoked() {
            return invoked;
        }
    }
}
