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

package io.github.sophonhq.sophon.commandhandling;

import io.github.sophonhq.sophon.common.SophonConfigurationException;
import io.github.sophonhq.sophon.common.SophonThreadFactory;
import io.github.sophonhq.sophon.common.transaction.NoTransactionManager;
import io.github.sophonhq.sophon.common.transaction.TransactionManager;
import io.github.sophonhq.sophon.messaging.MessageHandler;
import io.github.sophonhq.sophon.messaging.unitofwork.RollbackConfiguration;
import io.github.sophonhq.sophon.messaging.unitofwork.RollbackConfigurationType;
import io.github.sophonhq.sophon.monitoring.MessageMonitor;
import io.github.sophonhq.sophon.monitoring.NoOpMessageMonitor;
import io.github.sophonhq.sophon.tracing.SpanFactory;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.github.sophonhq.sophon.common.BuilderUtils.assertNonNull;

/**
 * Specialization of the SimpleCommandBus that processed Commands asynchronously from the calling thread. By default,
 * the AsynchronousCommandBus uses a Cached Thread Pool (see
 * {@link java.util.concurrent.Executors#newCachedThreadPool()}). It will reuse threads while possible, and shut them
 * down after 60 seconds of inactivity.
 * <p/>
 * Each Command is dispatched in a separate task, which is processed by the Executor.
 * <p/>
 * Note that you should call {@link #shutdown()} to stop any threads waiting for new tasks. Failure to do so may cause
 * the JVM to hang for up to 60 seconds on JVM shutdown.
 *
 * @author Allard Buijze
 * @since axon-1.3.4
 */
public class AsynchronousCommandBus extends SimpleCommandBus {

    private final Executor executor;

    /**
     * Instantiate a {@link AsynchronousCommandBus} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link TransactionManager}, {@link MessageMonitor}, {@link RollbackConfiguration} and
     * {@link Executor} are not {@code null}, and will throw an {@link SophonConfigurationException} if any of them is
     * {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AsynchronousCommandBus} instance
     */
    protected AsynchronousCommandBus(Builder builder) {
        super(builder);
        this.executor = builder.executor;
    }

    /**
     * Instantiate a Builder to be able to create a {@link AsynchronousCommandBus}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}, the {@link MessageMonitor} is
     * defaulted to a {@link NoOpMessageMonitor}, {@link RollbackConfiguration} defaults to a
     * {@link RollbackConfigurationType#UNCHECKED_EXCEPTIONS}, the {@link DuplicateCommandHandlerResolver} defaults to
     * {@link DuplicateCommandHandlerResolution#logAndOverride()}, the {@link Executor} defaults to a
     * {@link Executors#newCachedThreadPool} and the {@link CommandBusSpanFactory} defaults to a
     * {@link DefaultCommandBusSpanFactory} backed by a {@link io.github.sophonhq.sophon.tracing.NoOpSpanFactory}. The
     * default{@code executor} uses an {@link SophonThreadFactory} to create threads with a sensible naming scheme. The
     * TransactionManager, MessageMonitor, RollbackConfiguration and Executor are <b>hard requirements</b>. Thus setting
     * them to {@code null} will result in an {@link SophonConfigurationException}.
     *
     * @return a Builder to be able to create a {@link AsynchronousCommandBus}
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected <C, R> void handle(CommandMessage<C> command,
                                 MessageHandler<? super CommandMessage<?>> handler,
                                 CommandCallback<? super C, ? super R> callback) {
        executor.execute(() -> super.handle(command, handler, callback));
    }

    /**
     * Shuts down the Executor used to asynchronously dispatch incoming commands. If the {@code Executor} provided
     * in the constructor does not implement {@code ExecutorService}, this method does nothing.
     */
    public void shutdown() {
        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
            try {
                ((ExecutorService) executor).awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // we've been interrupted. Reset the interruption flag and continue
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Builder class to instantiate a {@link AsynchronousCommandBus}.
     * <p>
     * The {@link TransactionManager}, {@link MessageMonitor}, {@link RollbackConfiguration},
     * {@link DuplicateCommandHandlerResolver}, {@link CommandBusSpanFactory} to a {@link DefaultCommandBusSpanFactory}
     * backed by a {@link io.github.sophonhq.sophon.tracing.NoOpSpanFactory}, and {@link Executor} are respectively defaulted to
     * a {@link NoTransactionManager}, a {@link NoOpMessageMonitor}, a
     * {@link RollbackConfigurationType#UNCHECKED_EXCEPTIONS}, a
     * {@link DuplicateCommandHandlerResolution#logAndOverride()}, {@link io.github.sophonhq.sophon.tracing.NoOpSpanFactory} and
     * a {@link Executors#newCachedThreadPool}. The default {@code executor} uses an {@link SophonThreadFactory} to create
     * threads with a sensible naming scheme. The TransactionManager, MessageMonitor, RollbackConfiguration and Executor
     * are <b>hard requirements</b>. Thus setting them to {@code null} will result in an
     * {@link SophonConfigurationException}.
     */
    public static class Builder extends SimpleCommandBus.Builder {

        private Executor executor = Executors.newCachedThreadPool(
                new SophonThreadFactory(AsynchronousCommandBus.class.getSimpleName())
        );

        @Override
        public Builder transactionManager(@NonNull TransactionManager transactionManager) {
            super.transactionManager(transactionManager);
            return this;
        }

        @Override
        public Builder messageMonitor(@NonNull MessageMonitor<? super CommandMessage<?>> messageMonitor) {
            super.messageMonitor(messageMonitor);
            return this;
        }

        @Override
        public Builder rollbackConfiguration(@NonNull RollbackConfiguration rollbackConfiguration) {
            super.rollbackConfiguration(rollbackConfiguration);
            return this;
        }

        @Override
        public Builder defaultCommandCallback(@NonNull CommandCallback<Object, Object> defaultCommandCallback) {
            super.defaultCommandCallback(defaultCommandCallback);
            return this;
        }

        @Override
        public Builder duplicateCommandHandlerResolver(
                @NonNull DuplicateCommandHandlerResolver duplicateCommandHandlerResolver) {
            super.duplicateCommandHandlerResolver(duplicateCommandHandlerResolver);
            return this;
        }

        @Override
        public Builder spanFactory(@NonNull SpanFactory spanFactory) {
            super.spanFactory(spanFactory);
            return this;
        }

        @Override
        public Builder spanFactory(@NonNull CommandBusSpanFactory spanFactory) {
            super.spanFactory(spanFactory);
            return this;
        }

        /**
         * Sets the {@link Executor} which processes the Command dispatching threads.
         *
         * @param executor a {@link Executor} to processes the Command dispatching threads
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder executor(Executor executor) {
            assertNonNull(executor, "Executor may not be null");
            this.executor = executor;
            return this;
        }

        /**
         * Initializes a {@link AsynchronousCommandBus} as specified through this Builder.
         *
         * @return a {@link AsynchronousCommandBus} as specified through this Builder
         */
        public AsynchronousCommandBus build() {
            return new AsynchronousCommandBus(this);
        }
    }
}
