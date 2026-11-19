/*
 * Copyright (c) 2010-2026. Axon Framework
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

import com.thoughtworks.xstream.XStream;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sophonhq.sophon.commandhandling.CommandBus;
import org.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import org.sophonhq.sophon.common.jdbc.ConnectionProvider;
import org.sophonhq.sophon.common.jdbc.PersistenceExceptionResolver;
import org.sophonhq.sophon.common.jpa.EntityManagerProvider;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.eventhandling.gateway.EventGateway;
import org.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import org.sophonhq.sophon.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.jackson3.Jackson3Serializer;
import org.sophonhq.sophon.serialization.xml.XStreamSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration
class SophonAutoConfigurationWithEventSerializerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void contextInitialization() {
        assertThat(applicationContext).isNotNull();

        assertThat(applicationContext.getBean(CommandBus.class)).isNotNull();
        assertThat(applicationContext.getBean(EventBus.class)).isNotNull();
        assertThat(applicationContext.getBean(CommandGateway.class)).isNotNull();
        assertThat(applicationContext.getBean(EventGateway.class)).isNotNull();
        assertThat(applicationContext.getBean(Serializer.class)).isNotNull();
        org.sophonhq.sophon.config.Configuration sophonConfiguration =
                applicationContext.getBean(org.sophonhq.sophon.config.Configuration.class);
        assertThat(sophonConfiguration.eventSerializer()).isNotSameAs(sophonConfiguration.serializer());
        assertThat(applicationContext.getBean(TokenStore.class)).isNotNull();
        assertThat(applicationContext.getBean(JpaEventStorageEngine.class)).isNotNull();
        assertThat(applicationContext.getBean(PersistenceExceptionResolver.class).getClass())
                .isEqualTo(SQLErrorCodesResolver.class);
        assertThat(applicationContext.getBean(EntityManagerProvider.class)).isNotNull();
        assertThat(applicationContext.getBean(ConnectionProvider.class)).isNotNull();

        // for some reason, this test picks up some entities used in other tests
        assertThat(entityManager.getEntityManagerFactory().getMetamodel().getEntities()).hasSize(7);
    }

    @Test
    void eventStorageEngineUsesSerializerBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final Serializer eventSerializer = applicationContext.getBean("myEventSerializer", Serializer.class);
        final JpaEventStorageEngine engine = applicationContext.getBean(JpaEventStorageEngine.class);

        assertThat(engine.getSnapshotSerializer()).isEqualTo(serializer);
        assertThat(engine.getEventSerializer()).isEqualTo(eventSerializer);
    }

    @org.springframework.context.annotation.Configuration
    public static class Configuration {

        @Bean
        @Primary
        public Serializer mySerializer(XStream xStream) {
            return XStreamSerializer.builder()
                                    .xStream(xStream)
                                    .build();
        }

        @Bean
        @Qualifier("eventSerializer")
        public Serializer myEventSerializer() {
            return Jackson3Serializer.builder().build();
        }
    }
}
