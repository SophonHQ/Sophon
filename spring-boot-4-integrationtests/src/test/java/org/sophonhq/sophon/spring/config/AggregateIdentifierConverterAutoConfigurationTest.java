/*
 * Copyright (c) 2026. SophonHQ
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

package org.sophonhq.sophon.spring.config;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.modelling.command.AggregateIdentifierConverter;
import org.sophonhq.sophon.modelling.command.GenericJpaRepository;
import org.sophonhq.sophon.modelling.command.Repository;
import org.sophonhq.sophon.modelling.command.TargetAggregateIdentifier;
import org.sophonhq.sophon.spring.stereotype.Aggregate;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Test class validating that the {@link GenericJpaRepository} which {@link SpringAggregateConfigurer} configures for a
 * JPA-mapped {@link Aggregate} converts aggregate identifiers with the {@link AggregateIdentifierConverter} bean.
 * <p>
 * This test resides outside the {@code org.sophonhq.sophon.springboot} package on purpose: other tests auto-configure
 * JPA from that package and assert on the number of entities they find in it.
 */
class AggregateIdentifierConverterAutoConfigurationTest {

    private ApplicationContextRunner testApplicationContext;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner().withUserConfiguration(TestContext.class);
    }

    @Test
    void aggregateIdentifierConverterBeanIsUsedToLoadAggregatesOfEveryIdentifierType() {
        RecordingAggregateIdentifierConverter converter = new RecordingAggregateIdentifierConverter();
        testApplicationContext.withBean(AggregateIdentifierConverter.class, () -> converter).run(context -> {
            CommandGateway commandGateway = context.getBean(CommandGateway.class);
            UUID uuidIdentifier = UUID.randomUUID();
            String stringIdentifier = UUID.randomUUID().toString();
            commandGateway.sendAndWait(new CreateUuidIdentifiedAggregateCommand(uuidIdentifier));
            commandGateway.sendAndWait(new CreateStringIdentifiedAggregateCommand(stringIdentifier));

            UUID loadedUuidIdentifier =
                    commandGateway.sendAndWait(new LoadUuidIdentifiedAggregateCommand(uuidIdentifier));
            String loadedStringIdentifier =
                    commandGateway.sendAndWait(new LoadStringIdentifiedAggregateCommand(stringIdentifier));

            assertEquals(uuidIdentifier, loadedUuidIdentifier);
            assertEquals(stringIdentifier, loadedStringIdentifier);
            assertEquals(List.of(new Conversion(TestContext.UuidIdentifiedAggregate.class, uuidIdentifier.toString()),
                                 new Conversion(TestContext.StringIdentifiedAggregate.class, stringIdentifier)),
                         converter.conversions);
        });
    }

    @Test
    void aggregateIdentifierIsPassedUnchangedWithoutAggregateIdentifierConverterBean() {
        testApplicationContext.run(context -> {
            CommandGateway commandGateway = context.getBean(CommandGateway.class);
            String stringIdentifier = UUID.randomUUID().toString();
            commandGateway.sendAndWait(new CreateStringIdentifiedAggregateCommand(stringIdentifier));

            String loadedStringIdentifier =
                    commandGateway.sendAndWait(new LoadStringIdentifiedAggregateCommand(stringIdentifier));

            assertEquals(stringIdentifier, loadedStringIdentifier);
        });
    }

    @Test
    void primaryAggregateIdentifierConverterBeanIsUsed() {
        RecordingAggregateIdentifierConverter primaryConverter = new RecordingAggregateIdentifierConverter();
        RecordingAggregateIdentifierConverter otherConverter = new RecordingAggregateIdentifierConverter();
        testApplicationContext.withBean("primaryConverter", AggregateIdentifierConverter.class,
                                        () -> primaryConverter, definition -> definition.setPrimary(true))
                              .withBean("otherConverter", AggregateIdentifierConverter.class, () -> otherConverter)
                              .run(context -> {
                                  CommandGateway commandGateway = context.getBean(CommandGateway.class);
                                  UUID uuidIdentifier = UUID.randomUUID();
                                  commandGateway.sendAndWait(new CreateUuidIdentifiedAggregateCommand(uuidIdentifier));

                                  UUID loadedUuidIdentifier = commandGateway.sendAndWait(
                                          new LoadUuidIdentifiedAggregateCommand(uuidIdentifier)
                                  );

                                  assertEquals(uuidIdentifier, loadedUuidIdentifier);
                                  assertEquals(1, primaryConverter.conversions.size());
                                  assertTrue(otherConverter.conversions.isEmpty());
                              });
    }

    @Test
    void multipleAggregateIdentifierConverterBeansWithoutPrimaryFailStartup() {
        testApplicationContext.withBean("firstConverter", AggregateIdentifierConverter.class,
                                        RecordingAggregateIdentifierConverter::new)
                              .withBean("secondConverter", AggregateIdentifierConverter.class,
                                        RecordingAggregateIdentifierConverter::new)
                              .run(context -> {
                                  Throwable startupFailure = context.getStartupFailure();
                                  assertNotNull(startupFailure);
                                  SophonConfigurationException cause = findCause(startupFailure);
                                  assertNotNull(cause, "Expected the startup to fail on the ambiguous converter");
                                  assertTrue(cause.getMessage().contains("AggregateIdentifierConverter"));
                              });
    }

    @Test
    void userDefinedRepositoriesAreLeftUntouched() {
        Repository<?> conventionRepository = mock(Repository.class);
        testApplicationContext.withBean(AggregateIdentifierConverter.class, RecordingAggregateIdentifierConverter::new)
                              .withBean("stringIdentifiedAggregateRepository", Repository.class,
                                        () -> conventionRepository)
                              .run(context -> {
                                  org.sophonhq.sophon.config.Configuration configuration =
                                          context.getBean(org.sophonhq.sophon.config.Configuration.class);

                                  assertSame(conventionRepository,
                                             configuration.repository(TestContext.StringIdentifiedAggregate.class));
                                  assertSame(context.getBean("customRepository"),
                                             configuration.repository(TestContext.CustomRepositoryAggregate.class));
                              });
    }

    private static SophonConfigurationException findCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SophonConfigurationException sophonConfigurationException) {
                return sophonConfigurationException;
            }
            current = current.getCause();
        }
        return null;
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestContext {

        @Bean
        public Repository<CustomRepositoryAggregate> customRepository() {
            //noinspection unchecked
            return mock(Repository.class);
        }

        @SuppressWarnings("unused")
        @Entity(name = "uuidIdentifiedAggregate")
        @Aggregate
        static class UuidIdentifiedAggregate {

            @Id
            private UUID id;

            public UuidIdentifiedAggregate() {
                // Required by JPA
            }

            @CommandHandler
            public UuidIdentifiedAggregate(CreateUuidIdentifiedAggregateCommand command) {
                this.id = command.id;
            }

            @CommandHandler
            public UUID handle(LoadUuidIdentifiedAggregateCommand command) {
                return id;
            }
        }

        @SuppressWarnings("unused")
        @Entity(name = "stringIdentifiedAggregate")
        @Aggregate
        static class StringIdentifiedAggregate {

            @Id
            private String id;

            public StringIdentifiedAggregate() {
                // Required by JPA
            }

            @CommandHandler
            public StringIdentifiedAggregate(CreateStringIdentifiedAggregateCommand command) {
                this.id = command.id;
            }

            @CommandHandler
            public String handle(LoadStringIdentifiedAggregateCommand command) {
                return id;
            }
        }

        @SuppressWarnings("unused")
        @Entity(name = "customRepositoryAggregate")
        @Aggregate(repository = "customRepository")
        static class CustomRepositoryAggregate {

            @Id
            private String id;

            public CustomRepositoryAggregate() {
                // Required by JPA
            }
        }
    }

    private static class RecordingAggregateIdentifierConverter implements AggregateIdentifierConverter {

        private final List<Conversion> conversions = new ArrayList<>();

        @Override
        public Object convert(Class<?> aggregateType, String aggregateIdentifier) {
            conversions.add(new Conversion(aggregateType, aggregateIdentifier));
            return aggregateType == TestContext.UuidIdentifiedAggregate.class
                    ? UUID.fromString(aggregateIdentifier)
                    : aggregateIdentifier;
        }
    }

    private record Conversion(Class<?> aggregateType, String aggregateIdentifier) {

    }

    private record CreateUuidIdentifiedAggregateCommand(UUID id) {

    }

    private record LoadUuidIdentifiedAggregateCommand(@TargetAggregateIdentifier UUID id) {

    }

    private record CreateStringIdentifiedAggregateCommand(String id) {

    }

    private record LoadStringIdentifiedAggregateCommand(@TargetAggregateIdentifier String id) {

    }
}
