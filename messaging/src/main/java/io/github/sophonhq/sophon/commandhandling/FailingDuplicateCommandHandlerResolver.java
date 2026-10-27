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
 * Implementation of {@link DuplicateCommandHandlerResolver} that throws a {@link DuplicateCommandHandlerSubscriptionException}
 * when a duplicate registration is detected.
 *
 * @author Allard Buijze
 * @since axon-4.2
 */
public class FailingDuplicateCommandHandlerResolver implements DuplicateCommandHandlerResolver {

    private static final FailingDuplicateCommandHandlerResolver INSTANCE = new FailingDuplicateCommandHandlerResolver();

    /**
     * Returns a DuplicateCommandHandlerResolver that throws an exception when a duplicate registration is detected
     *
     * @return a DuplicateCommandHandlerResolver that throws an exception when a duplicate registration is detected
     */
    public static FailingDuplicateCommandHandlerResolver instance() {
        return INSTANCE;
    }

    private FailingDuplicateCommandHandlerResolver() {
    }

    @Override
    public MessageHandler<? super CommandMessage<?>> resolve(@NonNull String commandName,
                                                             @NonNull MessageHandler<? super CommandMessage<?>> registeredHandler,
                                                             @NonNull MessageHandler<? super CommandMessage<?>> candidateHandler) {
        throw new DuplicateCommandHandlerSubscriptionException(commandName, registeredHandler, candidateHandler);
    }
}
