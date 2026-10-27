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

package org.sophonhq.sophon.springboot.autoconfig.legacyjpa;

import org.sophonhq.sophon.common.jdbc.PersistenceExceptionResolver;
import org.sophonhq.sophon.common.legacyjpa.EntityManagerProvider;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.legacyjpa.JpaEventStorageEngine;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.springboot.autoconfig.JdbcAutoConfiguration;
import org.sophonhq.sophon.springboot.autoconfig.JpaEventStoreAutoConfiguration;
import org.sophonhq.sophon.springboot.autoconfig.SophonAutoConfiguration;
import org.sophonhq.sophon.springboot.util.RegisterDefaultEntities;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.persistence.EntityManagerFactory;

/**
 * Autoconfiguration class for Sophon's JPA specific event store components.
 *
 * @author Sara Pelligrini
 * @since axon-4.0
 * @deprecated in favor of using {@link JpaEventStoreAutoConfiguration} which moved to jakarta.
 */
@Deprecated
@AutoConfiguration
@ConditionalOnBean(EntityManagerFactory.class)
@ConditionalOnMissingBean({EventStorageEngine.class, EventBus.class, EventStore.class})
@AutoConfigureAfter(JpaJavaxAutoConfiguration.class)
@AutoConfigureBefore({JpaEventStoreAutoConfiguration.class, JdbcAutoConfiguration.class, SophonAutoConfiguration.class})
@RegisterDefaultEntities(packages = {
        "org.sophonhq.sophon.eventsourcing.eventstore.jpa"
})
public class JpaJavaxEventStoreAutoConfiguration {

    @Bean
    public EventStorageEngine eventStorageEngine(Serializer defaultSerializer,
                                                 PersistenceExceptionResolver persistenceExceptionResolver,
                                                 @Qualifier("eventSerializer") Serializer eventSerializer,
                                                 org.sophonhq.sophon.config.Configuration configuration,
                                                 EntityManagerProvider entityManagerProvider,
                                                 TransactionManager transactionManager) {
        return JpaEventStorageEngine.builder()
                                    .snapshotSerializer(defaultSerializer)
                                    .upcasterChain(configuration.upcasterChain())
                                    .persistenceExceptionResolver(persistenceExceptionResolver)
                                    .eventSerializer(eventSerializer)
                                    .snapshotFilter(configuration.snapshotFilter())
                                    .entityManagerProvider(entityManagerProvider)
                                    .transactionManager(transactionManager)
                                    .build();
    }
}
