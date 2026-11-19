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

package org.sophonhq.sophon.eventhandling;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.GenericCommandMessage;
import org.sophonhq.sophon.messaging.unitofwork.BatchingUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.utils.EventTestUtils.createEvents;
import static org.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;

class ConcludesBatchParameterResolverFactoryTest {

    private ConcludesBatchParameterResolverFactory subject = new ConcludesBatchParameterResolverFactory();

    @Test
    void createInstance() throws Exception {
        Method method = getClass().getDeclaredMethod("handle", String.class, Boolean.class);
        assertThat(subject.createInstance(method, method.getParameters(), 1)).isSameAs(subject.getResolver());
        method = getClass().getDeclaredMethod("handlePrimitive", String.class, boolean.class);
        assertThat(subject.createInstance(method, method.getParameters(), 1)).isSameAs(subject.getResolver());
    }

    @Test
    void onlyMatchesEventMessages() {
        assertThat(subject.matches(asEventMessage("testEvent"))).isTrue();
        assertThat(subject.matches(new GenericCommandMessage<>("testCommand"))).isFalse();
    }

    @Test
    void resolvesToTrueWithoutUnitOfWork() {
        assertThat(subject.resolveParameterValue(asEventMessage("testEvent"))).isTrue();
    }

    @Test
    void resolvesToTrueWithRegularUnitOfWork() {
        EventMessage<?> event = asEventMessage("testEvent");
        DefaultUnitOfWork.startAndGet(event).execute(() -> assertThat(subject.resolveParameterValue(event)).isTrue());
    }

    @Test
    void resolvesToFalseWithBatchingUnitOfWorkIfMessageIsNotLast() {
        List<? extends EventMessage<?>> events = createEvents(5);
        new BatchingUnitOfWork<>(events)
                .execute(() -> assertThat(subject.resolveParameterValue(events.get(0))).isFalse());
    }

    @Test
    void resolvesToFalseWithBatchingUnitOfWorkIfMessageIsLast() {
        List<? extends EventMessage<?>> events = createEvents(5);
        new BatchingUnitOfWork<>(events)
                .execute(() -> assertThat(subject.resolveParameterValue(events.get(4))).isTrue());
    }

    @SuppressWarnings("unused")
    private void handle(String eventPayload, @ConcludesBatch Boolean concludesBatch) {
    }

    @SuppressWarnings("unused")
    private void handlePrimitive(String eventPayload, @ConcludesBatch boolean concludesBatch) {
    }

}