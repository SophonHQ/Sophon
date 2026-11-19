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

package org.sophonhq.sophon.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Allard Buijze
 */
class SophonThreadFactoryTest {

    private SophonThreadFactory testSubject;

    @Test
    void createWithThreadGroupByName() {
        testSubject = new SophonThreadFactory("test");
        Thread t1 = testSubject.newThread(new NoOpRunnable());
        Thread t2 = testSubject.newThread(new NoOpRunnable());

        assertThat(t1.getThreadGroup().getName()).isEqualTo("test");
        assertThat(t1.getName()).isEqualTo("test-0");
        assertThat(t2.getName()).isEqualTo("test-1");
        assertThat(t2.getThreadGroup()).as("Expected only a single ThreadGroup").isSameAs(t1.getThreadGroup());
    }

    @Test
    void createWithThreadGroupByThreadGroupInstance() {
        ThreadGroup threadGroup = new ThreadGroup("test");
        testSubject = new SophonThreadFactory(threadGroup);
        Thread t1 = testSubject.newThread(new NoOpRunnable());
        Thread t2 = testSubject.newThread(new NoOpRunnable());

        assertThat(t1.getThreadGroup().getName()).isEqualTo("test");
        assertThat(t1.getName()).isEqualTo("test-0");
        assertThat(t1.getThreadGroup()).as("Expected only a single ThreadGroup").isSameAs(threadGroup);
        assertThat(t2.getThreadGroup()).as("Expected only a single ThreadGroup").isSameAs(threadGroup);
    }

    @Test
    void createWithPriority() {
        ThreadGroup threadGroup = new ThreadGroup("test");
        testSubject = new SophonThreadFactory(Thread.MAX_PRIORITY, threadGroup);
        Thread t1 = testSubject.newThread(new NoOpRunnable());
        Thread t2 = testSubject.newThread(new NoOpRunnable());

        assertThat(t1.getThreadGroup().getName()).isEqualTo("test");
        assertThat(t1.getPriority()).isEqualTo(Thread.MAX_PRIORITY);
        assertThat(t1.getThreadGroup()).as("Expected only a single ThreadGroup").isSameAs(threadGroup);
        assertThat(t2.getThreadGroup()).as("Expected only a single ThreadGroup").isSameAs(threadGroup);
    }

    @Test
    void rejectsTooHighPriority() {
        assertThatThrownBy(() -> new SophonThreadFactory(Thread.MAX_PRIORITY + 1, new ThreadGroup("")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooLowPriority() {
        assertThatThrownBy(() -> new SophonThreadFactory(Thread.MIN_PRIORITY - 1, new ThreadGroup("")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static class NoOpRunnable implements Runnable {

        @Override
        public void run() {
        }
    }
}
