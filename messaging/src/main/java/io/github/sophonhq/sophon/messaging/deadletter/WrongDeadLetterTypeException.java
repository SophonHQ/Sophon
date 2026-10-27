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

package io.github.sophonhq.sophon.messaging.deadletter;

import io.github.sophonhq.sophon.common.SophonException;

/**
 * Exception representing that a wrong dead letter was provided to the queue. All
 * {@link io.github.sophonhq.sophon.messaging.deadletter.DeadLetter}s supplied back to the
 * {@link io.github.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueue}, for example the
 * {@link io.github.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueue#evict(DeadLetter)} method, should be the
 * original supplied by the queue in the first place.
 *
 * @author Mitchell Herrijgers
 * @since axon-4.6.0
 */
public class WrongDeadLetterTypeException extends SophonException {

    /**
     * Constructs a {@code WrongDeadLetterTypeException} with the provided {@code message}.
     *
     * @param message The message containing more details about the cause.
     */
    public WrongDeadLetterTypeException(String message) {
        super(message);
    }
}
