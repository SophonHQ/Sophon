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

package org.sophonhq.sophon.eventsourcing.conflictresolution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.commandhandling.GenericCommandMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.UnitOfWork;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConflictResolutionTest {

    private Method method;
    private ConflictResolution subject;
    private ConflictResolver conflictResolver;
    private CommandMessage<String> commandMessage = new GenericCommandMessage<>("test");

    @BeforeEach
    void setUp() throws Exception {
        method = getClass().getDeclaredMethod("handle", String.class, ConflictResolver.class);
        subject = new ConflictResolution();
        conflictResolver = mock(ConflictResolver.class);
        DefaultUnitOfWork.startAndGet(commandMessage);
    }

    @AfterEach
    void tearDown() {
        CurrentUnitOfWork.ifStarted(UnitOfWork::commit);
    }

    @Test
    void factoryMethod() {
        assertThat(subject.createInstance(method, method.getParameters(), 1)).isNotNull();
        assertThat(subject.createInstance(method, method.getParameters(), 0)).isNull();
    }

    @Test
    void resolve() {
        ConflictResolution.initialize(conflictResolver);
        assertThat(subject.matches(GenericEventMessage.asEventMessage("testEvent"))).isFalse();
        assertThat(subject.matches(commandMessage)).isTrue();
        assertThat(ConflictResolution.getConflictResolver()).isSameAs(conflictResolver);
        assertThat(subject.resolveParameterValue(commandMessage)).isSameAs(conflictResolver);
    }

    @Test
    void resolveWithoutInitializationReturnsNoConflictsResolver() {
        assertThat(subject.matches(commandMessage)).isTrue();
        assertThat(subject.resolveParameterValue(commandMessage)).isSameAs(NoConflictResolver.INSTANCE);
    }

    @SuppressWarnings("unused") //used in set up
    private void handle(String command, ConflictResolver conflictResolver) {
    }
}
