/*
 * Copyright (c) 2010-2023. Axon Framework
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

import io.github.sophonhq.sophon.common.ReflectionUtils;
import io.github.sophonhq.sophon.config.EventProcessingModule;
import io.github.sophonhq.sophon.config.ProcessingGroup;
import io.github.sophonhq.sophon.eventhandling.AbstractEventProcessor;
import io.github.sophonhq.sophon.eventhandling.EventHandler;
import io.github.sophonhq.sophon.eventhandling.EventHandlerInvoker;
import io.github.sophonhq.sophon.eventhandling.EventMessage;
import io.github.sophonhq.sophon.eventhandling.EventProcessor;
import io.github.sophonhq.sophon.eventhandling.MultiEventHandlerInvoker;
import io.github.sophonhq.sophon.eventhandling.SimpleEventHandlerInvoker;
import io.github.sophonhq.sophon.eventhandling.TrackingEventProcessor;
import io.github.sophonhq.sophon.eventhandling.async.FullConcurrencyPolicy;
import io.github.sophonhq.sophon.eventhandling.async.SequencingPolicy;
import io.github.sophonhq.sophon.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import io.github.sophonhq.sophon.eventhandling.pooled.PooledStreamingEventProcessor;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.sophonhq.sophon.common.ReflectionUtils.ensureAccessible;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating configuration through a properties file, adjusting the {@link EventProcessorProperties}.
 *
 * @author Allard Buijze
 */
class EventProcessorConfigurationTest {

    @Test
    void processorConfigurationWithCustomPolicy() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withPropertyValues(
                        "sophon.eventhandling.processors.first.mode=tracking",
                        "sophon.eventhandling.processors.first.sequencingPolicy=customPolicy"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(EventProcessingModule.class);
                    EventProcessingModule eventProcessingConfig = context.getBean(EventProcessingModule.class);

                    Map<String, EventProcessor> processors = eventProcessingConfig.eventProcessors();
                    assertEquals(3, processors.size());

                    EventProcessor eventProcessor = processors.get("first");
                    assertNotNull(eventProcessor);
                    assertEquals(TrackingEventProcessor.class, eventProcessor.getClass());

                    long tokenClaimInterval = ReflectionUtils.getFieldValue(
                            TrackingEventProcessor.class.getDeclaredField("tokenClaimInterval"), eventProcessor
                    );
                    assertEquals(5000L, tokenClaimInterval, "Must be 5000 ms by default");

                    assertThat(context).hasSingleBean(SequencingPolicy.class);
                    //noinspection unchecked
                    SequencingPolicy<? super EventMessage<?>> expectedPolicy = context.getBean(SequencingPolicy.class);

                    MultiEventHandlerInvoker invoker = (MultiEventHandlerInvoker) ensureAccessible(
                            AbstractEventProcessor.class.getDeclaredMethod("eventHandlerInvoker")
                    ).invoke(eventProcessor);
                    SimpleEventHandlerInvoker simpleEventHandlerInvoker =
                            (SimpleEventHandlerInvoker) invoker.delegates().get(0);
                    SequencingPolicy<? super EventMessage<?>> policy = ReflectionUtils.getFieldValue(
                            SimpleEventHandlerInvoker.class.getDeclaredField("sequencingPolicy"),
                            simpleEventHandlerInvoker
                    );
                    assertEquals(expectedPolicy, policy);
                });
    }

    @Test
    void tokenClaimIntervalCanBeSetViaSpringConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withPropertyValues(
                        "sophon.eventhandling.processors.non_default_token_claim_interval.mode=tracking",
                        "sophon.eventhandling.processors.non_default_token_claim_interval.tokenClaimInterval=1000",
                        "sophon.eventhandling.processors.non_default_token_claim_interval.tokenClaimIntervalTimeUnit=MINUTES"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(EventProcessingModule.class);
                    EventProcessingModule eventProcessingConfig = context.getBean(EventProcessingModule.class);

                    Map<String, EventProcessor> processors = eventProcessingConfig.eventProcessors();
                    assertEquals(3, processors.size());

                    EventProcessor eventProcessor = processors.get("non_default_token_claim_interval");
                    assertNotNull(eventProcessor);
                    assertEquals(TrackingEventProcessor.class, eventProcessor.getClass());
                    long tokenClaimInterval = ReflectionUtils.getFieldValue(
                            TrackingEventProcessor.class.getDeclaredField("tokenClaimInterval"), eventProcessor
                    );

                    assertEquals(60000000L,
                                 tokenClaimInterval,
                                 "It must be possible to override token claim interval via Spring Configuration");
                });
    }

    @Test
    void configurePooledStreamingEventProcessor() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withPropertyValues(
                        "sophon.eventhandling.processors.second.mode=pooled",
                        "sophon.eventhandling.processors.second.initialSegmentCount=12",
                        "sophon.eventhandling.processors.second.tokenClaimInterval=1000",
                        "sophon.eventhandling.processors.second.tokenClaimIntervalTimeUnit=MINUTES",
                        "sophon.eventhandling.processors.second.batchSize=1024"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(EventProcessingModule.class);
                    EventProcessingModule eventProcessingConfig = context.getBean(EventProcessingModule.class);

                    Map<String, EventProcessor> processors = eventProcessingConfig.eventProcessors();
                    assertEquals(3, processors.size());

                    EventProcessor defaultProcessor = processors.get("first");
                    assertNotNull(defaultProcessor);
                    assertEquals(TrackingEventProcessor.class, defaultProcessor.getClass());

                    EventProcessor pooledProcessor = processors.get("second");
                    assertNotNull(pooledProcessor);
                    assertEquals(PooledStreamingEventProcessor.class, pooledProcessor.getClass());

                    long resultTokenClaimInterval = ReflectionUtils.getFieldValue(
                            PooledStreamingEventProcessor.class.getDeclaredField("tokenClaimInterval"), pooledProcessor
                    );
                    assertEquals(60000000L, resultTokenClaimInterval);

                    int resultBatchSize = ReflectionUtils.getFieldValue(
                            PooledStreamingEventProcessor.class.getDeclaredField("batchSize"), pooledProcessor
                    );
                    assertEquals(1024, resultBatchSize);
                });
    }

    @Test
    void sequencedDeadLetterQueueCanBeSetViaSpringConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withPropertyValues(
                        "sophon.eventhandling.processors.first.dlq.enabled=true"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(EventProcessingModule.class);
                    EventProcessingModule eventProcessingConfig = context.getBean(EventProcessingModule.class);

                    assertTrue(eventProcessingConfig.deadLetterQueue("first").isPresent());
                    assertFalse(eventProcessingConfig.deadLetterQueue("second").isPresent());
                });
    }

    @Test
    void sequencedDeadLetterQueueCacheCanBeSetViaSpringConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withPropertyValues(
                        "sophon.eventhandling.processors.first.dlq.enabled=true",
                        "sophon.eventhandling.processors.first.dlq.cache.enabled=true",
                        "sophon.eventhandling.processors.first.dlq.cache.size=10"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(EventProcessingModule.class);
                    EventProcessingModule eventProcessingConfig = context.getBean(EventProcessingModule.class);

                    Optional<EventProcessor> eventProcessor = eventProcessingConfig.eventProcessorByProcessingGroup(
                            "first");
                    assertTrue(eventProcessor.isPresent());
                    EventHandlerInvoker eventHandlerInvoker = ReflectionUtils.getFieldValue(
                            AbstractEventProcessor.class.getDeclaredField("eventHandlerInvoker"), eventProcessor.get()
                    );
                    assertNotNull(eventHandlerInvoker);
                    List<EventHandlerInvoker> delegates = ReflectionUtils.getFieldValue(
                            MultiEventHandlerInvoker.class.getDeclaredField("delegates"), eventHandlerInvoker
                    );
                    assertFalse(delegates.isEmpty());
                    DeadLetteringEventHandlerInvoker deadLetteringInvoker =
                            ((DeadLetteringEventHandlerInvoker) delegates.get(0));
                    boolean cacheEnabled = ReflectionUtils.getFieldValue(
                            DeadLetteringEventHandlerInvoker.class.getDeclaredField("sequenceIdentifierCacheEnabled"),
                            deadLetteringInvoker
                    );
                    assertTrue(cacheEnabled);
                    int cacheSize = ReflectionUtils.getFieldValue(
                            DeadLetteringEventHandlerInvoker.class.getDeclaredField("sequenceIdentifierCacheSize"),
                            deadLetteringInvoker
                    );
                    assertEquals(10, cacheSize);
                });
    }

    @SuppressWarnings("unused")
    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class Context {

        @Bean
        public SequencingPolicy<?> customPolicy() {
            return new FullConcurrencyPolicy();
        }

        @Bean
        public FirstHandler firstHandler() {
            return new FirstHandler();
        }

        @ProcessingGroup("first")
        public static class FirstHandler {

            @EventHandler
            public void handle(String event) {
            }
        }

        @Bean
        public SecondHandler secondHandler() {
            return new SecondHandler();
        }

        @ProcessingGroup("second")
        public static class SecondHandler {

            @EventHandler
            public void handle(String event) {
            }
        }

        @Bean
        public NonDefaultTokenClaimIntervalHandler nonDefaultTokenClaimIntervalHandler() {
            return new NonDefaultTokenClaimIntervalHandler();
        }

        @ProcessingGroup("non_default_token_claim_interval")
        public static class NonDefaultTokenClaimIntervalHandler {

            @EventHandler
            public void handle(String event) {
            }
        }
    }
}
