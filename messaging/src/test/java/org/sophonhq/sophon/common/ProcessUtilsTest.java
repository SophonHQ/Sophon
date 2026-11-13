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

package org.sophonhq.sophon.common;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Marc Gathier
 */
class ProcessUtilsTest {

    @Test
    void executeWithRetry() {
        AtomicLong retryCounter = new AtomicLong();

        ProcessUtils.executeWithRetry(() -> {
                                          if (retryCounter.getAndIncrement() < 5) {
                                              throw new IllegalArgumentException("Waiting for 5");
                                          }
                                      }, e -> ExceptionUtils.findException(e, IllegalArgumentException.class).isPresent(),
                                      100, TimeUnit.MILLISECONDS, 10);

        assertEquals(6, retryCounter.get());
    }

    @Test
    void executeWithRetryStops() {
        AtomicLong retryCounter = new AtomicLong();

        assertThrows(IllegalArgumentException.class, () ->
                ProcessUtils.executeWithRetry(() -> {
                            if (retryCounter.getAndIncrement() < 11) {
                                throw new IllegalArgumentException("Waiting for 11");
                            }
                        },
                        e -> ExceptionUtils.findException(e, IllegalArgumentException.class).isPresent(),
                        100,
                        TimeUnit.MILLISECONDS,
                        10)
        );
    }

    @Test
    void executeWithRetryImmediatelyStopsOnOther() {
        AtomicLong retryCounter = new AtomicLong();

        try {
            ProcessUtils.executeWithRetry(() -> {
                if (retryCounter.getAndIncrement() < 11) {
                    throw new IllegalArgumentException("Waiting for 11");
                }
            }, e -> false, 100, TimeUnit.MILLISECONDS, 10);
            fail("Should not get here");
        } catch (Exception ex) {
            assertTrue(ex instanceof IllegalArgumentException);
            assertEquals(1, retryCounter.get());
        }
    }

    @Test
    void executeUntilTrueRetries(){
        AtomicLong retryCounter = new AtomicLong();
        ProcessUtils.executeUntilTrue(() -> retryCounter.getAndIncrement() >= 1, 10L, 10L);
        assertEquals(2, retryCounter.get());
    }

    @Test
    void executeUntilTrueThrowsWhenMaxRetriesReached(){
        AtomicLong retryCounter = new AtomicLong();
        assertThrows(ProcessRetriesExhaustedException.class, () ->
                ProcessUtils.executeUntilTrue(() -> retryCounter.getAndIncrement() >= 100, 1L, 10L)
        );
        assertEquals(10, retryCounter.get());
    }

    @Nested
    class Safeguard {

        private ListAppender appender;
        private Logger logger;
        private Level previousLevel;
        private boolean previousAdditive;

        @BeforeEach
        void attachAppender() {
            appender = new ListAppender("ProcessUtilsTestAppender");
            appender.start();

            logger = (Logger) LogManager.getLogger(ProcessUtils.class);
            previousLevel = logger.getLevel();
            previousAdditive = logger.isAdditive();
            Configurator.setLevel(ProcessUtils.class.getName(), Level.DEBUG);
            logger.setAdditive(false);
            logger.addAppender(appender);
        }

        @AfterEach
        void detachAppender() {
            logger.removeAppender(appender);
            logger.setAdditive(previousAdditive);
            Configurator.setLevel(ProcessUtils.class.getName(), previousLevel);
            appender.stop();
        }

        private List<LogEvent> events() {
            return appender.getEvents();
        }

        @Test
        void executesTheGivenTask() {
            AtomicBoolean invoked = new AtomicBoolean(false);
            Runnable safeguarded = ProcessUtils.safeguard(() -> invoked.set(true), "error");

            safeguarded.run();

            assertTrue(invoked.get());
            assertTrue(events().isEmpty());
        }

        @Test
        void doesNotThrowAndLogsAtErrorLevelWhenTaskThrowsAnException() {
            RuntimeException exception = new RuntimeException("boom");
            Runnable safeguarded = ProcessUtils.safeguard(() -> {
                throw exception;
            }, "task failed");

            assertDoesNotThrow(safeguarded::run);

            assertEquals(1, events().size());
            LogEvent event = events().get(0);
            assertEquals(Level.ERROR, event.getLevel());
            assertEquals("task failed", event.getMessage().getFormattedMessage());
            assertSame(exception, event.getThrown());
        }

        @Test
        void doesNotThrowWhenTaskThrowsAnError() {
            Error error = new StackOverflowError("simulated");
            Runnable safeguarded = ProcessUtils.safeguard(() -> {
                throw error;
            }, "task failed with an error");

            assertDoesNotThrow(safeguarded::run);

            assertEquals(1, events().size());
            assertSame(error, events().get(0).getThrown());
        }

        @Test
        void canBeInvokedMultipleTimesIndependently() {
            AtomicLong invocationCount = new AtomicLong();
            Runnable safeguarded = ProcessUtils.safeguard(() -> {
                if (invocationCount.incrementAndGet() <= 2) {
                    throw new IllegalStateException("failure #" + invocationCount.get());
                }
            }, "repeated failure");

            safeguarded.run();
            safeguarded.run();
            safeguarded.run();

            // Two failures are logged, and the third invocation completes without logging.
            assertEquals(3, invocationCount.get());
            assertEquals(2, events().size());
            assertEquals("repeated failure", events().get(0).getMessage().getFormattedMessage());
            assertEquals("repeated failure", events().get(1).getMessage().getFormattedMessage());
        }
    }
}
