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

package io.github.sophonhq.sophon.eventhandling;

import io.github.sophonhq.sophon.commandhandling.GenericCommandMessage;
import io.github.sophonhq.sophon.messaging.unitofwork.BatchingUnitOfWork;
import io.github.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sophonhq.sophon.utils.EventTestUtils.createEvents;
import static io.github.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;

class ConcludesBatchParameterResolverFactoryTest {

    private ConcludesBatchParameterResolverFactory subject = new ConcludesBatchParameterResolverFactory();

    @Test
    void createInstance() throws Exception {
        Method method = getClass().getDeclaredMethod("handle", String.class, Boolean.class);
        assertSame(subject.getResolver(), subject.createInstance(method, method.getParameters(), 1));
        method = getClass().getDeclaredMethod("handlePrimitive", String.class, boolean.class);
        assertSame(subject.getResolver(), subject.createInstance(method, method.getParameters(), 1));
    }

    @Test
    void onlyMatchesEventMessages() {
        assertTrue(subject.matches(asEventMessage("testEvent")));
        assertFalse(subject.matches(new GenericCommandMessage<>("testCommand")));
    }

    @Test
    void resolvesToTrueWithoutUnitOfWork() {
        assertTrue(subject.resolveParameterValue(asEventMessage("testEvent")));
    }

    @Test
    void resolvesToTrueWithRegularUnitOfWork() {
        EventMessage<?> event = asEventMessage("testEvent");
        DefaultUnitOfWork.startAndGet(event).execute(() -> assertTrue(subject.resolveParameterValue(event)));
    }

    @Test
    void resolvesToFalseWithBatchingUnitOfWorkIfMessageIsNotLast() {
        List<? extends EventMessage<?>> events = createEvents(5);
        new BatchingUnitOfWork<>(events).execute(() -> assertFalse(subject.resolveParameterValue(events.get(0))));
    }

    @Test
    void resolvesToFalseWithBatchingUnitOfWorkIfMessageIsLast() {
        List<? extends EventMessage<?>> events = createEvents(5);
        new BatchingUnitOfWork<>(events).execute(() -> assertTrue(subject.resolveParameterValue(events.get(4))));
    }

    @SuppressWarnings("unused")
    private void handle(String eventPayload, @ConcludesBatch Boolean concludesBatch) {
    }

    @SuppressWarnings("unused")
    private void handlePrimitive(String eventPayload, @ConcludesBatch boolean concludesBatch) {
    }

}