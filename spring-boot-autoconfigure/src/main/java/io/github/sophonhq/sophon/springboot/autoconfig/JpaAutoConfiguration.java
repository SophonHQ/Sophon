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

package io.github.sophonhq.sophon.springboot.autoconfig;

import io.github.sophonhq.sophon.common.jdbc.PersistenceExceptionResolver;
import io.github.sophonhq.sophon.common.jpa.EntityManagerProvider;
import io.github.sophonhq.sophon.common.transaction.TransactionManager;
import io.github.sophonhq.sophon.eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue;
import io.github.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import io.github.sophonhq.sophon.eventhandling.tokenstore.jpa.JpaTokenStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import io.github.sophonhq.sophon.modelling.saga.repository.SagaStore;
import io.github.sophonhq.sophon.modelling.saga.repository.jpa.JpaSagaStore;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.springboot.EventProcessorProperties;
import io.github.sophonhq.sophon.springboot.TokenStoreProperties;
import io.github.sophonhq.sophon.springboot.util.DeadLetterQueueProviderConfigurerModule;
import io.github.sophonhq.sophon.springboot.util.RegisterDefaultEntities;
import io.github.sophonhq.sophon.springboot.util.jpa.ContainerManagedEntityManagerProvider;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import java.sql.SQLException;
import javax.sql.DataSource;

/**
 * Autoconfiguration class for Sophon's JPA specific infrastructure components.
 *
 * @author Allard Buijze
 * @since axon-3.0.3
 */
@AutoConfiguration
@ConditionalOnBean(EntityManagerFactory.class)
@EnableConfigurationProperties(TokenStoreProperties.class)
@RegisterDefaultEntities(packages = {
        "io.github.sophonhq.sophon.eventhandling.tokenstore",
        "io.github.sophonhq.sophon.eventhandling.deadletter.jpa",
        "io.github.sophonhq.sophon.modelling.saga.repository.jpa",
})
@AutoConfigureAfter(name = {
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
public class JpaAutoConfiguration {

    private final TokenStoreProperties tokenStoreProperties;

    public JpaAutoConfiguration(TokenStoreProperties tokenStoreProperties) {
        this.tokenStoreProperties = tokenStoreProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public EntityManagerProvider entityManagerProvider() {
        return new ContainerManagedEntityManagerProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenStore tokenStore(Serializer serializer, EntityManagerProvider entityManagerProvider) {
        return JpaTokenStore.builder()
                            .entityManagerProvider(entityManagerProvider)
                            .serializer(serializer)
                            .claimTimeout(tokenStoreProperties.getClaimTimeout())
                            .build();
    }

    @Lazy
    @Bean
    @ConditionalOnMissingBean(SagaStore.class)
    public JpaSagaStore sagaStore(Serializer serializer, EntityManagerProvider entityManagerProvider) {
        return JpaSagaStore.builder()
                           .entityManagerProvider(entityManagerProvider)
                           .serializer(serializer)
                           .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public PersistenceExceptionResolver persistenceExceptionResolver(DataSource dataSource) throws SQLException {
        return new SQLErrorCodesResolver(dataSource);
    }

    // tag::JpaDeadLetterQueueProviderConfigurerModule[]
    @Bean
    @ConditionalOnMissingBean
    public DeadLetterQueueProviderConfigurerModule deadLetterQueueProviderConfigurerModule(
            EventProcessorProperties eventProcessorProperties,
            EntityManagerProvider entityManagerProvider,
            TransactionManager transactionManager,
            Serializer genericSerializer,
            @Qualifier("eventSerializer") Serializer eventSerializer
    ) {
        return new DeadLetterQueueProviderConfigurerModule(
                eventProcessorProperties,
                processingGroup -> config -> JpaSequencedDeadLetterQueue.builder()
                                                                        .processingGroup(processingGroup)
                                                                        .entityManagerProvider(entityManagerProvider)
                                                                        .transactionManager(transactionManager)
                                                                        .genericSerializer(genericSerializer)
                                                                        .eventSerializer(eventSerializer)
                                                                        .build()
        );
    }
    // end::JpaDeadLetterQueueProviderConfigurerModule[]
}
