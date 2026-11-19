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

package org.sophonhq.sophon.commandhandling.callbacks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.commandhandling.CommandResultMessage;
import org.sophonhq.sophon.commandhandling.GenericCommandMessage;
import org.sophonhq.sophon.messaging.ResultMessage;
import org.sophonhq.sophon.utils.MockException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.commandhandling.GenericCommandResultMessage.asCommandResultMessage;

/**
 * @author Allard Buijze
 */
class FutureCallbackTest {

    private static final CommandMessage<Object> COMMAND_MESSAGE = GenericCommandMessage.asCommandMessage("Test");
    private static final CommandResultMessage<String> COMMAND_RESPONSE_MESSAGE =
            asCommandResultMessage("Hello world");
    private static final int THREAD_JOIN_TIMEOUT = 1000;
    private volatile FutureCallback<Object, Object> testSubject;
    private volatile Object resultFromParallelThread;

    @BeforeEach
    void setUp() {
        testSubject = new FutureCallback<>();
    }

    @Test
    void onSuccess() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                resultFromParallelThread = testSubject.get();
            } catch (Exception e) {
                resultFromParallelThread = e;
            }
        });
        t.start();
        assertThat(t.isAlive()).isTrue();
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);
        t.join(THREAD_JOIN_TIMEOUT);
        assertThat(resultFromParallelThread).isEqualTo(COMMAND_RESPONSE_MESSAGE);
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    void onFailure() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                resultFromParallelThread = testSubject.get();
            } catch (Exception e) {
                resultFromParallelThread = e;
            }
        });
        t.start();
        assertThat(t.isAlive()).isTrue();
        RuntimeException exception = new MockException();
        testSubject.onResult(COMMAND_MESSAGE, asCommandResultMessage(exception));
        t.join(THREAD_JOIN_TIMEOUT);
        assertThat(resultFromParallelThread).isInstanceOf(CommandResultMessage.class);
        assertThat(((CommandResultMessage) resultFromParallelThread).exceptionResult()).isEqualTo(exception);
    }

    @Test
    void onSuccessForLimitedTime_Timeout() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                resultFromParallelThread = testSubject.get(1, TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                resultFromParallelThread = e;
            }
        });
        t.start();
        t.join(1000);
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);
        assertThat(resultFromParallelThread).isInstanceOf(TimeoutException.class);
    }

    @Test
    void onResultReturnsMessageWithTimeoutExceptionOnTimeout() throws InterruptedException {
        Thread t = new Thread(() -> {
            resultFromParallelThread = testSubject.getResult(1, TimeUnit.NANOSECONDS);
        });
        t.start();
        t.join(1000);
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);
        assertThat(resultFromParallelThread).isInstanceOf(ResultMessage.class);
        assertThat(((ResultMessage) resultFromParallelThread).exceptionResult()).isInstanceOf(TimeoutException.class);
    }

    @Test
    void onResultUnwrapsExecutionResult() throws InterruptedException {
        Thread t = new Thread(() -> {
            resultFromParallelThread = testSubject.getResult();
        });
        t.start();
        testSubject.completeExceptionally(new MockException("Mocking an exception"));
        t.join();
        assertThat(resultFromParallelThread).isInstanceOf(ResultMessage.class);
        assertThat(((ResultMessage) resultFromParallelThread).exceptionResult()).isInstanceOf(MockException.class);
    }

    @Test
    void getThrowsExecutionException() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                testSubject.get();
            } catch (Exception e) {
                resultFromParallelThread = e;
            }
        });
        t.start();
        testSubject.completeExceptionally(new MockException("Mocking an exception"));
        t.join();
        assertThat(resultFromParallelThread).isInstanceOf(ExecutionException.class);
    }

    @Test
    void onSuccessForLimitedTime_InTime() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                resultFromParallelThread = testSubject.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                resultFromParallelThread = e;
            }
        });
        t.start();
        assertThat(t.isAlive()).isTrue();
        assertThat(testSubject.isDone()).isFalse();
        testSubject.onResult(COMMAND_MESSAGE, COMMAND_RESPONSE_MESSAGE);
        assertThat(testSubject.isDone()).isTrue();
        t.join(THREAD_JOIN_TIMEOUT);
        assertThat(resultFromParallelThread).isEqualTo(COMMAND_RESPONSE_MESSAGE);
    }
}
