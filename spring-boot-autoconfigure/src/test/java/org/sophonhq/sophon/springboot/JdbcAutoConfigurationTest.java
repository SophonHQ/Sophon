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

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.common.ReflectionUtils;
import org.sophonhq.sophon.common.jdbc.ConnectionProvider;
import org.sophonhq.sophon.common.jdbc.PersistenceExceptionResolver;
import org.sophonhq.sophon.common.jdbc.UnitOfWorkAwareConnectionProviderWrapper;
import org.sophonhq.sophon.config.EventProcessingConfiguration;
import org.sophonhq.sophon.config.EventProcessingModule;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.SimpleEventBus;
import org.sophonhq.sophon.eventhandling.deadletter.jdbc.DeadLetterSchema;
import org.sophonhq.sophon.eventhandling.deadletter.jdbc.JdbcSequencedDeadLetterQueue;
import org.sophonhq.sophon.eventhandling.tokenstore.TokenStore;
import org.sophonhq.sophon.eventhandling.tokenstore.jdbc.JdbcTokenStore;
import org.sophonhq.sophon.eventhandling.tokenstore.jdbc.TokenSchema;
import org.sophonhq.sophon.eventsourcing.eventstore.EmbeddedEventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.jdbc.EventSchema;
import org.sophonhq.sophon.eventsourcing.eventstore.jdbc.JdbcEventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueue;
import org.sophonhq.sophon.modelling.saga.repository.SagaStore;
import org.sophonhq.sophon.modelling.saga.repository.jdbc.JdbcSagaStore;
import org.sophonhq.sophon.springboot.util.DeadLetterQueueProviderConfigurerModule;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests JDBC auto-configuration.
 *
 * @author Milan Savic
 */
public class JdbcAutoConfigurationTest {

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        testContext = new ApplicationContextRunner()
                .withUserConfiguration(TestContext.class);
    }

    @Test
    void allJdbcComponentsAutoConfigured() {
        testContext.run(context -> {
            assertThat(context).hasSingleBean(JdbcEventStorageEngine.class);
            assertThat(context).getBean(EventStorageEngine.class).isInstanceOf(JdbcEventStorageEngine.class);
            assertThat(context).getBean(EventStore.class).isInstanceOf(EmbeddedEventStore.class);
            assertThat(context).getBean(TokenStore.class).isInstanceOf(JdbcTokenStore.class);
            assertThat(context).getBean(SagaStore.class).isInstanceOf(JdbcSagaStore.class);
            assertThat(context).getBean(TokenStore.class)
                               .isEqualTo(context.getBean(EventProcessingConfiguration.class)
                                                 .tokenStore("test"));
            assertThat(context).getBean(PersistenceExceptionResolver.class).isInstanceOf(
                    JdbcSQLErrorCodesResolver.class);
            assertThat(context).getBean(ConnectionProvider.class).isInstanceOf(
                    UnitOfWorkAwareConnectionProviderWrapper.class);
        });
    }

    @Test
    void defaultTokenSchemaDefinedWhenNoneAvailable() {
        testContext.run(context -> {
            assertThat(context).hasSingleBean(TokenSchema.class);
            TokenSchema tokenSchema = context.getBean(TokenSchema.class);
            assertThat(context).hasSingleBean(TokenStore.class);
            assertThat(context).getBean(TokenStore.class).extracting("schema").isSameAs(tokenSchema);
        });
    }

    @Test
    void customTokenSchema() {
        TokenSchema tokenSchema = TokenSchema.builder()
                                             .setTokenTable("TEST123")
                                             .build();

        testContext.withBean(TokenSchema.class, () -> tokenSchema)
                   .run(context -> {
                       assertThat(context).hasSingleBean(TokenStore.class);
                       assertThat(context).getBean(TokenStore.class).extracting("schema").isSameAs(tokenSchema);
                   });
    }

    @Test
    void defaultEventSchemaDefinedWhenNoneAvailable() {
        testContext.run(context -> {
            assertThat(context).hasSingleBean(EventSchema.class);
            EventSchema eventSchema = context.getBean(EventSchema.class);
            assertThat(context).hasSingleBean(EventStorageEngine.class);
            assertThat(context).getBean(EventStorageEngine.class).extracting("schema").isSameAs(eventSchema);
        });
    }

    @Test
    void customEventSchema() {
        EventSchema eventSchema = EventSchema.builder()
                                             .eventTable("TEST123")
                                             .build();

        testContext.withBean(EventSchema.class, () -> eventSchema)
                   .run(context -> {
                       assertThat(context).hasSingleBean(EventStorageEngine.class);
                       assertThat(context).getBean(EventStorageEngine.class).extracting("schema").isSameAs(eventSchema);
                   });
    }

    @Test
    void configurationOfEventBusPreventsEventStoreDefinition() {
        testContext.withUserConfiguration(ExplicitEventBusContext.class)
                   .run(context -> assertThat(context).doesNotHaveBean(EventStorageEngine.class)
                                                      .doesNotHaveBean(EventStore.class));
    }

    @Test
    void setTokenStoreClaimTimeout() {
        testContext.withPropertyValues("sophon.eventhandling.tokenstore.claim-timeout=10m")
                   .run(context -> {
                       Map<String, TokenStore> tokenStores =
                               context.getBeansOfType(TokenStore.class);
                       assertTrue(tokenStores.containsKey("tokenStore"));
                       TokenStore tokenStore = tokenStores.get("tokenStore");
                       TemporalAmount tokenClaimInterval = ReflectionUtils.getFieldValue(
                               JdbcTokenStore.class.getDeclaredField("claimTimeout"), tokenStore
                       );
                       assertEquals(Duration.ofMinutes(10L), tokenClaimInterval);
                   });
    }

    @Test
    void defaultDeadLetterSchemaIsPresent() {
        testContext.run(context -> {
            DeadLetterSchema schema = context.getBean(DeadLetterSchema.class);
            assertNotNull(schema);
            assertEquals("DeadLetterEntry", schema.deadLetterTable());
        });
    }

    @Test
    void customDeadLetterSchemaIsPresent() {
        String expectedDeadLetterTable = "custom-table-name";
        DeadLetterSchema customSchema = DeadLetterSchema.builder()
                                                        .deadLetterTable(expectedDeadLetterTable)
                                                        .build();

        testContext.withBean(DeadLetterSchema.class, () -> customSchema)
                   .run(context -> {
                       DeadLetterSchema schema = context.getBean(DeadLetterSchema.class);
                       assertNotNull(schema);
                       assertEquals(expectedDeadLetterTable, schema.deadLetterTable());
                   });
    }

    @Test
    void sequencedDeadLetterQueueCanBeSetViaSpringConfiguration() {
        testContext.withPropertyValues("sophon.eventhandling.processors.first.dlq.enabled=true")
                   .run(context -> {
                       assertNotNull(context.getBean(DeadLetterQueueProviderConfigurerModule.class));

                       EventProcessingModule eventProcessingConfig = context.getBean(EventProcessingModule.class);
                       assertNotNull(eventProcessingConfig);

                       Optional<SequencedDeadLetterQueue<EventMessage<?>>> dlq =
                               eventProcessingConfig.deadLetterQueue("first");
                       assertTrue(dlq.isPresent());
                       assertTrue(dlq.get() instanceof JdbcSequencedDeadLetterQueue);

                       dlq = eventProcessingConfig.deadLetterQueue("second");
                       assertFalse(dlq.isPresent());
                   });
    }

    @Test
    void deadLetterQueueProviderConfigurerModuleCanBeOverwritten() {
        testContext.withPropertyValues("sophon.eventhandling.processors.first.dlq.enabled=true")
                   .run(context -> {
                       assertNotNull(context.getBean("deadLetterQueueProviderConfigurerModule",
                                                     DeadLetterQueueProviderConfigurerModule.class));

                       EventProcessingModule eventProcessingConfig = context.getBean(EventProcessingModule.class);
                       assertNotNull(eventProcessingConfig);

                       Optional<SequencedDeadLetterQueue<EventMessage<?>>> dlq =
                               eventProcessingConfig.deadLetterQueue("first");
                       assertTrue(dlq.isPresent());
                       assertTrue(dlq.get() instanceof JdbcSequencedDeadLetterQueue);

                       dlq = eventProcessingConfig.deadLetterQueue("second");
                       assertFalse(dlq.isPresent());
                   });
    }

    @ContextConfiguration
    @EnableAutoConfiguration(exclude = {
            JpaRepositoriesAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    private static class TestContext {

        @Bean
        public DataSource dataSource() throws SQLException {
            DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
            when(databaseMetaData.getDatabaseProductName()).thenReturn("H2");
            Connection connection = mock(Connection.class);
            when(connection.getMetaData()).thenReturn(databaseMetaData);
            DataSource dataSource = mock(DataSource.class);
            when(dataSource.getConnection()).thenReturn(connection);
            return dataSource;
        }
    }

    private static class ExplicitEventBusContext {

        @Bean
        public EventBus eventBus() {
            return SimpleEventBus.builder().build();
        }
    }
}
