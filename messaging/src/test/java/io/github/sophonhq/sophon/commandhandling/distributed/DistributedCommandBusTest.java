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

package io.github.sophonhq.sophon.commandhandling.distributed;

import io.github.sophonhq.sophon.commandhandling.CommandBus;
import io.github.sophonhq.sophon.commandhandling.CommandBusSpanFactory;
import io.github.sophonhq.sophon.commandhandling.CommandCallback;
import io.github.sophonhq.sophon.commandhandling.CommandMessage;
import io.github.sophonhq.sophon.commandhandling.CommandResultMessage;
import io.github.sophonhq.sophon.commandhandling.DefaultCommandBusSpanFactory;
import io.github.sophonhq.sophon.commandhandling.GenericCommandMessage;
import io.github.sophonhq.sophon.commandhandling.GenericCommandResultMessage;
import io.github.sophonhq.sophon.commandhandling.NoHandlerForCommandException;
import io.github.sophonhq.sophon.commandhandling.callbacks.NoOpCallback;
import io.github.sophonhq.sophon.commandhandling.distributed.commandfilter.DenyAll;
import io.github.sophonhq.sophon.common.AxonConfigurationException;
import io.github.sophonhq.sophon.common.Registration;
import io.github.sophonhq.sophon.messaging.MessageHandler;
import io.github.sophonhq.sophon.messaging.MessageHandlerInterceptor;
import io.github.sophonhq.sophon.monitoring.MessageMonitor;
import io.github.sophonhq.sophon.tracing.TestSpanFactory;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

import static org.awaitility.Awaitility.await;
import static io.github.sophonhq.sophon.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static io.github.sophonhq.sophon.commandhandling.distributed.DistributedCommandBus.INITIAL_LOAD_FACTOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link DistributedCommandBus}.
 *
 * @author Allard Buijze
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class DistributedCommandBusTest {

    private DistributedCommandBus testSubject;

    @Mock
    private CommandRouter mockCommandRouter;
    @Spy
    private final CommandBusConnector mockConnector = new StubCommandBusConnector();
    @Mock
    private MessageMonitor<? super CommandMessage<?>> mockMessageMonitor;
    @Mock
    private MessageMonitor.MonitorCallback mockMonitorCallback;
    @Mock
    private Member mockMember;

    private TestSpanFactory spanFactory;
    private CommandBusSpanFactory commandBusSpanFactory;

    @BeforeEach
    void setUp() {
        spanFactory = new TestSpanFactory();
        commandBusSpanFactory = DefaultCommandBusSpanFactory.builder()
                                                            .spanFactory(spanFactory)
                                                            .build();
        testSubject = DistributedCommandBus.builder()
                                           .commandRouter(mockCommandRouter)
                                           .connector(mockConnector)
                                           .messageMonitor(mockMessageMonitor)
                                           .spanFactory(commandBusSpanFactory)
                                           .build();
    }

    @Test
    void dispatchWithoutCallbackWithMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);
        CountDownLatch waiter = new CountDownLatch(1);

        testSubject.dispatch(testCommandMessage, (cm, result) -> {
            spanFactory.verifySpanActive("CommandBus.dispatchDistributedCommand", testCommandMessage);
            waiter.countDown();
        });
        waiter.await();

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportSuccess();
        await().atMost(Duration.ofSeconds(3l))
               .untilAsserted(
                       () -> spanFactory.verifySpanCompleted("CommandBus.dispatchDistributedCommand")
               );
        await().atMost(Duration.ofSeconds(3l))
               .untilAsserted(
                       () -> spanFactory.verifySpanPropagated("CommandBus.dispatchDistributedCommand", testCommandMessage)
               );
    }

    @Test
    void defaultCallbackIsUsedWhenFireAndForget() {
        CommandMessage<Object> message = GenericCommandMessage.asCommandMessage("test");
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);
        testSubject = DistributedCommandBus.builder()
                                           .commandRouter(mockCommandRouter)
                                           .connector(mockConnector)
                                           .messageMonitor(mockMessageMonitor)
                                           .defaultCommandCallback(mockCallback)
                                           .build();

        testSubject.dispatch(message);

        verify(mockCallback).onResult(eq(message), any());
    }

    @Test
    void dispatchFailingCommandWithoutCallbackWithMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("fail");
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);

        testSubject.dispatch(testCommandMessage, NoOpCallback.INSTANCE);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(isA(Exception.class));
    }

    @Test
    void dispatchWithoutCallbackAndWithoutMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        testSubject = DistributedCommandBus.builder()
                                           .commandRouter(mockCommandRouter)
                                           .connector(mockConnector)
                                           .build();

        testSubject.dispatch(testCommandMessage);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, times(1)).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor, never()).onMessageIngested(any());
        verify(mockMonitorCallback, never()).reportSuccess();
    }

    @Test
    void unknownCommandWithoutCallbackAndWithoutMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("unknown");
        CommandCallback<Object, Object> callback = mock(CommandCallback.class);
        when(mockCommandRouter.findDestination(testCommandMessage)).thenReturn(Optional.empty());
        testSubject = DistributedCommandBus.builder()
                                           .commandRouter(mockCommandRouter)
                                           .connector(mockConnector)
                                           .spanFactory(commandBusSpanFactory)
                                           .build();

        testSubject.dispatch(testCommandMessage, callback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor, never()).onMessageIngested(any());
        verify(mockMonitorCallback, never()).reportSuccess();

        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(NoHandlerForCommandException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
        spanFactory.verifySpanHasException("CommandBus.dispatchDistributedCommand", NoHandlerForCommandException.class);
    }

    @Test
    void dispatchWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);

        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportSuccess();
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback).onResult(eq(testCommandMessage), commandResultMessageCaptor.capture());
        assertFalse(commandResultMessageCaptor.getValue().isExceptional());
        assertNull(commandResultMessageCaptor.getValue().getPayload());
    }

    @Test
    void unknownCommandWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("test");
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        when(mockCommandRouter.findDestination(testCommandMessage)).thenReturn(Optional.empty());
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);

        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockConnector, never()).send(eq(mockMember), eq(testCommandMessage));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(any());
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback).onResult(eq(testCommandMessage), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(NoHandlerForCommandException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
        spanFactory.verifySpanHasException("CommandBus.dispatchDistributedCommand", RuntimeException.class);
    }

    @Test
    void dispatchFailingCommandWithCallbackAndMessageMonitor() throws Exception {
        CommandMessage<Object> testCommandMessage = GenericCommandMessage.asCommandMessage("fail");
        CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
        when(mockCommandRouter.findDestination(any())).thenReturn(Optional.of(mockMember));
        when(mockMessageMonitor.onMessageIngested(any())).thenReturn(mockMonitorCallback);

        testSubject.dispatch(testCommandMessage, mockCallback);

        verify(mockCommandRouter).findDestination(testCommandMessage);
        verify(mockConnector).send(eq(mockMember), eq(testCommandMessage), any(CommandCallback.class));
        verify(mockMessageMonitor).onMessageIngested(any());
        verify(mockMonitorCallback).reportFailure(isA(Exception.class));
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(mockCallback).onResult(eq(testCommandMessage), commandResultMessageCaptor.capture());
        assertEquals(Exception.class, commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    void localSegmentReturnsTheCommandBusConnectorsLocalSegmentResult() {
        CommandBus expectedLocalSegment = mock(CommandBus.class);
        when(mockConnector.localSegment()).thenReturn(Optional.of(expectedLocalSegment));

        CommandBus resultLocalSegment = testSubject.localSegment();

        assertEquals(expectedLocalSegment, resultLocalSegment);
    }

    @Test
    void disconnectRemovesAllSubscribedCommandHandlers() {
        testSubject.disconnect();
        verify(mockCommandRouter).updateMembership(INITIAL_LOAD_FACTOR, DenyAll.INSTANCE);
    }

    @Test
    void shutdownDispatchingInitiatesShutdownOfCommandBusConnector() {
        testSubject.shutdownDispatching();
        verify(mockConnector).initiateShutdown();
    }

    @Test
    void loadFactorDefault() {
        assertEquals(INITIAL_LOAD_FACTOR, testSubject.getLoadFactor());
    }

    @Test
    void updateLoadFactor() {
        int expectedLoadFactor = 42;

        testSubject.updateLoadFactor(expectedLoadFactor);

        assertEquals(expectedLoadFactor, testSubject.getLoadFactor());
    }

    @Test
    void shouldThrowWithNullDefaultCommandCallback() {
        DistributedCommandBus.Builder builder = DistributedCommandBus.builder();
        assertThrows(AxonConfigurationException.class, () -> builder.defaultCommandCallback(null));
    }

    private static class StubCommandBusConnector implements CommandBusConnector {

        @Override
        public <C> void send(@NonNull Member destination, @NonNull CommandMessage<? extends C> command) {
            //Do nothing
        }

        @Override
        public <C, R> void send(@NonNull Member destination, @NonNull CommandMessage<C> command,
                                @NonNull CommandCallback<? super C, R> callback) {
            if ("fail".equals(command.getPayload())) {
                callback.onResult(command, asCommandResultMessage(new Exception("Failing")));
            } else {
                callback.onResult(command, new GenericCommandResultMessage<>((R) null));
            }
        }

        @Override
        public Registration subscribe(@NonNull String commandName,
                                      @NonNull MessageHandler<? super CommandMessage<?>> handler) {
            return null;
        }

        @Override
        public Registration registerHandlerInterceptor(
                @NonNull MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor
        ) {
            return null;
        }
    }
}
