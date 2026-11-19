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

package org.sophonhq.sophon.messaging.annotation;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.messaging.Message;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link HandlerComparator}.
 */
class HandlerComparatorTest {

    private MessageHandlingMember<?> stringHandler;
    private MessageHandlingMember<?> objectHandler;
    private MessageHandlingMember<?> longHandler;
    private MessageHandlingMember<?> numberHandler;
    private MessageHandlingMember<?> priorityHandler;
    private MessageHandlingMember<?> stringHandlerReversedOrder;
    private MessageHandlingMember<?> objectHandlerReversedOrder;

    private Comparator<MessageHandlingMember<?>> testSubject;

    @BeforeEach
    void setUp() {
        stringHandler = new StubMessageHandlingMember(String.class, 0);
        objectHandler = new StubMessageHandlingMember(Object.class, 0);
        longHandler = new StubMessageHandlingMember(Long.class, 0);
        numberHandler = new StubMessageHandlingMember(Number.class, 0);
        priorityHandler = new StubMessageHandlingMember(Object.class, 1);
        stringHandlerReversedOrder = new ReversedOrderMessageHandlingMember(String.class, 0);
        objectHandlerReversedOrder = new ReversedOrderMessageHandlingMember(Object.class, 0);

        testSubject = HandlerComparator.instance();
    }

    @RepeatedTest(10)
    void handlerOrderIsDeterministic() {
        List<MessageHandlingMember<?>> handlers = new ArrayList<>(Arrays.asList(
                stringHandler, objectHandler, longHandler, numberHandler, priorityHandler, stringHandlerReversedOrder,
                objectHandlerReversedOrder
        ));
        ArrayList<MessageHandlingMember<?>> handlers1 = new ArrayList<>(handlers);
        ArrayList<MessageHandlingMember<?>> handlers2 = new ArrayList<>(handlers);

        Collections.shuffle(handlers1, ThreadLocalRandom.current());
        Collections.shuffle(handlers2, ThreadLocalRandom.current());

        handlers1.sort(testSubject);
        handlers2.sort(testSubject);

        assertThat(handlers2).isEqualTo(handlers1);
    }

    @Test
    void subclassesBeforeSuperclasses() {
        assertThat(testSubject.compare(stringHandler, objectHandler) < 0)
                .as("String should appear before Object")
                .isTrue();
        assertThat(testSubject.compare(objectHandler, stringHandler) > 0)
                .as("String should appear before Object")
                .isTrue();

        assertThat(testSubject.compare(numberHandler, objectHandler) < 0)
                .as("Number should appear before Object")
                .isTrue();
        assertThat(testSubject.compare(objectHandler, numberHandler) > 0)
                .as("Number should appear before Object")
                .isTrue();

        assertThat(testSubject.compare(longHandler, numberHandler) < 0).as("Long should appear before Number").isTrue();
        assertThat(testSubject.compare(numberHandler, longHandler) > 0).as("Long should appear before Number").isTrue();

        assertThat(testSubject.compare(longHandler, objectHandler) < 0).as("Long should appear before Object").isTrue();
        assertThat(testSubject.compare(objectHandler, longHandler) > 0).as("Long should appear before Object").isTrue();
    }

    @SuppressWarnings("EqualsWithItself")
    @Test
    void handlersIsEqualWithItself() {
        assertThat(testSubject.compare(stringHandler, stringHandler)).isZero();
        assertThat(testSubject.compare(objectHandler, objectHandler)).isZero();
        assertThat(testSubject.compare(longHandler, longHandler)).isZero();
        assertThat(testSubject.compare(numberHandler, numberHandler)).isZero();
        assertThat(testSubject.compare(priorityHandler, priorityHandler)).isZero();

        assertThat(testSubject.compare(stringHandler, objectHandler)).isNotZero();
        assertThat(testSubject.compare(longHandler, stringHandler)).isNotZero();
        assertThat(testSubject.compare(numberHandler, stringHandler)).isNotZero();
        assertThat(testSubject.compare(objectHandler, longHandler)).isNotZero();
        assertThat(testSubject.compare(objectHandler, numberHandler)).isNotZero();
        assertThat(testSubject.compare(priorityHandler, numberHandler)).isNotZero();
    }

    @Test
    void handlersSortedCorrectly() {
        List<MessageHandlingMember<?>> members = new ArrayList<>(Arrays.asList(objectHandler,
                                                                               numberHandler,
                                                                               stringHandler,
                                                                               longHandler));

        members.sort(this.testSubject);
        assertThat(members.indexOf(longHandler)).isLessThan(members.indexOf(numberHandler));
        assertThat(members.indexOf(objectHandler)).isEqualTo(3);
    }

    @Test
    void notInSameHierarchyUsesPriorityBasedEvaluation() {
        assertThat(testSubject.compare(priorityHandler, stringHandler) < 0)
                .as("priorityHandler should appear before String based on priority")
                .isTrue();
        assertThat(testSubject.compare(stringHandler, priorityHandler) > 0)
                .as("priorityHandler should appear before String based on priority")
                .isTrue();
    }

    @Test
    void handlingMembersImplementingReversedOrderHaveThereOrderReversedAmongOneAnother() {
        assertThat(testSubject.compare(stringHandlerReversedOrder, objectHandler) < 0)
                .as("Reversed-order-String handler should appear before Object handler")
                .isTrue();
        assertThat(testSubject.compare(objectHandler, stringHandlerReversedOrder) > 0)
                .as("Reversed-order-String handler should appear before Object handler")
                .isTrue();
        assertThat(testSubject.compare(objectHandlerReversedOrder, stringHandler) < 0)
                .as("Reversed-order-Object handler should appear before String handler ")
                .isTrue();
        assertThat(testSubject.compare(stringHandler, objectHandlerReversedOrder) > 0)
                .as("Reversed-order-Object handler should appear before String handler ")
                .isTrue();
        assertThat(testSubject.compare(stringHandlerReversedOrder, objectHandlerReversedOrder) > 0)
                .as("Reversed-order-Object handler should appear before reversed-order-String handler")
                .isTrue();
        assertThat(testSubject.compare(objectHandlerReversedOrder, stringHandlerReversedOrder) < 0)
                .as("Reversed-order-Object handler should appear before reversed-order-String handler")
                .isTrue();
    }

    private static class StubMessageHandlingMember implements MessageHandlingMember<Object> {

        private final Class<?> payloadType;
        private final int priority;

        StubMessageHandlingMember(Class<?> payloadType, int priority) {
            this.payloadType = payloadType;
            this.priority = priority;
        }

        @Override
        public Class<?> payloadType() {
            return payloadType;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean canHandle(@NonNull Message<?> message) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean canHandleMessageType(@NonNull Class<? extends Message> messageType) {
            throw new UnsupportedOperationException("Not implemented (yet)");
        }

        @Override
        public Object handle(@NonNull Message<?> message, Object target) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public <HT> Optional<HT> unwrap(Class<HT> handlerType) {
            return Optional.empty();
        }

        @Override
        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            return false;
        }

        @Override
        public Optional<Map<String, Object>> annotationAttributes(Class<? extends Annotation> annotationType) {
            return Optional.empty();
        }

        @Override
        public <R> Optional<R> attribute(String attributeKey) {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "Handler {" +
                    payloadType +
                    ", p=" + priority +
                    '}';
        }
    }

    private static class ReversedOrderMessageHandlingMember implements MessageHandlingMember<Object> {

        private final Class<?> payloadType;
        private final int priority;

        ReversedOrderMessageHandlingMember(Class<?> payloadType, int priority) {
            this.payloadType = payloadType;
            this.priority = priority;
        }

        @Override
        public Class<?> payloadType() {
            return payloadType;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean canHandle(@NonNull Message<?> message) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        @SuppressWarnings("rawtypes")
        public boolean canHandleMessageType(@NonNull Class<? extends Message> messageType) {
            throw new UnsupportedOperationException("Not implemented (yet)");
        }

        @Override
        public Object handle(@NonNull Message<?> message, Object target) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public <HT> Optional<HT> unwrap(Class<HT> handlerType) {
            return Optional.empty();
        }

        @Override
        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            return false;
        }

        @Override
        public Optional<Map<String, Object>> annotationAttributes(Class<? extends Annotation> annotationType) {
            return Optional.empty();
        }

        @Override
        public <R> Optional<R> attribute(String attributeKey) {
            return "ResultHandler.resultType".equals(attributeKey) ? Optional.of((R) Object.class) : Optional.empty();
        }

        @Override
        public String toString() {
            return "ResultHandler {" +
                    payloadType +
                    ", p=" + priority +
                    '}';
        }
    }
}
