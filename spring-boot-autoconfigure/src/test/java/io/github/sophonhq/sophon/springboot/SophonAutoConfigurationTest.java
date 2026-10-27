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

package io.github.sophonhq.sophon.springboot;

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import io.github.sophonhq.sophon.commandhandling.CommandBus;
import io.github.sophonhq.sophon.commandhandling.CommandHandler;
import io.github.sophonhq.sophon.commandhandling.SimpleCommandBus;
import io.github.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import io.github.sophonhq.sophon.commandhandling.gateway.DefaultCommandGateway;
import io.github.sophonhq.sophon.common.SophonConfigurationException;
import io.github.sophonhq.sophon.config.Configurer;
import io.github.sophonhq.sophon.config.EventProcessingConfiguration;
import io.github.sophonhq.sophon.config.EventProcessingConfigurer;
import io.github.sophonhq.sophon.eventhandling.EventBus;
import io.github.sophonhq.sophon.eventhandling.EventHandler;
import io.github.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import io.github.sophonhq.sophon.eventhandling.gateway.EventGateway;
import io.github.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import io.github.sophonhq.sophon.eventsourcing.AggregateFactory;
import io.github.sophonhq.sophon.eventsourcing.EventCountSnapshotTriggerDefinition;
import io.github.sophonhq.sophon.eventsourcing.SnapshotTriggerDefinition;
import io.github.sophonhq.sophon.eventsourcing.Snapshotter;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import io.github.sophonhq.sophon.lifecycle.Lifecycle;
import io.github.sophonhq.sophon.messaging.annotation.FixedValueParameterResolver;
import io.github.sophonhq.sophon.messaging.annotation.MultiParameterResolverFactory;
import io.github.sophonhq.sophon.messaging.annotation.ParameterResolver;
import io.github.sophonhq.sophon.messaging.annotation.ParameterResolverFactory;
import io.github.sophonhq.sophon.messaging.annotation.SimpleResourceParameterResolverFactory;
import io.github.sophonhq.sophon.messaging.correlation.CorrelationDataProvider;
import io.github.sophonhq.sophon.messaging.correlation.SimpleCorrelationDataProvider;
import io.github.sophonhq.sophon.modelling.command.Repository;
import io.github.sophonhq.sophon.modelling.saga.SagaEventHandler;
import io.github.sophonhq.sophon.queryhandling.QueryBus;
import io.github.sophonhq.sophon.serialization.SerializedType;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.serialization.SimpleSerializedType;
import io.github.sophonhq.sophon.serialization.UnknownSerializedType;
import io.github.sophonhq.sophon.serialization.json.JacksonSerializer;
import io.github.sophonhq.sophon.serialization.xml.XStreamSerializer;
import io.github.sophonhq.sophon.spring.stereotype.Aggregate;
import io.github.sophonhq.sophon.spring.stereotype.Saga;
import io.github.sophonhq.sophon.springboot.util.LegacyPropertiesDetector;
import io.github.sophonhq.sophon.tracing.SpanFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singleton;
import static io.github.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SophonAutoConfigurationTest {

    @Test
    void contextInitialization() {
        new ApplicationContextRunner()
                .withUserConfiguration(SophonAutoConfigurationTest.Context.class)
                .run(applicationContext -> {
                    assertNotNull(applicationContext);
                    assertNotNull(applicationContext.getBean(Configurer.class));
                    assertNotNull(applicationContext.getBean(Snapshotter.class));

                    assertNotNull(applicationContext.getBean(CommandBus.class));
                    assertNotNull(applicationContext.getBean(EventBus.class));
                    assertNotNull(applicationContext.getBean(QueryBus.class));
                    assertNotNull(applicationContext.getBean(EventStore.class));
                    assertNotNull(applicationContext.getBean(CommandGateway.class));
                    assertNotNull(applicationContext.getBean(EventGateway.class));
                    assertNotNull(applicationContext.getBean(Serializer.class));
                    assertNotNull(applicationContext.getBean(SpanFactory.class));
                    assertEquals(MultiParameterResolverFactory.class,
                                 applicationContext.getBean(ParameterResolverFactory.class).getClass());
                    assertEquals(1, applicationContext.getBeansOfType(EventStorageEngine.class).size());
                    assertEquals(0, applicationContext.getBeansOfType(TokenStore.class).size());
                    assertNotNull(applicationContext.getBean(Context.MySaga.class));
                    assertNotNull(applicationContext.getBean(Context.MyAggregate.class));
                    assertNotNull(applicationContext.getBean(Repository.class));
                    assertNotNull(applicationContext.getBean(AggregateFactory.class));
                    assertNotNull(applicationContext.getBean(EventProcessingConfiguration.class));

                    assertEquals(2,
                                 applicationContext.getBean(io.github.sophonhq.sophon.config.Configuration.class)
                                                   .correlationDataProviders().size());

                    Context.SomeComponent someComponent = applicationContext.getBean(Context.SomeComponent.class);
                    assertEquals(0, someComponent.invocations.size());
                    applicationContext.getBean(EventBus.class).publish(asEventMessage("testing"));
                    assertEquals(1, someComponent.invocations.size());

                    assertTrue(applicationContext.getBean(Serializer.class) instanceof XStreamSerializer);
                    assertSame(applicationContext.getBean("eventSerializer"),
                               applicationContext.getBean("messageSerializer"));
                    assertSame(applicationContext.getBean("serializer"),
                               applicationContext.getBean("messageSerializer"));
                });
    }

    @Test
    void ambiguousComponentsThrowExceptionWhenRequestedFromConfiguration() {
        ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean("gatewayOne", CommandGateway.class, () -> DefaultCommandGateway.builder().commandBus(SimpleCommandBus.builder().build()).build())
                .withBean("gatewayTwo", CommandGateway.class, () -> DefaultCommandGateway.builder().commandBus(SimpleCommandBus.builder().build()).build());

        SophonConfigurationException actual = assertThrows(SophonConfigurationException.class, () -> {
            applicationContextRunner
                    .run(ctx -> ctx.getBean(io.github.sophonhq.sophon.config.Configuration.class).commandGateway());
        });
        assertTrue(actual.getMessage().contains("CommandGateway"));
        assertTrue(actual.getMessage().contains("gatewayOne"));
        assertTrue(actual.getMessage().contains("gatewayTwo"));

    }

    @Test
    void beansImplementingLifecycleHaveTheirHandlersRegistered() {
        ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean("lifecycletest", CustomLifecycleBean.class, CustomLifecycleBean::new);

        applicationContextRunner.run(context -> {
            assertTrue(context.getBean("lifecycletest", CustomLifecycleBean.class).isInvoked());
        });
    }

    @Test
    void shutDownCalledOnEmbeddedEventStoreEngine() {
        ApplicationContextRunner applicationContextRunner = new ApplicationContextRunner()
                .withUserConfiguration(Context.class);

        AtomicReference<EmbeddedEventStore> eventStore = new AtomicReference<>();

        applicationContextRunner.run(context -> {
            eventStore.set(context.getBean(EmbeddedEventStore.class));
            assertNotNull(eventStore.get());
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

        SophonConfigurationException actual = assertThrows(SophonConfigurationException.class, () -> {
            applicationContextRunner
                    .run(ctx -> ctx.getBean(io.github.sophonhq.sophon.config.Configuration.class).commandGateway());
        });
        assertTrue(actual.getMessage().contains("CommandGateway"));
        assertTrue(actual.getMessage().contains("gatewayOne"));
        assertTrue(actual.getMessage().contains("gatewayTwo"));
    }

    @Test
    void ambiguousBeanDependenciesThrowException() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean("gatewayOne", CommandBus.class, () -> SimpleCommandBus.builder().build())
                .withBean("gatewayTwo", CommandBus.class, () -> SimpleCommandBus.builder().build())
                .run(ctx -> {
                    Throwable startupFailure = ctx.getStartupFailure();
                    assertTrue(startupFailure instanceof UnsatisfiedDependencyException);
                    assertTrue(startupFailure.getMessage().contains("CommandBus"), "Expected mention of 'CommandBus' in: " + startupFailure.getMessage());
                    assertTrue(startupFailure.getMessage().contains("gatewayOne"), "Expected mention of 'gatewayOne' in: " + startupFailure.getMessage());
                    assertTrue(startupFailure.getMessage().contains("gatewayTwo"), "Expected mention of 'gatewayTwo' in: " + startupFailure.getMessage());
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
                    assertTrue((boolean) field.get(eventStore));
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
                    assertFalse((boolean) field.get(eventStore));
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

                    assertTrue(serializer instanceof JacksonSerializer);
                    assertSame(defaultCborMapper, ((JacksonSerializer) serializer).getObjectMapper());
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
                .run(context -> assertEquals(GlobalSequenceTrackingToken.class,
                                             context.getBean(Serializer.class).classForType(legacyType)));
    }

    @Test
    void serializersDoNotTranslateLegacyClassNamesByDefault() {
        SerializedType legacyType = new SimpleSerializedType(
                "org.axonframework.eventhandling.GlobalSequenceTrackingToken", null
        );
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> assertEquals(UnknownSerializedType.class,
                                             context.getBean(Serializer.class).classForType(legacyType)));
    }

    @Test
    void legacyPropertiesDetectorIsRegistered() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> assertNotNull(context.getBean(LegacyPropertiesDetector.class)));
    }

    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
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

            public SomeComponent(io.github.sophonhq.sophon.config.Configuration sophonConfig) {
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
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
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
