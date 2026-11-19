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

package org.sophonhq.sophon.commandhandling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.UnitOfWork;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Allard Buijze
 */
class CurrentUnitOfWorkParameterResolverFactoryTest {

    private CurrentUnitOfWorkParameterResolverFactory testSubject;
    private Method method;

    @BeforeEach
    void setUp() throws Exception {
        testSubject = new CurrentUnitOfWorkParameterResolverFactory();
        method = getClass().getMethod("equals", Object.class);
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public void someMethod(UnitOfWork unitOfWork) {
    }

    @Test
    void createInstance() throws Exception {
        Method someMethod = getClass().getMethod("someMethod", UnitOfWork.class);

        assertThat(testSubject.createInstance(method, method.getParameters(), 0)).isNull();
        assertThat(testSubject.createInstance(someMethod, someMethod.getParameters(), 0)).isSameAs(testSubject);
    }

    @Test
    void resolveParameterValue() {
        DefaultUnitOfWork.startAndGet(null);
        try {
            assertThat(testSubject.resolveParameterValue(mock(GenericCommandMessage.class)))
                    .isSameAs(CurrentUnitOfWork.get());
        } finally {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void resolveParameterValueWithoutActiveUnitOfWork() {
        assertThat(testSubject.resolveParameterValue(mock(GenericCommandMessage.class))).isNull();
    }

    @Test
    void matches() {
        assertThat(testSubject.matches(mock(GenericCommandMessage.class))).isTrue();
        DefaultUnitOfWork.startAndGet(null);
        try {
            assertThat(testSubject.matches(mock(Message.class))).isTrue();
            assertThat(testSubject.matches(mock(EventMessage.class))).isTrue();
            assertThat(testSubject.matches(mock(GenericEventMessage.class))).isTrue();
            assertThat(testSubject.matches(mock(GenericCommandMessage.class))).isTrue();
        } finally {
            CurrentUnitOfWork.get().rollback();
        }
    }
}
