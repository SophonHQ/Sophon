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

package org.sophonhq.sophon.config;

import com.thoughtworks.xstream.XStream;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.commandhandling.distributed.DistributedCommandBus;
import org.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.common.jpa.EntityManagerProvider;
import org.sophonhq.sophon.common.jpa.SimpleEntityManagerProvider;
import org.sophonhq.sophon.common.lock.PessimisticLockFactory;
import org.sophonhq.sophon.disruptor.commandhandling.DisruptorCommandBus;
import org.sophonhq.sophon.eventhandling.DomainEventData;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.eventsourcing.AggregateFactory;
import org.sophonhq.sophon.eventsourcing.EventSourcingHandler;
import org.sophonhq.sophon.eventsourcing.GenericAggregateFactory;
import org.sophonhq.sophon.eventsourcing.NoSnapshotTriggerDefinition;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import org.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.sophonhq.sophon.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import org.sophonhq.sophon.eventsourcing.snapshotting.RevisionSnapshotFilter;
import org.sophonhq.sophon.eventsourcing.snapshotting.SnapshotFilter;
import org.sophonhq.sophon.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.sophonhq.sophon.messaging.annotation.ParameterResolverFactory;
import org.sophonhq.sophon.modelling.command.AggregateCreationPolicy;
import org.sophonhq.sophon.modelling.command.AggregateIdentifier;
import org.sophonhq.sophon.modelling.command.AggregateIdentifierConverter;
import org.sophonhq.sophon.modelling.command.CreationPolicy;
import org.sophonhq.sophon.modelling.command.CreationPolicyAggregateFactory;
import org.sophonhq.sophon.modelling.command.Repository;
import org.sophonhq.sophon.modelling.command.TargetAggregateIdentifier;
import org.sophonhq.sophon.modelling.command.inspection.AggregateMetaModelFactory;
import org.sophonhq.sophon.modelling.command.inspection.AggregateModel;
import org.sophonhq.sophon.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.sophonhq.sophon.serialization.AnnotationRevisionResolver;
import org.sophonhq.sophon.serialization.Revision;
import org.sophonhq.sophon.serialization.RevisionResolver;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.xml.CompactDriver;
import org.sophonhq.sophon.serialization.xml.XStreamSerializer;
import org.sophonhq.sophon.tracing.TestSpanFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.config.utils.TestSerializer.xStreamSerializer;
import static org.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;

/**
 * Test class to validate the {@link AggregateConfigurer}'s inner workings.
 *
 * @author Steven van Beelen
 */
public class AggregateConfigurerTest {

    private static final AggregateIdentifierConverter LONG_IDENTIFIER_CONVERTER =
            (aggregateType, aggregateIdentifier) -> aggregateType == LongIdentifiedAggregate.class
                    ? Long.valueOf(aggregateIdentifier)
                    : aggregateIdentifier;

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
    void aggregateConfigurationThrowsSophonConfigExceptionWhenCreatingRevisionSnapshotFilterForUndefinedDeclaredType() {
        //noinspection unchecked
        AggregateModel<TestAggregateWithRevision> mockModel = mock(AggregateModel.class);
        when(mockModel.declaredType(TestAggregateWithRevision.class)).thenReturn(Optional.empty());
        AggregateMetaModelFactory mockModelFactory = mock(AggregateMetaModelFactory.class);
        when(mockModelFactory.createModel(eq(TestAggregateWithRevision.class), any())).thenReturn(mockModel);
        when(mockConfiguration.getComponent(eq(AggregateMetaModelFactory.class), any())).thenReturn(mockModelFactory);

        AggregateConfigurer<TestAggregateWithRevision> undefinedDeclaredAggregateTypeTestSubject =
                new AggregateConfigurer<>(TestAggregateWithRevision.class);

        undefinedDeclaredAggregateTypeTestSubject.initialize(mockConfiguration);

        assertThrows(SophonConfigurationException.class, undefinedDeclaredAggregateTypeTestSubject::snapshotFilter);
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
    void jpaMappedConfigurationConvertsIdentifierWithRegisteredAggregateIdentifierConverter() {
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.find(LongIdentifiedAggregate.class, 42L, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(new LongIdentifiedAggregate(42L));

        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(AggregateConfigurer.jpaMappedConfiguration(
                                                        LongIdentifiedAggregate.class
                                                ))
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .registerComponent(
                                                        EntityManagerProvider.class,
                                                        c -> new SimpleEntityManagerProvider(entityManager)
                                                )
                                                .registerComponent(AggregateIdentifierConverter.class,
                                                                   c -> LONG_IDENTIFIER_CONVERTER)
                                                .start();

        Long result = config.commandGateway().sendAndWait(new LoadLongIdentifiedAggregateCommand(42L));

        assertEquals(42L, result);
        verify(entityManager).find(LongIdentifiedAggregate.class, 42L, LockModeType.PESSIMISTIC_WRITE);

        config.shutdown();
    }

    @Test
    void jpaMappedConfigurationWithEntityManagerProviderConvertsIdentifierWithRegisteredAggregateIdentifierConverter() {
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.find(LongIdentifiedAggregate.class, 42L, LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(new LongIdentifiedAggregate(42L));

        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(AggregateConfigurer.jpaMappedConfiguration(
                                                        LongIdentifiedAggregate.class,
                                                        new SimpleEntityManagerProvider(entityManager)
                                                ))
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .registerComponent(AggregateIdentifierConverter.class,
                                                                   c -> LONG_IDENTIFIER_CONVERTER)
                                                .start();

        Long result = config.commandGateway().sendAndWait(new LoadLongIdentifiedAggregateCommand(42L));

        assertEquals(42L, result);
        verify(entityManager).find(LongIdentifiedAggregate.class, 42L, LockModeType.PESSIMISTIC_WRITE);

        config.shutdown();
    }

    @Test
    void jpaMappedConfigurationPassesIdentifierUnchangedWithoutAggregateIdentifierConverter() {
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.find(LongIdentifiedAggregate.class, "42", LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(new LongIdentifiedAggregate(42L));

        Configuration config = DefaultConfigurer.defaultConfiguration()
                                                .configureAggregate(AggregateConfigurer.jpaMappedConfiguration(
                                                        LongIdentifiedAggregate.class,
                                                        new SimpleEntityManagerProvider(entityManager)
                                                ))
                                                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                                .start();

        Long result = config.commandGateway().sendAndWait(new LoadLongIdentifiedAggregateCommand(42L));

        assertEquals(42L, result);
        verify(entityManager).find(LongIdentifiedAggregate.class, "42", LockModeType.PESSIMISTIC_WRITE);

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

    private static class LoadLongIdentifiedAggregateCommand {

        @TargetAggregateIdentifier
        private final Long id;

        private LoadLongIdentifiedAggregateCommand(Long id) {
            this.id = id;
        }
    }

    private static class LongIdentifiedAggregate {

        @AggregateIdentifier
        private Long id;

        LongIdentifiedAggregate() {
            // No-op constructor
        }

        LongIdentifiedAggregate(Long id) {
            this.id = id;
        }

        @CommandHandler
        public Long handle(LoadLongIdentifiedAggregateCommand cmd) {
            return id;
        }
    }
}
