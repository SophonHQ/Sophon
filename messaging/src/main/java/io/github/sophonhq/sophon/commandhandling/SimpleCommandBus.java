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

package io.github.sophonhq.sophon.commandhandling;

import io.github.sophonhq.sophon.commandhandling.callbacks.LoggingCallback;
import io.github.sophonhq.sophon.commandhandling.callbacks.NoOpCallback;
import io.github.sophonhq.sophon.common.Registration;
import io.github.sophonhq.sophon.common.SophonConfigurationException;
import io.github.sophonhq.sophon.common.transaction.NoTransactionManager;
import io.github.sophonhq.sophon.common.transaction.TransactionManager;
import io.github.sophonhq.sophon.messaging.DefaultInterceptorChain;
import io.github.sophonhq.sophon.messaging.InterceptorChain;
import io.github.sophonhq.sophon.messaging.MessageDispatchInterceptor;
import io.github.sophonhq.sophon.messaging.MessageHandler;
import io.github.sophonhq.sophon.messaging.MessageHandlerInterceptor;
import io.github.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import io.github.sophonhq.sophon.messaging.unitofwork.RollbackConfiguration;
import io.github.sophonhq.sophon.messaging.unitofwork.RollbackConfigurationType;
import io.github.sophonhq.sophon.messaging.unitofwork.UnitOfWork;
import io.github.sophonhq.sophon.monitoring.MessageMonitor;
import io.github.sophonhq.sophon.monitoring.NoOpMessageMonitor;
import io.github.sophonhq.sophon.tracing.NoOpSpanFactory;
import io.github.sophonhq.sophon.tracing.Span;
import io.github.sophonhq.sophon.tracing.SpanFactory;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static java.lang.String.format;
import static io.github.sophonhq.sophon.commandhandling.GenericCommandResultMessage.asCommandResultMessage;
import static io.github.sophonhq.sophon.common.BuilderUtils.assertNonNull;
import static io.github.sophonhq.sophon.common.ObjectUtils.getOrDefault;

/**
 * Implementation of the CommandBus that dispatches commands to the handlers subscribed to that specific command's name.
 * Interceptors may be configured to add processing to commands regardless of their type or name, for example logging,
 * security (authorization), sla monitoring, etc.
 *
 * @author Allard Buijze
 * @author Martin Tilma
 * @since axon-0.5
 */
public class SimpleCommandBus implements CommandBus {

    private static final Logger logger = LoggerFactory.getLogger(SimpleCommandBus.class);

    private final TransactionManager transactionManager;
    private final MessageMonitor<? super CommandMessage<?>> messageMonitor;
    private final DuplicateCommandHandlerResolver duplicateCommandHandlerResolver;
    private final ConcurrentMap<String, MessageHandler<? super CommandMessage<?>>> subscriptions =
            new ConcurrentHashMap<>();
    private final List<MessageHandlerInterceptor<? super CommandMessage<?>>> handlerInterceptors =
            new CopyOnWriteArrayList<>();
    private final List<MessageDispatchInterceptor<? super CommandMessage<?>>> dispatchInterceptors =
            new CopyOnWriteArrayList<>();
    private final CommandCallback<Object, Object> defaultCommandCallback;
    private RollbackConfiguration rollbackConfiguration;
    private final CommandBusSpanFactory spanFactory;

    /**
     * Instantiate a Builder to be able to create a {@link SimpleCommandBus}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}, the {@link MessageMonitor} is
     * defaulted to a {@link NoOpMessageMonitor}, the {@link RollbackConfiguration} defaults to a
     * {@link RollbackConfigurationType#UNCHECKED_EXCEPTIONS}, the {@link DuplicateCommandHandlerResolver} defaults to
     * {@link DuplicateCommandHandlerResolution#logAndOverride()} and the {@link CommandBusSpanFactory} defaults to a
     * {@link DefaultCommandBusSpanFactory} with a {@link NoOpSpanFactory}. The {@link TransactionManager},
     * {@link MessageMonitor} and {@link RollbackConfiguration} are <b>hard requirements</b>. Thus setting them to
     * {@code null} will result in an {@link SophonConfigurationException}.
     *
     * @return a Builder to be able to create a {@link SimpleCommandBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link SimpleCommandBus} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link TransactionManager}, {@link MessageMonitor} and {@link RollbackConfiguration} are not
     * {@code null}, and will throw an {@link SophonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link SimpleCommandBus} instance
     */
    protected SimpleCommandBus(Builder builder) {
        builder.validate();
        this.transactionManager = builder.transactionManager;
        this.messageMonitor = builder.messageMonitor;
        this.rollbackConfiguration = builder.rollbackConfiguration;
        this.duplicateCommandHandlerResolver = builder.duplicateCommandHandlerResolver;
        this.defaultCommandCallback = builder.defaultCommandCallback;
        this.spanFactory = builder.builderSpanFactory;
    }

    @Override
    public <C> void dispatch(@NonNull CommandMessage<C> command) {
        dispatch(command, defaultCommandCallback);
    }

    @Override
    public <C, R> void dispatch(@NonNull CommandMessage<C> command,
                                @NonNull final CommandCallback<? super C, ? super R> callback) {
        Span span = spanFactory.createDispatchCommandSpan(command, false);
        span.run(() -> {
            CommandCallback<? super C, ? super R> spanAwareCallback = callback.wrap((commandMessage, commandResultMessage) -> {
                if (commandResultMessage.isExceptional()) {
                    span.recordException(commandResultMessage.exceptionResult());
                }
            });
            doDispatch(intercept(command), spanAwareCallback);
        });
    }

    /**
     * Invokes all the dispatch interceptors.
     *
     * @param command The original command being dispatched
     * @param <C>     The type of payload contained in the CommandMessage
     * @return The command to actually dispatch
     */
    @SuppressWarnings("unchecked")
    protected <C> CommandMessage<C> intercept(CommandMessage<C> command) {
        CommandMessage<C> commandToDispatch = command;
        for (MessageDispatchInterceptor<? super CommandMessage<?>> interceptor : dispatchInterceptors) {
            commandToDispatch = (CommandMessage<C>) interceptor.handle(commandToDispatch);
        }
        return commandToDispatch;
    }

    /**
     * Performs the actual dispatching logic. The dispatch interceptors must have been invoked at this point.
     *
     * @param command  The actual command to dispatch to the handler
     * @param callback The callback to notify of the result
     * @param <C>      The type of payload of the command
     * @param <R>      The type of result expected from the command handler
     */
    protected <C, R> void doDispatch(CommandMessage<C> command, CommandCallback<? super C, ? super R> callback) {
        MessageMonitor.MonitorCallback monitorCallback = messageMonitor.onMessageIngested(command);

        Optional<MessageHandler<? super CommandMessage<?>>> optionalHandler = findCommandHandlerFor(command);
        if (optionalHandler.isPresent()) {
            CommandMessage<C> commandWithContext = spanFactory.propagateContext(command);
            handle(commandWithContext, optionalHandler.get(), new MonitorAwareCallback<>(callback, monitorCallback));
        } else {
            NoHandlerForCommandException exception = new NoHandlerForCommandException(format(
                    "No handler was subscribed for command [%s].", command.getCommandName()
            ));
            monitorCallback.reportFailure(exception);
            callback.onResult(command, asCommandResultMessage(exception));
        }
    }

    private Optional<MessageHandler<? super CommandMessage<?>>> findCommandHandlerFor(CommandMessage<?> command) {
        return Optional.ofNullable(subscriptions.get(command.getCommandName()));
    }

    /**
     * Performs the actual handling logic.
     *
     * @param command  The actual command to handle
     * @param handler  The handler that must be invoked for this command
     * @param callback The callback to notify of the result
     * @param <C>      The type of payload of the command
     * @param <R>      The type of result expected from the command handler
     */
    protected <C, R> void handle(CommandMessage<C> command,
                                 MessageHandler<? super CommandMessage<?>> handler,
                                 CommandCallback<? super C, ? super R> callback) {
        CommandResultMessage<R> resultMessage = spanFactory.createHandleCommandSpan(command, false).runSupplier(() -> {
            if (logger.isDebugEnabled()) {
                logger.debug("Handling command [{}]", command.getCommandName());
            }

            UnitOfWork<CommandMessage<?>> unitOfWork = DefaultUnitOfWork.startAndGet(command);
            unitOfWork.attachTransaction(transactionManager);
            InterceptorChain chain = new DefaultInterceptorChain<>(unitOfWork, handlerInterceptors, handler);

            return asCommandResultMessage(unitOfWork.executeWithResult(chain::proceed,
                                                                       rollbackConfiguration));
        });
        callback.onResult(command, resultMessage);
    }

    /**
     * Subscribe the given {@code handler} to commands with given {@code commandName}. If a subscription already exists
     * for the given name, the configured {@link DuplicateCommandHandlerResolver} will resolve the command handler which
     * should be subscribed.
     */
    @Override
    public Registration subscribe(@NonNull String commandName,
                                  @NonNull MessageHandler<? super CommandMessage<?>> handler) {
        logger.debug("Subscribing command with name [{}]", commandName);
        assertNonNull(handler, "handler may not be null");
        subscriptions.compute(commandName, (k, existingHandler) -> {
            if (existingHandler == null || existingHandler == handler) {
                return handler;
            } else {
                return duplicateCommandHandlerResolver.resolve(commandName, existingHandler, handler);
            }
        });
        return () -> subscriptions.remove(commandName, handler);
    }

    /**
     * Registers the given interceptor to the command bus. All incoming commands will pass through the registered
     * interceptors at the given order before the command is passed to the handler for processing.
     *
     * @param handlerInterceptor The interceptor to invoke when commands are handled
     * @return handle to deregister the interceptor
     */
    @Override
    public Registration registerHandlerInterceptor(
            @NonNull MessageHandlerInterceptor<? super CommandMessage<?>> handlerInterceptor
    ) {
        handlerInterceptors.add(handlerInterceptor);
        return () -> handlerInterceptors.remove(handlerInterceptor);
    }

    /**
     * Registers the given list of dispatch interceptors to the command bus. All incoming commands will pass through the
     * interceptors at the given order before the command is dispatched toward the command handler.
     *
     * @param dispatchInterceptor The interceptors to invoke when commands are dispatched
     * @return handle to deregister the interceptor
     */
    @Override
    public Registration registerDispatchInterceptor(
            @NonNull MessageDispatchInterceptor<? super CommandMessage<?>> dispatchInterceptor
    ) {
        dispatchInterceptors.add(dispatchInterceptor);
        return () -> dispatchInterceptors.remove(dispatchInterceptor);
    }

    /**
     * Sets the {@link RollbackConfiguration} that allows you to change when the {@link UnitOfWork} is rolled back. If
     * not set the, {@link RollbackConfigurationType#UNCHECKED_EXCEPTIONS} will be used, which triggers a rollback on
     * all unchecked exceptions.
     *
     * @param rollbackConfiguration a {@link RollbackConfiguration} specifying when a {@link UnitOfWork} should be
     *                              rolled back
     */
    public void setRollbackConfiguration(@NonNull RollbackConfiguration rollbackConfiguration) {
        this.rollbackConfiguration = rollbackConfiguration;
    }

    /**
     * Builder class to instantiate a {@link SimpleCommandBus}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}, the {@link MessageMonitor} is
     * defaulted to a {@link NoOpMessageMonitor}, the {@link RollbackConfiguration} defaults to a
     * {@link RollbackConfigurationType#UNCHECKED_EXCEPTIONS}, the {@link DuplicateCommandHandlerResolver} defaults to
     * {@link DuplicateCommandHandlerResolution#logAndOverride()} and the {@link CommandBusSpanFactory} defaults to a
     * {@link DefaultCommandBusSpanFactory} with a {@link NoOpSpanFactory}.
     * <p>
     * The {@link TransactionManager}, {@link MessageMonitor} and {@link RollbackConfiguration} are <b>hard
     * requirements</b>. Thus setting them to {@code null} will result in an {@link SophonConfigurationException}.
     */
    public static class Builder {

        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private MessageMonitor<? super CommandMessage<?>> messageMonitor = NoOpMessageMonitor.INSTANCE;
        private RollbackConfiguration rollbackConfiguration = RollbackConfigurationType.UNCHECKED_EXCEPTIONS;
        private DuplicateCommandHandlerResolver duplicateCommandHandlerResolver =
                DuplicateCommandHandlerResolution.logAndOverride();
        private CommandCallback<Object, Object> defaultCommandCallback = LoggingCallback.INSTANCE;
        private CommandBusSpanFactory builderSpanFactory = DefaultCommandBusSpanFactory
                .builder().spanFactory(NoOpSpanFactory.INSTANCE).build();

        /**
         * Sets the {@link TransactionManager} used to manage transactions. Defaults to a {@link NoTransactionManager}.
         *
         * @param transactionManager a {@link TransactionManager} used to manage transactions
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(@NonNull TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link MessageMonitor} of generic type {@link CommandMessage} used the to monitor the command bus.
         * Defaults to a {@link NoOpMessageMonitor}.
         *
         * @param messageMonitor a {@link MessageMonitor} used the message monitor to monitor the command bus
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder messageMonitor(@NonNull MessageMonitor<? super CommandMessage<?>> messageMonitor) {
            assertNonNull(messageMonitor, "MessageMonitor may not be null");
            this.messageMonitor = messageMonitor;
            return this;
        }

        /**
         * Sets the {@link RollbackConfiguration} which allows you to specify when a {@link UnitOfWork} should be rolled
         * back. Defaults to a {@link RollbackConfigurationType#UNCHECKED_EXCEPTIONS}, which triggers a rollback on all
         * unchecked exceptions.
         *
         * @param rollbackConfiguration a {@link RollbackConfiguration} specifying when a {@link UnitOfWork} should be
         *                              rolled back
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder rollbackConfiguration(@NonNull RollbackConfiguration rollbackConfiguration) {
            assertNonNull(rollbackConfiguration, "RollbackConfiguration may not be null");
            this.rollbackConfiguration = rollbackConfiguration;
            return this;
        }

        /**
         * Sets the {@link DuplicateCommandHandlerResolver} used to resolves the road to take when a duplicate command
         * handler is subscribed. Defaults to {@link DuplicateCommandHandlerResolution#logAndOverride()}.
         *
         * @param duplicateCommandHandlerResolver a {@link DuplicateCommandHandlerResolver} used to resolves the road to
         *                                        take when a duplicate command handler is subscribed
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder duplicateCommandHandlerResolver(
                @NonNull DuplicateCommandHandlerResolver duplicateCommandHandlerResolver) {
            assertNonNull(duplicateCommandHandlerResolver, "DuplicateCommandHandlerResolver may not be null");
            this.duplicateCommandHandlerResolver = duplicateCommandHandlerResolver;
            return this;
        }

        /**
         * Sets the callback to use when commands are dispatched in a "fire and forget" method, such as {@link
         * #dispatch(CommandMessage)}. Defaults to a {@link LoggingCallback}. Passing {@code null} will result in a
         * {@link NoOpCallback} being used.
         *
         * @param defaultCommandCallback the callback to invoke when no explicit callback is provided for a command
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder defaultCommandCallback(@NonNull CommandCallback<Object, Object> defaultCommandCallback) {
            this.defaultCommandCallback = getOrDefault(defaultCommandCallback, NoOpCallback.INSTANCE);
            return this;
        }

        /**
         * Sets the {@link SpanFactory} implementation to use for providing tracing capabilities. Defaults to a
         * {@link NoOpSpanFactory} by default, which provides no tracing capabilities.
         *
         * @param spanFactory The {@link SpanFactory} implementation.
         * @return The current Builder instance, for fluent interfacing.
         * @deprecated Use {@link #spanFactory(CommandBusSpanFactory)} instead as it provides more configurability.
         */
        @Deprecated
        public Builder spanFactory(@NonNull SpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.builderSpanFactory = DefaultCommandBusSpanFactory
                    .builder().spanFactory(spanFactory).build();
            return this;
        }

        /**
         * Sets the {@link CommandBusSpanFactory} implementation to use for providing tracing capabilities. Defaults to
         * a {@link DefaultCommandBusSpanFactory} backed by {@link NoOpSpanFactory} by default, which provides no
         * tracing capabilities.
         *
         * @param spanFactory The {@link CommandBusSpanFactory} implementation.
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@NonNull CommandBusSpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.builderSpanFactory = spanFactory;
            return this;
        }

        /**
         * Initializes a {@link SimpleCommandBus} as specified through this Builder.
         *
         * @return a {@link SimpleCommandBus} as specified through this Builder
         */
        public SimpleCommandBus build() {
            return new SimpleCommandBus(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws SophonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            // Method kept for overriding
        }
    }
}
