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

package org.sophonhq.sophon.eventhandling;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.utils.MockException;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropagatingErrorHandlerTest {

    private PropagatingErrorHandler testSubject = PropagatingErrorHandler.instance();

    @Test
    void handleErrorRethrowsOriginalWhenError() throws Exception {
        ErrorContext context = new ErrorContext("test", new MockError(), Collections.emptyList());

        assertThatThrownBy(() -> testSubject.handleError(context)).isInstanceOf(MockError.class);
    }

    @Test
    void handleErrorRethrowsOriginalWhenException() throws Exception {
        ErrorContext context = new ErrorContext("test", new MockException(), Collections.emptyList());

        assertThatThrownBy(() -> testSubject.handleError(context)).isInstanceOf(MockException.class);
    }

    @Test
    void handleErrorWrapsOriginalWhenThrowable() throws Exception {
        ErrorContext context = new ErrorContext("test", new Throwable("Unknown"), Collections.emptyList());

        assertThatThrownBy(() -> testSubject.handleError(context)).isInstanceOf(EventProcessingException.class);
    }

    private static class MockError extends Error {
        public MockError() {super("Mock");}
    }
}
