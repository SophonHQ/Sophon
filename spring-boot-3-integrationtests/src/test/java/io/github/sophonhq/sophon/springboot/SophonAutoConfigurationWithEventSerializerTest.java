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

import com.thoughtworks.xstream.XStream;
import io.github.sophonhq.sophon.commandhandling.CommandBus;
import io.github.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import io.github.sophonhq.sophon.common.jdbc.ConnectionProvider;
import io.github.sophonhq.sophon.common.jdbc.PersistenceExceptionResolver;
import io.github.sophonhq.sophon.common.jpa.EntityManagerProvider;
import io.github.sophonhq.sophon.eventhandling.EventBus;
import io.github.sophonhq.sophon.eventhandling.gateway.EventGateway;
import io.github.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import io.github.sophonhq.sophon.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.serialization.json.JacksonSerializer;
import io.github.sophonhq.sophon.serialization.xml.XStreamSerializer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.context.annotation.Primary;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class
})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class SophonAutoConfigurationWithEventSerializerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void contextInitialization() {
        assertNotNull(applicationContext);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(EventGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        io.github.sophonhq.sophon.config.Configuration sophonConfiguration =
                applicationContext.getBean(io.github.sophonhq.sophon.config.Configuration.class);
        assertNotSame(sophonConfiguration.serializer(), sophonConfiguration.eventSerializer());
        assertNotNull(applicationContext.getBean(TokenStore.class));
        assertNotNull(applicationContext.getBean(JpaEventStorageEngine.class));
        assertEquals(SQLErrorCodesResolver.class,
                     applicationContext.getBean(PersistenceExceptionResolver.class).getClass());
        assertNotNull(applicationContext.getBean(EntityManagerProvider.class));
        assertNotNull(applicationContext.getBean(ConnectionProvider.class));

        // for some reason, this test picks up some entities used in other tests
        assertEquals(7, entityManager.getEntityManagerFactory().getMetamodel().getEntities().size());
    }

    @Test
    void eventStorageEngineUsesSerializerBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final Serializer eventSerializer = applicationContext.getBean("myEventSerializer", Serializer.class);
        final JpaEventStorageEngine engine = applicationContext.getBean(JpaEventStorageEngine.class);

        assertEquals(serializer, engine.getSnapshotSerializer());
        assertEquals(eventSerializer, engine.getEventSerializer());
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
            return JacksonSerializer.builder().build();
        }
    }
}
