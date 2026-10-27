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

package io.github.sophonhq.sophon.config;

import com.thoughtworks.xstream.XStream;
import io.github.sophonhq.sophon.commandhandling.CommandHandler;
import io.github.sophonhq.sophon.commandhandling.distributed.DistributedCommandBus;
import io.github.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import io.github.sophonhq.sophon.common.AxonConfigurationException;
import io.github.sophonhq.sophon.common.jpa.EntityManagerProvider;
import io.github.sophonhq.sophon.common.jpa.SimpleEntityManagerProvider;
import io.github.sophonhq.sophon.common.lock.PessimisticLockFactory;
import io.github.sophonhq.sophon.disruptor.commandhandling.DisruptorCommandBus;
import io.github.sophonhq.sophon.eventhandling.DomainEventData;
import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import io.github.sophonhq.sophon.eventsourcing.AggregateFactory;
import io.github.sophonhq.sophon.eventsourcing.EventSourcingHandler;
import io.github.sophonhq.sophon.eventsourcing.GenericAggregateFactory;
import io.github.sophonhq.sophon.eventsourcing.NoSnapshotTriggerDefinition;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import io.github.sophonhq.sophon.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import io.github.sophonhq.sophon.eventsourcing.snapshotting.RevisionSnapshotFilter;
import io.github.sophonhq.sophon.eventsourcing.snapshotting.SnapshotFilter;
import io.github.sophonhq.sophon.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import io.github.sophonhq.sophon.messaging.annotation.ParameterResolverFactory;
import io.github.sophonhq.sophon.modelling.command.AggregateCreationPolicy;
import io.github.sophonhq.sophon.modelling.command.AggregateIdentifier;
import io.github.sophonhq.sophon.modelling.command.CreationPolicy;
import io.github.sophonhq.sophon.modelling.command.CreationPolicyAggregateFactory;
import io.github.sophonhq.sophon.modelling.command.Repository;
import io.github.sophonhq.sophon.modelling.command.TargetAggregateIdentifier;
import io.github.sophonhq.sophon.modelling.command.inspection.AggregateMetaModelFactory;
import io.github.sophonhq.sophon.modelling.command.inspection.AggregateModel;
import io.github.sophonhq.sophon.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import io.github.sophonhq.sophon.serialization.AnnotationRevisionResolver;
import io.github.sophonhq.sophon.serialization.Revision;
import io.github.sophonhq.sophon.serialization.RevisionResolver;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.serialization.xml.CompactDriver;
import io.github.sophonhq.sophon.serialization.xml.XStreamSerializer;
import io.github.sophonhq.sophon.tracing.TestSpanFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.sophonhq.sophon.config.utils.TestSerializer.xStreamSerializer;
import static io.github.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class to validate the {@link AggregateConfigurer}'s inner workings.
 *
 * @author Steven van Beelen
 */
public class AggregateConfigurerTest {

    private Configuration mockConfiguration;

    private EventStore testEventStore;
    private ParameterResolverFactory testParameterResolverFactory;
    private RevisionResolver revisionResolver = Mockito.mock(AnnotationRevisionResolver.class);
    private AggregateConfigurer<TestAggregate> testSubject;

    @BeforeEach
    public void setUp() {
        mockConfiguration = mock(Configuration.class);

        testEventStore = mock(EventStore.class);
        when(mockConfiguration.eventBus()).thenReturn(testEventStore);
        when(mockConfiguration.eventStore()).thenReturn(testEventStore);

        testParameterResolverFactory = mock(ParameterResolverFactory.class);
        when(mockConfiguration.parameterResolverFactory()).thenReturn(testParameterResolverFactory);
        when(mockConfiguration.getComponent(eq(AggregateMetaModelFactory.class), any()))
                .thenReturn(new AnnotatedAggregateMetaModelFactory(
                        testParameterResolverFactory, new AnnotatedMessageHandlingMemberDefinition()
                ));

        when(revisionResolver.revisionOf(TestAggregate.class)).thenReturn("1.0");

        testSubject = new AggregateConfigurer<>(TestAggregate.class);
    }

    @Test
    void configuredDisruptorCommandBusCreatesTheRepository() {
        //noinspection unchecked
        Repository<Object> expectedRepository = mock(Repository.class);

        DisruptorCommandBus disruptorCommandBus = mock(DisruptorCommandBus.class);
        when(disruptorCommandBus.createRepository(any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedRepository);
        when(mockConfiguration.commandBus()).thenReturn(disruptorCommandBus);

        testSubject.initialize(mockConfiguration);

        Repository<TestAggregate> resultRepository = testSubject.repository();

        assertEquals(expectedRepository, resultRepository);
        //noinspection unchecked
        verify(disruptorCommandBus).createRepository(
                eq(testEventStore), isA(GenericAggregateFactory.class), eq(NoSnapshotTriggerDefinition.INSTANCE),
                eq(testParameterResolverFactory), any(), any()
        );
    }

    @Test
    void configuredDisruptorCommandBusAsLocalSegmentCreatesTheRepository() {
        //noinspection unchecked
        Repository<Object> expectedRepository = mock(Repository.class);

        DisruptorCommandBus disruptorCommandBus = mock(DisruptorCommandBus.class);
        when(disruptorCommandBus.createRepository(any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedRepository);
        DistributedCommandBus distributedCommandBusImplementation = mock(DistributedCommandBus.class);
        when(distributedCommandBusImplementation.localSegment()).thenReturn(disruptorCommandBus);
        when(mockConfiguration.commandBus()).thenReturn(distributedCommandBusImplementation);

        testSubject.initialize(mockConfiguration);

        Repository<TestAggregate> resultRepository = testSubject.repository();

        assertEquals(expectedRepository, resultRepository);
        //noinspection unchecked
        verify(disruptorCommandBus).createRepository(
                eq(testEventStore), isA(GenericAggregateFactory.class), eq(NoSnapshotTriggerDefinition.INSTANCE),
                eq(testParameterResolverFactory), any(), any()
        );
    }

    @Test
    void polymorphicConfig() {
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(A.class)
                                                                        .withSubtypes(B.class);

        Configuration configuration = DefaultConfigurer.defaultConfiguration()
                                                       .configureAggregate(aggregateConfigurer)
                                                       .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                       .buildConfiguration();
        configuration.start();

        CommandGateway commandGateway = configuration.commandGateway();
        String aggregateAId = commandGateway.sendAndWait(new CreateACommand("123"));
        String aggregateBId = commandGateway.sendAndWait(new CreateBCommand("456"));
        String result1 = commandGateway.sendAndWait(new DoSomethingCommand(aggregateAId));
        String result2 = commandGateway.sendAndWait(new DoSomethingCommand(aggregateBId));
        String result3 = commandGateway.sendAndWait(new BSpecificCommand(aggregateBId));
        assertEquals("A123", result1);
        assertEquals("B456", result2);
        assertEquals("bSpecific456", result3);

        configuration.shutdown();
    }

    @Test
    void aggregateFactoryConfiguration() {
        AggregateFactory<TestAggregate> expectedAggregateFactory = new GenericAggregateFactory<>(TestAggregate.class);

        testSubject.configureAggregateFactory(configuration -> expectedAggregateFactory);

        assertEquals(expectedAggregateFactory, testSubject.aggregateFactory());
    }

    @Test
    void snapshotFilterConfiguration() {
        SnapshotFilter testFilter = snapshotData -> true;

        testSubject.configureSnapshotFilter(configuration -> testFilter);

        assertEquals(testFilter, testSubject.snapshotFilter());
    }

    @Test
    void aggregateConfigurationCreatesRevisionSnapshotFilterForAggregateWithRevision() {
        DomainEventMessage<TestAggregateWithRevision> snapshotEvent = new GenericDomainEventMessage<>(
                TestAggregateWithRevision.class.getName(), "some-aggregate-id", 0, new TestAggregateWithRevision()
        );
        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, xStreamSerializer());

        AggregateConfigurer<TestAggregateWithRevision> revisionAggregateConfigurerTestSubject =
                new AggregateConfigurer<>(TestAggregateWithRevision.class);

        revisionAggregateConfigurerTestSubject.initialize(mockConfiguration);

        SnapshotFilter result = revisionAggregateConfigurerTestSubject.snapshotFilter();

        assertTrue(result instanceof RevisionSnapshotFilter);
        assertTrue(result.allow(testDomainEventData));
    }

    @Test
    void aggregateConfigurationThrowsAxonConfigExceptionWhenCreatingRevisionSnapshotFilterForUndefinedDeclaredType() {
        //noinspection unchecked
        AggregateModel<TestAggregateWithRevision> mockModel = mock(AggregateModel.class);
        when(mockModel.declaredType(TestAggregateWithRevision.class)).thenReturn(Optional.empty());
        AggregateMetaModelFactory mockModelFactory = mock(AggregateMetaModelFactory.class);
        when(mockModelFactory.createModel(eq(TestAggregateWithRevision.class), any())).thenReturn(mockModel);
        when(mockConfiguration.getComponent(eq(AggregateMetaModelFactory.class), any())).thenReturn(mockModelFactory);

        AggregateConfigurer<TestAggregateWithRevision> undefinedDeclaredAggregateTypeTestSubject =
                new AggregateConfigurer<>(TestAggregateWithRevision.class);

        undefinedDeclaredAggregateTypeTestSubject.initialize(mockConfiguration);

        assertThrows(AxonConfigurationException.class, undefinedDeclaredAggregateTypeTestSubject::snapshotFilter);
    }

    @Test
    void configureLockFactoryForEventSourcedAggregate() {
        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(A.class)
                                                                        .configureLockFactory(config -> lockFactory);

        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));
        verify(lockFactory).obtainLock(testAggregateIdentifier);

        config.shutdown();
    }

    @Test
    void configureSpanFactoryForEventSourcedAggregate() {
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.defaultConfiguration(A.class);

        TestSpanFactory testSpanFactory = new TestSpanFactory();
        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .configureSpanFactory(c -> testSpanFactory)
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));

        testSpanFactory.verifySpanCompleted("CommandBus.dispatchCommand");

        config.shutdown();
    }

    @Test
    void configureLockFactoryForStateStoredAggregateWithConfiguredEntityManagerProviderComponent() {
        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.jpaMappedConfiguration(A.class)
                                                                        .configureLockFactory(config -> lockFactory);

        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .registerComponent(
                                                        EntityManagerProvider.class,
                                                        c -> new SimpleEntityManagerProvider(mock(EntityManager.class))
                                                )
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));

        config.shutdown();
    }

    @Test
    void configureSpanFactoryForStateStoredAggregateWithConfiguredEntityManagerProviderComponent() {
        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
        AggregateConfigurer<A> aggregateConfigurer = AggregateConfigurer.jpaMappedConfiguration(A.class)
                                                                        .configureLockFactory(config -> lockFactory);

        TestSpanFactory testSpanFactory = new TestSpanFactory();
        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .registerComponent(
                                                        EntityManagerProvider.class,
                                                        c -> new SimpleEntityManagerProvider(mock(EntityManager.class))
                                                )
                                                .configureSpanFactory(c -> testSpanFactory)
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));
        testSpanFactory.verifySpanCompleted("CommandBus.dispatchCommand");

        config.shutdown();
    }

    @Test
    void configureLockFactoryForStateStoredAggregate() {
        PessimisticLockFactory lockFactory = spy(PessimisticLockFactory.usingDefaults());
        AggregateConfigurer<A> aggregateConfigurer =
                AggregateConfigurer.jpaMappedConfiguration(
                                           A.class, new SimpleEntityManagerProvider(mock(EntityManager.class))
                                   )
                                   .configureLockFactory(config -> lockFactory);

        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));
        verify(lockFactory).obtainLock(testAggregateIdentifier);

        config.shutdown();
    }

    @Test
    void configureSpanFactoryForStateStoredAggregate() {
        AggregateConfigurer<A> aggregateConfigurer =
                AggregateConfigurer.jpaMappedConfiguration(
                        A.class, new SimpleEntityManagerProvider(mock(EntityManager.class))
                );

        TestSpanFactory testSpanFactory = new TestSpanFactory();
        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(aggregateConfigurer)
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .configureSpanFactory(c -> testSpanFactory)
                                                .buildConfiguration();
        config.start();

        CommandGateway commandGateway = config.commandGateway();
        String testAggregateIdentifier = "123";
        commandGateway.sendAndWait(new CreateACommand(testAggregateIdentifier));

        testSpanFactory.verifySpanCompleted("CommandBus.dispatchCommand");

        config.shutdown();
    }

    @Test
    void nullRevisionEventAndNullRevisionAggregateAllowed() {
        DomainEventMessage<TestAggregate> snapshotEvent = new GenericDomainEventMessage<>(
                TestAggregate.class.getSimpleName(), "some-aggregate-id", 0, new TestAggregate());

        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, xStreamSerializer());

        AggregateConfigurer<TestAggregate> revisionAggregateConfigurerTestSubject =
                new AggregateConfigurer<>(TestAggregate.class);

        revisionAggregateConfigurerTestSubject.initialize(mockConfiguration);

        SnapshotFilter result = revisionAggregateConfigurerTestSubject.snapshotFilter();

        assertTrue(result instanceof RevisionSnapshotFilter);
        assertTrue(result.allow(testDomainEventData));
    }

    @Test
    void nonNullEventRevisionAndNullAggregateRevisionNotAllowed() {
        DomainEventMessage<TestAggregate> snapshotEvent = new GenericDomainEventMessage<>(
                TestAggregate.class.getSimpleName(), "some-aggregate-id", 0, new TestAggregate()
        );
        Serializer serializer = XStreamSerializer.builder()
                                                 .xStream(new XStream(new CompactDriver()))
                                                 .revisionResolver(revisionResolver)
                                                 .build();

        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);

        AggregateConfigurer<TestAggregate> revisionAggregateConfigurerTestSubject =
                new AggregateConfigurer<>(TestAggregate.class);

        revisionAggregateConfigurerTestSubject.initialize(mockConfiguration);

        SnapshotFilter result = revisionAggregateConfigurerTestSubject.snapshotFilter();

        assertTrue(result instanceof RevisionSnapshotFilter);
        assertFalse(result.allow(testDomainEventData));
    }

    @Test
    void configuredCreationPolicyAggregateFactoryIsUsedDuringAggregateConstruction() {
        AtomicBoolean counter = new AtomicBoolean(false);

        CreationPolicyAggregateFactory<A> testFactory = identifier -> {
            counter.set(true);
            return new A(identifier != null ? identifier.toString() : "null");
        };

        AggregateConfigurer<A> testAggregateConfigurer =
                AggregateConfigurer.defaultConfiguration(A.class)
                                   .configureCreationPolicyAggregateFactory(c -> testFactory);

        Configuration testConfig = DefaultConfigurer.defaultConfiguration()
                                                    .configureAggregate(testAggregateConfigurer)
                                                    .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                    .start();

        CreationPolicyAggregateFactory<A> resultFactory = testAggregateConfigurer.creationPolicyAggregateFactory();
        assertEquals(testFactory, resultFactory);

        testConfig.commandGateway()
                  .sendAndWait(new AlwaysCreationPolicyCommand("some-id"));
        assertTrue(counter.get());
    }

    private static class TestAggregate {

        TestAggregate() {
            // No-op constructor
        }
    }

    @Revision("some-revision")
    private static class TestAggregateWithRevision {

        TestAggregateWithRevision() {
            // No-op constructor
        }
    }

    private static class CreateACommand {

        private final String id;

        private CreateACommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class AlwaysCreationPolicyCommand {

        @TargetAggregateIdentifier
        private final String id;

        private AlwaysCreationPolicyCommand(String id) {
            this.id = id;
        }
    }

    private static class ACreatedEvent {

        private final String id;

        private ACreatedEvent(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class CreateBCommand {

        private final String id;

        private CreateBCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class DoSomethingCommand {

        @TargetAggregateIdentifier
        private final String id;

        private DoSomethingCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class BSpecificCommand {

        @TargetAggregateIdentifier
        private final String id;

        private BSpecificCommand(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    private static class A {

        @AggregateIdentifier
        protected String id;

        public A() {
        }

        @CommandHandler
        public A(CreateACommand cmd) {
            this(cmd.getId());
        }

        public A(String id) {
            apply(new ACreatedEvent(id));
        }

        @CreationPolicy(AggregateCreationPolicy.ALWAYS)
        @CommandHandler
        public void handle(AlwaysCreationPolicyCommand command) {
            apply(new ACreatedEvent(id));
        }

        @EventSourcingHandler
        public void on(ACreatedEvent evt) {
            this.id = evt.getId();
        }

        @CommandHandler
        public String handle(DoSomethingCommand cmd) {
            return this.getClass().getSimpleName() + cmd.getId();
        }

        public String getId() {
            return id;
        }
    }

    private static class B extends A {

        public B() {
        }

        @CommandHandler
        public B(CreateBCommand cmd) {
            super(cmd.getId());
        }

        @CommandHandler
        public String handle(BSpecificCommand cmd) {
            return "bSpecific" + cmd.getId();
        }
    }
}
