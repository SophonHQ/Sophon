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

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.messaging.DefaultInterceptorChain;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class UnitOfWorkTimeoutInterceptorTest {

    @Test
    void interruptsUnitOfWorkThatTakesTooLong() throws Exception {
        UnitOfWorkTimeoutInterceptor testSubject = new UnitOfWorkTimeoutInterceptor("MyUnitOfWork", 100, 50, 10);

        DefaultUnitOfWork<EventMessage<String>> uow = new DefaultUnitOfWork<>(
                GenericEventMessage.asEventMessage("test")
        );
        DefaultInterceptorChain<EventMessage<String>> interceptorChain = new DefaultInterceptorChain<>(
                uow,
                Collections.singletonList(testSubject),
                message -> {
                    Thread.sleep(300);
                    return null;
                });
        uow.executeWithResult(interceptorChain::proceed);
        assertThat(uow.isRolledBack()).isTrue();
        assertThat(uow.getExecutionResult().isExceptionResult()).isTrue();
        assertThat(uow.getExecutionResult().getExceptionResult()).isInstanceOf(SophonTimeoutException.class);
    }


    @Test
    void doesNotInterruptWorkWithinTime() throws Exception {
        UnitOfWorkTimeoutInterceptor testSubject = new UnitOfWorkTimeoutInterceptor("MyUnitOfWork", 100, 50, 10);

        DefaultUnitOfWork<EventMessage<String>> uow = new DefaultUnitOfWork<>(
                GenericEventMessage.asEventMessage("test")
        );
        DefaultInterceptorChain<EventMessage<String>> interceptorChain = new DefaultInterceptorChain<>(
                uow,
                Collections.singletonList(testSubject),
                message -> {
                    Thread.sleep(80);
                    return null;
                });
        uow.executeWithResult(interceptorChain::proceed);
        assertThat(uow.isRolledBack()).isFalse();
        assertThat(uow.getExecutionResult().isExceptionResult()).isFalse();
    }
}