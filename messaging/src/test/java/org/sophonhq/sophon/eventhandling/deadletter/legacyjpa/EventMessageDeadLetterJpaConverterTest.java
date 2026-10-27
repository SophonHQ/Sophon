/*
 * Copyright (c) 2026. SophonHQ
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

package org.sophonhq.sophon.eventhandling.deadletter.legacyjpa;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.eventhandling.deadletter.jpa.DeadLetterEventEntry;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link EventMessageDeadLetterJpaConverter}.
 */
class EventMessageDeadLetterJpaConverterTest {

    private final EventMessageDeadLetterJpaConverter converter = new EventMessageDeadLetterJpaConverter();

    @Test
    void canConvertDeadLettersStoredWithAxonFrameworkMessageTypes() {
        DeadLetterEventEntry legacyEntry = new DeadLetterEventEntry(
                "org.axonframework.eventhandling.GenericEventMessage", "identifier", Instant.now().toString(),
                null, null, null, null, null, null, null, null, null
        );

        assertTrue(converter.canConvert(legacyEntry));
    }

    @Test
    void cannotConvertUnknownMessageTypes() {
        DeadLetterEventEntry entry = new DeadLetterEventEntry(
                "com.example.CustomEventMessage", "identifier", Instant.now().toString(),
                null, null, null, null, null, null, null, null, null
        );

        assertFalse(converter.canConvert(entry));
    }
}
