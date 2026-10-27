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

package io.github.sophonhq.sophon.springboot;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import io.github.sophonhq.sophon.commandhandling.CommandBus;
import io.github.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import io.github.sophonhq.sophon.common.jdbc.ConnectionProvider;
import io.github.sophonhq.sophon.common.jdbc.PersistenceExceptionResolver;
import io.github.sophonhq.sophon.common.jpa.EntityManagerProvider;
import io.github.sophonhq.sophon.eventhandling.EventBus;
import io.github.sophonhq.sophon.eventhandling.GenericEventMessage;
import io.github.sophonhq.sophon.eventhandling.TrackedEventMessage;
import io.github.sophonhq.sophon.eventhandling.gateway.EventGateway;
import io.github.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import io.github.sophonhq.sophon.eventsourcing.Snapshotter;
import io.github.sophonhq.sophon.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import io.github.sophonhq.sophon.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.serialization.upcasting.Upcaster;
import io.github.sophonhq.sophon.serialization.upcasting.event.EventUpcaster;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        JmxAutoConfiguration.class,
        WebClientAutoConfiguration.class
})
@EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
class SophonAutoConfigurationWithHibernateTest {

    @Autowired
    private ApplicationContext applicationContext;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private Upcaster upcaster;

    @Autowired
    private Snapshotter snapshotter;

    @Test
    void contextInitialization() {
        assertNotNull(applicationContext);
        assertNotNull(snapshotter);

        assertNotNull(applicationContext.getBean(CommandBus.class));
        assertNotNull(applicationContext.getBean(EventBus.class));
        assertNotNull(applicationContext.getBean(CommandGateway.class));
        assertNotNull(applicationContext.getBean(EventGateway.class));
        assertNotNull(applicationContext.getBean(Serializer.class));
        assertNotNull(applicationContext.getBean(TokenStore.class));
        assertNotNull(applicationContext.getBean(JpaEventStorageEngine.class));
        Assertions.assertEquals(SQLErrorCodesResolver.class,
                                applicationContext.getBean(PersistenceExceptionResolver.class).getClass());
        assertNotNull(applicationContext.getBean(EntityManagerProvider.class));
        assertNotNull(applicationContext.getBean(ConnectionProvider.class));

        // for some reason, this test picks up some entities used in other tests
        Assertions.assertEquals(7, entityManager.getEntityManagerFactory().getMetamodel().getEntities().size());
    }

    @Transactional
    @Test
    public void eventStorageEngineUsesSerializerBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final JpaEventStorageEngine engine = applicationContext.getBean(JpaEventStorageEngine.class);

        Assertions.assertEquals(serializer, engine.getSnapshotSerializer());

        engine.appendEvents(GenericEventMessage.asEventMessage("hello"));
        List<? extends TrackedEventMessage<?>> events = engine.readEvents(null, false).collect(Collectors.toList());
        assertEquals(1, events.size());

        verify(upcaster).upcast(any());
    }

    @Configuration
    public static class Config {
        @Bean
        public EventUpcaster upcaster() {
            EventUpcaster mock = mock(EventUpcaster.class);
            when(mock.upcast(any())).thenAnswer(i -> i.getArgument(0));
            return mock;
        }
    }
}
