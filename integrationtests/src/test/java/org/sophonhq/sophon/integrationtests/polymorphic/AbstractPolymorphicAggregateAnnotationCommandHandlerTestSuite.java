/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.sophonhq.sophon.integrationtests.polymorphic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandBus;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.commandhandling.NoHandlerForCommandException;
import org.sophonhq.sophon.commandhandling.SimpleCommandBus;
import org.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import org.sophonhq.sophon.commandhandling.gateway.DefaultCommandGateway;
import org.sophonhq.sophon.common.transaction.Transaction;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import org.sophonhq.sophon.modelling.command.AggregateAnnotationCommandHandler;
import org.sophonhq.sophon.modelling.command.Repository;
import org.sophonhq.sophon.modelling.command.inspection.AggregateModel;
import org.sophonhq.sophon.modelling.command.inspection.AggregateModellingException;
import org.sophonhq.sophon.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests aggregate polymorphism.
 *
 * @author Milan Savic
 */
public abstract class AbstractPolymorphicAggregateAnnotationCommandHandlerTestSuite {

    private CommandBus commandBus;
    private CommandGateway commandGateway;
    private Repository<ParentAggregate> repository;
    private EntityManager entityManager;
    private TransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        Map<String, String> persistenceProperties = new HashMap<>(3);
        persistenceProperties.put("hibernate.connection.url", "jdbc:h2:mem:sophontest;DB_CLOSE_DELAY=-1");
        persistenceProperties.put("hibernate.connection.username", "sa");
        persistenceProperties.put("hibernate.hbm2ddl.auto", "create-drop");
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("polymorphic", persistenceProperties);
        entityManager = emf.createEntityManager();

        transactionManager = new EntityManagerTransactionManager(entityManager);

        commandBus = SimpleCommandBus.builder()
                                     .transactionManager(transactionManager)
                                     .build();
        commandGateway = DefaultCommandGateway.builder()
                                              .commandBus(commandBus)
                                              .build();

        Set<Class<? extends ParentAggregate>> subtypes =
                new HashSet<>(asList(Child1Aggregate.class, Child2Aggregate.class));
        AggregateModel<ParentAggregate> model =
                new AnnotatedAggregateMetaModelFactory().createModel(ParentAggregate.class, subtypes);

        repository = repository(ParentAggregate.class, subtypes, entityManager);

        AggregateAnnotationCommandHandler<ParentAggregate> ch =
                AggregateAnnotationCommandHandler.<ParentAggregate>builder()
                                                 .aggregateType(ParentAggregate.class)
                                                 .aggregateModel(model)
                                                 .repository(repository)
                                                 .build();
        //noinspection resource
        ch.subscribe(commandBus);
    }

    /**
     * Constructs a polymorphic {@link Repository} for the given root {@code aggregateType}.
     *
     * @param aggregateType The root aggregate type for the polymorphic {@link Repository}.
     * @param subTypes      The subtypes of the given {@code aggregateType}, making the model supported by the
     *                      {@link Repository} polymorphic/
     * @param entityManager The entity manager required for state-stored polymorphic aggregates.
     * @param <T>           The root type of the polymorphic aggregate.
     * @return A polymorphic {@link Repository} for the given root {@code aggregateType}.
     */
    public abstract <T> Repository<T> repository(Class<T> aggregateType,
                                                 Set<Class<? extends T>> subTypes,
                                                 EntityManager entityManager);

    @AfterEach
    void tearDown() {
        entityManager.close();
    }

    @Test
    void createChild1() {
        String id = commandGateway.sendAndWait(new CreateChild1Command("123"));
        String result = commandGateway.sendAndWait(new CommonCommand(id));
        assertThat(result).isEqualTo("Child1Aggregate123");
    }

    @Test
    void createChild2() {
        String id = commandGateway.sendAndWait(new CreateChild2Command("123"));
        String result = commandGateway.sendAndWait(new CommonCommand(id));
        assertThat(result).isEqualTo("Child2Aggregate123");
    }

    @Test
    void factoryCreate() {
        String id = commandGateway.sendAndWait(new CreateChildFactoryCommand("123", 1));
        String result = commandGateway.sendAndWait(new CommonCommand(id));
        assertThat(result).isEqualTo("Child1Aggregate123");

        id = commandGateway.sendAndWait(new CreateChildFactoryCommand("456", 2));
        result = commandGateway.sendAndWait(new CommonCommand(id));
        assertThat(result).isEqualTo("Child2Aggregate456");
    }

    @Test
    void child1OnlyCommandOnAggregate2() {
        String c1Id = commandGateway.sendAndWait(new CreateChild1Command("123"));
        String c2Id = commandGateway.sendAndWait(new CreateChild2Command("456"));
        String result = commandGateway.sendAndWait(new Child1OnlyCommand(c1Id));
        assertThat(result).isEqualTo("Child1Aggregate123");
        assertThatThrownBy(() -> commandGateway.sendAndWait(new Child1OnlyCommand(c2Id)))
                .isInstanceOf(NoHandlerForCommandException.class);
    }

    @Test
    void parentEventAppliedFromChild() {
        String id = commandGateway.sendAndWait(new CreateChild1Command("123"));
        commandGateway.sendAndWait(new FireParentEventCommand(id));
        assertAggregateState(id, "parent123");
    }

    @Test
    void childEventAppliedFromParent() {
        String c1Id = commandGateway.sendAndWait(new CreateChild1Command("123"));
        String c2Id = commandGateway.sendAndWait(new CreateChild2Command("456"));
        commandGateway.sendAndWait(new FireChildEventCommand(c1Id));
        commandGateway.sendAndWait(new FireChildEventCommand(c2Id));
        assertAggregateState(c1Id, "child1123");
        assertAggregateState(c2Id, "child2456");
    }

    @Test
    void commandInterceptedByParentHandledByChild() {
        String c1Id = commandGateway.sendAndWait(new CreateChild1Command("123"));
        String c2Id = commandGateway.sendAndWait(new CreateChild2Command("456"));
        String child1Result = commandGateway.sendAndWait(new InterceptedByParentCommand(c1Id, "state"));
        String child2Result = commandGateway.sendAndWait(new InterceptedByParentCommand(c2Id, "state"));
        assertThat(child1Result).isEqualTo("stateInterceptedByParentHandledByChild1");
        assertThat(child2Result).isEqualTo("stateInterceptedByParentHandledByChild2");
    }

    @Test
    void commandInterceptedByChildHandledByParent() {
        String c1Id = commandGateway.sendAndWait(new CreateChild1Command("123"));
        String c2Id = commandGateway.sendAndWait(new CreateChild2Command("456"));
        String child1Result = commandGateway.sendAndWait(new InterceptedByChildCommand(c1Id, "state"));
        String child2Result = commandGateway.sendAndWait(new InterceptedByChildCommand(c2Id, "state"));
        assertThat(child1Result).isEqualTo("stateInterceptedByChild1HandledByParent");
        assertThat(child2Result).isEqualTo("stateInterceptedByChild2HandledByParent");
    }

    @Test
    void abstractCommandHandler() {
        String c1Id = commandGateway.sendAndWait(new CreateChild1Command("123"));
        String c2Id = commandGateway.sendAndWait(new CreateChild2Command("456"));
        String child1Result = commandGateway.sendAndWait(new AbstractCommandHandlerCommand(c1Id));
        String child2Result = commandGateway.sendAndWait(new AbstractCommandHandlerCommand(c2Id));
        assertThat(child1Result).isEqualTo("handledByChild1");
        assertThat(child2Result).isEqualTo("handledByChild2");
    }

    @Test
    void inspectionOfAbstractAggregateWithCommandHandlerOnConstructor() {
        assertThatThrownBy(() -> new AnnotatedAggregateMetaModelFactory()
                .createModel(AbstractAggregateWithCommandHandlerConstructor.class))
                .isInstanceOf(AggregateModellingException.class);
    }

    @Test
    void inspectionOfPolymorphicAggregateWithSameCreationalCommandHandlers() {
        assertThatThrownBy(() -> new AnnotatedAggregateMetaModelFactory()
                .createModel(A.class, new HashSet<>(asList(B.class, C.class))))
                .isInstanceOf(AggregateModellingException.class);
    }

    @Test
    void creationOfPolymorphicAggregate() {
        AggregateModel<SimpleAggregate> model = new AnnotatedAggregateMetaModelFactory()
                .createModel(SimpleAggregate.class);

        Repository<SimpleAggregate> repository =
                repository(SimpleAggregate.class, Collections.emptySet(), entityManager);

        AggregateAnnotationCommandHandler<SimpleAggregate> ch =
                AggregateAnnotationCommandHandler.<SimpleAggregate>builder()
                                                 .aggregateType(SimpleAggregate.class)
                                                 .aggregateModel(model)
                                                 .repository(repository)
                                                 .build();
        //noinspection resource
        ch.subscribe(commandBus);

        String simpleAggregateId = "id";
        String child1AggregateId = "child1" + simpleAggregateId;
        commandGateway.sendAndWait(new CreateSimpleAggregateCommand(simpleAggregateId));
        String result = commandGateway.sendAndWait(new CommonCommand(child1AggregateId));
        assertThat(result).isEqualTo("Child1Aggregate" + child1AggregateId);
    }

    private void assertAggregateState(String aggregateId, String expectedState) {
        DefaultUnitOfWork<Message<?>> uow = DefaultUnitOfWork.startAndGet(null);
        uow.attachTransaction(transactionManager);
        String state = uow.executeWithResult(() -> {
            AtomicReference<String> rv = new AtomicReference<>();
            repository.load(aggregateId).execute(a -> rv.set(a.getState()));
            return rv.get();
        }).getPayload();
        assertThat(state).isEqualTo(expectedState);
    }

    private static abstract class AbstractAggregateWithCommandHandlerConstructor {

        @CommandHandler
        public AbstractAggregateWithCommandHandlerConstructor(String cmd) {
        }
    }

    private static abstract class A {

    }

    private static class B extends A {

        @CommandHandler
        public B(String cmd) {
        }
    }

    private static class C extends A {

        @CommandHandler
        public C(String cmd) {
        }
    }

    private static class EntityManagerTransactionManager implements TransactionManager {

        private final EntityManager em;

        public EntityManagerTransactionManager(EntityManager em) {
            this.em = em;
        }

        @Override
        public Transaction startTransaction() {
            EntityTransaction tx = em.getTransaction();
            if (tx.isActive()) {
                return new Transaction() {
                    @Override
                    public void commit() {
                    }

                    @Override
                    public void rollback() {
                    }
                };
            }
            tx.begin();
            return new Transaction() {
                @Override
                public void commit() {
                    tx.commit();
                }

                @Override
                public void rollback() {
                    tx.rollback();
                }
            };
        }
    }
}
