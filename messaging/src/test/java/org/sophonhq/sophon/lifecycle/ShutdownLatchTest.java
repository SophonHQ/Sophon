/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.sophonhq.sophon.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests targeted towards the {@link ShutdownLatch}.
 *
 * @author Steven van Beelen
 */
class ShutdownLatchTest {

    private final ShutdownLatch testSubject = new ShutdownLatch();

    @Test
    void initializeCancelsEarlierShutdown() {
        testSubject.registerActivity();

        CompletableFuture<Void> latch = testSubject.initiateShutdown();
        testSubject.initialize();

        assertThat(latch.isCompletedExceptionally()).isTrue();
    }

    @Test
    void incrementThrowsShutdownInProgressExceptionIfShuttingDown() {
        testSubject.initiateShutdown();

        assertThatThrownBy(testSubject::registerActivity).isInstanceOf(ShutdownInProgressException.class);
    }

    @Test
    void decrementCompletesWaitProcess() {
        ShutdownLatch.ActivityHandle activityHandle = testSubject.registerActivity();

        CompletableFuture<Void> latch = testSubject.initiateShutdown();

        assertThat(latch.isDone()).isFalse();

        activityHandle.end();

        assertThat(latch.isDone()).isTrue();
    }

    @Test
    void isShuttingDownIsFalseForNonAwaitedLatch() {
        assertThat(testSubject.isShuttingDown()).isFalse();
    }

    @Test
    void initiateShutdownOnEmptyLatchOpensImmediately() {
        CompletableFuture<Void> latch = testSubject.initiateShutdown();

        assertThat(latch.isDone()).isTrue();
    }

    @Test
    void isShuttingDownIsTrueForAwaitedLatch() {
        CompletableFuture<Void> latch = testSubject.initiateShutdown();

        assertThat(testSubject.isShuttingDown()).isTrue();
        assertThat(latch.isDone()).isTrue();
    }

    @Test
    void isShuttingDownThrowsSuppliedExceptionForAwaitedLatch() {
        CompletableFuture<Void> latch = testSubject.initiateShutdown();

        assertThatThrownBy(() -> testSubject.ifShuttingDown(SomeException::new)).isInstanceOf(SomeException.class);
        assertThat(latch.isDone()).isTrue();
    }

    @Test
    void subsequentActivityHandleEndCallsDoNotInfluenceOtherHandles() {
        ShutdownLatch.ActivityHandle handleOne = testSubject.registerActivity();
        ShutdownLatch.ActivityHandle handleTwo = testSubject.registerActivity();

        // Calling end twice on the first handle should not make the latch closed
        handleOne.end();
        handleOne.end();

        CompletableFuture<Void> latch = testSubject.initiateShutdown();
        assertThat(latch.isDone()).isFalse();
        // Only ending to other activity handle will open the latch
        handleTwo.end();
        assertThat(latch.isDone()).isTrue();
    }

    private static class SomeException extends RuntimeException {

    }
}