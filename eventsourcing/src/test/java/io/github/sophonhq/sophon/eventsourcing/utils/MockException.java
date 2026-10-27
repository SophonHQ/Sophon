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

package io.github.sophonhq.sophon.eventsourcing.utils;

/**
 * Mock exception that provides no stack trace.
 *
 * @author Allard Buijze
 * @since axon-2.0
 */
public class MockException extends RuntimeException {

    public MockException(String message) {
        super(message);
    }

    public MockException() {
        super("Mock");
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return new StackTraceElement[]{};
    }
}
