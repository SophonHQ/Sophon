/*
 * Copyright (c) 2010-2021. Axon Framework
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

package io.github.sophonhq.sophon.modelling.command;

import io.github.sophonhq.sophon.common.lock.Lock;
import io.github.sophonhq.sophon.messaging.GenericMessage;
import io.github.sophonhq.sophon.messaging.Message;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the basics of the {@link LockAwareAggregate}.
 *
 * @author Steven van Beelen
 */
class LockAwareAggregateTest {

    @SuppressWarnings("unchecked")
    private final Aggregate<Object> mockAggregate = mock(Aggregate.class);

    private final Lock mockLock = mock(Lock.class);
    private final AtomicBoolean lockSupplierInvoked = new AtomicBoolean(false);
    private final Supplier<Lock> lockSupplier = () -> {
        lockSupplierInvoked.set(true);
        return mockLock;
    };

    private final LockAwareAggregate<Object, Aggregate<Object>> testSubject =
            new LockAwareAggregate<>(mockAggregate, lockSupplier);

    @Test
    void getWrappedAggregate() {
        assertEquals(mockAggregate, testSubject.getWrappedAggregate());
    }

    @Test
    void isLockHeld() {
        when(mockLock.isHeld()).thenReturn(true);

        assertTrue(testSubject.isLockHeld());
    }

    @Test
    void typeMethodInvokesWrappedAggregate() {
        testSubject.type();

        verify(mockAggregate).type();
    }

    @Test
    void identifierMethodInvokesWrappedAggregate() {
        testSubject.identifier();

        verify(mockAggregate).identifier();
    }

    @Test
    void versionMethodInvokesWrappedAggregate() {
        testSubject.version();

        verify(mockAggregate).version();
    }

    @Test
    void handleMethodInvokesWrappedAggregateAndInspectsLock() throws Exception {
        Message<?> testMessage = GenericMessage.asMessage("some-message");

        testSubject.handle(testMessage);

        verify(mockAggregate).handle(testMessage);
        assertTrue(lockSupplierInvoked.get());
    }

    @Test
    void invokeMethodInvokesWrappedAggregateAndInspectsLock() {
        testSubject.invoke(someField -> "some-return");

        verify(mockAggregate).invoke(any());
        assertTrue(lockSupplierInvoked.get());
    }

    @Test
    void executeMethodInvokesWrappedAggregateAndInspectsLock() {
        testSubject.execute(someField -> {
        });

        verify(mockAggregate).execute(any());
        assertTrue(lockSupplierInvoked.get());
    }

    @Test
    void isDeletedMethodInvokesWrappedAggregate() {
        testSubject.isDeleted();

        verify(mockAggregate).isDeleted();
    }

    @Test
    void rootTypeMethodInvokesWrappedAggregate() {
        testSubject.rootType();

        verify(mockAggregate).rootType();
    }
}