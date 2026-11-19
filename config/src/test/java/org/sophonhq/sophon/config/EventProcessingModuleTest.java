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

package org.sophonhq.sophon.config;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.sophonhq.sophon.common.ReflectionUtils;
import org.sophonhq.sophon.common.Registration;
import org.sophonhq.sophon.common.transaction.NoTransactionManager;
import org.sophonhq.sophon.common.transaction.Transaction;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.eventhandling.AbstractEventProcessor;
import org.sophonhq.sophon.eventhandling.AnnotationEventHandlerAdapter;
import org.sophonhq.sophon.eventhandling.ErrorContext;
import org.sophonhq.sophon.eventhandling.ErrorHandler;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.eventhandling.EventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.EventMessageHandler;
import org.sophonhq.sophon.eventhandling.EventProcessor;
import org.sophonhq.sophon.eventhandling.EventProcessorSpanFactory;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.eventhandling.ListenerInvocationErrorHandler;
import org.sophonhq.sophon.eventhandling.MultiEventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.PropagatingErrorHandler;
import org.sophonhq.sophon.eventhandling.SimpleEventBus;
import org.sophonhq.sophon.eventhandling.SimpleEventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.SubscribingEventProcessor;
import org.sophonhq.sophon.eventhandling.TrackedEventMessage;
import org.sophonhq.sophon.eventhandling.TrackingEventProcessor;
import org.sophonhq.sophon.eventhandling.TrackingEventProcessorConfiguration;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.eventhandling.async.FullConcurrencyPolicy;
import org.sophonhq.sophon.eventhandling.async.SequentialPolicy;
import org.sophonhq.sophon.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.pooled.PooledStreamingEventProcessor;
import org.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import org.sophonhq.sophon.eventhandling.tokenstore.inmemory.InMemoryTokenStore;
import org.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.sophonhq.sophon.lifecycle.LifecycleHandlerInvocationException;
import org.sophonhq.sophon.messaging.InterceptorChain;
import org.sophonhq.sophon.messaging.MessageHandlerInterceptor;
import org.sophonhq.sophon.messaging.StreamableMessageSource;
import org.sophonhq.sophon.messaging.SubscribableMessageSource;
import org.sophonhq.sophon.messaging.deadletter.Decisions;
import org.sophonhq.sophon.messaging.deadletter.EnqueuePolicy;
import org.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterProcessor;
import org.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueue;
import org.sophonhq.sophon.messaging.interceptors.CorrelationDataInterceptor;
import org.sophonhq.sophon.messaging.unitofwork.RollbackConfigurationType;
import org.sophonhq.sophon.messaging.unitofwork.UnitOfWork;
import org.sophonhq.sophon.tracing.TestSpanFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.common.ReflectionUtils.getFieldValue;
import static org.sophonhq.sophon.utils.AssertUtils.assertWithin;

/**
 * Test class validating the {@link EventProcessingModule}.
 *
 * @author Allard Buijze
 */
@ExtendWith(MockitoExtension.class)
class EventProcessingModuleTest {

    private EventStore eventStoreOne;
    private EventStore eventStoreTwo;

    private Configurer configurer;

    @BeforeEach
    void setUp() {
        configurer = DefaultConfigurer.defaultConfiguration();

        eventStoreOne = spy(EmbeddedEventStore.builder()
                                              .storageEngine(new InMemoryEventStorageEngine())
                                              .build());
        eventStoreTwo = spy(EmbeddedEventStore.builder()
                                              .storageEngine(new InMemoryEventStorageEngine())
                                              .build());

        eventStoreOne.publish(GenericEventMessage.asEventMessage("test1"));
        eventStoreTwo.publish(GenericEventMessage.asEventMessage("test2"));
    }

    @Test
    void assignmentRules() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();
        AnnotatedBean annotatedBean = new AnnotatedBean();
        AnnotatedBeanSubclass annotatedBeanSubclass = new AnnotatedBeanSubclass();

        configurer.eventProcessing()
                  .registerEventProcessorFactory((name, config, eventHandlerInvoker) -> {
                      StubEventProcessor processor =
                              new StubEventProcessor(name, eventHandlerInvoker);
                      processors.put(name, processor);
                      return processor;
                  })
                  .assignHandlerInstancesMatching("java.util.concurrent", "concurrent"::equals)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "") // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.util.concurrent
                  .registerEventHandler(c -> map) // --> java.util.concurrent
                  .registerEventHandler(c -> annotatedBean)
                  .registerEventHandler(c -> annotatedBeanSubclass);
        Configuration configuration = configurer.start();

        assertThat(configuration.eventProcessingConfiguration().eventProcessors()).hasSize(3);
        assertThat(processors.get("java.util.concurrent").getEventHandlers().contains("concurrent")).isTrue();
        assertThat(processors.get("java.util.concurrent").getEventHandlers().contains(map)).isTrue();
        assertThat(processors.get("java.lang").getEventHandlers().contains("")).isTrue();
        assertThat(processors.get("processingGroup").getEventHandlers().contains(annotatedBean)).isTrue();
        assertThat(processors.get("processingGroup").getEventHandlers().contains(annotatedBeanSubclass)).isTrue();
    }

    @Test
    void byTypeAssignmentRules() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();
        AnnotatedBean annotatedBean = new AnnotatedBean();
        AnnotatedBeanSubclass annotatedBeanSubclass = new AnnotatedBeanSubclass();

        configurer.eventProcessing()
                  .registerEventProcessorFactory((name, config, eventHandlerInvoker) -> {
                      StubEventProcessor processor =
                              new StubEventProcessor(name, eventHandlerInvoker);
                      processors.put(name, processor);
                      return processor;
                  })
                  .assignHandlerTypesMatching("special", ConcurrentHashMap.class::isAssignableFrom)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "") // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.lang
                  .registerEventHandler(c -> map) // --> java.util.concurrent
                  .registerEventHandler(c -> annotatedBean)
                  .registerEventHandler(c -> annotatedBeanSubclass);
        Configuration configuration = configurer.start();

        assertThat(configuration.eventProcessingConfiguration().eventProcessors()).hasSize(3);
        assertThat(processors.get("java.lang").getEventHandlers().contains("concurrent")).isTrue();
        assertThat(processors.get("special").getEventHandlers().contains(map)).isTrue();
        assertThat(processors.get("java.lang").getEventHandlers().contains("")).isTrue();
        assertThat(processors.get("processingGroup").getEventHandlers().contains(annotatedBean)).isTrue();
        assertThat(processors.get("processingGroup").getEventHandlers().contains(annotatedBeanSubclass)).isTrue();
    }

    @Test
    void processorsDefaultToSubscribingWhenUsingSimpleEventBus() {
        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .configureEventBus(c -> SimpleEventBus.builder().build())
                                                       .eventProcessing(ep -> ep.registerEventHandler(c -> new SubscribingEventHandler())
                                                                                .registerEventHandler(c -> new TrackingEventHandler()))
                                                       .start();

        EventProcessingConfiguration processingConfig = configuration.eventProcessingConfiguration();

        assertThat(processingConfig.eventProcessor("subscribing")).isPresent();
        assertThat(processingConfig.eventProcessor("subscribing")
                                   .map(p -> p instanceof SubscribingEventProcessor)
                                   .orElse(false)).isTrue();
        assertThat(processingConfig.eventProcessor("tracking")).isPresent();
        assertThat(processingConfig.eventProcessor("tracking")
                                   .map(p -> p instanceof SubscribingEventProcessor)
                                   .orElse(false)).isTrue();
    }

    @Test
    void assigningATrackingProcessorFailsWhenUsingSimpleEventBus() {
        Configurer configurer = DefaultConfigurer.defaultConfiguration()
                                                 .configureEventBus(c -> SimpleEventBus.builder().build())
                                                 .eventProcessing(ep -> ep.registerEventHandler(c -> new SubscribingEventHandler())
                                                                          .registerEventHandler(c -> new TrackingEventHandler())
                                                                          .registerTrackingEventProcessor("tracking"));

        assertThatThrownBy(configurer::start).isInstanceOf(LifecycleHandlerInvocationException.class);
    }

    @Test
    void assignmentRulesOverrideThoseWithLowerPriority() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();
        configurer.eventProcessing()
                  .registerEventProcessorFactory((name, config, handlers) -> {
                      StubEventProcessor processor = new StubEventProcessor(name, handlers);
                      processors.put(name, processor);
                      return processor;
                  })
                  .assignHandlerInstancesMatching("java.util.concurrent", "concurrent"::equals)
                  .assignHandlerInstancesMatching("java.util.concurrent2",
                                                  1,
                                                  "concurrent"::equals)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "") // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.util.concurrent2
                  .registerEventHandler(c -> map); // --> java.util.concurrent
        Configuration configuration = configurer.start();

        assertThat(configuration.eventProcessingConfiguration().eventProcessors()).hasSize(3);
        assertThat(processors.get("java.util.concurrent2").getEventHandlers().contains("concurrent")).isTrue();
        assertThat(processors.get("java.util.concurrent2").getHandlerInterceptors().iterator()
                             .next()).isInstanceOf(CorrelationDataInterceptor.class);
        assertThat(processors.get("java.util.concurrent").getEventHandlers().contains(map)).isTrue();
        assertThat(processors.get("java.util.concurrent").getHandlerInterceptors().iterator()
                             .next()).isInstanceOf(CorrelationDataInterceptor.class);
        assertThat(processors.get("java.lang").getEventHandlers().contains("")).isTrue();
        assertThat(processors.get("java.lang").getHandlerInterceptors().iterator()
                             .next()).isInstanceOf(CorrelationDataInterceptor.class);
    }

    @Test
    void defaultAssignToKeepsAnnotationScanning() {
        Map<String, StubEventProcessor> processors = new HashMap<>();
        AnnotatedBean annotatedBean = new AnnotatedBean();
        Object object = new Object();
        configurer.eventProcessing()
                  .registerEventProcessorFactory((name, config, handlers) -> {
                      StubEventProcessor processor = new StubEventProcessor(name, handlers);
                      processors.put(name, processor);
                      return processor;
                  })
                  .assignHandlerInstancesMatching("java.util.concurrent", "concurrent"::equals)
                  .byDefaultAssignTo("default")
                  .registerEventHandler(c -> object)        // --> default
                  .registerEventHandler(c -> "concurrent")  // --> java.util.concurrent
                  .registerEventHandler(c -> annotatedBean); // --> processingGroup
        configurer.start();

        assertThat(processors).hasSize(3);
        assertThat(processors.get("default").getEventHandlers().contains(object)).isTrue();
        assertThat(processors.get("java.util.concurrent").getEventHandlers().contains("concurrent")).isTrue();
        assertThat(processors.get("processingGroup").getEventHandlers().contains(annotatedBean)).isTrue();
    }

    @Test
    void typeAssignmentWithCustomDefault() {
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("myGroup", String.class::equals)
                  .byDefaultAssignHandlerTypesTo(
                          t -> Object.class.equals(t) ? "obj" : t.getSimpleName() + "CustomProcessor"
                  )
                  .registerSaga(Object.class)
                  .registerSaga(ConcurrentMap.class)
                  .registerSaga(String.class)
                  .registerEventHandler(c -> new HashMap<>());
        EventProcessingConfiguration configuration = configurer.start()
                                                               .eventProcessingConfiguration();

        assertThat(configuration.sagaProcessingGroup(String.class)).isEqualTo("myGroup");
        assertThat(configuration.sagaProcessingGroup(Object.class)).isEqualTo("obj");
        assertThat(configuration.sagaProcessingGroup(ConcurrentMap.class)).isEqualTo("ConcurrentMapCustomProcessor");

        assertThat(configuration.eventProcessors()).hasSize(4);
        assertThat(configuration.eventProcessor("myGroup")).isPresent();
        assertThat(configuration.eventProcessor("obj")).isPresent();
        assertThat(configuration.eventProcessor("java.util")).isPresent();
        assertThat(configuration.eventProcessor("ConcurrentMapCustomProcessor")).isPresent();
    }

    @Test
    void typeAssignment() {
        configurer.eventProcessing()
                  .assignHandlerTypesMatching("myGroup", c -> "java.lang".equals(c.getPackage().getName()))
                  .registerSaga(Object.class)
                  .registerSaga(ConcurrentMap.class)
                  .registerSaga(String.class)
                  .registerEventHandler(c -> new HashMap<>());
        EventProcessingConfiguration configuration = configurer.start()
                                                               .eventProcessingConfiguration();

        assertThat(configuration.sagaProcessingGroup(String.class)).isEqualTo("myGroup");
        assertThat(configuration.sagaProcessingGroup(Object.class)).isEqualTo("myGroup");
        assertThat(configuration.sagaProcessingGroup(ConcurrentMap.class)).isEqualTo("ConcurrentMapProcessor");

        assertThat(configuration.eventProcessors()).hasSize(3);
        assertThat(configuration.eventProcessor("myGroup")).isPresent();
        assertThat(configuration.eventProcessor("java.util")).isPresent();
        assertThat(configuration.eventProcessor("ConcurrentMapProcessor")).isPresent();
    }

    @Test
    void assignSequencingPolicy() throws NoSuchFieldException {
        Object mockHandler = new Object();
        Object specialHandler = new Object();
        SequentialPolicy sequentialPolicy = new SequentialPolicy();
        FullConcurrencyPolicy fullConcurrencyPolicy = new FullConcurrencyPolicy();
        configurer.eventProcessing()
                  .registerEventHandler(c -> mockHandler)
                  .registerEventHandler(c -> specialHandler)
                  .assignHandlerInstancesMatching("special", specialHandler::equals)
                  .byDefaultAssignTo("default")
                  .registerDefaultSequencingPolicy(c -> sequentialPolicy)
                  .registerSequencingPolicy("special", c -> fullConcurrencyPolicy);
        Configuration config = configurer.start();

        Optional<AbstractEventProcessor> defaultProcessorOptional =
                config.eventProcessingConfiguration().eventProcessor("default", AbstractEventProcessor.class);
        assertThat(defaultProcessorOptional).isPresent();
        AbstractEventProcessor defaultProcessor = defaultProcessorOptional.get();

        Optional<AbstractEventProcessor> specialProcessorOptional =
                config.eventProcessingConfiguration().eventProcessor("special", AbstractEventProcessor.class);
        assertThat(specialProcessorOptional).isPresent();
        AbstractEventProcessor specialProcessor = specialProcessorOptional.get();

        MultiEventHandlerInvoker defaultInvoker =
                getFieldValue(AbstractEventProcessor.class.getDeclaredField("eventHandlerInvoker"), defaultProcessor);
        MultiEventHandlerInvoker specialInvoker =
                getFieldValue(AbstractEventProcessor.class.getDeclaredField("eventHandlerInvoker"), specialProcessor);

        assertThat(((SimpleEventHandlerInvoker) defaultInvoker.delegates().get(0)).getSequencingPolicy())
                .isEqualTo(sequentialPolicy);
        assertThat(((SimpleEventHandlerInvoker) specialInvoker.delegates().get(0)).getSequencingPolicy())
                .isEqualTo(fullConcurrencyPolicy);
    }

    @Test
    void createSubscribingEventProcessorIfSubscribableMessageSourceDefinitionBuilderPresent(
            @Mock EventProcessingConfigurer.SubscribableMessageSourceDefinitionBuilder mockBuilder,
            @Mock SubscribableMessageSourceDefinition<EventMessage<?>> definition,
            @Mock SubscribableMessageSource source) {
        when(mockBuilder.build("pooled-streaming")).thenReturn(definition);
        when(mockBuilder.build("tracking")).thenReturn(definition);
        when(definition.create(any())).thenReturn(source);

        configurer.eventProcessing()
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerEventHandler(config -> new TrackingEventHandler())
                  .usingSubscribingEventProcessors(mockBuilder);
        Configuration config = configurer.start();

        Map<String, EventProcessor> processorMap = config.eventProcessingConfiguration().eventProcessors();

        processorMap.forEach((c, processor) -> assertThat(processor).isInstanceOf(SubscribingEventProcessor.class));
        assertThat(processorMap).hasSize(2);
        verify(mockBuilder, times(2)).build(anyString());
    }

    @Test
    void assignInterceptors() {
        StubInterceptor interceptor1 = new StubInterceptor();
        StubInterceptor interceptor2 = new StubInterceptor();
        configurer.eventProcessing()
                  .registerEventProcessor("default", (name, config, handlers) -> new StubEventProcessor(name, handlers))
                  .byDefaultAssignTo("default")
                  .assignHandlerInstancesMatching("concurrent", 1, "concurrent"::equals)
                  .registerEventHandler(c -> new Object()) // --> java.lang
                  .registerEventHandler(c -> "concurrent") // --> java.util.concurrent2
                  .registerHandlerInterceptor("default", c -> interceptor1)
                  .registerDefaultHandlerInterceptor((c, n) -> interceptor2);
        Configuration config = configurer.start();

        // CorrelationDataInterceptor is automatically configured
        Optional<EventProcessor> defaultProcessor = config.eventProcessingConfiguration()
                                                          .eventProcessor("default");
        assertThat(defaultProcessor).isPresent();
        assertThat(defaultProcessor.get().getHandlerInterceptors()).hasSize(3);
    }

    @Test
    void configureMonitor() throws Exception {
        MessageCollectingMonitor subscribingMonitor = new MessageCollectingMonitor();
        MessageCollectingMonitor trackingMonitor = new MessageCollectingMonitor(1);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.eventProcessing()
                  .registerMessageMonitor("subscribing", c -> subscribingMonitor)
                  .registerMessageMonitor("tracking", c -> trackingMonitor);
        Configuration config = configurer.start();

        try {
            config.eventBus().publish(new GenericEventMessage<Object>("test"));

            assertThat(subscribingMonitor.getMessages()).hasSize(1);
            assertThat(trackingMonitor.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(tokenStoreInvocation.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            config.shutdown();
        }
    }

    @Test
    void configureSpanFactory() {
        TestSpanFactory spanFactory = new TestSpanFactory();
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.configureSpanFactory(c -> spanFactory);
        Configuration config = configurer.start();

        try {
            GenericEventMessage<Object> message = new GenericEventMessage<>("test");
            config.eventBus().publish(message);

            spanFactory.verifySpanCompleted("EventProcessor.process", message);
            assertWithin(2, TimeUnit.SECONDS,
                         () -> spanFactory.verifySpanCompleted("StreamingEventProcessor.process", message));
        } finally {
            config.shutdown();
        }
    }

    @Test
    void configureDefaultListenerInvocationErrorHandler() throws Exception {
        GenericEventMessage<Boolean> errorThrowingEventMessage = new GenericEventMessage<>(true);

        int expectedListenerInvocationErrorHandlerCalls = 2;

        StubErrorHandler errorHandler = new StubErrorHandler(2);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.eventProcessing()
                  .registerDefaultListenerInvocationErrorHandler(config -> errorHandler);
        Configuration config = configurer.start();

        //noinspection Duplicates
        try {
            config.eventBus().publish(errorThrowingEventMessage);
            assertThat(tokenStoreInvocation.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(errorHandler.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(errorHandler.getErrorCounter()).isEqualTo(expectedListenerInvocationErrorHandlerCalls);
        } finally {
            config.shutdown();
        }
    }

    @Test
    void configureListenerInvocationErrorHandlerPerEventProcessor() throws Exception {
        GenericEventMessage<Boolean> errorThrowingEventMessage = new GenericEventMessage<>(true);

        int expectedErrorHandlerCalls = 1;

        StubErrorHandler subscribingErrorHandler = new StubErrorHandler(1);
        StubErrorHandler trackingErrorHandler = new StubErrorHandler(1);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.eventProcessing()
                  .registerListenerInvocationErrorHandler("subscribing", config -> subscribingErrorHandler)
                  .registerListenerInvocationErrorHandler("tracking", config -> trackingErrorHandler);
        Configuration config = configurer.start();

        //noinspection Duplicates
        try {
            config.eventBus().publish(errorThrowingEventMessage);

            assertThat(subscribingErrorHandler.getErrorCounter()).isEqualTo(expectedErrorHandlerCalls);

            assertThat(tokenStoreInvocation.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(trackingErrorHandler.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(trackingErrorHandler.getErrorCounter()).isEqualTo(expectedErrorHandlerCalls);
        } finally {
            config.shutdown();
        }
    }

    @Test
    void configureDefaultErrorHandler() throws Exception {
        GenericEventMessage<Integer> failingEventMessage = new GenericEventMessage<>(1000);

        int expectedErrorHandlerCalls = 2;

        StubErrorHandler errorHandler = new StubErrorHandler(2);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.eventProcessing()
                  .registerDefaultListenerInvocationErrorHandler(c -> PropagatingErrorHandler.instance())
                  .registerDefaultErrorHandler(config -> errorHandler);
        Configuration config = configurer.start();

        //noinspection Duplicates
        try {
            config.eventBus().publish(failingEventMessage);
            assertThat(tokenStoreInvocation.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(errorHandler.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(errorHandler.getErrorCounter()).isEqualTo(expectedErrorHandlerCalls);
        } finally {
            config.shutdown();
        }
    }

    @Test
    void trackingProcessorsUsesConfiguredDefaultStreamableMessageSource() {
        configurer.eventProcessing().configureDefaultStreamableMessageSource(c -> eventStoreOne);
        configurer.eventProcessing().usingTrackingEventProcessors();
        configurer.registerEventHandler(c -> new TrackingEventHandler());

        Configuration config = configurer.start();
        Optional<TrackingEventProcessor> processor = config.eventProcessingConfiguration()
                                                           .eventProcessor("tracking", TrackingEventProcessor.class);
        assertThat(processor).isPresent();
        assertThat(processor.get().getMessageSource()).isEqualTo(eventStoreOne);
    }

    @Test
    void trackingProcessorsUsesSpecificSource() {
        configurer.eventProcessing()
                  .configureDefaultStreamableMessageSource(c -> eventStoreOne)
                  .registerTrackingEventProcessor("tracking", c -> eventStoreTwo)
                  .registerEventHandler(c -> new TrackingEventHandler());

        Configuration config = configurer.start();
        Optional<TrackingEventProcessor> processor = config.eventProcessingConfiguration()
                                                           .eventProcessor("tracking", TrackingEventProcessor.class);
        assertThat(processor).isPresent();
        assertThat(processor.get().getMessageSource()).isEqualTo(eventStoreTwo);
    }

    @Test
    void subscribingProcessorsUsesConfiguredDefaultSubscribableMessageSource() {
        configurer.eventProcessing().configureDefaultSubscribableMessageSource(c -> eventStoreOne);
        configurer.eventProcessing().usingSubscribingEventProcessors();
        configurer.registerEventHandler(c -> new SubscribingEventHandler());

        Configuration config = configurer.start();
        Optional<SubscribingEventProcessor> processor = config.eventProcessingConfiguration()
                                                              .eventProcessor("subscribing");
        assertThat(processor).isPresent();
        assertThat(processor.get().getMessageSource()).isEqualTo(eventStoreOne);
    }

    @Test
    void subscribingProcessorsUsesSpecificSource() {
        configurer.eventProcessing()
                  .configureDefaultSubscribableMessageSource(c -> eventStoreOne)
                  .registerSubscribingEventProcessor("subscribing", c -> eventStoreTwo)
                  .registerEventHandler(c -> new SubscribingEventHandler());

        Configuration config = configurer.start();
        Optional<SubscribingEventProcessor> processor = config.eventProcessingConfiguration()
                                                              .eventProcessor("subscribing");
        assertThat(processor).isPresent();
        assertThat(processor.get().getMessageSource()).isEqualTo(eventStoreTwo);
    }


    @Test
    void configureErrorHandlerPerEventProcessor() throws Exception {
        GenericEventMessage<Integer> failingEventMessage = new GenericEventMessage<>(1000);

        int expectedErrorHandlerCalls = 1;

        StubErrorHandler subscribingErrorHandler = new StubErrorHandler(1);
        StubErrorHandler trackingErrorHandler = new StubErrorHandler(1);
        CountDownLatch tokenStoreInvocation = new CountDownLatch(1);

        buildComplexEventHandlingConfiguration(tokenStoreInvocation);
        configurer.eventProcessing()
                  .registerDefaultListenerInvocationErrorHandler(c -> PropagatingErrorHandler.instance())
                  .registerErrorHandler("subscribing", config -> subscribingErrorHandler)
                  .registerErrorHandler("tracking", config -> trackingErrorHandler);
        Configuration config = configurer.start();

        //noinspection Duplicates
        try {
            config.eventBus().publish(failingEventMessage);

            assertThat(subscribingErrorHandler.getErrorCounter()).isEqualTo(expectedErrorHandlerCalls);

            assertThat(tokenStoreInvocation.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(trackingErrorHandler.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(trackingErrorHandler.getErrorCounter()).isEqualTo(expectedErrorHandlerCalls);
        } finally {
            config.shutdown();
        }
    }

    @Test
    void packageOfObject() {
        String expectedPackageName = EventProcessingModule.class.getPackage().getName();
        assertThat(EventProcessingModule.packageOfObject(this)).isEqualTo(expectedPackageName);
    }

    @Test
    void defaultTrackingEventProcessingConfiguration() throws NoSuchFieldException {
        Object someHandler = new Object();
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> someHandler)
                  .registerEventHandler(config -> new TrackingEventHandler())
                  .registerTrackingEventProcessorConfiguration(config -> testTepConfig);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTrackingTep =
                config.eventProcessingConfiguration().eventProcessor("tracking", TrackingEventProcessor.class);
        assertThat(resultTrackingTep).isPresent();
        TrackingEventProcessor trackingTep = resultTrackingTep.get();
        int trackingTepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), trackingTep);
        assertThat(trackingTepSegmentsSize).isEqualTo(4);

        Optional<TrackingEventProcessor> resultDefaultTep =
                config.eventProcessingConfiguration().eventProcessor("default", TrackingEventProcessor.class);
        assertThat(resultDefaultTep).isPresent();
        TrackingEventProcessor defaultTep = resultDefaultTep.get();
        int defaultTepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), defaultTep);
        assertThat(defaultTepSegmentsSize).isEqualTo(4);
    }

    @Test
    void customTrackingEventProcessingConfiguration() throws NoSuchFieldException {
        Object someHandler = new Object();
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> someHandler)
                  .registerEventHandler(config -> new TrackingEventHandler())
                  .registerTrackingEventProcessorConfiguration("tracking", config -> testTepConfig);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTrackingTep =
                config.eventProcessingConfiguration().eventProcessor("tracking", TrackingEventProcessor.class);
        assertThat(resultTrackingTep).isPresent();
        TrackingEventProcessor trackingTep = resultTrackingTep.get();
        int trackingTepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), trackingTep);
        assertThat(trackingTepSegmentsSize).isEqualTo(4);

        Optional<TrackingEventProcessor> resultDefaultTep =
                config.eventProcessingConfiguration().eventProcessor("default", TrackingEventProcessor.class);
        assertThat(resultDefaultTep).isPresent();
        TrackingEventProcessor defaultTep = resultDefaultTep.get();
        int defaultTepSegmentsSize =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), defaultTep);
        assertThat(defaultTepSegmentsSize).isOne();
    }

    @Test
    void sagaTrackingProcessorConstructionUsesDefaultSagaProcessorConfigIfNoCustomizationIsPresent()
            throws NoSuchFieldException {
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerSaga(Object.class);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTep =
                config.eventProcessingConfiguration().eventProcessor("ObjectProcessor", TrackingEventProcessor.class);
        assertThat(resultTep).isPresent();
        TrackingEventProcessor tep = resultTep.get();
        int tepSegmentsSize = getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), tep);
        assertThat(tepSegmentsSize).isOne();

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> tepInitialTokenBuilder =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("initialTrackingTokenBuilder"), tep);
        tepInitialTokenBuilder.apply(eventStoreTwo);
        verify(eventStoreTwo, times(0)).createTailToken();
        // The default Saga Config starts the stream at the head
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaTrackingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomProcessor()
            throws NoSuchFieldException {
        configurer.eventProcessing()
                  .assignProcessingGroup(someGroup -> "custom-processor")
                  .registerTrackingEventProcessor("custom-processor", config -> eventStoreOne)
                  .registerSaga(CustomSaga.class);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTep = config.eventProcessingConfiguration().eventProcessor(
                "custom-processor", TrackingEventProcessor.class
        );
        assertThat(resultTep).isPresent();
        TrackingEventProcessor tep = resultTep.get();
        int tepSegmentsSize = getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), tep);
        assertThat(tepSegmentsSize).isOne();

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> tepInitialTokenBuilder =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("initialTrackingTokenBuilder"), tep);
        TrackingToken actualInitialToken = tepInitialTokenBuilder.apply(eventStoreTwo);
        // In absence of the default Saga Config, the stream starts at the tail
        assertThat(actualInitialToken.position().orElse(-1)).isZero();
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaTrackingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomTrackingProcessorBuilder()
            throws NoSuchFieldException {
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(3);
        configurer.eventProcessing()
                  .registerTrackingEventProcessor("ObjectProcessor", config -> eventStoreOne, config -> testTepConfig)
                  .registerSaga(Object.class);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTep =
                config.eventProcessingConfiguration().eventProcessor("ObjectProcessor", TrackingEventProcessor.class);
        assertThat(resultTep).isPresent();
        TrackingEventProcessor tep = resultTep.get();
        int tepSegmentsSize = getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), tep);
        assertThat(tepSegmentsSize).isEqualTo(3);

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> tepInitialTokenBuilder =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("initialTrackingTokenBuilder"), tep);
        TrackingToken initialToken = tepInitialTokenBuilder.apply(eventStoreTwo);
        // In absence of the default Saga Config, the stream starts at the tail
        assertThat(initialToken.position().orElse(-1)).isZero();
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaTrackingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomConfigInstance()
            throws NoSuchFieldException {
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerSaga(Object.class)
                  .registerTrackingEventProcessorConfiguration("ObjectProcessor", config -> testTepConfig);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTep =
                config.eventProcessingConfiguration().eventProcessor("ObjectProcessor", TrackingEventProcessor.class);
        assertThat(resultTep).isPresent();
        TrackingEventProcessor tep = resultTep.get();
        int tepSegmentsSize = getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), tep);
        assertThat(tepSegmentsSize).isEqualTo(4);

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> tepInitialTokenBuilder =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("initialTrackingTokenBuilder"), tep);
        TrackingToken actualInitialToken = tepInitialTokenBuilder.apply(eventStoreTwo);
        // In absence of the default Saga Config, the stream starts at the tail
        assertThat(actualInitialToken.position().orElse(-1)).isZero();
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaTrackingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomDefaultConfig()
            throws NoSuchFieldException {
        TrackingEventProcessorConfiguration testTepConfig =
                TrackingEventProcessorConfiguration.forParallelProcessing(4);
        configurer.eventProcessing()
                  .usingTrackingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerSaga(Object.class)
                  .registerTrackingEventProcessorConfiguration(config -> testTepConfig);
        Configuration config = configurer.start();

        Optional<TrackingEventProcessor> resultTep =
                config.eventProcessingConfiguration().eventProcessor("ObjectProcessor", TrackingEventProcessor.class);
        assertThat(resultTep).isPresent();
        TrackingEventProcessor tep = resultTep.get();
        int tepSegmentsSize = getFieldValue(TrackingEventProcessor.class.getDeclaredField("segmentsSize"), tep);
        assertThat(tepSegmentsSize).isEqualTo(4);

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> tepInitialTokenBuilder =
                getFieldValue(TrackingEventProcessor.class.getDeclaredField("initialTrackingTokenBuilder"), tep);
        TrackingToken actualInitialToken = tepInitialTokenBuilder.apply(eventStoreTwo);
        // In absence of the default Saga Config, the stream starts at the tail
        assertThat(actualInitialToken.position().orElse(-1)).isZero();
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaPooledStreamingProcessorConstructionUsesDefaultSagaProcessorConfigIfNoCustomizationIsPresent()
            throws NoSuchFieldException {
        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerSaga(Object.class);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("ObjectProcessor", PooledStreamingEventProcessor.class);
        assertThat(resultPsep).isPresent();

        PooledStreamingEventProcessor psep = resultPsep.get();
        long tokenClaimInterval =
                getFieldValue(PooledStreamingEventProcessor.class.getDeclaredField("tokenClaimInterval"), psep);
        assertThat(tokenClaimInterval).isEqualTo(5000L);

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken =
                getFieldValue(PooledStreamingEventProcessor.class.getDeclaredField("initialToken"), psep);
        initialToken.apply(eventStoreTwo);
        verify(eventStoreTwo, times(0)).createTailToken();
        // The default Saga Config starts the stream at the head
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaPooledStreamingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomProcessor()
            throws NoSuchFieldException {
        configurer.eventProcessing()
                  .assignProcessingGroup(someGroup -> "custom-processor")
                  .registerPooledStreamingEventProcessor("custom-processor", config -> eventStoreOne)
                  .registerSaga(CustomSaga.class);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("custom-processor", PooledStreamingEventProcessor.class);
        assertThat(resultPsep).isPresent();

        PooledStreamingEventProcessor psep = resultPsep.get();
        long tokenClaimInterval =
                getFieldValue(PooledStreamingEventProcessor.class.getDeclaredField("tokenClaimInterval"), psep);
        assertThat(tokenClaimInterval).isEqualTo(5000L);

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken =
                getFieldValue(PooledStreamingEventProcessor.class.getDeclaredField("initialToken"), psep);
        TrackingToken actualInitialToken = initialToken.apply(eventStoreTwo);
        // In absence of the default Saga Config, the stream starts at the tail
        assertThat(actualInitialToken.position().orElse(-1)).isZero();
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaPooledStreamingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomPooledStreamingProcessorBuilder()
            throws NoSuchFieldException {
        EventProcessingConfigurer.PooledStreamingProcessorConfiguration testPsepConfig =
                (config, builder) -> builder.maxClaimedSegments(4);
        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessor("ObjectProcessor", config -> eventStoreOne, testPsepConfig)
                  .registerSaga(Object.class);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("ObjectProcessor", PooledStreamingEventProcessor.class);
        assertThat(resultPsep).isPresent();

        PooledStreamingEventProcessor psep = resultPsep.get();

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken =
                getFieldValue(PooledStreamingEventProcessor.class.getDeclaredField("initialToken"), psep);
        TrackingToken actualInitialToken = initialToken.apply(eventStoreTwo);
        // In absence of the default Saga Config, the stream starts at the tail
        assertThat(actualInitialToken.position().orElse(-1)).isZero();
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaPooledStreamingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomConfigInstance()
            throws NoSuchFieldException {
        EventProcessingConfigurer.PooledStreamingProcessorConfiguration testPsepConfig =
                (config, builder) -> builder.maxClaimedSegments(4);
        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerSaga(Object.class)
                  .registerPooledStreamingEventProcessorConfiguration("ObjectProcessor", testPsepConfig);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("ObjectProcessor", PooledStreamingEventProcessor.class);
        assertThat(resultPsep).isPresent();

        PooledStreamingEventProcessor psep = resultPsep.get();

        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken =
                getFieldValue(PooledStreamingEventProcessor.class.getDeclaredField("initialToken"), psep);

        TrackingToken actualInitialToken = initialToken.apply(eventStoreTwo);
        // In absence of the default Saga Config, the stream starts at the tail
        assertThat(actualInitialToken.position().orElse(-1)).isZero();
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void sagaPooledStreamingProcessorConstructionDoesNotPickDefaultSagaProcessorConfigForCustomDefaultConfig()
            throws NoSuchFieldException {
        EventProcessingConfigurer.PooledStreamingProcessorConfiguration psepConfig =
                (config, builder) -> builder.maxClaimedSegments(4);
        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors(psepConfig)
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerSaga(Object.class);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("ObjectProcessor", PooledStreamingEventProcessor.class);
        assertThat(resultPsep).isPresent();

        PooledStreamingEventProcessor psep = resultPsep.get();
        Function<StreamableMessageSource<TrackedEventMessage<?>>, TrackingToken> initialToken =
                getFieldValue(PooledStreamingEventProcessor.class.getDeclaredField("initialToken"), psep);
        TrackingToken actualInitialToken = initialToken.apply(eventStoreTwo);
        // In absence of the default Saga Config, the stream starts at the tail
        assertThat(actualInitialToken.position().orElse(-1)).isZero();
        // to create the default replay token, we need to retrieve the head token
        verify(eventStoreTwo).createHeadToken();
    }

    @Test
    void defaultPooledStreamingEventProcessingConfiguration() {
        Object someHandler = new Object();
        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> someHandler)
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> resultPooledPsep =
                config.eventProcessingConfiguration()
                      .eventProcessor("pooled-streaming", PooledStreamingEventProcessor.class);
        assertThat(resultPooledPsep).isPresent();

        Optional<PooledStreamingEventProcessor> resultDefaultPsep =
                config.eventProcessingConfiguration().eventProcessor("default", PooledStreamingEventProcessor.class);
        assertThat(resultDefaultPsep).isPresent();
    }

    @Test
    void configurePooledStreamingEventProcessorFailsInAbsenceOfStreamableMessageSource() {
        String testName = "pooled-streaming";
        // This configurer does not contain an EventStore or other StreamableMessageSource.
        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessor(testName)
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        assertThatThrownBy(() -> configurer.start()).isInstanceOf(LifecycleHandlerInvocationException.class);
    }

    @Test
    void configurePooledStreamingEventProcessor() throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        TokenStore testTokenStore = new InMemoryTokenStore();
        TestSpanFactory testSpanFactory = new TestSpanFactory();

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .configureSpanFactory(c -> testSpanFactory)
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(testName)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerRollbackConfiguration(testName, config -> RollbackConfigurationType.ANY_THROWABLE)
                  .registerErrorHandler(testName, config -> PropagatingErrorHandler.INSTANCE)
                  .registerTokenStore(testName, config -> testTokenStore)
                  .registerTransactionManager(testName, config -> NoTransactionManager.INSTANCE);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        PooledStreamingEventProcessor result = optionalResult.get();
        assertThat(result.getName()).isEqualTo(testName);
        assertThatObject(getField(AbstractEventProcessor.class, "rollbackConfiguration", result))
                .isEqualTo(RollbackConfigurationType.ANY_THROWABLE);
        assertThatObject(getField(AbstractEventProcessor.class, "errorHandler", result))
                .isEqualTo(PropagatingErrorHandler.INSTANCE);
        assertThatObject(getField("tokenStore", result)).isEqualTo(testTokenStore);
        assertThatObject(getField("transactionManager", result)).isEqualTo(NoTransactionManager.INSTANCE);
        assertThatObject(getField(AbstractEventProcessor.class, "spanFactory", result))
                .isEqualTo(config.getComponent(EventProcessorSpanFactory.class));
    }

    @Test
    void configurePooledStreamingEventProcessorWithSource() throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        TokenStore testTokenStore = new InMemoryTokenStore();

        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessor(testName, config -> eventStoreOne)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerRollbackConfiguration(testName, config -> RollbackConfigurationType.ANY_THROWABLE)
                  .registerErrorHandler(testName, config -> PropagatingErrorHandler.INSTANCE)
                  .registerTokenStore(testName, config -> testTokenStore)
                  .registerTransactionManager(testName, config -> NoTransactionManager.INSTANCE);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        PooledStreamingEventProcessor result = optionalResult.get();
        assertThat(result.getName()).isEqualTo(testName);
        assertThatObject(getField(AbstractEventProcessor.class, "rollbackConfiguration", result))
                .isEqualTo(RollbackConfigurationType.ANY_THROWABLE);
        assertThatObject(getField(AbstractEventProcessor.class, "errorHandler", result))
                .isEqualTo(PropagatingErrorHandler.INSTANCE);
        assertThatObject(getField("messageSource", result)).isEqualTo(eventStoreOne);
        assertThatObject(getField("tokenStore", result)).isEqualTo(testTokenStore);
        assertThatObject(getField("transactionManager", result)).isEqualTo(NoTransactionManager.INSTANCE);
    }

    @Test
    void configurePooledStreamingEventProcessorWithConfiguration() throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;

        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessor(
                          testName,
                          config -> eventStoreOne,
                          (config, builder) -> builder.maxClaimedSegments(testCapacity)
                  )
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        PooledStreamingEventProcessor result = optionalResult.get();
        assertThat(result.maxCapacity()).isEqualTo(testCapacity);
        assertThatObject(getField("messageSource", result)).isEqualTo(eventStoreOne);
    }

    @Test
    void registerPooledStreamingEventProcessorConfigurationIsUsedDuringAllPsepConstructions()
            throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        Object testHandler = new Object();

        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .registerPooledStreamingEventProcessorConfiguration(
                          (config, builder) -> builder.maxClaimedSegments(testCapacity)
                  )
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> testHandler);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        PooledStreamingEventProcessor result = optionalResult.get();
        assertThat(result.maxCapacity()).isEqualTo(testCapacity);
        assertThatObject(getField("messageSource", result)).isEqualTo(eventStoreOne);

        optionalResult = config.eventProcessingConfiguration()
                               .eventProcessor("default", PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        result = optionalResult.get();
        assertThat(result.maxCapacity()).isEqualTo(testCapacity);
        assertThatObject(getField("messageSource", result)).isEqualTo(eventStoreOne);
    }

    @Test
    void usingPooledStreamingEventProcessorWithConfigurationIsUsedDuringAllPsepConstructions()
            throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        Object testHandler = new Object();

        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors((config, builder) -> builder.maxClaimedSegments(testCapacity))
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> testHandler);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        PooledStreamingEventProcessor result = optionalResult.get();
        assertThat(result.maxCapacity()).isEqualTo(testCapacity);
        assertThatObject(getField("messageSource", result)).isEqualTo(eventStoreOne);

        optionalResult = config.eventProcessingConfiguration()
                               .eventProcessor("default", PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        result = optionalResult.get();
        assertThat(result.maxCapacity()).isEqualTo(testCapacity);
        assertThatObject(getField("messageSource", result)).isEqualTo(eventStoreOne);
    }

    @Test
    void registerPooledStreamingEventProcessorConfigurationForNameIsUsedDuringSpecificPsepConstruction()
            throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        Object testHandler = new Object();

        configurer.eventProcessing()
                  .usingPooledStreamingEventProcessors()
                  .registerPooledStreamingEventProcessorConfiguration(
                          "pooled-streaming", (config, builder) -> builder.maxClaimedSegments(testCapacity)
                  )
                  .configureDefaultStreamableMessageSource(config -> eventStoreOne)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .byDefaultAssignTo("default")
                  .registerEventHandler(config -> testHandler);
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        PooledStreamingEventProcessor result = optionalResult.get();
        assertThat(result.maxCapacity()).isEqualTo(testCapacity);
        assertThatObject(getField("messageSource", result)).isEqualTo(eventStoreOne);

        optionalResult = config.eventProcessingConfiguration()
                               .eventProcessor("default", PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        result = optionalResult.get();
        assertThat(result.maxCapacity()).isEqualTo(Short.MAX_VALUE);
        assertThatObject(getField("messageSource", result)).isEqualTo(eventStoreOne);
    }

    @Test
    void registerPooledStreamingEventProcessorWithConfigurationOverridesDefaultPsepConfiguration()
            throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        int incorrectCapacity = 1745;

        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessorConfiguration(
                          (config, builder) -> builder.maxClaimedSegments(incorrectCapacity)
                  )
                  .registerPooledStreamingEventProcessor(
                          testName,
                          config -> eventStoreOne,
                          (config, builder) -> builder.maxClaimedSegments(testCapacity)
                  )
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        Configuration config = configurer.start();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        PooledStreamingEventProcessor result = optionalResult.get();
        assertThat(result.maxCapacity()).isEqualTo(testCapacity);
        assertThatObject(getField("messageSource", result)).isEqualTo(eventStoreOne);
    }

    @Test
    void registerPooledStreamingEventProcessorWithConfigurationOverridesCustomPsepConfiguration()
            throws NoSuchFieldException, IllegalAccessException {
        String testName = "pooled-streaming";
        int testCapacity = 24;
        int wrongCapacity = 42;
        int incorrectCapacity = 1729;

        configurer.eventProcessing()
                  .registerPooledStreamingEventProcessorConfiguration(
                          (config, builder) -> builder.batchSize(100).maxClaimedSegments(wrongCapacity)
                  )
                  .registerPooledStreamingEventProcessorConfiguration(
                          "pooled-streaming", (config, builder) -> builder.maxClaimedSegments(incorrectCapacity)
                  )
                  .registerPooledStreamingEventProcessor(
                          testName,
                          config -> eventStoreOne,
                          (config, builder) -> builder.maxClaimedSegments(testCapacity)
                  )
                  .registerEventHandler(config -> new PooledStreamingEventHandler());
        Configuration config = configurer.buildConfiguration();

        Optional<PooledStreamingEventProcessor> optionalResult =
                config.eventProcessingConfiguration()
                      .eventProcessor(testName, PooledStreamingEventProcessor.class);

        assertThat(optionalResult).isPresent();
        PooledStreamingEventProcessor result = optionalResult.get();
        assertThat(result.maxCapacity()).isEqualTo(testCapacity);
        assertThatObject(getField("messageSource", result)).isEqualTo(eventStoreOne);
        assertThat((int) getField("batchSize", result)).isEqualTo(100);
    }

    @Test
    void defaultTransactionManagerIsUsedUponEventProcessorConstruction() throws InterruptedException {
        String testName = "pooled-streaming";
        GenericEventMessage<Integer> testEvent = new GenericEventMessage<>(1000);

        CountDownLatch transactionCommitted = new CountDownLatch(1);
        TransactionManager defaultTransactionManager = new StubTransactionManager(transactionCommitted);

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(testName)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDefaultTransactionManager(c -> defaultTransactionManager);
        Configuration config = configurer.start();

        try {
            config.eventBus().publish(testEvent);
            assertThat(transactionCommitted.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            config.shutdown();
        }
    }

    @Test
    void defaultTransactionManagerIsOverriddenByProcessorSpecificInstance() throws InterruptedException {
        String testName = "pooled-streaming";
        GenericEventMessage<Integer> testEvent = new GenericEventMessage<>(1000);

        TransactionManager defaultTransactionManager = spy(TransactionManager.class);
        CountDownLatch transactionCommitted = new CountDownLatch(1);
        TransactionManager processorSpecificTransactionManager = new StubTransactionManager(transactionCommitted);

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(testName)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDefaultTransactionManager(c -> defaultTransactionManager)
                  .registerTransactionManager(testName, c -> processorSpecificTransactionManager);
        Configuration config = configurer.start();

        try {
            config.eventBus().publish(testEvent);
            assertThat(transactionCommitted.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            config.shutdown();
        }
        verifyNoInteractions(defaultTransactionManager);
    }

    @Test
    void registerDeadLetterQueueConstructsDeadLetteringEventHandlerInvoker(
            @Mock SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE);
        Configuration config = configurer.start();

        Optional<EnqueuePolicy<EventMessage<?>>> optionalPolicy = config.eventProcessingConfiguration()
                                                                        .deadLetterPolicy(processingGroup);
        assertThat(optionalPolicy).isPresent();
        EnqueuePolicy<EventMessage<?>> expectedPolicy = optionalPolicy.get();

        Optional<SequencedDeadLetterQueue<EventMessage<?>>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertThat(configuredDlq).isPresent();
        assertThat(configuredDlq.get()).isEqualTo(deadLetterQueue);

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertThat(optionalProcessor).isPresent();
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker =
                getField(AbstractEventProcessor.class, "eventHandlerInvoker", resultProcessor);
        assertThat(resultInvoker.getClass()).isEqualTo(MultiEventHandlerInvoker.class);

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertThat(delegates).isNotEmpty();
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.get(0));

        assertThatObject(getField("queue", resultDeadLetteringInvoker)).isEqualTo(deadLetterQueue);
        assertThatObject(getField("enqueuePolicy", resultDeadLetteringInvoker)).isEqualTo(expectedPolicy);
        assertThatObject(getField("transactionManager", resultDeadLetteringInvoker))
                .isEqualTo(NoTransactionManager.INSTANCE);
    }

    @Test
    void registerDefaultDeadLetterPolicyIsUsed(@Mock SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue)
            throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";
        EnqueuePolicy<EventMessage<?>> expectedPolicy = (letter, cause) -> Decisions.ignore();

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerDefaultDeadLetterPolicy(c -> expectedPolicy)
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE);
        Configuration config = configurer.start();

        Optional<EnqueuePolicy<EventMessage<?>>> optionalPolicy = config.eventProcessingConfiguration()
                                                                        .deadLetterPolicy(processingGroup);
        assertThat(optionalPolicy).isPresent();
        EnqueuePolicy<EventMessage<?>> resultPolicy = optionalPolicy.get();
        assertThat(resultPolicy).isEqualTo(expectedPolicy);

        Optional<SequencedDeadLetterQueue<EventMessage<?>>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertThat(configuredDlq).isPresent();
        assertThat(configuredDlq.get()).isEqualTo(deadLetterQueue);

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertThat(optionalProcessor).isPresent();
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker =
                getField(AbstractEventProcessor.class, "eventHandlerInvoker", resultProcessor);
        assertThat(resultInvoker.getClass()).isEqualTo(MultiEventHandlerInvoker.class);

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertThat(delegates).isNotEmpty();
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.get(0));

        assertThatObject(getField("enqueuePolicy", resultDeadLetteringInvoker)).isEqualTo(expectedPolicy);
    }

    @Test
    void registerDeadLetterPolicyIsUsed(@Mock SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue)
            throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";
        EnqueuePolicy<EventMessage<?>> expectedPolicy = (letter, cause) -> Decisions.ignore();
        EnqueuePolicy<EventMessage<?>> unexpectedPolicy = (letter, cause) -> Decisions.evict();

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerDeadLetterPolicy(processingGroup, c -> expectedPolicy)
                  .registerDeadLetterPolicy("unused-processing-group", c -> unexpectedPolicy)
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE);
        Configuration config = configurer.start();

        Optional<EnqueuePolicy<EventMessage<?>>> optionalPolicy = config.eventProcessingConfiguration()
                                                                        .deadLetterPolicy(processingGroup);
        assertThat(optionalPolicy).isPresent();
        EnqueuePolicy<EventMessage<?>> resultPolicy = optionalPolicy.get();
        assertThat(resultPolicy).isEqualTo(expectedPolicy);
        assertThat(resultPolicy).isNotEqualTo(unexpectedPolicy);

        Optional<SequencedDeadLetterQueue<EventMessage<?>>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertThat(configuredDlq).isPresent();
        assertThat(configuredDlq.get()).isEqualTo(deadLetterQueue);

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertThat(optionalProcessor).isPresent();
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker =
                getField(AbstractEventProcessor.class, "eventHandlerInvoker", resultProcessor);
        assertThat(resultInvoker.getClass()).isEqualTo(MultiEventHandlerInvoker.class);

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertThat(delegates).isNotEmpty();
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.get(0));

        assertThatObject(getField("enqueuePolicy", resultDeadLetteringInvoker)).isEqualTo(expectedPolicy);
        assertThatObject(getField("enqueuePolicy", resultDeadLetteringInvoker)).isNotEqualTo(unexpectedPolicy);
    }

    @Test
    void registeredDeadLetteringEventHandlerInvokerConfigurationIsUsed(
            @Mock SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerDeadLetteringEventHandlerInvokerConfiguration(
                          processingGroup, (config, builder) -> builder.allowReset(true)
                  )
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE);
        Configuration config = configurer.start();

        Optional<SequencedDeadLetterQueue<EventMessage<?>>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertThat(configuredDlq).isPresent();
        assertThat(configuredDlq.get()).isEqualTo(deadLetterQueue);

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertThat(optionalProcessor).isPresent();
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker =
                getField(AbstractEventProcessor.class, "eventHandlerInvoker", resultProcessor);
        assertThat(resultInvoker.getClass()).isEqualTo(MultiEventHandlerInvoker.class);

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertThat(delegates).isNotEmpty();
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.get(0));

        assertThat((Boolean) getField("allowReset", resultDeadLetteringInvoker)).isTrue();
    }

    @Test
    void sequencedDeadLetterProcessorReturnsForProcessingGroupWithDlq(
            @Mock SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue
    ) {
        String processingGroup = "pooled-streaming";
        String otherProcessingGroup = "tracking";

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerPooledStreamingEventProcessor(otherProcessingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerEventHandler(config -> new TrackingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE);

        Configuration config = configurer.start();
        EventProcessingConfiguration eventProcessingConfig = config.eventProcessingConfiguration();

        Optional<SequencedDeadLetterQueue<EventMessage<?>>> configuredDlq =
                eventProcessingConfig.deadLetterQueue(processingGroup);
        assertThat(configuredDlq).isPresent();
        assertThat(configuredDlq.get()).isEqualTo(deadLetterQueue);

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                eventProcessingConfig.eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertThat(optionalProcessor).isPresent();

        Optional<SequencedDeadLetterProcessor<EventMessage<?>>> optionalDeadLetterProcessor =
                eventProcessingConfig.sequencedDeadLetterProcessor(processingGroup);
        assertThat(optionalDeadLetterProcessor).isPresent();
        assertThat(eventProcessingConfig.sequencedDeadLetterProcessor(otherProcessingGroup)).isNotPresent();
        assertThat(eventProcessingConfig.sequencedDeadLetterProcessor("non-existing-group")).isNotPresent();
    }

    @Test
    void interceptorsOnDeadLetterProcessorShouldBePresent(
            @Mock SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";
        StubInterceptor interceptor1 = new StubInterceptor();
        StubInterceptor interceptor2 = new StubInterceptor();

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> deadLetterQueue)
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE)
                  .registerHandlerInterceptor(processingGroup, c -> interceptor1)
                  .registerDefaultHandlerInterceptor((c, n) -> interceptor2);

        Configuration config = configurer.start();
        EventProcessingConfiguration eventProcessingConfig = config.eventProcessingConfiguration();

        Optional<SequencedDeadLetterProcessor<EventMessage<?>>> optionalDeadLetterProcessor =
                eventProcessingConfig.sequencedDeadLetterProcessor(processingGroup);
        assertThat(optionalDeadLetterProcessor).isPresent();
        List<MessageHandlerInterceptor<?>> interceptors = getField("interceptors", optionalDeadLetterProcessor.get());
        assertThat(interceptors).hasSize(3);
    }

    @Test
    void registerDeadLetterQueueProviderConstructsDeadLetteringEventHandlerInvoker(
            @Mock SequencedDeadLetterQueue<EventMessage<?>> deadLetterQueue
    ) throws NoSuchFieldException, IllegalAccessException {
        String processingGroup = "pooled-streaming";

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueueProvider(p -> c -> deadLetterQueue)
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE);
        Configuration config = configurer.start();

        Optional<EnqueuePolicy<EventMessage<?>>> optionalPolicy = config.eventProcessingConfiguration()
                                                                        .deadLetterPolicy(processingGroup);
        assertThat(optionalPolicy).isPresent();
        EnqueuePolicy<EventMessage<?>> expectedPolicy = optionalPolicy.get();

        Optional<SequencedDeadLetterQueue<EventMessage<?>>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertThat(configuredDlq).isPresent();
        assertThat(configuredDlq.get()).isEqualTo(deadLetterQueue);

        Optional<PooledStreamingEventProcessor> optionalProcessor =
                config.eventProcessingConfiguration()
                      .eventProcessor(processingGroup, PooledStreamingEventProcessor.class);
        assertThat(optionalProcessor).isPresent();
        PooledStreamingEventProcessor resultProcessor = optionalProcessor.get();

        EventHandlerInvoker resultInvoker =
                getField(AbstractEventProcessor.class, "eventHandlerInvoker", resultProcessor);
        assertThat(resultInvoker.getClass()).isEqualTo(MultiEventHandlerInvoker.class);

        MultiEventHandlerInvoker resultMultiInvoker = ((MultiEventHandlerInvoker) resultInvoker);
        List<EventHandlerInvoker> delegates = getField("delegates", resultMultiInvoker);
        assertThat(delegates).isNotEmpty();
        DeadLetteringEventHandlerInvoker resultDeadLetteringInvoker =
                ((DeadLetteringEventHandlerInvoker) delegates.get(0));

        assertThatObject(getField("queue", resultDeadLetteringInvoker)).isEqualTo(deadLetterQueue);
        assertThatObject(getField("enqueuePolicy", resultDeadLetteringInvoker)).isEqualTo(expectedPolicy);
        assertThatObject(getField("transactionManager", resultDeadLetteringInvoker))
                .isEqualTo(NoTransactionManager.INSTANCE);
    }

    @Test
    void whenADeadLetterHasBeenRegisteredForASpecificGroupItWillBeUsedInsteadOfTheGenericOne(
            @Mock SequencedDeadLetterQueue<EventMessage<?>> specificDeadLetterQueue,
            @Mock SequencedDeadLetterQueue<EventMessage<?>> genericDeadLetterQueue
    ) {
        String processingGroup = "pooled-streaming";

        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .eventProcessing()
                  .registerPooledStreamingEventProcessor(processingGroup)
                  .registerEventHandler(config -> new PooledStreamingEventHandler())
                  .registerDeadLetterQueue(processingGroup, c -> specificDeadLetterQueue)
                  .registerDeadLetterQueueProvider(p -> c -> genericDeadLetterQueue)
                  .registerTransactionManager(processingGroup, c -> NoTransactionManager.INSTANCE);
        Configuration config = configurer.start();

        Optional<SequencedDeadLetterQueue<EventMessage<?>>> configuredDlq =
                config.eventProcessingConfiguration().deadLetterQueue(processingGroup);
        assertThat(configuredDlq).isPresent();
        assertThat(configuredDlq.get()).isEqualTo(specificDeadLetterQueue);
    }

    private <O, R> R getField(String fieldName, O object) throws NoSuchFieldException, IllegalAccessException {
        return getField(object.getClass(), fieldName, object);
    }

    private <C, O, R> R getField(Class<C> clazz,
                                 String fieldName,
                                 O object) throws NoSuchFieldException, IllegalAccessException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        //noinspection unchecked
        return (R) field.get(object);
    }

    private void buildComplexEventHandlingConfiguration(CountDownLatch tokenStoreInvocation) {
        // Use InMemoryEventStorageEngine so tracking processors don't miss events
        configurer.configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine());
        configurer.eventProcessing()
                  .registerSubscribingEventProcessor("subscribing")
                  .registerTrackingEventProcessor("tracking")
                  .assignHandlerInstancesMatching(
                          "subscribing", eh -> eh.getClass().isAssignableFrom(SubscribingEventHandler.class)
                  )
                  .assignHandlerInstancesMatching(
                          "tracking", eh -> eh.getClass().isAssignableFrom(TrackingEventHandler.class)
                  )
                  .registerEventHandler(c -> new SubscribingEventHandler())
                  .registerEventHandler(c -> new TrackingEventHandler())
                  .registerTokenStore("tracking", c -> new InMemoryTokenStore() {
                      @Override
                      public int[] fetchSegments(@NonNull String processorName) {
                          tokenStoreInvocation.countDown();
                          return super.fetchSegments(processorName);
                      }
                  });
    }

    @SuppressWarnings("WeakerAccess")
    private static class StubEventProcessor implements EventProcessor {

        private final String name;
        private final EventHandlerInvoker eventHandlerInvoker;
        private final List<MessageHandlerInterceptor<? super EventMessage<?>>> interceptors = new ArrayList<>();

        public StubEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker) {
            this.name = name;
            this.eventHandlerInvoker = eventHandlerInvoker;
        }

        @Override
        public String getName() {
            return name;
        }

        public EventHandlerInvoker getEventHandlerInvoker() {
            return eventHandlerInvoker;
        }

        public List<?> getEventHandlers() {
            List<EventHandlerInvoker> invokers = ((MultiEventHandlerInvoker) getEventHandlerInvoker()).delegates();
            return ((SimpleEventHandlerInvoker) invokers.get(0))
                    .eventHandlers()
                    .stream()
                    .map(eventHandlingComponent -> {
                        try {
                            Field handlerField =
                                    AnnotationEventHandlerAdapter.class.getDeclaredField("annotatedEventListener");
                            return ReflectionUtils.getFieldValue(handlerField, eventHandlingComponent);
                        } catch (NoSuchFieldException e) {
                            return null;
                        }
                    })
                    .collect(Collectors.toList());
        }

        @Override
        public Registration registerHandlerInterceptor(
                @NonNull MessageHandlerInterceptor<? super EventMessage<?>> interceptor) {
            interceptors.add(interceptor);
            return () -> interceptors.remove(interceptor);
        }

        @Override
        public List<MessageHandlerInterceptor<? super EventMessage<?>>> getHandlerInterceptors() {
            return interceptors;
        }

        @Override
        public void start() {
            // noop
        }

        @Override
        public void shutDown() {
            // noop
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }
    }

    @SuppressWarnings("WeakerAccess")
    @ProcessingGroup("processingGroup")
    public static class AnnotatedBean {

    }

    @SuppressWarnings("WeakerAccess")
    public static class AnnotatedBeanSubclass extends AnnotatedBean {

    }

    private static class StubInterceptor implements MessageHandlerInterceptor<EventMessage<?>> {

        @Override
        public Object handle(@NonNull UnitOfWork<? extends EventMessage<?>> unitOfWork,
                             @NonNull InterceptorChain interceptorChain)
                throws Exception {
            return interceptorChain.proceed();
        }
    }

    @SuppressWarnings("unused")
    @ProcessingGroup("subscribing")
    private static class SubscribingEventHandler {

        @EventHandler
        public void handle(Integer event, UnitOfWork<?> unitOfWork) {
            throw new IllegalStateException();
        }

        @EventHandler
        public void handle(Boolean event) {
            throw new IllegalStateException();
        }

        @EventHandler
        public void handle(String event) {
            // Handler for String events used by configureSpanFactory test
        }
    }

    @SuppressWarnings("unused")
    @ProcessingGroup("tracking")
    private static class TrackingEventHandler {

        @EventHandler
        public void handle(String event) {

        }

        @EventHandler
        public void handle(Integer event, UnitOfWork<?> unitOfWork) {
            throw new IllegalStateException();
        }

        @EventHandler
        public void handle(Boolean event) {
            throw new IllegalStateException();
        }
    }

    @SuppressWarnings("unused")
    @ProcessingGroup("pooled-streaming")
    private static class PooledStreamingEventHandler {

        @EventHandler
        public void handle(String event) {

        }
    }

    private static class StubErrorHandler implements ErrorHandler, ListenerInvocationErrorHandler {

        private final CountDownLatch latch;
        private final AtomicInteger errorCounter = new AtomicInteger();

        private StubErrorHandler(int count) {
            this.latch = new CountDownLatch(count);
        }

        @Override
        public void handleError(@NonNull ErrorContext errorContext) {
            errorCounter.incrementAndGet();
            latch.countDown();
        }

        @Override
        public void onError(@NonNull Exception exception, @NonNull EventMessage<?> event,
                            @NonNull EventMessageHandler eventHandler) {
            errorCounter.incrementAndGet();
            latch.countDown();
        }

        @SuppressWarnings("WeakerAccess")
        public int getErrorCounter() {
            return errorCounter.get();
        }

        public boolean await(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return latch.await(timeout, timeUnit);
        }
    }

    @ProcessingGroup("my-saga-processing-group")
    private static class CustomSaga {

    }

    private static class StubTransactionManager implements TransactionManager {

        private final CountDownLatch transactionCommitted;

        private StubTransactionManager(CountDownLatch transactionCommitted) {
            this.transactionCommitted = transactionCommitted;
        }

        @Override
        public Transaction startTransaction() {
            return new Transaction() {
                @Override
                public void commit() {
                    transactionCommitted.countDown();
                }

                @Override
                public void rollback() {

                }
            };
        }
    }
}
