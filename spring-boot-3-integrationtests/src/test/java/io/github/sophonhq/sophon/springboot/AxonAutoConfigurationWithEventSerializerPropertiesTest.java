/*
 * Copyright (c) 2010-2023. Axon Framework
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

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.github.sophonhq.sophon.serialization.JavaSerializer;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.serialization.json.JacksonSerializer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AxonAutoConfigurationWithEventSerializerPropertiesTest.TestContext.class)
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class
})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
@TestPropertySource("classpath:application.serializertest.properties")
class AxonAutoConfigurationWithEventSerializerPropertiesTest {

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
        assertNotNull(applicationContext.getBean("messageSerializer", Serializer.class));
        assertNotNull(applicationContext.getBean("eventSerializer", Serializer.class));
        io.github.sophonhq.sophon.config.Configuration axonConfiguration =
                applicationContext.getBean(io.github.sophonhq.sophon.config.Configuration.class);
        assertSame(axonConfiguration.serializer(), axonConfiguration.eventSerializer());
        assertNotSame(axonConfiguration.serializer(), axonConfiguration.messageSerializer());
        assertNotSame(axonConfiguration.messageSerializer(), axonConfiguration.eventSerializer());
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
        final Serializer eventSerializer = applicationContext.getBean("eventSerializer", Serializer.class);
        final Serializer messageSerializer = applicationContext.getBean("messageSerializer", Serializer.class);
        final JpaEventStorageEngine engine = applicationContext.getBean(JpaEventStorageEngine.class);

        assertTrue(messageSerializer instanceof JavaSerializer);
        assertEquals(serializer, engine.getSnapshotSerializer());
        assertEquals(eventSerializer, engine.getEventSerializer());
    }

    @Test
    void eventSerializerIsOfTypeJacksonSerializerAndUsesDefinedObjectMapperBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final Serializer eventSerializer = applicationContext.getBean("eventSerializer", Serializer.class);
        final ObjectMapper objectMapper = applicationContext.getBean("testObjectMapper", ObjectMapper.class);

        assertTrue(serializer instanceof JacksonSerializer);
        assertEquals(objectMapper, ((JacksonSerializer) serializer).getObjectMapper());
        assertTrue(eventSerializer instanceof JacksonSerializer);
        assertEquals(objectMapper, ((JacksonSerializer) eventSerializer).getObjectMapper());
    }

    @Configuration
    public static class TestContext {

        @Bean("testObjectMapper")
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
