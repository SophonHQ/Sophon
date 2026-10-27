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

package io.github.sophonhq.sophon.springboot.util;

import org.junit.jupiter.api.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link LegacyPropertiesDetector}.
 */
class LegacyPropertiesDetectorTest {

    @Test
    void detectsAxonPropertiesAndEnvironmentVariables() {
        MockEnvironment environment = new MockEnvironment().withProperty("axon.serializer.general", "jackson")
                                                           .withProperty("sophon.serializer.events", "jackson")
                                                           .withProperty("spring.application.name", "app");
        environment.getPropertySources().addLast(new MapPropertySource(
                "systemEnvironment", Collections.singletonMap("AXON_EVENTHANDLING_TOKENSTORE_CLAIMTIMEOUT", "5s")
        ));

        LegacyPropertiesDetector testSubject = new LegacyPropertiesDetector(environment);

        assertEquals(new TreeSet<>(Arrays.asList("AXON_EVENTHANDLING_TOKENSTORE_CLAIMTIMEOUT",
                                                 "axon.serializer.general")),
                     testSubject.legacyPropertyNames());
    }

    @Test
    void detectsNothingWithoutAxonProperties() {
        MockEnvironment environment = new MockEnvironment().withProperty("sophon.serializer.general", "jackson");

        LegacyPropertiesDetector testSubject = new LegacyPropertiesDetector(environment);

        assertTrue(testSubject.legacyPropertyNames().isEmpty());
        assertDoesNotThrow(testSubject::afterPropertiesSet);
    }
}
