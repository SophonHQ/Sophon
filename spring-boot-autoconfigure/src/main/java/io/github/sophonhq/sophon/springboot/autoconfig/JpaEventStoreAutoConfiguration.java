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
import io.github.sophonhq.sophon.eventhandling.EventBus;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.springboot.util.RegisterDefaultEntities;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration class for Sophon's JPA specific event store components.
 *
 * @author Sara Pelligrini
 * @since axon-4.0
 */
@AutoConfiguration
@ConditionalOnBean(EntityManagerFactory.class)
@ConditionalOnMissingBean({EventStorageEngine.class, EventBus.class, EventStore.class})
@AutoConfigureAfter(name = {
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
})
@AutoConfigureBefore(SophonAutoConfiguration.class)
@RegisterDefaultEntities(packages = {
        "io.github.sophonhq.sophon.eventsourcing.eventstore.jpa"
})
public class JpaEventStoreAutoConfiguration {

    @Bean
    public EventStorageEngine eventStorageEngine(Serializer defaultSerializer,
                                                 PersistenceExceptionResolver persistenceExceptionResolver,
                                                 @Qualifier("eventSerializer") Serializer eventSerializer,
                                                 io.github.sophonhq.sophon.config.Configuration configuration,
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
