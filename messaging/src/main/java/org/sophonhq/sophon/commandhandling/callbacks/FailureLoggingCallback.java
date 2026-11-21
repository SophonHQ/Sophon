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

package org.sophonhq.sophon.commandhandling.callbacks;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.sophonhq.sophon.commandhandling.CommandCallback;
import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.commandhandling.CommandResultMessage;

/**
 * A {@link CommandCallback} implementation that concisely logs failed commands. The full stacktrace is not logged.
 * Use {@link CommandCallback#wrap(CommandCallback)} to combine it with another callback that receives the full
 * exception.
 *
 * @param <C> The type of payload of the command
 * @param <R> The return value of the command handler
 */
public class FailureLoggingCallback<C, R> implements CommandCallback<C, R> {

    private final Logger logger;

    /**
     * Initialize the callback to log failures on the given {@code logger} (on warn level).
     *
     * @param logger   The logger to log exceptions on
     */
    public FailureLoggingCallback(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void onResult(@NonNull CommandMessage<? extends C> commandMessage,
                         @NonNull CommandResultMessage<? extends R> commandResultMessage) {
        commandResultMessage.optionalExceptionResult()
                            .ifPresent(cause ->
                                               logger.warn("Command '{}' resulted in {}({})",
                                                           commandMessage.getCommandName(),
                                                           cause.getClass().getName(),
                                                           cause.getMessage()));
    }
}
