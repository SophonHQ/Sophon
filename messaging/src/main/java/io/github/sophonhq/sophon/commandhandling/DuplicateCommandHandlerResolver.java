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

package io.github.sophonhq.sophon.commandhandling;

import io.github.sophonhq.sophon.messaging.MessageHandler;
import org.jspecify.annotations.NonNull;

/**
 * Functional interface towards resolving the occurrence of a duplicate command handler being subscribed. As such it
 * ingests two {@link MessageHandler} instances and returns another one as the resolution.
 *
 * @author Steven van Beelen
 * @since axon-4.2
 */
@FunctionalInterface
public interface DuplicateCommandHandlerResolver {

    /**
     * Chooses what to do when a duplicate handler is registered, returning the handler that should be selected for
     * command handling, or otherwise throwing an exception to reject registration altogether.
     *
     * @param commandName       The name of the Command for which the duplicate was detected
     * @param registeredHandler the {@link MessageHandler} previously registered with the Command Bus
     * @param candidateHandler  the {@link MessageHandler} that is newly registered and conflicts with the existing registration
     * @return the resolved {@link MessageHandler}. Could be the {@code registeredHandler}, the {@code candidateHandler} or
     * another handler entirely
     * @throws RuntimeException when registration should fail
     */
    MessageHandler<? super CommandMessage<?>> resolve(@NonNull String commandName,
                                                      @NonNull MessageHandler<? super CommandMessage<?>> registeredHandler,
                                                      @NonNull MessageHandler<? super CommandMessage<?>> candidateHandler);
}
