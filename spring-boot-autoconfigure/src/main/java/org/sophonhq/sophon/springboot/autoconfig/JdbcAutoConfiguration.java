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

package org.sophonhq.sophon.springboot.autoconfig;

import org.sophonhq.sophon.common.jdbc.ConnectionProvider;
import org.sophonhq.sophon.common.jdbc.PersistenceExceptionResolver;
import org.sophonhq.sophon.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.eventhandling.deadletter.jdbc.DeadLetterSchema;
import org.sophonhq.sophon.eventhandling.deadletter.jdbc.JdbcSequencedDeadLetterQueue;
import org.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import org.sophonhq.sophon.eventhandling.tokenstore.jdbc.JdbcTokenStore;
import org.sophonhq.sophon.eventhandling.tokenstore.jdbc.TokenSchema;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.jdbc.EventSchema;
import org.sophonhq.sophon.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.sophonhq.sophon.modelling.saga.repository.SagaStore;
import org.sophonhq.sophon.modelling.saga.repository.jdbc.GenericSagaSqlSchema;
import org.sophonhq.sophon.modelling.saga.repository.jdbc.JdbcSagaStore;
import org.sophonhq.sophon.modelling.saga.repository.jdbc.SagaSqlSchema;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.spring.jdbc.SpringDataSourceConnectionProvider;
import org.sophonhq.sophon.springboot.EventProcessorProperties;
import org.sophonhq.sophon.springboot.TokenStoreProperties;
import org.sophonhq.sophon.springboot.util.DeadLetterQueueProviderConfigurerModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Autoconfiguration class for Sophon's JDBC specific infrastructure components.
 *
 * @author Allard Buijze
 * @since axon-3.1
 */
@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@EnableConfigurationProperties(TokenStoreProperties.class)
@AutoConfigureAfter({JpaAutoConfiguration.class, JpaEventStoreAutoConfiguration.class})
@AutoConfigureBefore(SophonAutoConfiguration.class)
public class JdbcAutoConfiguration {

    private final TokenStoreProperties tokenStoreProperties;

    public JdbcAutoConfiguration(TokenStoreProperties tokenStoreProperties) {
        this.tokenStoreProperties = tokenStoreProperties;
    }

    @Bean
    @ConditionalOnMissingBean({EventStorageEngine.class, EventSchema.class, EventStore.class})
    public EventSchema eventSchema() {
        return new EventSchema();
    }

    @Bean
    @ConditionalOnMissingBean({EventStorageEngine.class, EventBus.class, EventStore.class})
    public EventStorageEngine eventStorageEngine(Serializer defaultSerializer,
                                                 PersistenceExceptionResolver persistenceExceptionResolver,
                                                 @Qualifier("eventSerializer") Serializer eventSerializer,
                                                 org.sophonhq.sophon.config.Configuration configuration,
                                                 ConnectionProvider connectionProvider,
                                                 TransactionManager transactionManager,
                                                 EventSchema eventSchema) {
        return JdbcEventStorageEngine.builder()
                                     .snapshotSerializer(defaultSerializer)
                                     .upcasterChain(configuration.upcasterChain())
                                     .persistenceExceptionResolver(persistenceExceptionResolver)
                                     .eventSerializer(eventSerializer)
                                     .snapshotFilter(configuration.snapshotFilter())
                                     .connectionProvider(connectionProvider)
                                     .transactionManager(transactionManager)
                                     .schema(eventSchema)
                                     .build();
    }

    @Bean
    @ConditionalOnMissingBean({PersistenceExceptionResolver.class, EventStore.class})
    public PersistenceExceptionResolver jdbcSQLErrorCodesResolver() {
        return new JdbcSQLErrorCodesResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public ConnectionProvider connectionProvider(DataSource dataSource) {
        return new UnitOfWorkAwareConnectionProviderWrapper(new SpringDataSourceConnectionProvider(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean({TokenStore.class, TokenSchema.class})
    public TokenSchema tokenSchema() {
        return new TokenSchema();
    }

    @Bean
    @ConditionalOnMissingBean(TokenStore.class)
    public TokenStore tokenStore(ConnectionProvider connectionProvider, Serializer serializer,
                                 TokenSchema tokenSchema) {
        return JdbcTokenStore.builder()
                             .connectionProvider(connectionProvider)
                             .schema(tokenSchema)
                             .serializer(serializer)
                             .claimTimeout(tokenStoreProperties.getClaimTimeout())
                             .build();
    }

    @Bean
    @ConditionalOnMissingBean({SagaStore.class, SagaSqlSchema.class})
    public JdbcSagaStore sagaStoreNoSchema(ConnectionProvider connectionProvider, Serializer serializer) {
        return JdbcSagaStore.builder()
                            .connectionProvider(connectionProvider)
                            .sqlSchema(new GenericSagaSqlSchema())
                            .serializer(serializer)
                            .build();
    }

    @Bean
    @ConditionalOnMissingBean(SagaStore.class)
    @ConditionalOnBean(SagaSqlSchema.class)
    public JdbcSagaStore sagaStoreWithSchema(ConnectionProvider connectionProvider, Serializer serializer,
                                             SagaSqlSchema schema) {
        return JdbcSagaStore.builder()
                            .connectionProvider(connectionProvider)
                            .sqlSchema(schema)
                            .serializer(serializer)
                            .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeadLetterSchema deadLetterSchema() {
        return DeadLetterSchema.defaultSchema();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeadLetterQueueProviderConfigurerModule deadLetterQueueProviderConfigurerModule(
            EventProcessorProperties eventProcessorProperties,
            ConnectionProvider connectionProvider,
            TransactionManager transactionManager,
            DeadLetterSchema schema,
            @Qualifier("eventSerializer") Serializer eventSerializer,
            Serializer genericSerializer
    ) {
        return new DeadLetterQueueProviderConfigurerModule(
                eventProcessorProperties,
                processingGroup -> config -> JdbcSequencedDeadLetterQueue.builder()
                                                                         .processingGroup(processingGroup)
                                                                         .connectionProvider(connectionProvider)
                                                                         .transactionManager(transactionManager)
                                                                         .schema(schema)
                                                                         .genericSerializer(genericSerializer)
                                                                         .eventSerializer(eventSerializer)
                                                                         .build()
        );
    }
}
