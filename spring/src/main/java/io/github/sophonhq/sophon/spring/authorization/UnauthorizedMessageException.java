/*
 * Copyright (c) 2010-2024. Axon Framework
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

package io.github.sophonhq.sophon.spring.authorization;

import io.github.sophonhq.sophon.common.SophonNonTransientException;

/**
 * Exception indicating that a message has been rejected due to a lack of authorization.
 *
 * @author Roald Bankras
 * @since axon-4.11.0
 */
public class UnauthorizedMessageException extends SophonNonTransientException {

    /**
     * Construct the exception with the given {$code message}.
     *
     * @param message The message describing the cause.
     */
    public UnauthorizedMessageException(String message) {
        super(message);
    }
}
