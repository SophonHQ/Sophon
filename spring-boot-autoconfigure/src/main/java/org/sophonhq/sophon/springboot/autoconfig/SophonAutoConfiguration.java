/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.sophonhq.sophon.springboot.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.thoughtworks.xstream.XStream;
import org.apache.avro.message.SchemaStore;
import org.jspecify.annotations.NonNull;
import org.sophonhq.sophon.commandhandling.CommandBus;
import org.sophonhq.sophon.commandhandling.CommandBusSpanFactory;
import org.sophonhq.sophon.commandhandling.DuplicateCommandHandlerResolver;
import org.sophonhq.sophon.commandhandling.LoggingDuplicateCommandHandlerResolver;
import org.sophonhq.sophon.commandhandling.SimpleCommandBus;
import org.sophonhq.sophon.commandhandling.distributed.AnnotationRoutingStrategy;
import org.sophonhq.sophon.commandhandling.distributed.RoutingStrategy;
import org.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import org.sophonhq.sophon.commandhandling.gateway.DefaultCommandGateway;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.common.SophonThreadFactory;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.config.*;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.eventhandling.EventBusSpanFactory;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.SimpleEventBus;
import org.sophonhq.sophon.eventhandling.TrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackingEventProcessorConfiguration;
import org.sophonhq.sophon.eventhandling.async.SequencingPolicy;
import org.sophonhq.sophon.eventhandling.async.SequentialPerAggregatePolicy;
import org.sophonhq.sophon.eventhandling.gateway.DefaultEventGateway;
import org.sophonhq.sophon.eventhandling.gateway.EventGateway;
import org.sophonhq.sophon.eventsourcing.Snapshotter;
import org.sophonhq.sophon.eventsourcing.SnapshotterSpanFactory;
import org.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import org.sophonhq.sophon.messaging.StreamableMessageSource;
import org.sophonhq.sophon.messaging.SubscribableMessageSource;
import org.sophonhq.sophon.messaging.annotation.HandlerDefinition;
import org.sophonhq.sophon.messaging.annotation.ParameterResolverFactory;
import org.sophonhq.sophon.messaging.correlation.CorrelationDataProvider;
import org.sophonhq.sophon.messaging.correlation.MessageOriginProvider;
import org.sophonhq.sophon.messaging.interceptors.CorrelationDataInterceptor;
import org.sophonhq.sophon.queryhandling.DefaultQueryGateway;
import org.sophonhq.sophon.queryhandling.LoggingQueryInvocationErrorHandler;
import org.sophonhq.sophon.queryhandling.QueryBus;
import org.sophonhq.sophon.queryhandling.QueryBusSpanFactory;
import org.sophonhq.sophon.queryhandling.QueryGateway;
import org.sophonhq.sophon.queryhandling.QueryInvocationErrorHandler;
import org.sophonhq.sophon.queryhandling.QueryUpdateEmitter;
import org.sophonhq.sophon.queryhandling.QueryUpdateEmitterSpanFactory;
import org.sophonhq.sophon.queryhandling.SimpleQueryBus;
import org.sophonhq.sophon.queryhandling.SimpleQueryUpdateEmitter;
import org.sophonhq.sophon.serialization.AnnotationRevisionResolver;
import org.sophonhq.sophon.serialization.ChainingConverter;
import org.sophonhq.sophon.serialization.JavaSerializer;
import org.sophonhq.sophon.serialization.RevisionResolver;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.avro.AvroSerializer;
import org.sophonhq.sophon.serialization.avro.AvroSerializerStrategy;
import org.sophonhq.sophon.serialization.jackson3.Jackson3Serializer;
import org.sophonhq.sophon.serialization.jackson3.Jackson3SerializerCustomizer;
import org.sophonhq.sophon.serialization.json.JacksonSerializer;
import org.sophonhq.sophon.serialization.xml.XStreamSerializer;
import org.sophonhq.sophon.spring.eventsourcing.SpringAggregateSnapshotter;
import org.sophonhq.sophon.springboot.DistributedCommandBusProperties;
import org.sophonhq.sophon.springboot.EmbeddedEventStoreProperties;
import org.sophonhq.sophon.springboot.EventProcessorProperties;
import org.sophonhq.sophon.springboot.SerializerProperties;
import org.sophonhq.sophon.springboot.TagsConfigurationProperties;
import org.sophonhq.sophon.springboot.util.ConditionalOnMissingQualifiedBean;
import org.sophonhq.sophon.springboot.util.LegacyPropertiesDetector;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

import static java.lang.String.format;
import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

/**
 * @author Allard Buijze
 * @author Josh Long
 */
@AutoConfiguration
@AutoConfigureAfter(EventProcessingAutoConfiguration.class)
@EnableConfigurationProperties(value = {
        EmbeddedEventStoreProperties.class,
        EventProcessorProperties.class,
        DistributedCommandBusProperties.class,
        SerializerProperties.class,
        TagsConfigurationProperties.class
})
public class SophonAutoConfiguration implements BeanClassLoaderAware {

    private final EmbeddedEventStoreProperties embeddedEventStoreProperties;
    private final EventProcessorProperties eventProcessorProperties;
    private final SerializerProperties serializerProperties;
    private final TagsConfigurationProperties tagsConfigurationProperties;
    private final ApplicationContext applicationContext;

    private ClassLoader beanClassLoader;

    public SophonAutoConfiguration(EmbeddedEventStoreProperties embeddedEventStoreProperties,
                                 EventProcessorProperties eventProcessorProperties,
                                 SerializerProperties serializerProperties,
                                 TagsConfigurationProperties tagsConfigurationProperties,
                                 ApplicationContext applicationContext) {
        this.embeddedEventStoreProperties = embeddedEventStoreProperties;
        this.eventProcessorProperties = eventProcessorProperties;
        this.serializerProperties = serializerProperties;
        this.tagsConfigurationProperties = tagsConfigurationProperties;
        this.applicationContext = applicationContext;
    }

    @Bean
    public TagsConfiguration tagsConfiguration() {
        return tagsConfigurationProperties.toTagsConfiguration();
    }

    @Bean
    @ConditionalOnMissingBean
    public RevisionResolver revisionResolver() {
        return new AnnotationRevisionResolver();
    }

    @Bean
    @Primary
    @ConditionalOnMissingQualifiedBean(beanClass = Serializer.class, qualifier = "!eventSerializer,messageSerializer")
    public Serializer serializer(RevisionResolver revisionResolver) {
        if (SerializerProperties.SerializerType.AVRO.equals(serializerProperties.getGeneral())) {
            throw new SophonConfigurationException(format(
                    "Invalid serializer type [%s] configured as general serializer. "
                            + "The Avro Serializer can be used as message or event serializer only.",
                    serializerProperties.getGeneral().name()
            ));
        }
        return buildSerializer(revisionResolver, serializerProperties.getGeneral(), null);
    }

    @Bean
    @Qualifier("messageSerializer")
    @ConditionalOnMissingQualifiedBean(beanClass = Serializer.class, qualifier = "messageSerializer")
    public Serializer messageSerializer(Serializer generalSerializer, RevisionResolver revisionResolver) {
        if (SerializerProperties.SerializerType.DEFAULT.equals(serializerProperties.getMessages())
                || serializerProperties.getGeneral().equals(serializerProperties.getMessages())) {
            return generalSerializer;
        }
        return buildSerializer(revisionResolver, serializerProperties.getMessages(), generalSerializer);
    }

    @Bean
    @Qualifier("eventSerializer")
    @ConditionalOnMissingQualifiedBean(beanClass = Serializer.class, qualifier = "eventSerializer")
    public Serializer eventSerializer(@Qualifier("messageSerializer") Serializer messageSerializer,
                                      Serializer generalSerializer,
                                      RevisionResolver revisionResolver) {
        if (SerializerProperties.SerializerType.DEFAULT.equals(serializerProperties.getEvents())
                || serializerProperties.getEvents().equals(serializerProperties.getMessages())) {
            return messageSerializer;
        } else if (serializerProperties.getGeneral().equals(serializerProperties.getEvents())) {
            return generalSerializer;
        }
        return buildSerializer(revisionResolver, serializerProperties.getEvents(), generalSerializer);
    }

    @Bean
    public ConfigurerModule serializerConfigurer(@Qualifier("eventSerializer") Serializer eventSerializer,
                                                 @Qualifier("messageSerializer") Serializer messageSerializer,
                                                 Serializer generalSerializer) {
        return configurer -> {
            configurer.configureEventSerializer(c -> eventSerializer);
            configurer.configureMessageSerializer(c -> messageSerializer);
            configurer.configureSerializer(c -> generalSerializer);
        };
    }

    private Serializer buildSerializer(RevisionResolver revisionResolver,
                                       SerializerProperties.SerializerType serializerType,
                                       Serializer generalSerializer) {
        switch (serializerType) {
            case AVRO:
                Map<String, SchemaStore> schemaStoreBeans = beansOfTypeIncludingAncestors(applicationContext,
                                                                                          SchemaStore.class);
                SchemaStore schemaStore = schemaStoreBeans.containsKey("defaultSophonSchemaStore")
                        ? schemaStoreBeans.get("defaultSophonSchemaStore")
                        : schemaStoreBeans.values().stream().findFirst()
                                          .orElseThrow(() -> new NoSuchBeanDefinitionException(SchemaStore.class));

                if (generalSerializer == null) {
                    throw new SophonConfigurationException(
                            "General serializer is mandatory as a fallback Avro Serializer, but none was provided."
                    );
                }
                Map<String, AvroSerializerStrategy> serializationStrategies = beansOfTypeIncludingAncestors(
                        applicationContext,
                        AvroSerializerStrategy.class);
                AvroSerializer.Builder builder = AvroSerializer.builder()
                                                               .schemaStore(schemaStore)
                                                               .serializerDelegate(generalSerializer)
                                                               .revisionResolver(revisionResolver);
                serializationStrategies.values().forEach(builder::addSerializerStrategy);
                return builder.build();

            case JACKSON:
                Map<String, ObjectMapper> objectMapperBeans = beansOfTypeIncludingAncestors(applicationContext,
                                                                                            ObjectMapper.class);
                ObjectMapper objectMapper = objectMapperBeans.containsKey("defaultSophonObjectMapper")
                        ? objectMapperBeans.get("defaultSophonObjectMapper")
                        : objectMapperBeans.values().stream().findFirst()
                                           .orElseThrow(() -> new NoSuchBeanDefinitionException(ObjectMapper.class));
                ChainingConverter converter = new ChainingConverter(beanClassLoader);
                JacksonSerializer.Builder jacksonBuilder = JacksonSerializer.builder()
                                                                            .revisionResolver(revisionResolver)
                                                                            .converter(converter)
                                                                            .objectMapper(objectMapper);
                if (serializerProperties.isTranslateLegacyClassNames()) {
                    jacksonBuilder.translateLegacyClassNames();
                }
                return jacksonBuilder.build();
            case JACKSON3:
                Map<String, Jackson3SerializerCustomizer> customizerBeans = beansOfTypeIncludingAncestors(applicationContext, Jackson3SerializerCustomizer.class);
                Jackson3SerializerCustomizer customizer = customizerBeans.values().stream().findFirst().orElse(null);
                ChainingConverter converter3 = new ChainingConverter(beanClassLoader);

                Jackson3Serializer.Builder jackson3Builder = Jackson3Serializer.builder()
                                                                               .revisionResolver(revisionResolver)
                                                                               .converter(converter3)
                                                                               .jsonMapperBuilderCustomizer(customizer);
                if (serializerProperties.isTranslateLegacyClassNames()) {
                    jackson3Builder.translateLegacyClassNames();
                }
                return jackson3Builder.build();
            case CBOR:
                Map<String, CBORMapper> cborMapperBeans = beansOfTypeIncludingAncestors(applicationContext,
                                                                                        CBORMapper.class);
                ObjectMapper cborMapper = cborMapperBeans.containsKey("defaultSophonCborMapper")
                        ? cborMapperBeans.get("defaultSophonCborMapper")
                        : cborMapperBeans.values().stream().findFirst()
                                         .orElseThrow(() -> new NoSuchBeanDefinitionException(CBORMapper.class));
                ChainingConverter cborConverter = new ChainingConverter(beanClassLoader);
                JacksonSerializer.Builder cborBuilder = JacksonSerializer.builder()
                                                                         .revisionResolver(revisionResolver)
                                                                         .converter(cborConverter)
                                                                         .objectMapper(cborMapper);
                if (serializerProperties.isTranslateLegacyClassNames()) {
                    cborBuilder.translateLegacyClassNames();
                }
                return cborBuilder.build();
            case JAVA:
                JavaSerializer.Builder javaBuilder = JavaSerializer.builder().revisionResolver(revisionResolver);
                if (serializerProperties.isTranslateLegacyClassNames()) {
                    javaBuilder.translateLegacyClassNames();
                }
                return javaBuilder.build();
            case XSTREAM:
            case DEFAULT:
            default:
                Map<String, XStream> xStreamBeans = beansOfTypeIncludingAncestors(applicationContext, XStream.class);
                XStream xStream = xStreamBeans.containsKey("defaultSophonXStream")
                        ? xStreamBeans.get("defaultSophonXStream")
                        : xStreamBeans.values().stream().findFirst()
                                      .orElseThrow(() -> new NoSuchBeanDefinitionException(XStream.class));
                XStreamSerializer.Builder xStreamBuilder = XStreamSerializer.builder()
                                                                            .xStream(xStream)
                                                                            .revisionResolver(revisionResolver)
                                                                            .classLoader(beanClassLoader);
                if (serializerProperties.isTranslateLegacyClassNames()) {
                    xStreamBuilder.translateLegacyClassNames();
                }
                return xStreamBuilder.build();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public CorrelationDataProvider messageOriginProvider() {
        return new MessageOriginProvider();
    }

    @Qualifier("eventStore")
    @Bean(name = "eventBus")
    @ConditionalOnMissingBean(EventBus.class)
    @ConditionalOnBean(EventStorageEngine.class)
    public EmbeddedEventStore eventStore(EventStorageEngine storageEngine, Configuration configuration) {
        return EmbeddedEventStore.builder()
                                 .storageEngine(storageEngine)
                                 .messageMonitor(configuration.messageMonitor(EventStore.class, "eventStore"))
                                 .spanFactory(configuration.getComponent(EventBusSpanFactory.class))
                                 .optimizeEventConsumption(embeddedEventStoreProperties.isOptimizeEventConsumption())
                                 .cachedEvents(embeddedEventStoreProperties.getCachedEvents())
                                 .fetchDelay(embeddedEventStoreProperties.getFetchDelay().toMillis())
                                 .cleanupDelay(embeddedEventStoreProperties.getCleanupDelay().toMillis())
                                 .build();
    }

    @ConditionalOnMissingBean
    @Bean
    public CommandGateway commandGateway(CommandBus commandBus) {
        return DefaultCommandGateway.builder().commandBus(commandBus).build();
    }

    @ConditionalOnMissingBean
    @Bean
    public QueryGateway queryGateway(QueryBus queryBus) {
        return DefaultQueryGateway.builder().queryBus(queryBus).build();
    }

    @Bean
    @ConditionalOnMissingBean({EventStorageEngine.class, EventBus.class})
    public SimpleEventBus eventBus(Configuration configuration) {
        return SimpleEventBus.builder()
                             .messageMonitor(configuration.messageMonitor(EventStore.class, "eventStore"))
                             .spanFactory(configuration.getComponent(EventBusSpanFactory.class))
                             .build();
    }

    @ConditionalOnMissingBean
    @Bean
    public EventGateway eventGateway(EventBus eventBus) {
        return DefaultEventGateway.builder().eventBus(eventBus).build();
    }

    @ConditionalOnMissingBean(Snapshotter.class)
    @ConditionalOnBean(EventStore.class)
    @Bean
    public SpringAggregateSnapshotter aggregateSnapshotter(Configuration configuration,
                                                           HandlerDefinition handlerDefinition,
                                                           ParameterResolverFactory parameterResolverFactory,
                                                           EventStore eventStore,
                                                           TransactionManager transactionManager,
                                                           SnapshotterSpanFactory spanFactory) {
        return SpringAggregateSnapshotter.builder()
                                         .repositoryProvider(configuration::repository)
                                         .transactionManager(transactionManager)
                                         .eventStore(eventStore)
                                         .parameterResolverFactory(parameterResolverFactory)
                                         .handlerDefinition(handlerDefinition)
                                         .spanFactory(spanFactory)
                                         .build();
    }

    @SuppressWarnings("unchecked")
    @Autowired
    public void configureEventHandling(EventProcessingConfigurer eventProcessingConfigurer,
                                       ApplicationContext applicationContext) {
        eventProcessorProperties.getProcessors().forEach((name, settings) -> {
            Function<Configuration, SequencingPolicy<? super EventMessage<?>>> sequencingPolicy =
                    resolveSequencingPolicy(applicationContext, settings);
            eventProcessingConfigurer.registerSequencingPolicy(name, sequencingPolicy);

            if (settings.getMode() == EventProcessorProperties.Mode.TRACKING) {
                TrackingEventProcessorConfiguration config = TrackingEventProcessorConfiguration
                        .forParallelProcessing(settings.getThreadCount())
                        .andBatchSize(settings.getBatchSize())
                        .andInitialSegmentsCount(initialSegmentCount(settings, 1))
                        .andTokenClaimInterval(settings.getTokenClaimInterval(),
                                               settings.getTokenClaimIntervalTimeUnit());
                Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> messageSource =
                        resolveMessageSource(applicationContext, settings);
                eventProcessingConfigurer.registerTrackingEventProcessor(name, messageSource, c -> config);
            } else if (settings.getMode() == EventProcessorProperties.Mode.POOLED) {
                eventProcessingConfigurer.registerPooledStreamingEventProcessor(
                        name,
                        resolveMessageSource(applicationContext, settings),
                        (config, builder) -> {
                            ScheduledExecutorService workerExecutor = Executors.newScheduledThreadPool(
                                    settings.getThreadCount(), new SophonThreadFactory("WorkPackage[" + name + "]")
                            );
                            config.onShutdown(workerExecutor::shutdown);
                            return builder.workerExecutor(workerExecutor)
                                          .initialSegmentCount(initialSegmentCount(settings, 16))
                                          .tokenClaimInterval(tokenClaimIntervalMillis(settings))
                                          .batchSize(settings.getBatchSize());
                        }
                );
            } else {
                if (settings.getSource() == null) {
                    eventProcessingConfigurer.registerSubscribingEventProcessor(name);
                } else {
                    eventProcessingConfigurer.registerSubscribingEventProcessor(
                            name,
                            c -> {
                                Object bean = applicationContext.getBean(settings.getSource());
                                if (bean instanceof SubscribableMessageSourceDefinition) {
                                    return ((SubscribableMessageSourceDefinition<? extends EventMessage<?>>) bean)
                                            .create(c);
                                }
                                if (bean instanceof SubscribableMessageSource) {
                                    return (SubscribableMessageSource<? extends EventMessage<?>>) bean;
                                }
                                throw new SophonConfigurationException(format(
                                        "Invalid message source [%s] configured for Event Processor [%s]. "
                                                + "The message source should be a SubscribableMessageSource or SubscribableMessageSourceFactory",
                                        settings.getSource(), name
                                ));
                            }
                    );
                }
            }
            if (settings.getDlq().getCache().isEnabled()) {
                eventProcessingConfigurer.registerDeadLetteringEventHandlerInvokerConfiguration(
                        name,
                        (c, builder) -> builder
                                .enableSequenceIdentifierCache()
                                .sequenceIdentifierCacheSize(settings.getDlq().getCache().getSize()));
            }
        });
    }

    private int initialSegmentCount(EventProcessorProperties.ProcessorSettings settings, int defaultCount) {
        return settings.getInitialSegmentCount() != null ? settings.getInitialSegmentCount() : defaultCount;
    }

    private long tokenClaimIntervalMillis(EventProcessorProperties.ProcessorSettings settings) {
        return settings.getTokenClaimIntervalTimeUnit().toMillis(settings.getTokenClaimInterval());
    }

    @SuppressWarnings("unchecked")
    private Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> resolveMessageSource(
            ApplicationContext applicationContext, EventProcessorProperties.ProcessorSettings v) {
        Function<Configuration, StreamableMessageSource<TrackedEventMessage<?>>> messageSource;
        if (v.getSource() == null) {
            messageSource = Configuration::eventStore;
        } else {
            messageSource = c -> applicationContext.getBean(v.getSource(), StreamableMessageSource.class);
        }
        return messageSource;
    }

    @SuppressWarnings("unchecked")
    private Function<Configuration, SequencingPolicy<? super EventMessage<?>>> resolveSequencingPolicy(
            ApplicationContext applicationContext, EventProcessorProperties.ProcessorSettings v) {
        Function<Configuration, SequencingPolicy<? super EventMessage<?>>> sequencingPolicy;
        if (v.getSequencingPolicy() != null) {
            sequencingPolicy = c -> applicationContext.getBean(v.getSequencingPolicy(), SequencingPolicy.class);
        } else {
            sequencingPolicy = c -> SequentialPerAggregatePolicy.instance();
        }
        return sequencingPolicy;
    }

    @Bean
    @ConditionalOnMissingBean
    public DuplicateCommandHandlerResolver duplicateCommandHandlerResolver() {
        return LoggingDuplicateCommandHandlerResolver.instance();
    }

    @Bean
    @ConditionalOnMissingBean
    public RoutingStrategy routingStrategy() {
        return AnnotationRoutingStrategy.defaultStrategy();
    }

    @Bean
    public LegacyPropertiesDetector legacyPropertiesDetector(Environment environment) {
        return new LegacyPropertiesDetector(environment);
    }

    @ConditionalOnMissingBean(
            ignoredType = {
                    "org.sophonhq.sophon.commandhandling.distributed.DistributedCommandBus"
            },
            value = CommandBus.class
    )
    @Qualifier("localSegment")
    @Bean
    public SimpleCommandBus commandBus(TransactionManager txManager, Configuration sophonConfiguration,
                                       DuplicateCommandHandlerResolver duplicateCommandHandlerResolver) {
        SimpleCommandBus commandBus =
                SimpleCommandBus.builder()
                                .transactionManager(txManager)
                                .duplicateCommandHandlerResolver(duplicateCommandHandlerResolver)
                                .spanFactory(sophonConfiguration.getComponent(CommandBusSpanFactory.class))
                                .messageMonitor(sophonConfiguration.messageMonitor(CommandBus.class, "commandBus"))
                                .build();
        commandBus.registerHandlerInterceptor(
                new CorrelationDataInterceptor<>(sophonConfiguration.correlationDataProviders())
        );
        return commandBus;
    }

    @ConditionalOnMissingBean(value = QueryBus.class)
    @Qualifier("localSegment")
    @Bean
    public SimpleQueryBus queryBus(Configuration sophonConfiguration, TransactionManager transactionManager) {
        return SimpleQueryBus.builder()
                             .messageMonitor(sophonConfiguration.messageMonitor(QueryBus.class, "queryBus"))
                             .transactionManager(transactionManager)
                             .errorHandler(sophonConfiguration.getComponent(
                                     QueryInvocationErrorHandler.class,
                                     () -> LoggingQueryInvocationErrorHandler.builder().build()
                             ))
                             .queryUpdateEmitter(sophonConfiguration.getComponent(QueryUpdateEmitter.class))
                             .spanFactory(sophonConfiguration.getComponent(QueryBusSpanFactory.class))
                             .build();
    }

    @Bean
    public QueryUpdateEmitter queryUpdateEmitter(Configuration configuration) {
        return SimpleQueryUpdateEmitter.builder()
                                       .updateMessageMonitor(configuration.messageMonitor(
                                               QueryUpdateEmitter.class, "queryUpdateEmitter"
                                       ))
                                       .spanFactory(configuration.getComponent(QueryUpdateEmitterSpanFactory.class))
                                       .build();
    }

    @Override
    public void setBeanClassLoader(@NonNull ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }
}
