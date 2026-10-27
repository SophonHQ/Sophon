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

import io.github.sophonhq.sophon.commandhandling.CommandHandler;
import io.github.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import io.github.sophonhq.sophon.common.transaction.TransactionManager;
import io.github.sophonhq.sophon.eventhandling.EventHandler;
import io.github.sophonhq.sophon.eventsourcing.AggregateSnapshotter;
import io.github.sophonhq.sophon.eventsourcing.EventCountSnapshotTriggerDefinition;
import io.github.sophonhq.sophon.eventsourcing.GenericAggregateFactory;
import io.github.sophonhq.sophon.eventsourcing.SnapshotTriggerDefinition;
import io.github.sophonhq.sophon.eventsourcing.Snapshotter;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import io.github.sophonhq.sophon.eventsourcing.snapshotting.SnapshotFilter;
import io.github.sophonhq.sophon.eventsourcing.utils.TestSerializer;
import io.github.sophonhq.sophon.modelling.command.AggregateIdentifier;
import io.github.sophonhq.sophon.modelling.command.AggregateLifecycle;
import io.github.sophonhq.sophon.modelling.command.TargetAggregateIdentifier;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.spring.stereotype.Aggregate;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating auto configured snapshotting logic.
 *
 * @author Steven van Beelen
 */
class JpaEventStoreAutoConfigurationWithSnapshottingTest {

    private static final String AGGREGATE_ID = "some-aggregate";

    private ApplicationContextRunner testContext;

    @BeforeEach
    void setUp() {
        TestContext.SNAPSHOT_FILTER_INVOKED.set(false);
        testContext = new ApplicationContextRunner()
                .withUserConfiguration(TestContext.class);
    }

    @Test
    void snapshotterAndSnapshotTriggerDefinitionAreInvoked() {
        testContext.run(context -> {
            SnapshotTriggerDefinition snapshotTriggerDefinition =
                    context.getBean(SnapshotTriggerDefinition.class);
            assertNotNull(snapshotTriggerDefinition);
            Snapshotter snapshotter = context.getBean(Snapshotter.class);
            assertNotNull(snapshotter);
            assertNotNull(context.getBean(JpaEventStorageEngine.class));

            CommandGateway commandGateway = context.getBean(CommandGateway.class);
            commandGateway.send(new TestContext.CreateCommand(AGGREGATE_ID));
            commandGateway.send(new TestContext.UpdateCommand(AGGREGATE_ID));

            verify(snapshotTriggerDefinition, atLeastOnce()).prepareTrigger(TestContext.TestAggregate.class);
            verify(snapshotter, atLeastOnce()).scheduleSnapshot(TestContext.TestAggregate.class, AGGREGATE_ID);
        });
    }

    @Test
    void snapshotFilterIsInvoked() {
        testContext.run(context -> {
            SnapshotFilter snapshotFilter = context.getBean(SnapshotFilter.class);
            assertNotNull(snapshotFilter);
            assertNotNull(context.getBean(JpaEventStorageEngine.class));

            CommandGateway commandGateway = context.getBean(CommandGateway.class);
            commandGateway.send(new TestContext.CreateCommand(AGGREGATE_ID));
            commandGateway.send(new TestContext.UpdateCommand(AGGREGATE_ID));

            EventStore eventStore = context.getBean(EventStore.class);
            eventStore.readEvents(AGGREGATE_ID);

            assertTrue(TestContext.SNAPSHOT_FILTER_INVOKED.get());
        });
    }

    @Configuration
    @EnableAutoConfiguration
    protected static class TestContext {

        protected static final AtomicBoolean SNAPSHOT_FILTER_INVOKED = new AtomicBoolean(false);

        @Bean
        public Snapshotter snapshotter(EventStore eventStore, TransactionManager transactionManager) {
            return spy(AggregateSnapshotter.builder()
                                           .aggregateFactories(new GenericAggregateFactory<>(TestAggregate.class))
                                           .eventStore(eventStore)
                                           .transactionManager(transactionManager)
                                           .build());
        }

        @Bean
        public SnapshotTriggerDefinition snapshotTriggerDefinition(Snapshotter snapshotter) {
            return spy(new EventCountSnapshotTriggerDefinition(snapshotter, 1));
        }

        @Bean
        public SnapshotFilter snapshotFilter() {
            return snapshotData -> {
                SNAPSHOT_FILTER_INVOKED.set(true);
                return true;
            };
        }

        @Bean
        @Primary
        public Serializer serializer() {
            return TestSerializer.xStreamSerializer();
        }

        public static class CreateCommand {

            @TargetAggregateIdentifier
            private final String aggregateIdentifier;

            public CreateCommand(String aggregateIdentifier) {
                this.aggregateIdentifier = aggregateIdentifier;
            }

            public String getAggregateIdentifier() {
                return aggregateIdentifier;
            }
        }

        public static class CreatedEvent {

            private final String aggregateIdentifier;

            public CreatedEvent(String aggregateIdentifier) {
                this.aggregateIdentifier = aggregateIdentifier;
            }

            public String getAggregateIdentifier() {
                return aggregateIdentifier;
            }
        }

        public static class UpdateCommand {

            @TargetAggregateIdentifier
            private final String aggregateIdentifier;

            public UpdateCommand(String aggregateIdentifier) {
                this.aggregateIdentifier = aggregateIdentifier;
            }

            public String getAggregateIdentifier() {
                return aggregateIdentifier;
            }
        }


        @SuppressWarnings("unused")
        public static class UpdatedEvent {

            private final String aggregateIdentifier;

            public UpdatedEvent(String aggregateIdentifier) {
                this.aggregateIdentifier = aggregateIdentifier;
            }

            public String getAggregateIdentifier() {
                return aggregateIdentifier;
            }
        }

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        @Aggregate(snapshotTriggerDefinition = "snapshotTriggerDefinition", snapshotFilter = "snapshotFilter")
        public static class TestAggregate {

            @AggregateIdentifier
            private String aggregateIdentifier;

            public TestAggregate() {
                // Required default constructor
            }

            @CommandHandler
            public TestAggregate(CreateCommand command) {
                AggregateLifecycle.apply(new CreatedEvent(command.getAggregateIdentifier()));
            }

            @CommandHandler
            public void handle(UpdateCommand command) {
                AggregateLifecycle.apply(new UpdatedEvent(command.getAggregateIdentifier()));
            }

            @EventHandler
            public void on(CreatedEvent event) {
                aggregateIdentifier = event.getAggregateIdentifier();
            }
        }
    }
}
