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

package org.sophonhq.sophon.test.aggregate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;
import static org.sophonhq.sophon.modelling.command.AggregateLifecycle.markDeleted;

class StubAggregateLifecycleTest {
    private StubAggregateLifecycle testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new StubAggregateLifecycle();
    }

    @AfterEach
    void tearDown() {
        testSubject.close();
    }

    @Test
    void lifecycleIsNotRegisteredAutomatically() {
        assertThatThrownBy(() -> apply("test")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void applyingEventsAfterDeactivationFails() {
        testSubject.activate();
        testSubject.close();

        assertThatThrownBy(() -> apply("test")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void appliedEventsArePassedToActiveLifecycle() {
        testSubject.activate();
        apply("test");

        assertThat(testSubject.getAppliedEvents()).hasSize(1);
        assertThat(testSubject.getAppliedEventPayloads().get(0)).isEqualTo("test");
        assertThat(testSubject.getAppliedEvents().get(0).getPayload()).isEqualTo("test");
    }

    @Test
    void markDeletedIsRegisteredWithActiveLifecycle() {
        testSubject.activate();
        markDeleted();

        assertThat(testSubject.getAppliedEvents()).hasSize(0);
        assertThat(testSubject.isMarkedDeleted()).isTrue();
    }
}
