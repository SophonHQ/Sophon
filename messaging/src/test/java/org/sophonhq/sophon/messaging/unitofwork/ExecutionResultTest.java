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

package org.sophonhq.sophon.messaging.unitofwork;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.messaging.ResultMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.messaging.GenericResultMessage.asResultMessage;

/**
 * @author Rene de Waele
 */
class ExecutionResultTest {

    @Test
    void normalExecutionResult() {
        Object resultPayload = new Object();
        ResultMessage<Object> result = asResultMessage(resultPayload);
        ExecutionResult subject = new ExecutionResult(result);
        assertThat(subject.getResult()).isSameAs(result);
        assertThat(subject.isExceptionResult()).isFalse();
        assertThat(subject.getExceptionResult()).isNull();
    }

    @Test
    void uncheckedExceptionResult() {
        RuntimeException mockException = new RuntimeException();
        ResultMessage<RuntimeException> resultMessage = asResultMessage(mockException);
        ExecutionResult subject = new ExecutionResult(resultMessage);
        assertThat(subject.isExceptionResult()).isTrue();
        assertThat(subject.getExceptionResult()).isSameAs(mockException);
        assertThat(subject.getResult().exceptionResult()).isSameAs(mockException);
    }

    @Test
    void checkedExceptionResult() {
        Exception mockException = new Exception();
        ResultMessage<Exception> resultMessage = asResultMessage(mockException);
        ExecutionResult subject = new ExecutionResult(resultMessage);
        assertThat(subject.isExceptionResult()).isTrue();
        assertThat(subject.getExceptionResult()).isSameAs(mockException);
        assertThat(subject.getResult().exceptionResult()).isSameAs(mockException);
    }
}