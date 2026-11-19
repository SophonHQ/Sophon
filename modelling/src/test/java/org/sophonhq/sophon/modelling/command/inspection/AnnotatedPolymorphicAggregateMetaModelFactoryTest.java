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

package org.sophonhq.sophon.modelling.command.inspection;

import jakarta.persistence.Id;
import org.junit.jupiter.api.*;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.messaging.annotation.MessageHandlingMember;
import org.sophonhq.sophon.modelling.command.AggregateIdentifier;
import org.sophonhq.sophon.modelling.command.CommandHandlerInterceptor;

import java.lang.reflect.Executable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for polymorphic aggregate model.
 *
 * @author Milan Savic
 */
class AnnotatedPolymorphicAggregateMetaModelFactoryTest {

    @Test
    void polymorphicHierarchy() throws NoSuchMethodException {
        Executable aHandle = A.class.getMethod("handle", String.class);
        Executable bHandle = B.class.getMethod("handle", Boolean.class);
        Executable cHandle = C.class.getMethod("handle", Boolean.class);
        Executable aOn = A.class.getMethod("on", Integer.class);
        Executable bOn = B.class.getMethod("on", Long.class);
        Executable cOn = C.class.getMethod("on", Integer.class);
        Executable aIntercept = A.class.getMethod("intercept", String.class);
        Executable bIntercept = B.class.getMethod("intercept", String.class);
        Executable cIntercept = C.class.getMethod("intercept", String.class);

        AnnotatedAggregateMetaModelFactory factory = new AnnotatedAggregateMetaModelFactory();
        AggregateModel<A> model = factory.createModel(A.class, new HashSet<>(asList(B.class, C.class)));
        Map<Class<?>, List<MessageHandlingMember<? super A>>> allCommandHandlers = model.allCommandHandlers();
        Map<Class<?>, List<MessageHandlingMember<? super A>>> allEventHandlers = model.allEventHandlers();
        Map<Class<?>, List<MessageHandlingMember<? super A>>> allCommandHandlerInterceptors = model
                .allCommandHandlerInterceptors();

        assertThat(allCommandHandlers).hasSize(3);
        assertThat(allEventHandlers).hasSize(3);
        assertThat(allCommandHandlerInterceptors).hasSize(3);

        assertThat(executables(allCommandHandlers.get(A.class))).containsExactly(aHandle);
        assertThat(executables(model.commandHandlers(A.class))).containsExactly(aHandle);
        assertThat(executables(allCommandHandlerInterceptors.get(A.class)))
                .containsExactly(aIntercept);
        assertThat(executables(model.commandHandlerInterceptors(A.class)))
                .containsExactly(aIntercept);
        assertThat(executables(allEventHandlers.get(A.class))).containsExactly(aOn);

        assertThat(executables(allCommandHandlers.get(B.class))).containsExactly(aHandle, bHandle);
        assertThat(executables(model.commandHandlers(B.class))).containsExactly(aHandle, bHandle);
        assertThat(executables(allCommandHandlerInterceptors.get(B.class)))
                .containsExactly(aIntercept, bIntercept);
        assertThat(executables(model.commandHandlerInterceptors(B.class)))
                .containsExactly(aIntercept, bIntercept);
        assertThat(executables(allEventHandlers.get(B.class))).containsExactly(aOn, bOn);

        assertThat(executables(allCommandHandlers.get(C.class))).containsExactly(aHandle, cHandle);
        assertThat(executables(model.commandHandlers(C.class))).containsExactly(aHandle, cHandle);
        assertThat(executables(allCommandHandlerInterceptors.get(C.class)))
                .containsExactly(aIntercept, cIntercept);
        assertThat(executables(model.commandHandlerInterceptors(C.class)))
                .containsExactly(aIntercept, cIntercept);
        assertThat(executables(allEventHandlers.get(C.class))).containsExactly(aOn, cOn);
    }

    @Test
    void uniqueAggregateIdentifierField() {
        assertThatThrownBy(() -> AnnotatedAggregateMetaModelFactory.inspectAggregate(D.class, Set.of(E.class)))
                .isInstanceOf(AggregateModellingException.class);
    }

    @Test
    void uniqueAggregateIdentifierFieldShouldIgnorePersistenceId() {
        // should not throw an exception
        AnnotatedAggregateMetaModelFactory.inspectAggregate(D.class, new HashSet<>(singleton(F.class)));
    }

    private <T> List<Executable> executables(List<MessageHandlingMember<? super T>> members) {
        return executables(members.stream());
    }

    private <T> List<Executable> executables(Stream<MessageHandlingMember<? super T>> members) {
        return members.map(mhm -> mhm.unwrap(Executable.class).get())
                      .collect(toList());
    }

    private static class A {

        @CommandHandlerInterceptor
        public void intercept(String a) {
        }

        @CommandHandler
        public void handle(String a) {
        }

        @EventHandler
        public void on(Integer a) {
        }
    }

    private static class B extends A {

        @CommandHandlerInterceptor
        public void intercept(String a) {
        }

        @CommandHandler
        public void handle(Boolean a) {
        }

        @EventHandler
        public void on(Long b) {
        }
    }

    private static class C extends A {

        @CommandHandlerInterceptor
        public void intercept(String a) {
        }

        @CommandHandler
        public void handle(Boolean a) {
        }

        @EventHandler
        public void on(Integer a) {
        }
    }

    private static class D {

        @AggregateIdentifier
        private String id;

        @CommandHandler
        public void handle(String a) {
        }
    }

    private static class E extends D {

        @AggregateIdentifier
        private String id1;

        @CommandHandler
        public void handle(Integer a) {
        }
    }

    private static class F extends D {

        @Id
        private String id1;

        @CommandHandler
        public void handle(Integer a) {
        }
    }
}
