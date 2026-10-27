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

package io.github.sophonhq.sophon.test.aggregate;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;

import static io.github.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;
import static io.github.sophonhq.sophon.modelling.command.AggregateLifecycle.markDeleted;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link StubAggregateLifecycleExtension}.
 *
 * @author Stefan Dragisic
 */
class StubAggregateLifecycleExtensionTest {

    @RegisterExtension
    static final StubAggregateLifecycleExtension TEST_SUBJECT = new StubAggregateLifecycleExtension();

    @Test
    void appliedEventsArePassedToActiveLifecycle() {
        apply("test");

        assertEquals(1, TEST_SUBJECT.getAppliedEvents().size());
        assertEquals("test", TEST_SUBJECT.getAppliedEventPayloads().get(0));
        assertEquals("test", TEST_SUBJECT.getAppliedEvents().get(0).getPayload());
    }

    @Test
    void testMarkDeletedIsRegisteredWithActiveLifecycle() {
        markDeleted();

        assertEquals(0, TEST_SUBJECT.getAppliedEvents().size());
        assertTrue(TEST_SUBJECT.isMarkedDeleted());
    }
}
