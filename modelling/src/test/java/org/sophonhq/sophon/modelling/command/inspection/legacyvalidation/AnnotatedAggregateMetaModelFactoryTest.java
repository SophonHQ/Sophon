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

package org.sophonhq.sophon.modelling.command.inspection.legacyvalidation;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.commandhandling.CommandMessageHandlingMember;
import org.sophonhq.sophon.commandhandling.GenericCommandMessage;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.messaging.annotation.MessageHandlingMember;
import org.sophonhq.sophon.modelling.command.AggregateIdentifier;
import org.sophonhq.sophon.modelling.command.AggregateMember;
import org.sophonhq.sophon.modelling.command.AggregateRoot;
import org.sophonhq.sophon.modelling.command.AggregateVersion;
import org.sophonhq.sophon.modelling.command.inspection.AggregateModel;
import org.sophonhq.sophon.modelling.command.inspection.AggregateModellingException;
import org.sophonhq.sophon.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;

import javax.persistence.Id;
import java.lang.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sophonhq.sophon.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;

/**
 * Test case to validate all operations performed by the {@link AnnotatedAggregateMetaModelFactory}.
 *
 * @author Allard Buijze
 */
class AnnotatedAggregateMetaModelFactoryTest {

    @Test
    void detectAllAnnotatedHandlers() throws Exception {
        AggregateModel<SomeAnnotatedHandlers> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeAnnotatedHandlers.class);

        CommandMessage<?> message = asCommandMessage("ok");
        assertThat(getHandler(inspector, message).handle(message, new SomeAnnotatedHandlers())).isEqualTo(true);
        assertThat(getHandler(inspector, message).handle(asCommandMessage("ko"), new SomeAnnotatedHandlers()))
                .isEqualTo(false);
    }

    @Test
    void detectAllAnnotatedHandlersInHierarchy() throws Exception {
        AggregateModel<SomeSubclass> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeSubclass.class);

        SomeSubclass target = new SomeSubclass();
        CommandMessage<?> message = asCommandMessage("sub");
        assertThat(getHandler(inspector, message).handle(message, target)).isEqualTo(true);
        assertThat(getHandler(inspector, message).handle(asCommandMessage("ok"), target)).isEqualTo(false);
    }

    @Test
    void detectFactoryMethodHandler() {
        AggregateModel<SomeAnnotatedFactoryMethodClass> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeAnnotatedFactoryMethodClass.class);

        CommandMessage<?> message = asCommandMessage("string");
        final MessageHandlingMember<? super SomeAnnotatedFactoryMethodClass> messageHandlingMember =
                getHandler(inspector, message);
        final Optional<CommandMessageHandlingMember> unwrap =
                messageHandlingMember.unwrap(CommandMessageHandlingMember.class);
        assertThat(unwrap).isNotNull();
        assertThat(unwrap).isPresent();
        final CommandMessageHandlingMember commandMessageHandlingMember = unwrap.get();
        assertThat(commandMessageHandlingMember.isFactoryHandler()).isTrue();
    }


    @Test
    void eventIsPublishedThroughoutRecursiveHierarchy() {
        // Note that if the inspector does not support recursive entities this will throw an StackOverflowError.
        AggregateModel<SomeRecursiveEntity> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeRecursiveEntity.class);

        // Create a hierarchy that we will use in this test.
        // The resulting hierarchy will look as follows:
        // root
        //      child1
        //              child2
        //              child3
        //                      child4
        String rootId = "root";
        String childId1 = "child1";
        String childId2 = "child2";
        String childId3 = "child3";
        String childId4 = "child4";

        SomeRecursiveEntity root = new SomeRecursiveEntity(LinkedList::new, null, rootId);

        // Assert root: it should not have any children (yet)
        assertThat(root.children.size()).isZero();

        // Publish an event that is picked up by root. It should have 1 child afterwards.
        inspector.publish(asEventMessage(new CreateChild(rootId, childId1)), root);
        assertThat(root.children.size()).isOne();

        // Assert child1: it should not have any children (yet)
        SomeRecursiveEntity child1 = root.getChild(childId1);
        assertThat(child1).isNotNull();
        assertThat(child1.children.size()).isZero();

        // Publish 2 events that are picked up by child1. It should have 2 children afterwards.
        inspector.publish(asEventMessage(new CreateChild(childId1, childId2)), root);
        inspector.publish(asEventMessage(new CreateChild(childId1, childId3)), root);
        assertThat(child1.children.size()).isEqualTo(2);

        // Assert child2: it should not have any children
        SomeRecursiveEntity child2 = child1.getChild(childId2);
        assertThat(child2).isNotNull();
        assertThat(child2.children.size()).isZero();

        // Assert child3: it should not have any children
        SomeRecursiveEntity child3 = child1.getChild(childId3);
        assertThat(child3).isNotNull();
        assertThat(child3.children.size()).isZero();

        // Publish an event that is picked up by child3. It should have 1 child afterwards.
        inspector.publish(asEventMessage(new CreateChild(childId3, childId4)), root);
        assertThat(child3.children.size()).isOne();

        // Assert child4: it should not have any children
        SomeRecursiveEntity child4 = child3.getChild(childId4);
        assertThat(child4).isNotNull();
        assertThat(child4.children.size()).isZero();
    }

    @Test
    void linkedListIsModifiedDuringIterationInRecursiveHierarchy() {
        testCollectionIsModifiedDuringIterationInRecursiveHierarchy(LinkedList::new);
    }

    @Test
    void hashSetIsModifiedDuringIterationInRecursiveHierarchy() {
        testCollectionIsModifiedDuringIterationInRecursiveHierarchy(HashSet::new);
    }

    @Test
    void copyOnWriteArrayListIsModifiedDuringIterationInRecursiveHierarchy() {
        testCollectionIsModifiedDuringIterationInRecursiveHierarchy(CopyOnWriteArrayList::new);
    }

    @Test
    void concurrentLinkedQueueIsModifiedDuringIterationInRecursiveHierarchy() {
        testCollectionIsModifiedDuringIterationInRecursiveHierarchy(ConcurrentLinkedQueue::new);
    }

    private void testCollectionIsModifiedDuringIterationInRecursiveHierarchy(
            Supplier<Collection<SomeRecursiveEntity>> supplier) {
        // Note that if the inspector does not support recursive entities this will throw an StackOverflowError.
        AggregateModel<SomeRecursiveEntity> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeRecursiveEntity.class);

        // Create a hierarchy that we will use in this test.
        // The resulting hierarchy will look as follows:
        // root
        //      child1
        //              child2
        //                      child3
        String rootId = "root";
        String childId1 = "child1";
        String childId2 = "child2";
        String childId3 = "child3";

        SomeRecursiveEntity root = new SomeRecursiveEntity(supplier, null, rootId);
        inspector.publish(asEventMessage(new CreateChild(rootId, childId1)), root);
        inspector.publish(asEventMessage(new CreateChild(childId1, childId2)), root);
        inspector.publish(asEventMessage(new CreateChild(childId2, childId3)), root);
        SomeRecursiveEntity child1 = root.getChild(childId1);

        // Assert child1: it should have 1 child
        assertThat(child1.children.size()).isOne();

        // Now move child3 up one level so it is a child of child1.
        // The resulting hierarchy will look as follows:
        // root
        //      child1
        //              child2
        //              child3

        // Note that if the inspector does not use copy-iterators this will throw an ConcurrentModificationException.
        inspector.publish(asEventMessage(new MoveChildUp(childId2, childId3)), root);
        assertThat(child1.children.size()).isEqualTo(2);
    }

    @Test
    void eventIsPublishedThroughoutHierarchy() {
        AggregateModel<SomeSubclass> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeSubclass.class);

        AtomicLong payload = new AtomicLong();

        inspector.publish(new GenericEventMessage<>(payload), new SomeSubclass());

        assertThat(payload.get()).isEqualTo(2L);
    }

    @Test
    void expectCommandToBeForwardedToEntity() throws Exception {
        AggregateModel<SomeSubclass> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeSubclass.class);

        GenericCommandMessage<?> message = new GenericCommandMessage<>(BigDecimal.ONE);
        SomeSubclass target = new SomeSubclass();
        MessageHandlingMember<? super SomeSubclass> handler = getHandler(inspector, message);
        assertThat(handler.handle(message, target)).isEqualTo("1");
    }

    @Test
    void methodIdentifierWithMethodParameters() {
        assertThatThrownBy(() -> AnnotatedAggregateMetaModelFactory
                .inspectAggregate(SomeIllegalAnnotatedIdMethodClass.class)).isInstanceOf(AggregateModellingException.class);
        assertThatThrownBy(() -> AnnotatedAggregateMetaModelFactory
                .inspectAggregate(SomeIllegalAnnotatedPersistenceIdMethodClass.class)).isInstanceOf(AggregateModellingException.class);
    }

    @Test
    void aggregateIdentifierPriority() {
        AggregateModel<SomeDifferentDoubleIdAnnotatedHandler> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeDifferentDoubleIdAnnotatedHandler.class);

        assertThat(inspector.type()).isEqualTo("SomeDifferentDoubleIdAnnotatedHandler");
        assertThat(inspector.getIdentifier(new SomeDifferentDoubleIdAnnotatedHandler())).isEqualTo("id");
        assertThat(inspector.routingKey()).isEqualTo("id");
    }

    @Test
    void findIdentifier() {
        AggregateModel<SomeAnnotatedHandlers> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeAnnotatedHandlers.class);

        assertThat(inspector.type()).isEqualTo("SomeAnnotatedHandlers");
        assertThat(inspector.getIdentifier(new SomeAnnotatedHandlers())).isEqualTo("id");
        assertThat(inspector.routingKey()).isEqualTo("id");
    }

    @Test
    void findGetterIdentifier() {
        AggregateModel<SomeGetterIdAnnotatedHandlers> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeGetterIdAnnotatedHandlers.class);

        assertThat(inspector.type()).isEqualTo("SomeGetterIdAnnotatedHandlers");
        assertThat(inspector.getIdentifier(new SomeGetterIdAnnotatedHandlers())).isEqualTo("id");
        assertThat(inspector.routingKey()).isEqualTo("id");
    }

    @Test
    void findMethodIdentifier() {
        AggregateModel<SomeMethodIdAnnotatedHandlers> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeMethodIdAnnotatedHandlers.class);

        assertThat(inspector.type()).isEqualTo("SomeMethodIdAnnotatedHandlers");
        assertThat(inspector.getIdentifier(new SomeMethodIdAnnotatedHandlers())).isEqualTo("id");
        assertThat(inspector.routingKey()).isEqualTo("calculatedId");
    }

    @Test
    void findJavaxPersistenceIdentifier() {
        AggregateModel<JavaxPersistenceAnnotatedHandlers> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(JavaxPersistenceAnnotatedHandlers.class);

        assertThat(inspector.getIdentifier(new JavaxPersistenceAnnotatedHandlers())).isEqualTo("id");
        assertThat(inspector.routingKey()).isEqualTo("id");
    }

    @Test
    void findJavaxPersistenceGetterIdentifier() {
        AggregateModel<JavaxPersistenceGetterAnnotatedHandlers> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(JavaxPersistenceGetterAnnotatedHandlers.class);

        assertThat(inspector.getIdentifier(new JavaxPersistenceGetterAnnotatedHandlers())).isEqualTo("id");
        assertThat(inspector.routingKey()).isEqualTo("id");
    }

    @Test
    void findJavaxPersistenceMethodIdentifier() {
        AggregateModel<JavaxPersistenceMethodIdAnnotatedHandlers> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(JavaxPersistenceMethodIdAnnotatedHandlers.class);

        assertThat(inspector.getIdentifier(new JavaxPersistenceMethodIdAnnotatedHandlers())).isEqualTo("id");
        assertThat(inspector.routingKey()).isEqualTo("calculatedId");
    }

    @Test
    void findIdentifierInSuperClass() {
        AggregateModel<SomeSubclass> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeSubclass.class);

        assertThat(inspector.type()).isEqualTo("SomeOtherName");
        assertThat(inspector.getIdentifier(new SomeSubclass())).isEqualTo("id");
    }

    @Test
    void findGetterIdentifierInSuperClass() {
        AggregateModel<SomeGetterIdSubclass> inspector =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(SomeGetterIdSubclass.class);

        assertThat(inspector.type()).isEqualTo("SomeOtherGetterName");
        assertThat(inspector.getIdentifier(new SomeGetterIdSubclass())).isEqualTo("id");
    }

    @Test
    void entityInitializationIsThreadSafe() {
        for (int i = 0; i < 100; i++) {
            AggregateModel<PolyMorphAggregate> inspector =
                    AnnotatedAggregateMetaModelFactory.inspectAggregate(PolyMorphAggregate.class);

            PolyMorphAggregate instance1 = new PolyMorphAggregate();
            PolyMorphAggregate instance2 = new PolyMorphAggregate();

            AtomicLong counter = new AtomicLong();

            ForkJoinTask<?> task1 = ForkJoinPool.commonPool()
                    .submit(() -> inspector.publish(asEventMessage(counter), instance1));
            ForkJoinTask<?> task2 = ForkJoinPool.commonPool()
                    .submit(() -> inspector.publish(asEventMessage(counter), instance2));

            task1.join();
            task2.join();

            assertThat(counter.get()).as("Concurrency issue detected after " + i + " attempts.").isEqualTo(2);
        }
    }

    @Test
    void illegalFactoryMethodThrowsExceptionClass() {
        assertThatThrownBy(() -> AnnotatedAggregateMetaModelFactory
                .inspectAggregate(SomeIllegalAnnotatedFactoryMethodClass.class))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void typedAggregateIdentifier() {
        assertThatThrownBy(() -> AnnotatedAggregateMetaModelFactory.inspectAggregate(TypedIdentifierAggregate.class))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void getterTypedAggregateIdentifier() {
        assertThatThrownBy(() -> AnnotatedAggregateMetaModelFactory
                .inspectAggregate(GetterTypedIdentifierAggregate.class))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void illegalDoubleIdentifiers() {
        assertThatThrownBy(() ->
                AnnotatedAggregateMetaModelFactory.inspectAggregate(IllegalDoubleIdFieldsAnnotatedAggregate.class)).isInstanceOf(SophonConfigurationException.class);
        assertThatThrownBy(() ->
                AnnotatedAggregateMetaModelFactory.inspectAggregate(IllegalDoubleIdMixedAnnotatedAggregate.class)).isInstanceOf(SophonConfigurationException.class);
        assertThatThrownBy(() ->
                AnnotatedAggregateMetaModelFactory.inspectAggregate(IllegalDoubleIdMethodsAnnotatedAggregate.class)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void voidMethodIdentifier() {
        assertThatThrownBy(() ->
                AnnotatedAggregateMetaModelFactory.inspectAggregate(IllegalVoidIdMethodAnnotatedAggregate.class)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void aggregateVersionAnnotatedField() {
        AggregateModel<AggregateWithAggregateVersionField> testSubject =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(AggregateWithAggregateVersionField.class);

        assertThat(testSubject.getVersion(new AggregateWithAggregateVersionField())).isEqualTo(42);
    }

    @Test
    void aggregateVersionAnnotatedMethod() {
        AggregateModel<AggregateWithAggregateVersionMethod> testSubject =
                AnnotatedAggregateMetaModelFactory.inspectAggregate(AggregateWithAggregateVersionMethod.class);

        assertThat(testSubject.getVersion(new AggregateWithAggregateVersionMethod())).isEqualTo(9001);
    }

    @Test
    void illegalDoubleAggregateVersions() {
        assertThatThrownBy(() -> AnnotatedAggregateMetaModelFactory
                .inspectAggregate(IllegalAggregateWithSeveralAggregateVersions.class))
                .isInstanceOf(AggregateModellingException.class);
    }

    @SuppressWarnings("unchecked")
    private <T> MessageHandlingMember<T> getHandler(AggregateModel<?> members, CommandMessage<?> message) {
        return (MessageHandlingMember<T>) members.commandHandlers()
                .stream()
                .filter(ch -> ch.canHandle(message))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected handler for this message"
                ));
    }

    @Documented
    @EventHandler
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyCustomEventHandler {

    }

    @Documented
    @CommandHandler
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MyCustomCommandHandler {

    }

    @SuppressWarnings("unused")
    private static class SomeDifferentDoubleIdAnnotatedHandler {

        @AggregateIdentifier
        private String id = "id";
        @Id
        private String javaxPersistenceId = "javaxPersistenceId";

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }


    @SuppressWarnings("unused")
    private static class JavaxPersistenceAnnotatedHandlers {

        @Id
        private String id = "id";

        @CommandHandler(commandName = "java.lang.String")
        public boolean handle(CharSequence test) {
            return test.equals("ok");
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @SuppressWarnings("unused")
    private static class JavaxPersistenceGetterAnnotatedHandlers {

        private String id = "id";

        @Id
        public String getId() {
            return id;
        }

        @CommandHandler(commandName = "java.lang.String")
        public boolean handle(CharSequence test) {
            return test.equals("ok");
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @SuppressWarnings("unused")
    private static class JavaxPersistenceMethodIdAnnotatedHandlers {

        private String id = "id";

        @Id
        public String calculatedId() {
            return id;
        }

        @CommandHandler(commandName = "java.lang.String")
        public boolean handle(CharSequence test) {
            return test.equals("ok");
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @SuppressWarnings("unused")
    private static class SomeAnnotatedHandlers {

        @AggregateIdentifier
        private String id = "id";

        @CommandHandler(commandName = "java.lang.String")
        public boolean handle(CharSequence test) {
            return test.equals("ok");
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @SuppressWarnings("unused")
    private static class SomeGetterIdAnnotatedHandlers {

        private String id = "id";

        @AggregateIdentifier
        public String getId() {
            return id;
        }

        @CommandHandler(commandName = "java.lang.String")
        public boolean handle(CharSequence test) {
            return test.equals("ok");
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @SuppressWarnings("unused")
    private static class SomeMethodIdAnnotatedHandlers {

        private String id = "id";

        @AggregateIdentifier
        public String calculatedId() {
            return id;
        }

        @CommandHandler(commandName = "java.lang.String")
        public boolean handle(CharSequence test) {
            return test.equals("ok");
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @SuppressWarnings("unused")
    private static class IllegalDoubleIdFieldsAnnotatedAggregate {

        @AggregateIdentifier
        private String id = "id";

        @AggregateIdentifier
        private String idTwo = "idTwo";

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @SuppressWarnings("unused")
    private static class IllegalDoubleIdMixedAnnotatedAggregate {

        @AggregateIdentifier
        private String id = "id";

        @AggregateIdentifier
        public String getIdTwo() {
            return "idTwo";
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @SuppressWarnings("unused")
    private static class IllegalDoubleIdMethodsAnnotatedAggregate {

        @AggregateIdentifier
        public String getId() {
            return "id";
        }


        @AggregateIdentifier
        public String getIdTwo() {
            return "idTwo";
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @SuppressWarnings("unused")
    private static class IllegalVoidIdMethodAnnotatedAggregate {

        @AggregateIdentifier
        public void getId() {
        }

        @CommandHandler
        public boolean testInt(Integer test) {
            return test > 0;
        }
    }

    @SuppressWarnings("unused")
    @AggregateRoot(type = "SomeOtherName")
    private static class SomeSubclass extends SomeAnnotatedHandlers {

        @AggregateMember
        private SomeOtherEntity entity = new SomeOtherEntity();

        @MyCustomCommandHandler
        public boolean handleInSubclass(String test) {
            return test.contains("sub");
        }

        @MyCustomEventHandler
        public void handle(AtomicLong value) {
            value.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    @AggregateRoot(type = "SomeOtherGetterName")
    private static class SomeGetterIdSubclass extends SomeGetterIdAnnotatedHandlers {

        @AggregateMember
        private SomeOtherEntity entity = new SomeOtherEntity();

        @MyCustomCommandHandler
        public boolean handleInSubclass(String test) {
            return test.contains("sub");
        }

        @MyCustomEventHandler
        public void handle(AtomicLong value) {
            value.incrementAndGet();
        }
    }

    @SuppressWarnings("unused")
    @AggregateRoot
    private static class PolyMorphAggregate {

        @AggregateMember
        private Object entity = new SomeOtherEntity();
    }

    private static class CustomIdentifier {

    }

    @SuppressWarnings("unused")
    @AggregateRoot
    private static class TypedIdentifierAggregate {

        @AggregateIdentifier
        private CustomIdentifier aggregateIdentifier = new CustomIdentifier();

        @CommandHandler
        public boolean handleInSubclass(String test) {
            return test.contains("sub");
        }

        @EventHandler
        public void handle(AtomicLong value) {
            value.incrementAndGet();
        }
    }

    @AggregateRoot
    private static class GetterTypedIdentifierAggregate {

        private CustomIdentifier aggregateIdentifier = new CustomIdentifier();

        @AggregateIdentifier
        public CustomIdentifier getAggregateIdentifier() {
            return aggregateIdentifier;
        }

        @CommandHandler
        public boolean handleInSubclass(String test) {
            return test.contains("sub");
        }

        @EventHandler
        public void handle(AtomicLong value) {
            value.incrementAndGet();
        }
    }

    private static class SomeOtherEntity {

        @CommandHandler
        public String handle(BigDecimal cmd) {
            return cmd.toPlainString();
        }

        @EventHandler
        public void handle(AtomicLong value) {
            value.incrementAndGet();
        }
    }

    private static class CreateChild {

        private final String parentId;
        private final String childId;

        public CreateChild(String parentId, String childId) {
            this.parentId = parentId;
            this.childId = childId;
        }
    }

    private static class MoveChildUp {

        private final String parentId;
        private final String childId;

        private MoveChildUp(String parentId, String childId) {
            this.parentId = parentId;
            this.childId = childId;
        }
    }

    private static class SomeRecursiveEntity {

        private final Supplier<Collection<SomeRecursiveEntity>> supplier;
        private final SomeRecursiveEntity parent;
        private final String entityId;

        @AggregateMember
        private final SomeIterable<SomeRecursiveEntity> children;

        public SomeRecursiveEntity(Supplier<Collection<SomeRecursiveEntity>> supplier,
                                   SomeRecursiveEntity parent,
                                   String entityId) {
            this.supplier = supplier;
            this.parent = parent;
            this.entityId = entityId;
            this.children = new SomeIterable<>(supplier.get());
        }

        public SomeRecursiveEntity getChild(String childId) {
            for (SomeRecursiveEntity c : children) {
                if (Objects.equals(c.entityId, childId)) {
                    return c;
                }
            }
            return null;
        }

        @EventHandler
        public void handle(CreateChild event) {
            if (Objects.equals(this.entityId, event.parentId)) {
                children.add(new SomeRecursiveEntity(supplier, this, event.childId));
            }
        }

        @EventHandler
        public void handle(MoveChildUp event) {
            if (Objects.equals(this.entityId, event.parentId)) {
                SomeRecursiveEntity child = getChild(event.childId);
                assertThat(child).isNotNull();
                assertThat(this.children.remove(child)).isTrue();
                assertThat(parent.children.add(child)).isTrue();
            }
        }
    }

    @SuppressWarnings({"UnusedAssignment", "unused"})
    public static class SomeAnnotatedFactoryMethodClass {

        @AggregateIdentifier
        private String id = "id";

        @CommandHandler
        public static SomeAnnotatedFactoryMethodClass factoryMethod(String id) {
            return new SomeAnnotatedFactoryMethodClass(id);
        }

        SomeAnnotatedFactoryMethodClass(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    @SuppressWarnings({"UnusedAssignment", "unused"})
    public static class SomeIllegalAnnotatedFactoryMethodClass {

        @AggregateIdentifier
        private String id = "id";

        @CommandHandler
        public static Object illegalFactoryMethod(String id) {
            return new SomeAnnotatedFactoryMethodClass(id);
        }

        SomeIllegalAnnotatedFactoryMethodClass(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class SomeIllegalAnnotatedIdMethodClass {

        private String id = "id";

        @CommandHandler
        SomeIllegalAnnotatedIdMethodClass(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @AggregateIdentifier
        public String calculatedIdMethod(String seed) {
            return seed + id;
        }
    }

    public static class SomeIllegalAnnotatedPersistenceIdMethodClass {

        private String id = "id";

        @CommandHandler
        SomeIllegalAnnotatedPersistenceIdMethodClass(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Id
        public String fancyCalculatedIdMethod(String seed) {
            return seed + id;
        }
    }

    /**
     * Wrapper implementation to ensure that the @AggregateMember field is solely triggered by the fact it implements
     * Iterable, and doesn't depend on any other interface being declared. See issue #461.
     *
     * @param <T> The type contained in this iterable
     */
    private static class SomeIterable<T> implements Iterable<T> {

        private final Collection<T> contents;

        public SomeIterable(Collection<T> contents) {
            this.contents = contents;
        }

        @NonNull
        @Override
        public Iterator<T> iterator() {
            return contents.iterator();
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            contents.forEach(action);
        }

        @Override
        public Spliterator<T> spliterator() {
            return contents.spliterator();
        }

        public boolean add(T item) {
            return contents.add(item);
        }

        public boolean remove(T item) {
            return contents.remove(item);
        }

        public int size() {
            return contents.size();
        }
    }


    private static class AggregateWithAggregateVersionField {

        @AggregateIdentifier
        private final String aggregateIdentifier = "aggregateIdentifier";
        @AggregateVersion
        private final long aggregateVersion = 42L;
    }

    private static class AggregateWithAggregateVersionMethod {

        @AggregateIdentifier
        private final String aggregateIdentifier = "aggregateIdentifier";

        @AggregateVersion
        public long getAggregateVersion() {
            return 9001L;
        }
    }

    private static class IllegalAggregateWithSeveralAggregateVersions {

        @AggregateIdentifier
        private String aggregateIdentifier;
        @AggregateVersion
        private long fieldAggregateVersion;

        @AggregateVersion
        public long getMethodAggregateVersion() {
            return 1337;
        }
    }
}
