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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link LegacyClassNames}.
 */
class LegacyClassNamesTest {

    @Test
    void translateMovesClassesToTheSophonPackage() {
        assertThat(LegacyClassNames.translate("org.axonframework.eventhandling.GlobalSequenceTrackingToken"))
                .isEqualTo("org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken");
    }

    @Test
    void translateRenamesClassesWhoseNameContainedAxon() {
        assertThat(LegacyClassNames.translate("org.axonframework.common.AxonNonTransientException"))
                .isEqualTo("org.sophonhq.sophon.common.SophonNonTransientException");
    }

    @Test
    void translateKeepsNestedClassNames() {
        assertThat(LegacyClassNames.translate("org.axonframework.eventhandling.tokenstore.AbstractTokenEntry$Inner"))
                .isEqualTo("org.sophonhq.sophon.eventhandling.tokenstore.AbstractTokenEntry$Inner");
    }

    @Test
    void translateLeavesOtherClassNamesUntouched() {
        assertThat(LegacyClassNames.translate("com.example.AxonOrder")).isEqualTo("com.example.AxonOrder");
        assertThat(LegacyClassNames.translate("java.lang.String")).isEqualTo("java.lang.String");
    }

    @Test
    void translateAllReplacesEveryClassNameInText() {
        String xml = "<org.axonframework.eventhandling.ReplayToken>"
                + "<tokenAtReset class=\"org.axonframework.eventhandling.GlobalSequenceTrackingToken\"/>"
                + "<payload>com.example.Order</payload>"
                + "</org.axonframework.eventhandling.ReplayToken>";

        assertThat(LegacyClassNames.translateAll(xml))
                .isEqualTo("<org.sophonhq.sophon.eventhandling.ReplayToken>"
                        + "<tokenAtReset class=\"org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken\"/>"
                        + "<payload>com.example.Order</payload>"
                        + "</org.sophonhq.sophon.eventhandling.ReplayToken>");
    }

    @Test
    void translateAllReplacesClassNamesInArrayDescriptors() {
        assertThat(LegacyClassNames.translateAll("[Lorg.axonframework.eventhandling.TrackingToken;"))
                .isEqualTo("[Lorg.sophonhq.sophon.eventhandling.TrackingToken;");
    }
}
