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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.ReflectionUtils;
import org.sophonhq.sophon.common.jpa.EntityManagerProvider;
import org.sophonhq.sophon.config.EventProcessingModule;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import org.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import org.sophonhq.sophon.eventhandling.tokenstore.jpa.JpaTokenStore;
import org.sophonhq.sophon.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueue;
import org.sophonhq.sophon.modelling.saga.repository.SagaStore;
import org.sophonhq.sophon.modelling.saga.repository.jpa.JpaSagaStore;
import org.sophonhq.sophon.springboot.util.DeadLetterQueueProviderConfigurerModule;
import org.sophonhq.sophon.springboot.util.jpa.ContainerManagedEntityManagerProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.context.ContextConfiguration;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests JPA auto-configuration
 *
 * @author Sara Pellegrini
 */
class JpaAutoConfigurationTest {

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner().withUserConfiguration(TestContext.class);
    }

    @Test
    void contextInitialization() {
        testContext.run(context -> {
            Map<String, EntityManagerProvider> entityManagerProviders =
                    context.getBeansOfType(EntityManagerProvider.class);
            assertThat(entityManagerProviders).containsKey("entityManagerProvider");
            assertThat(entityManagerProviders.get("entityManagerProvider").getClass())
                    .isEqualTo(ContainerManagedEntityManagerProvider.class);

            Map<String, TokenStore> tokenStores =
                    context.getBeansOfType(TokenStore.class);
            assertThat(tokenStores).containsKey("tokenStore");
            assertThat(tokenStores.get("tokenStore").getClass()).isEqualTo(JpaTokenStore.class);

            //noinspection rawtypes
            Map<String, SagaStore> sagaStores =
                    context.getBeansOfType(SagaStore.class);
            assertThat(sagaStores).containsKey("sagaStore");
            assertThat(sagaStores.get("sagaStore").getClass()).isEqualTo(JpaSagaStore.class);

            Map<String, SQLErrorCodesResolver> persistenceExceptionResolvers =
                    context.getBeansOfType(SQLErrorCodesResolver.class);
            assertThat(persistenceExceptionResolvers).containsKey("persistenceExceptionResolver");
            assertThat(persistenceExceptionResolvers.get("persistenceExceptionResolver").getClass())
                    .isEqualTo(SQLErrorCodesResolver.class);
        });
    }

    @Test
    void setTokenStoreClaimTimeout() {
        testContext.withPropertyValues("sophon.eventhandling.tokenstore.claim-timeout=3000")
                   .run(context -> {
                       Map<String, TokenStore> tokenStores =
                               context.getBeansOfType(TokenStore.class);
                       assertThat(tokenStores).containsKey("tokenStore");
                       TokenStore tokenStore = tokenStores.get("tokenStore");
                       TemporalAmount tokenClaimInterval = ReflectionUtils.getFieldValue(
                               JpaTokenStore.class.getDeclaredField("claimTimeout"), tokenStore
                       );
                       assertThat(tokenClaimInterval).isEqualTo(Duration.ofSeconds(3L));
                   });
    }

    @Test
    void sequencedDeadLetterQueueCanBeSetViaSpringConfiguration() {
        testContext.withPropertyValues("sophon.eventhandling.processors.first.dlq.enabled=true")
                   .run(context -> {
                       assertThat(context.getBean(DeadLetterQueueProviderConfigurerModule.class)).isNotNull();

                       EventProcessingModule eventProcessingConfig = context.getBean(EventProcessingModule.class);
                       assertThat(eventProcessingConfig).isNotNull();

                       Optional<SequencedDeadLetterQueue<EventMessage<?>>> dlq =
                               eventProcessingConfig.deadLetterQueue("first");
                       assertThat(dlq).isPresent();
                       assertThat(dlq.get()).isInstanceOf(JpaSequencedDeadLetterQueue.class);

                       dlq = eventProcessingConfig.deadLetterQueue("second");
                       assertThat(dlq).isNotPresent();
                   });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    private static class TestContext {

    }
}
