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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SophonTimeLimitedTask}.
 *
 * @author Mitchell Herrijgers
 */
class SophonTimeLimitedTaskTest {

    @AfterEach
    void tearDown() throws InterruptedException {
        //noinspection ResultOfMethodCallIgnored | Awaiting termination to ensure none of the SophonTimeLimitedTask hang
        SophonTaskJanitor.INSTANCE
                .awaitTermination(250, TimeUnit.MILLISECONDS);
    }

    @Test
    void correctlyInterruptsTaskWhenNoWarningWasConfiguredOnUncustomizedConstructor() {
        SophonTimeLimitedTask testSubject = new SophonTimeLimitedTask("My test task", 100, 100, 1);

        assertThatThrownBy(() -> {
            testSubject.start();
            // Even though the timeout is 100ms, the InterruptedException apparently needs time to travel up.
            // We accept a 150ms delay.
            Thread.sleep(150);
        }).isInstanceOf(InterruptedException.class);

        assertThat(testSubject.isInterrupted()).isTrue();
        assertThat(testSubject.isCompleted()).isFalse();
    }


    @Test
    void correctlyInterruptsTaskWithWarningWasConfiguredOnUncustomizedConstructor() {
        SophonTimeLimitedTask testSubject = new SophonTimeLimitedTask("My test task", 100, 50, 10);

        assertThatThrownBy(() -> {
            testSubject.start();
            // Even though the timeout is 100ms, the InterruptedException apparently needs time to travel up.
            // We accept a 150ms delay.
            Thread.sleep(150);
        }).isInstanceOf(InterruptedException.class);

        assertThat(testSubject.isInterrupted()).isTrue();
        assertThat(testSubject.isCompleted()).isFalse();
    }

    @Test
    void correctlyLogsWarningsAndInterruptsWhenWarningWasConfiguredOnCustomizedConstructor() {
        Logger logger = spy(LoggerFactory.getLogger("MyLogger"));
        SophonTimeLimitedTask testSubject =
                new SophonTimeLimitedTask("My test task", 1000, 100, 100, SophonTaskJanitor.INSTANCE, logger);

        assertThatThrownBy(() -> {
            testSubject.start();
            // Even though the timeout is 100ms, the InterruptedException apparently needs time to travel up.
            // We accept a 1500ms delay.
            Thread.sleep(1500);
        }).isInstanceOf(InterruptedException.class);

        assertThat(testSubject.isInterrupted()).isTrue();
        assertThat(testSubject.isCompleted()).isFalse();
        verify(logger, atLeast(8)).warn(anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void doesNotInterruptButLogsWarningsIfProcessWasCompletedBeforeTimeout() throws InterruptedException {
        Logger logger = spy(LoggerFactory.getLogger("MyLogger"));
        SophonTimeLimitedTask testSubject =
                new SophonTimeLimitedTask("My test task", 1000, 100, 100, SophonTaskJanitor.INSTANCE, logger);

        testSubject.start();
        // Even though the timeout is 100ms, the InterruptedException apparently needs time to travel up.
        // We accept a 500ms delay.
        Thread.sleep(500);

        assertThat(testSubject.isInterrupted()).isFalse();
        assertThat(testSubject.isCompleted()).isFalse();
        // Complete manually to ensure it does not block the SophonTaskJanitor!
        testSubject.complete();
        verify(logger, atLeast(3)).warn(anyString(), any(), any(), any(), any(), any());
    }
}