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

package org.sophonhq.sophon.serialization;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link LegacyClassNames}.
 */
class LegacyClassNamesTest {

    @Test
    void translateMovesClassesToTheSophonPackage() {
        assertEquals("org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken",
                     LegacyClassNames.translate("org.axonframework.eventhandling.GlobalSequenceTrackingToken"));
    }

    @Test
    void translateRenamesClassesWhoseNameContainedAxon() {
        assertEquals("org.sophonhq.sophon.common.SophonNonTransientException",
                     LegacyClassNames.translate("org.axonframework.common.AxonNonTransientException"));
    }

    @Test
    void translateKeepsNestedClassNames() {
        assertEquals("org.sophonhq.sophon.eventhandling.tokenstore.AbstractTokenEntry$Inner",
                     LegacyClassNames.translate("org.axonframework.eventhandling.tokenstore.AbstractTokenEntry$Inner"));
    }

    @Test
    void translateLeavesOtherClassNamesUntouched() {
        assertEquals("com.example.AxonOrder", LegacyClassNames.translate("com.example.AxonOrder"));
        assertEquals("java.lang.String", LegacyClassNames.translate("java.lang.String"));
    }

    @Test
    void translateAllReplacesEveryClassNameInText() {
        String xml = "<org.axonframework.eventhandling.ReplayToken>"
                + "<tokenAtReset class=\"org.axonframework.eventhandling.GlobalSequenceTrackingToken\"/>"
                + "<payload>com.example.Order</payload>"
                + "</org.axonframework.eventhandling.ReplayToken>";

        assertEquals("<org.sophonhq.sophon.eventhandling.ReplayToken>"
                             + "<tokenAtReset class=\"org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken\"/>"
                             + "<payload>com.example.Order</payload>"
                             + "</org.sophonhq.sophon.eventhandling.ReplayToken>",
                     LegacyClassNames.translateAll(xml));
    }

    @Test
    void translateAllReplacesClassNamesInArrayDescriptors() {
        assertEquals("[Lorg.sophonhq.sophon.eventhandling.TrackingToken;",
                     LegacyClassNames.translateAll("[Lorg.axonframework.eventhandling.TrackingToken;"));
    }
}
