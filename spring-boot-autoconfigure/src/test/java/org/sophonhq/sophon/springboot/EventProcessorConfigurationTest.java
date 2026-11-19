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

package org.sophonhq.sophon.springboot;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.ReflectionUtils;
import org.sophonhq.sophon.config.EventProcessingModule;
import org.sophonhq.sophon.config.ProcessingGroup;
import org.sophonhq.sophon.eventhandling.AbstractEventProcessor;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.eventhandling.EventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.EventProcessor;
import org.sophonhq.sophon.eventhandling.MultiEventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.SimpleEventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.TrackingEventProcessor;
import org.sophonhq.sophon.eventhandling.async.FullConcurrencyPolicy;
import org.sophonhq.sophon.eventhandling.async.SequencingPolicy;
import org.sophonhq.sophon.eventhandling.deadletter.DeadLetteringEventHandlerInvoker;
import org.sophonhq.sophon.eventhandling.pooled.PooledStreamingEventProcessor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.common.ReflectionUtils.ensureAccessible;

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
                    assertThat(processors).hasSize(3);

                    EventProcessor eventProcessor = processors.get("first");
                    assertThat(eventProcessor).isNotNull();
                    assertThat(eventProcessor.getClass()).isEqualTo(TrackingEventProcessor.class);

                    long tokenClaimInterval = ReflectionUtils.getFieldValue(
                            TrackingEventProcessor.class.getDeclaredField("tokenClaimInterval"), eventProcessor
                    );
                    assertThat(tokenClaimInterval).as("Must be 5000 ms by default").isEqualTo(5000L);

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
                    assertThat(policy).isEqualTo(expectedPolicy);
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
                    assertThat(processors).hasSize(3);

                    EventProcessor eventProcessor = processors.get("non_default_token_claim_interval");
                    assertThat(eventProcessor).isNotNull();
                    assertThat(eventProcessor.getClass()).isEqualTo(TrackingEventProcessor.class);
                    long tokenClaimInterval = ReflectionUtils.getFieldValue(
                            TrackingEventProcessor.class.getDeclaredField("tokenClaimInterval"), eventProcessor
                    );

                    assertThat(tokenClaimInterval)
                            .as("It must be possible to override token claim interval via Spring Configuration")
                            .isEqualTo(60000000L);
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
                    assertThat(processors).hasSize(3);

                    EventProcessor defaultProcessor = processors.get("first");
                    assertThat(defaultProcessor).isNotNull();
                    assertThat(defaultProcessor.getClass()).isEqualTo(TrackingEventProcessor.class);

                    EventProcessor pooledProcessor = processors.get("second");
                    assertThat(pooledProcessor).isNotNull();
                    assertThat(pooledProcessor.getClass()).isEqualTo(PooledStreamingEventProcessor.class);

                    long resultTokenClaimInterval = ReflectionUtils.getFieldValue(
                            PooledStreamingEventProcessor.class.getDeclaredField("tokenClaimInterval"), pooledProcessor
                    );
                    assertThat(resultTokenClaimInterval).isEqualTo(60000000L);

                    int resultBatchSize = ReflectionUtils.getFieldValue(
                            PooledStreamingEventProcessor.class.getDeclaredField("batchSize"), pooledProcessor
                    );
                    assertThat(resultBatchSize).isEqualTo(1024);
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

                    assertThat(eventProcessingConfig.deadLetterQueue("first")).isPresent();
                    assertThat(eventProcessingConfig.deadLetterQueue("second")).isNotPresent();
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
                    assertThat(eventProcessor).isPresent();
                    EventHandlerInvoker eventHandlerInvoker = ReflectionUtils.getFieldValue(
                            AbstractEventProcessor.class.getDeclaredField("eventHandlerInvoker"), eventProcessor.get()
                    );
                    assertThat(eventHandlerInvoker).isNotNull();
                    List<EventHandlerInvoker> delegates = ReflectionUtils.getFieldValue(
                            MultiEventHandlerInvoker.class.getDeclaredField("delegates"), eventHandlerInvoker
                    );
                    assertThat(delegates).isNotEmpty();
                    DeadLetteringEventHandlerInvoker deadLetteringInvoker =
                            ((DeadLetteringEventHandlerInvoker) delegates.get(0));
                    boolean cacheEnabled = ReflectionUtils.getFieldValue(
                            DeadLetteringEventHandlerInvoker.class.getDeclaredField("sequenceIdentifierCacheEnabled"),
                            deadLetteringInvoker
                    );
                    assertThat(cacheEnabled).isTrue();
                    int cacheSize = ReflectionUtils.getFieldValue(
                            DeadLetteringEventHandlerInvoker.class.getDeclaredField("sequenceIdentifierCacheSize"),
                            deadLetteringInvoker
                    );
                    assertThat(cacheSize).isEqualTo(10);
                });
    }

    @SuppressWarnings("unused")
    @ContextConfiguration
    @EnableAutoConfiguration
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
