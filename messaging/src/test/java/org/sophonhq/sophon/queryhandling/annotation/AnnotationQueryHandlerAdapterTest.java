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
package org.sophonhq.sophon.queryhandling.annotation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.Registration;
import org.sophonhq.sophon.messaging.InterceptorChain;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.messaging.annotation.UnsupportedHandlerException;
import org.sophonhq.sophon.messaging.interceptors.ExceptionHandler;
import org.sophonhq.sophon.messaging.interceptors.MessageHandlerInterceptor;
import org.sophonhq.sophon.messaging.responsetypes.ResponseTypes;
import org.sophonhq.sophon.queryhandling.GenericQueryMessage;
import org.sophonhq.sophon.queryhandling.NoHandlerForQueryException;
import org.sophonhq.sophon.queryhandling.QueryBus;
import org.sophonhq.sophon.queryhandling.QueryHandler;
import org.sophonhq.sophon.queryhandling.QueryMessage;
import org.sophonhq.sophon.utils.MockException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AnnotationQueryHandlerAdapter}.
 *
 * @author Marc Gathier
 */
class AnnotationQueryHandlerAdapterTest {

    private AnnotationQueryHandlerAdapter<?> testSubject;

    private final QueryBus queryBus = mock(QueryBus.class);

    @BeforeEach
    void setUp() {
        testSubject = new AnnotationQueryHandlerAdapter<>(new MyQueryHandler());
    }

    @SuppressWarnings("resource")
    @Test
    void subscribe() {
        when(queryBus.subscribe(any(), any(), any())).thenReturn(() -> true);
        Registration registration = testSubject.subscribe(queryBus);

        verify(queryBus, times(1)).subscribe(eq(String.class.getName()), eq(String.class), any());
        verify(queryBus, times(1)).subscribe(eq("Hello"), eq(String.class), any());

        assertThat(registration.cancel()).isTrue();
    }

    @Test
    void subscribeFailsForHandlerWithInvalidParameters() {
        assertThatThrownBy(() -> new AnnotationQueryHandlerAdapter<>(new MySecondQueryHandler()))
                .isInstanceOf(UnsupportedHandlerException.class);
    }

    @Test
    void subscribeFailsForHandlerWithVoidReturnType() {
        assertThatThrownBy(() -> new AnnotationQueryHandlerAdapter<>(new MyThirdQueryHandler()))
                .isInstanceOf(UnsupportedHandlerException.class);
    }

    @Test
    void handleQuery() throws Exception {
        String testResponse = "hello";
        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>(testResponse, ResponseTypes.instanceOf(String.class));
        Object result = testSubject.handle(testQueryMessage);

        assertThat(result).isEqualTo(testResponse);
    }

    @Test
    void handleQueryWithException() {
        QueryMessage<String, Integer> testQuery =
                new GenericQueryMessage<>("hello", ResponseTypes.instanceOf(Integer.class));

        assertThatThrownBy(() -> testSubject.handle(testQuery)).isInstanceOf(MockException.class);
    }

    @Test
    void handleQueryWithEmptyOptional() throws Exception {
        QueryMessage<String, String> testQuery =
                new GenericQueryMessage<>("hello", "noEcho", ResponseTypes.instanceOf(String.class));

        assertThat(testSubject.handle(testQuery)).isNull();
    }

    @Test
    void handleQueryWithProvidedOptional() throws Exception {
        QueryMessage<String, String> testQuery =
                new GenericQueryMessage<>("hello", "Hello", ResponseTypes.instanceOf(String.class));

        assertThat(testSubject.handle(testQuery)).isEqualTo("hello");
    }

    @Test
    void handleQueryForCollection() throws Exception {
        int testResponse = 5;
        QueryMessage<Integer, List<String>> testQueryMessage =
                new GenericQueryMessage<>(testResponse, ResponseTypes.multipleInstancesOf(String.class));

        //noinspection unchecked
        Collection<String> result = (Collection<String>) testSubject.handle(testQueryMessage);

        assertThat(result).hasSize(testResponse);
    }

    @Test
    void handleQueryThrowsNoHandlerForQueryException() {
        QueryMessage<Long, List<String>> testQueryMessage =
                new GenericQueryMessage<>(42L, ResponseTypes.multipleInstancesOf(String.class));

        assertThatThrownBy(() -> testSubject.handle(testQueryMessage))
                .isInstanceOf(NoHandlerForQueryException.class);
    }

    @Test
    void interceptMessages() throws Exception {
        List<QueryMessage<?, ?>> withInterceptor = new ArrayList<>();
        List<QueryMessage<?, ?>> withoutInterceptor = new ArrayList<>();
        testSubject = new AnnotationQueryHandlerAdapter<>(
                new MyInterceptingQueryHandler(withoutInterceptor, withInterceptor, new ArrayList<>())
        );

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("Hi", "Hello", ResponseTypes.instanceOf(String.class));

        String result = (String) testSubject.handle(testQueryMessage);

        assertThat(result).isEqualTo("Hi");
        assertThat(withInterceptor).isEqualTo(Collections.singletonList(testQueryMessage));
        assertThat(withoutInterceptor).isEqualTo(Collections.singletonList(testQueryMessage));
    }

    @Test
    void canHandleMessage() {
        QueryMessage<String, Integer> testIntegerQuery =
                new GenericQueryMessage<>("hello", ResponseTypes.instanceOf(Integer.class));
        QueryMessage<String, Long> testLongQuery =
                new GenericQueryMessage<>("hello", ResponseTypes.instanceOf(Long.class));

        assertThat(testSubject.canHandle(testIntegerQuery)).isTrue();
        assertThat(testSubject.canHandle(testLongQuery)).isFalse();
    }

    @Test
    void exceptionHandlerAnnotatedMethodsAreSupportedForQueryHandlingComponents() throws Exception {
        List<Exception> interceptedExceptions = new ArrayList<>();
        testSubject = new AnnotationQueryHandlerAdapter<>(
                new MyInterceptingQueryHandler(new ArrayList<>(), new ArrayList<>(), interceptedExceptions)
        );

        QueryMessage<ArrayList<Object>, Object> testQueryMessage =
                new GenericQueryMessage<>(new ArrayList<>(), ResponseTypes.instanceOf(Object.class));

        Object result = testSubject.handle(testQueryMessage);

        assertThat(result).isNull();
        assertThat(interceptedExceptions)
                .isNotEmpty()
                .hasSize(1);
        Exception interceptedException = interceptedExceptions.get(0);
        assertThat(interceptedException).isInstanceOf(RuntimeException.class);
        assertThat(interceptedException.getMessage()).isEqualTo("Some exception");
    }

    @SuppressWarnings("unused")
    private static class MyQueryHandler {

        @QueryHandler
        public String echo(String echo) {
            return echo;
        }

        @QueryHandler(queryName = "Hello")
        public Optional<String> echo2(String echo) {
            return Optional.ofNullable(echo);
        }

        @QueryHandler
        public Integer echo3(String echo) {
            throw new MockException("Mock");
        }

        @QueryHandler(queryName = "noEcho")
        public Optional<String> echo4(String echo) {
            return Optional.empty();
        }

        @QueryHandler
        public List<? extends String> echo4(Integer count) {
            List<String> value = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                value.add("echo");
            }
            return value;
        }

        @QueryHandler
        public Object handle(ArrayList<Object> query) {
            throw new RuntimeException("Some exception");
        }
    }

    @SuppressWarnings("unused")
    private static class MySecondQueryHandler {

        @QueryHandler
        public String echo(MetaData metaData, String echo) {
            return echo;
        }
    }

    @SuppressWarnings("unused")
    private static class MyThirdQueryHandler {

        @QueryHandler
        public void echo(String echo) {
        }
    }

    @SuppressWarnings("unused")
    private static class MyInterceptingQueryHandler extends MyQueryHandler {

        private final List<QueryMessage<?, ?>> interceptedWithoutInterceptorChain;
        private final List<QueryMessage<?, ?>> interceptedWithInterceptorChain;
        private final List<Exception> interceptedExceptions;

        private MyInterceptingQueryHandler(List<QueryMessage<?, ?>> interceptedWithoutInterceptorChain,
                                           List<QueryMessage<?, ?>> interceptedWithInterceptorChain,
                                           List<Exception> interceptedExceptions) {
            this.interceptedWithoutInterceptorChain = interceptedWithoutInterceptorChain;
            this.interceptedWithInterceptorChain = interceptedWithInterceptorChain;
            this.interceptedExceptions = interceptedExceptions;
        }

        @MessageHandlerInterceptor
        public void interceptAny(QueryMessage<?, ?> query) {
            interceptedWithoutInterceptorChain.add(query);
        }

        @MessageHandlerInterceptor
        public Object interceptAny(QueryMessage<?, ?> query, InterceptorChain chain) throws Exception {
            interceptedWithInterceptorChain.add(query);
            return chain.proceed();
        }

        @ExceptionHandler(resultType = RuntimeException.class)
        public void handle(RuntimeException exception) {
            interceptedExceptions.add(exception);
        }
    }
}
