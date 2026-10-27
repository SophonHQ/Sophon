/*
 * Copyright (c) 2010-2025. Axon Framework
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
package org.sophonhq.sophon.messaging.timeout;

/**
 * Exception indicated that a Sophon-specific task has timed out. This task can represent an entire
 * {@link org.sophonhq.sophon.messaging.unitofwork.UnitOfWork} or the handling of a specific
 * {@link org.sophonhq.sophon.messaging.Message}.
 *
 * @author Mitchell Herrijgers
 * @see SophonTimeLimitedTask
 * @see SophonTaskJanitor
 * @since axon-4.11.3
 */
public class SophonTimeoutException extends RuntimeException {

    /**
     * Initializes an {@link SophonTimeoutException} with the given {@code message}.
     *
     * @param message The message describing the cause of the timeout.
     */
    public SophonTimeoutException(String message) {
        super(message);
    }
}
