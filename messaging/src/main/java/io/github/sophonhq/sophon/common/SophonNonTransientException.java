/*
 * Copyright (c) 2010-2018. Axon Framework
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

package io.github.sophonhq.sophon.common;

/**
 * Exception indicating an error has been cause that cannot be resolved without intervention. Retrying the operation
 * that threw the exception will most likely result in the same exception being thrown.
 * <p/>
 * Examples of such errors are programming errors and version conflicts.
 *
 * @author Allard Buijze
 * @since axon-0.6
 */
public abstract class SophonNonTransientException extends SophonException {

    private static final long serialVersionUID = -2119569988731244940L;

    /**
     * Indicates whether the given {@code throwable} is a SophonNonTransientException exception or indicates to be
     * caused by one.
     *
     * @param throwable The throwable to inspect
     * @return {@code true} if the given instance or one of it's causes is an instance of
     *         SophonNonTransientException, otherwise {@code false}
     */
    public static boolean isCauseOf(Throwable throwable) {
        return throwable != null
                && (throwable instanceof SophonNonTransientException || isCauseOf(throwable.getCause()));
    }

    /**
     * Initializes the exception using the given {@code message}.
     *
     * @param message The message describing the exception
     */
    public SophonNonTransientException(String message) {
        super(message);
    }

    /**
     * Initializes the exception using the given {@code message} and {@code cause}.
     *
     * @param message The message describing the exception
     * @param cause   The underlying cause of the exception
     */
    public SophonNonTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
