/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.sophonhq.sophon.test.eventscheduler;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Allard Buijze
 */
class StubEventSchedulerTest {

    private StubEventScheduler testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new StubEventScheduler();
    }

    @Test
    void scheduleEvent() {
        testSubject.schedule(Instant.now().plus(Duration.ofDays(1)), event(new MockEvent()));
        assertThat(testSubject.getScheduledItems()).hasSize(1);
    }

    @Test
    void eventContainsTimestampOfScheduledTime() {
        Instant triggerTime = Instant.now().plusSeconds(60);
        testSubject.schedule(triggerTime, "gone");
        List<EventMessage<?>> triggered = new ArrayList<>();
        testSubject.advanceTimeBy(Duration.ofMinutes(75), triggered::add);

        assertThat(triggered).hasSize(1);
        assertThat(triggered.get(0).getTimestamp()).isEqualTo(triggerTime);
    }

    @Test
    void initializeAtDateTimeAfterSchedulingEvent() {
        testSubject.schedule(Instant.now().plus(Duration.ofDays(1)), event(new MockEvent()));

        assertThatThrownBy(() ->
                                   testSubject.initializeAt(Instant.now().minus(10, ChronoUnit.MINUTES)))
                .isInstanceOf(IllegalStateException.class);
    }

    private EventMessage<MockEvent> event(MockEvent mockEvent) {
        return new GenericEventMessage<>(mockEvent);
    }

    private static class MockEvent {

    }
}
