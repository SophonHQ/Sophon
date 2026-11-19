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
package org.sophonhq.sophon.queryhandling;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.common.ReflectionUtils;
import org.sophonhq.sophon.common.Registration;
import org.sophonhq.sophon.common.transaction.Transaction;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.MessageHandler;
import org.sophonhq.sophon.messaging.MessageHandlerInterceptor;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.messaging.correlation.MessageOriginProvider;
import org.sophonhq.sophon.messaging.interceptors.CorrelationDataInterceptor;
import org.sophonhq.sophon.messaging.responsetypes.ResponseType;
import org.sophonhq.sophon.messaging.responsetypes.ResponseTypes;
import org.sophonhq.sophon.monitoring.MessageMonitor;
import org.sophonhq.sophon.queryhandling.registration.DuplicateQueryHandlerResolution;
import org.sophonhq.sophon.queryhandling.registration.DuplicateQueryHandlerSubscriptionException;
import org.sophonhq.sophon.tracing.TestSpanFactory;
import org.sophonhq.sophon.utils.MockException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.common.ReflectionUtils.methodOf;
import static org.sophonhq.sophon.queryhandling.registration.DuplicateQueryHandlerResolution.silentlyAdd;

/**
 * Test class validating the {@link SimpleQueryBus}.
 *
 * @author Marc Gathier
 */
class SimpleQueryBusTest {

    private static final String TRACE_ID = "traceId";
    private static final String CORRELATION_ID = "correlationId";

    private final ResponseType<String> singleStringResponse = ResponseTypes.instanceOf(String.class);
    private final ResponseType<List<String>> multipleStringResponse = ResponseTypes.multipleInstancesOf(String.class);
    private SimpleQueryBus testSubject;
    private MessageMonitor<QueryMessage<?, ?>> messageMonitor;
    private QueryInvocationErrorHandler errorHandler;
    private MessageMonitor.MonitorCallback monitorCallback;
    private TestSpanFactory spanFactory;
    private QueryBusSpanFactory queryBusSpanFactory;
    private QueryUpdateEmitterSpanFactory queryUpdateEmitterSpanFactory;

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        queryBusSpanFactory = DefaultQueryBusSpanFactory.builder().spanFactory(spanFactory).build();
        queryUpdateEmitterSpanFactory = DefaultQueryUpdateEmitterSpanFactory.builder().spanFactory(spanFactory).build();
        //noinspection unchecked
        messageMonitor = mock(MessageMonitor.class);
        errorHandler = mock(QueryInvocationErrorHandler.class);
        monitorCallback = mock(MessageMonitor.MonitorCallback.class);
        when(messageMonitor.onMessageIngested(any())).thenReturn(monitorCallback);

        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .errorHandler(errorHandler)
                                    .spanFactory(queryBusSpanFactory)
                                    .queryUpdateEmitter(SimpleQueryUpdateEmitter.builder()
                                                                                .spanFactory(
                                                                                        queryUpdateEmitterSpanFactory)
                                                                                .build())
                                    .duplicateQueryHandlerResolver(silentlyAdd())
                                    .build();

        MessageHandlerInterceptor<QueryMessage<?, ?>> correlationDataInterceptor =
                new CorrelationDataInterceptor<>(new MessageOriginProvider(CORRELATION_ID, TRACE_ID));
        //noinspection resource
        testSubject.registerHandlerInterceptor(correlationDataInterceptor);
    }

    @Test
    public void handlerInterceptorThrowsException() throws ExecutionException, InterruptedException {
        //noinspection resource
        testSubject.subscribe("test", String.class, q -> q.getPayload().toString());
        //noinspection resource
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            throw new RuntimeException("Faking");
        });

        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(
                new GenericQueryMessage<>("hello", "test", ResponseTypes.instanceOf(String.class))
        );

        assertThat(result.isDone()).isTrue();
        assertThat(result.get().isExceptional()).isTrue();
    }

    @Test
    void subscribe() {
        //noinspection resource
        testSubject.subscribe("test", String.class, Message::getPayload);

        assertThat(testSubject.getSubscriptions()).hasSize(1);
        assertThat(testSubject.getSubscriptions().values().iterator().next()).hasSize(1);

        //noinspection resource
        testSubject.subscribe("test", String.class, (q) -> "aa" + q.getPayload());

        assertThat(testSubject.getSubscriptions()).hasSize(1);
        assertThat(testSubject.getSubscriptions().values().iterator().next()).hasSize(2);

        //noinspection resource
        testSubject.subscribe("test2", String.class, (q) -> "aa" + q.getPayload());

        assertThat(testSubject.getSubscriptions()).hasSize(2);
    }

    @Test
    void subscribingSameHandlerTwiceInvokedOnce() throws Exception {
        AtomicInteger invocationCount = new AtomicInteger();
        MessageHandler<QueryMessage<?, String>> handler = message -> {
            invocationCount.incrementAndGet();
            return "reply";
        };
        //noinspection resource
        Registration subscription = testSubject.subscribe("test", String.class, handler);
        //noinspection resource
        testSubject.subscribe("test", String.class, handler);

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("request", "test", singleStringResponse);
        String result = testSubject.query(testQueryMessage).thenApply(QueryResponseMessage::getPayload).get();

        assertThat(result).isEqualTo("reply");
        assertThat(invocationCount.get()).isOne();
        assertThat(subscription.cancel()).isTrue();
        assertThat(testSubject.query(testQueryMessage).isDone()).isTrue();
        assertThat(testSubject.query(testQueryMessage).isCompletedExceptionally()).isTrue();
    }

    @Test
    void subscribingSameQueryTwiceWithThrowingDuplicateResolver() {
        // Modify query bus with failing duplicate resolver
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .errorHandler(errorHandler)
                                    .duplicateQueryHandlerResolver(DuplicateQueryHandlerResolution.rejectDuplicates())
                                    .build();
        MessageHandler<QueryMessage<?, String>> handlerOne = message -> "reply";
        MessageHandler<QueryMessage<?, String>> handlerTwo = message -> "reply";
        //noinspection resource
        testSubject.subscribe("test", String.class, handlerOne);
        //noinspection resource
        assertThatThrownBy(() -> testSubject.subscribe("test", String.class, handlerTwo))
                .isInstanceOf(DuplicateQueryHandlerSubscriptionException.class);
    }

    /*
     * This test ensures that the QueryResponseMessage is created inside the scope of the Unit of Work, and therefore
     * contains the correlation data registered with the Unit of Work
     */
    @Test
    void queryResultContainsCorrelationData() throws Exception {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse)
                .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertThat(result.isDone()).as("SimpleQueryBus should resolve CompletableFutures directly").isTrue();
        assertThat(result.get().getPayload()).isEqualTo("hello1234");
        assertThat(result.get().getMetaData())
                .isEqualTo(
                        MetaData.with(CORRELATION_ID, testQueryMessage.getIdentifier())
                                .and(TRACE_ID, "fakeTraceId")
                );
    }

    @Test
    void nullResponseProperlyReturned() throws ExecutionException, InterruptedException {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, p -> null);
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse)
                .andMetaData(Collections.singletonMap(TRACE_ID, "fakeTraceId"));
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertThat(result.isDone()).as("SimpleQueryBus should resolve CompletableFutures directly").isTrue();
        assertThat(result.get().getPayload()).isNull();
        assertThat(result.get().getPayloadType()).isEqualTo(String.class);
        assertThat(result.get().getMetaData())
                .isEqualTo(MetaData.with(CORRELATION_ID, testQueryMessage.getIdentifier())
                                   .and(TRACE_ID, "fakeTraceId"));
    }

    @Test
    void queryWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .transactionManager(mockTxManager)
                                    .build();

        //noinspection resource
        testSubject.subscribe(String.class.getName(),
                              methodOf(this.getClass(), "stringListQueryHandler").getGenericReturnType(),
                              q -> asList(q.getPayload() + "1234", q.getPayload() + "567"));

        QueryMessage<String, List<String>> testQueryMessage =
                new GenericQueryMessage<>("hello", ResponseTypes.multipleInstancesOf(String.class));
        CompletableFuture<List<String>> result = testSubject.query(testQueryMessage)
                                                            .thenApply(QueryResponseMessage::getPayload);

        assertThat(result.isDone()).isTrue();
        List<String> completedResult = result.get();
        assertThat(completedResult)
                .contains("hello1234")
                .contains("hello567");
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @SuppressWarnings("unused") // Used by 'testQueryWithTransaction()' to generate query handler response type
    public List<String> stringListQueryHandler() {
        return new ArrayList<>();
    }

    @Test
    void querySingleWithTransaction() throws Exception {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .transactionManager(mockTxManager)
                                    .build();

        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<String> result = testSubject.query(testQueryMessage)
                                                      .thenApply(QueryResponseMessage::getPayload);

        assertThat(result.get()).isEqualTo("hello1234");
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    void querySingleIsTraced() throws ExecutionException, InterruptedException {
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            spanFactory.verifySpanActive("QueryBus.query", testQueryMessage);
            return q.getPayload() + "1234";
        });

        testSubject.query(testQueryMessage).get();

        spanFactory.verifySpanCompleted("QueryBus.query", testQueryMessage);
    }

    @Test
    void ScatterGatherIsTraced() {
        QueryMessage<String, List<String>> testQueryMessage =
                new GenericQueryMessage<>("hello", multipleStringResponse);

        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery", testQueryMessage);
            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery-0");
            return q.getPayload() + "1234";
        });
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery", testQueryMessage);
            spanFactory.verifySpanActive("QueryBus.scatterGatherQuery-1");
            return q.getPayload() + "12345678";
        });

        //noinspection ResultOfMethodCallIgnored
        testSubject.scatterGather(testQueryMessage, 500, TimeUnit.MILLISECONDS).collect(Collectors.toList());

        spanFactory.verifySpanCompleted("QueryBus.scatterGatherQuery", testQueryMessage);
        spanFactory.verifySpanCompleted("QueryBus.scatterGatherHandler-0");
        spanFactory.verifySpanCompleted("QueryBus.scatterGatherHandler-1");
    }


    @Test
    void queryListWithSingleHandlerReturnsSingleAsList() throws Exception {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");

        QueryMessage<String, List<String>> testQueryMessage = new GenericQueryMessage<>("hello",
                                                                                        multipleStringResponse);
        CompletableFuture<List<String>> result = testSubject.query(testQueryMessage)
                                                            .thenApply(QueryResponseMessage::getPayload);

        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0)).isEqualTo("hello1234");
    }


    @Test
    void queryListWithBothSingleHandlerAndListHandlerReturnsListResult() throws Exception {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String[].class, (q) -> Arrays.asList(
                q.getPayload() + "1234", q.getPayload() + "5678"
        ));

        QueryMessage<String, List<String>> testQueryMessage = new GenericQueryMessage<>("hello",
                                                                                        multipleStringResponse);
        CompletableFuture<List<String>> result = testSubject.query(testQueryMessage)
                                                            .thenApply(QueryResponseMessage::getPayload);

        assertThat(result.get()).hasSize(2);
        assertThat(result.get().get(0)).isEqualTo("hello1234");
        assertThat(result.get().get(1)).isEqualTo("hello5678");
    }

    @Test
    void queryForSingleResultWithUnsuitableHandlers() throws Exception {
        AtomicInteger invocationCount = new AtomicInteger();
        MessageHandler<? super QueryMessage<?, ?>> failingHandler = message -> {
            invocationCount.incrementAndGet();
            throw new NoHandlerForQueryException("Mock");
        };
        MessageHandler<? super QueryMessage<?, String>> passingHandler = message -> {
            invocationCount.incrementAndGet();
            return "reply";
        };
        //noinspection resource
        testSubject.subscribe("query", String.class, failingHandler);
        //noinspection FunctionalExpressionCanBeFolded,Convert2MethodRef,Convert2MethodRef,resource
        testSubject.subscribe("query", String.class, message -> failingHandler.handle(message));
        //noinspection resource
        testSubject.subscribe("query", String.class, passingHandler);

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("query", "query", singleStringResponse);
        CompletableFuture<String> result = testSubject.query(testQueryMessage)
                                                      .thenApply(QueryResponseMessage::getPayload);

        assertThat(result.isDone()).isTrue();
        assertThat(result.get()).isEqualTo("reply");
        assertThat(invocationCount.get()).isEqualTo(3);
    }

    @Test
    void queryWithOnlyUnsuitableResultsInException() throws Exception {
        //noinspection resource
        testSubject.subscribe("query", String.class, message -> {
            throw new NoHandlerForQueryException("Mock");
        });

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("query", "query", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertThat(result.isDone()).isTrue();
        assertThat(result.isCompletedExceptionally()).isTrue();
        assertThat(result.thenApply(QueryResponseMessage::getPayload)
                         .exceptionally(e -> e.getCause().getClass().getSimpleName())
                         .get()).isEqualTo("NoHandlerForQueryException");
    }

    @Test
    void queryReturnsResponseMessageFromHandlerAsIs() throws Exception {
        GenericQueryResponseMessage<String> soleResult =
                new GenericQueryResponseMessage<>("soleResult");
        //noinspection resource
        testSubject.subscribe("query", String.class, message -> soleResult);

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("query", "query", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertThat(result.isDone()).isTrue();
        assertThat(result.get()).isSameAs(soleResult);
    }

    @Test
    void queryWithHandlersResultsInException() throws Exception {
        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("query", "query", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertThat(result.isDone()).isTrue();
        assertThat(result.isCompletedExceptionally()).isTrue();
        assertThat(result.thenApply(QueryResponseMessage::getPayload)
                         .exceptionally(e -> e.getCause().getClass().getSimpleName())
                         .get()).isEqualTo("NoHandlerForQueryException");
    }

    @Test
    void queryForSingleResultWillReportErrors() throws Exception {
        MessageHandler<? super QueryMessage<?, ?>> failingHandler = message -> {
            throw new MockException("Mock");
        };
        //noinspection resource
        testSubject.subscribe("query", String.class, failingHandler);

        QueryMessage<String, String> testQueryMessage =
                new GenericQueryMessage<>("query", "query", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertThat(result.isDone()).isTrue();
        assertThat(result.isCompletedExceptionally()).isFalse();
        QueryResponseMessage<String> queryResponseMessage = result.get();
        assertThat(queryResponseMessage.isExceptional()).isTrue();
        assertThat(queryResponseMessage.exceptionResult().getMessage()).isEqualTo("Mock");
    }

    @Test
    void queryWithInterceptors() throws Exception {
        //noinspection resource
        testSubject.registerDispatchInterceptor(
                messages -> (i, m) -> m.andMetaData(Collections.singletonMap("key", "value"))
        );
        //noinspection resource
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (unitOfWork.getMessage().getMetaData().containsKey("key")) {
                return "fakeReply";
            }
            return interceptorChain.proceed();
        });
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<String> result = testSubject.query(testQueryMessage)
                                                      .thenApply(QueryResponseMessage::getPayload);

        assertThat(result.get()).isEqualTo("fakeReply");
    }

    @Test
    void queryDoesNotArriveAtUnsubscribedHandler() throws Exception {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> "1234");
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<String> result = testSubject.query(testQueryMessage)
                                                      .thenApply(QueryResponseMessage::getPayload);

        assertThat(result.get()).isEqualTo("1234");
    }

    @Test
    void queryReturnsException() throws Exception {
        MockException mockException = new MockException();
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            throw mockException;
        });

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertThat(result.isDone()).isTrue();
        assertThat(result.isCompletedExceptionally()).isFalse();
        QueryResponseMessage<String> queryResponseMessage = result.get();
        assertThat(queryResponseMessage.isExceptional()).isTrue();
        assertThat(queryResponseMessage.exceptionResult()).isEqualTo(mockException);
    }

    @Test
    void queryUnknown() throws Exception {
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<?> result = testSubject.query(testQueryMessage);

        try {
            result.get();
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertThat(e.getCause().getClass()).isEqualTo(NoHandlerForQueryException.class);
        }
        spanFactory.verifySpanHasException("QueryBus.query", NoHandlerForQueryException.class);
    }

    @Test
    void queryUnsubscribedHandlers() throws Exception {
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + " is not here!").close();

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<?> result = testSubject.query(testQueryMessage);

        try {
            result.get();
            fail("Expected exception");
        } catch (ExecutionException e) {
            assertThat(e.getCause().getClass()).isEqualTo(NoHandlerForQueryException.class);
        }
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportFailure(any());
    }

    @Test
    void scatterGather() {
        int expectedResults = 3;

        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, q -> q.getPayload() + "1234");
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, q -> q.getPayload() + "5678");
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, q -> q.getPayload() + "90");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<QueryResponseMessage<String>> results = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS)
                                                               .collect(toSet());

        assertThat(results).hasSize(expectedResults);
        Set<String> resultSet = results.stream().map(Message::getPayload).collect(toSet());
        assertThat(resultSet).hasSize(expectedResults);
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(3)).reportSuccess();
    }

    @Test
    void scatterGatherOnArrayQueryHandlers() throws NoSuchMethodException {
        int expectedQueryResponses = 3;
        int expectedResults = 6;

        //noinspection resource
        testSubject.subscribe(String.class.getName(),
                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
                              q -> new String[]{q.getPayload() + "12", q.getPayload() + "34"});
        //noinspection resource
        testSubject.subscribe(String.class.getName(),
                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
                              q -> new String[]{q.getPayload() + "56", q.getPayload() + "78"});
        //noinspection resource
        testSubject.subscribe(String.class.getName(),
                              methodOf(getClass(), "stringArrayQueryHandler").getGenericReturnType(),
                              q -> new String[]{q.getPayload() + "9", q.getPayload() + "0"});

        QueryMessage<String, List<String>> testQueryMessage =
                new GenericQueryMessage<>("Hello, World", ResponseTypes.multipleInstancesOf(String.class));
        Set<QueryResponseMessage<List<String>>> results =
                testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS)
                           .collect(toSet());

        assertThat(results).hasSize(expectedQueryResponses);
        Set<String> resultSet = results.stream()
                                       .map(Message::getPayload)
                                       .flatMap(Collection::stream)
                                       .collect(toSet());
        assertThat(resultSet).hasSize(expectedResults);
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(3)).reportSuccess();
    }

    @SuppressWarnings("unused")// Used by 'testScatterGatherOnArrayQueryHandlers' to generate queryHandler responseType
    public String[] stringArrayQueryHandler() {
        return new String[]{};
    }

    @Test
    void scatterGatherWithTransaction() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .transactionManager(mockTxManager)
                                    .errorHandler(errorHandler)
                                    .duplicateQueryHandlerResolver(silentlyAdd())
                                    .build();

        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<Object> results = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertThat(results).hasSize(2);
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(2)).commit();
    }

    @Test
    void scatterGatherWithTransactionRollsBackOnFailure() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .transactionManager(mockTxManager)
                                    .errorHandler(errorHandler)
                                    .duplicateQueryHandlerResolver(silentlyAdd())
                                    .build();

        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            throw new MockException();
        });

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<Object> results = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertThat(results).hasSize(1);
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
        verify(mockTxManager, times(2)).startTransaction();
        verify(mockTx, times(1)).commit();
        verify(mockTx, times(1)).rollback();
    }

    @Test
    void queryFirstFromScatterGatherWillCommitUnitOfWork() {
        TransactionManager mockTxManager = mock(TransactionManager.class);
        Transaction mockTx = mock(Transaction.class);
        when(mockTxManager.startTransaction()).thenReturn(mockTx);
        testSubject = SimpleQueryBus.builder()
                                    .messageMonitor(messageMonitor)
                                    .transactionManager(mockTxManager)
                                    .errorHandler(errorHandler)
                                    .duplicateQueryHandlerResolver(silentlyAdd())
                                    .build();

        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Optional<QueryResponseMessage<String>> firstResult =
                testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).findFirst();

        assertThat(firstResult).isPresent();
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, atMost(2)).reportSuccess();
        verify(mockTxManager).startTransaction();
        verify(mockTx).commit();
    }

    @Test
    void scatterGatherWithInterceptors() {
        //noinspection resource
        testSubject.registerDispatchInterceptor(
                messages -> (i, m) -> m.andMetaData(Collections.singletonMap("key", "value"))
        );
        //noinspection resource
        testSubject.registerHandlerInterceptor((unitOfWork, interceptorChain) -> {
            if (unitOfWork.getMessage().getMetaData().containsKey("key")) {
                return "fakeReply";
            }
            return interceptorChain.proceed();
        });
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "567");

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        List<String> results = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS)
                                          .map(Message::getPayload)
                                          .collect(Collectors.toList());

        assertThat(results).hasSize(2);
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(2)).reportSuccess();
        assertThat(results).containsExactly("fakeReply", "fakeReply");
    }

    @Test
    void scatterGatherReturnsEmptyStreamWhenNoHandlersAvailable() {
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<Object> allResults = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertThat(allResults).isEmpty();
        verify(messageMonitor).onMessageIngested(any());
        verify(monitorCallback).reportIgnored();
    }

    @Test
    void scatterGatherReportsExceptionsWithErrorHandler() {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> {
            throw new MockException();
        });

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        Set<Object> results = testSubject.scatterGather(testQueryMessage, 0, TimeUnit.SECONDS).collect(toSet());

        assertThat(results).hasSize(1);
        verify(errorHandler).onError(isA(MockException.class), eq(testQueryMessage), isA(MessageHandler.class));
        verify(messageMonitor, times(1)).onMessageIngested(any());
        verify(monitorCallback, times(1)).reportSuccess();
        verify(monitorCallback, times(1)).reportFailure(isA(MockException.class));
    }

    @Test
    void queryResponseMessageCorrelationData() throws ExecutionException, InterruptedException {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, (q) -> q.getPayload() + "1234");
        //noinspection resource
        testSubject.registerHandlerInterceptor(new CorrelationDataInterceptor<>(new MessageOriginProvider()));
        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("Hello, World", singleStringResponse);
        QueryResponseMessage<String> queryResponseMessage = testSubject.query(testQueryMessage).get();
        assertThat(queryResponseMessage.getMetaData()).containsEntry("traceId", testQueryMessage.getIdentifier());
        assertThat(queryResponseMessage.getMetaData()).containsEntry("correlationId", testQueryMessage.getIdentifier());
        assertThat(queryResponseMessage.getPayload()).isEqualTo("Hello, World1234");
    }

    @Test
    void subscriptionQueryReportsExceptionInInitialResult() {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, q -> {
            throw new MockException();
        });

        //noinspection resource
        SubscriptionQueryResult<QueryResponseMessage<String>, SubscriptionQueryUpdateMessage<String>> result = testSubject
                .subscriptionQuery(new GenericSubscriptionQueryMessage<>("test",
                                                                         ResponseTypes.instanceOf(String.class),
                                                                         ResponseTypes.instanceOf(String.class)));
        Mono<QueryResponseMessage<String>> initialResult = result.initialResult();
        //noinspection ConstantConditions
        assertThat(initialResult.map(r -> false).onErrorReturn(MockException.class::isInstance, true).block())
                .as("Exception by handler should be reported in result, not on Mono")
                .isFalse();
        //noinspection ConstantConditions
        assertThat(initialResult.block().isExceptional()).isTrue();
    }

    @Test
    void subscriptionQueryIncreasingProjection() throws InterruptedException {
        CountDownLatch ten = new CountDownLatch(1);
        CountDownLatch hundred = new CountDownLatch(1);
        CountDownLatch thousand = new CountDownLatch(1);
        final AtomicLong value = new AtomicLong();
        //noinspection resource
        testSubject.subscribe("queryName", Long.class, q -> value.get());
        QueryUpdateEmitter updateEmitter = testSubject.queryUpdateEmitter();
        Disposable disposable = Flux.interval(Duration.ofMillis(0), Duration.ofMillis(3))
                                    .doOnNext(next -> {
                                        if (next == 10L) {
                                            ten.countDown();
                                        }
                                        if (next == 100L) {
                                            hundred.countDown();
                                        }
                                        if (next == 1000L) {
                                            thousand.countDown();
                                        }
                                        value.set(next);
                                        updateEmitter.emit(query -> "queryName".equals(query.getQueryName()), next);
                                    })
                                    .doOnComplete(() -> updateEmitter.complete(query -> "queryName".equals(query.getQueryName())))
                                    .subscribe();


        //noinspection resource
        SubscriptionQueryResult<QueryResponseMessage<Long>, SubscriptionQueryUpdateMessage<Long>> result = testSubject
                .subscriptionQuery(new GenericSubscriptionQueryMessage<>("test",
                                                                         "queryName",
                                                                         ResponseTypes.instanceOf(Long.class),
                                                                         ResponseTypes.instanceOf(Long.class)));
        Mono<QueryResponseMessage<Long>> initialResult = result.initialResult();
        ten.await();
        Long firstInitialResult = Objects.requireNonNull(initialResult.block()).getPayload();
        hundred.await();
        Long fistUpdate = Objects.requireNonNull(result.updates().next().block()).getPayload();
        thousand.await();
        Long anotherInitialResult = Objects.requireNonNull(initialResult.block()).getPayload();
        assertThat(fistUpdate).isLessThanOrEqualTo(firstInitialResult + 1);
        assertThat(firstInitialResult).isLessThanOrEqualTo(anotherInitialResult);
        disposable.dispose();
    }

    @Test
    void subscriptionQueryIsTraced() throws InterruptedException {
        CountDownLatch updatedLatch = new CountDownLatch(2);
        final AtomicLong value = new AtomicLong();
        //noinspection resource
        testSubject.subscribe("queryName", Long.class, q -> value.get());
        QueryUpdateEmitter updateEmitter = testSubject.queryUpdateEmitter();
        Disposable disposable = Flux.interval(Duration.ofMillis(0), Duration.ofMillis(20))
                                    .doOnNext(next -> {
                                        updatedLatch.countDown();
                                        updateEmitter.emit(query -> "queryName".equals(query.getQueryName()), next);
                                    })
                                    .doOnComplete(() -> updateEmitter.complete(query -> "queryName".equals(query.getQueryName())))
                                    .subscribe();


        try {
            //noinspection resource
            SubscriptionQueryResult<QueryResponseMessage<Long>, SubscriptionQueryUpdateMessage<Long>> result = testSubject
                    .subscriptionQuery(new GenericSubscriptionQueryMessage<>("test",
                                                                             "queryName",
                                                                             ResponseTypes.instanceOf(Long.class),
                                                                             ResponseTypes.instanceOf(Long.class)));
            Mono<QueryResponseMessage<Long>> initialResult = result.initialResult();
            Objects.requireNonNull(initialResult.block()).getPayload();
            spanFactory.verifySpanCompleted("QueryBus.query");
            updatedLatch.await();
            Objects.requireNonNull(result.updates().next().block()).getPayload();
            spanFactory.verifySpanCompleted("QueryUpdateEmitter.emitQueryUpdateMessage");
        } finally {
            disposable.dispose();
        }
    }

    @Test
    void queryReportsExceptionInResponseMessage() throws ExecutionException, InterruptedException {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, q -> {
            throw new MockException();
        });

        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(
                new GenericQueryMessage<>("test", ResponseTypes.instanceOf(String.class))
        );
        assertThat(result.thenApply(r -> false).exceptionally(MockException.class::isInstance).get())
                .as("Exception by handler should be reported in result, not on Mono")
                .isFalse();
        assertThat(result.get().isExceptional()).isTrue();
    }

    @Test
    void queryHandlerDeclaresFutureResponseType() throws Exception {
        Type responseType = ReflectionUtils.methodOf(getClass(), "futureMethod").getGenericReturnType();
        //noinspection resource
        testSubject.subscribe(String.class.getName(),
                              responseType,
                              (q) -> CompletableFuture.completedFuture(q.getPayload() + "1234"));

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertThat(result.isDone()).as("SimpleQueryBus should resolve CompletableFutures directly").isTrue();
        assertThat(result.get().getPayload()).isEqualTo("hello1234");
    }

    @Test
    void queryHandlerDeclaresCompletableFutureResponseType() throws Exception {
        Type responseType = ReflectionUtils.methodOf(getClass(), "completableFutureMethod").getGenericReturnType();
        //noinspection resource
        testSubject.subscribe(String.class.getName(),
                              responseType,
                              (q) -> CompletableFuture.completedFuture(q.getPayload() + "1234"));

        QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", singleStringResponse);
        CompletableFuture<QueryResponseMessage<String>> result = testSubject.query(testQueryMessage);

        assertThat(result.isDone()).as("SimpleQueryBus should resolve CompletableFutures directly").isTrue();
        assertThat(result.get().getPayload()).isEqualTo("hello1234");
    }

    @Test
    void onSubscriptionQueryCancelTheActiveSubscriptionIsRemovedFromTheEmitterIfFluxIsNotSubscribed() {
        //noinspection resource
        testSubject.subscribe(String.class.getName(), String.class, q -> q.getPayload() + "1234");

        SubscriptionQueryMessage<String, String, String> testQuery = new GenericSubscriptionQueryMessage<>(
                "test", ResponseTypes.instanceOf(String.class), ResponseTypes.instanceOf(String.class)
        );

        //noinspection resource
        SubscriptionQueryResult<QueryResponseMessage<String>, SubscriptionQueryUpdateMessage<String>> result =
                testSubject.subscriptionQuery(testQuery);

        result.cancel();
        assertThat(testSubject.queryUpdateEmitter().activeSubscriptions()).hasSize(0);
    }

    @SuppressWarnings("unused")
    public Future<String> futureMethod() {
        return null;
    }

    @SuppressWarnings("unused")
    public CompletableFuture<String> completableFutureMethod() {
        return null;
    }
}
