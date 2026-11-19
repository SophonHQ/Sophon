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

package org.sophonhq.sophon.eventhandling.scheduling.jobrunr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.sophonhq.sophon.eventhandling.scheduling.ScheduleToken;
import org.sophonhq.sophon.serialization.TestSerializer;

import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JobRunrScheduleTokenTest {

    @Test
    void equalsIfSameUuidIsUsed() {
        UUID uuid = UUID.randomUUID();
        ScheduleToken one = new JobRunrScheduleToken(uuid);
        ScheduleToken other = new JobRunrScheduleToken(uuid);

        assertThat(other).isEqualTo(one);
        assertThat(other)
                .hasToString(one.toString())
                .hasSameHashCodeAs(one);
    }

    @Test
    void notEqualsIfDifferentUuidIsUsed() {
        ScheduleToken one = new JobRunrScheduleToken(UUID.randomUUID());
        ScheduleToken other = new JobRunrScheduleToken(UUID.randomUUID());

        assertThat(other).isNotEqualTo(one);
        assertThat(other.toString()).isNotEqualTo(one.toString());
        assertThat(other.hashCode()).isNotEqualTo(one.hashCode());
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestSerializer serializer) {
        ScheduleToken tokenToTest = new JobRunrScheduleToken(UUID.randomUUID());
        assertThat(serializer.serializeDeserialize(tokenToTest)).isEqualTo(tokenToTest);
    }

    public static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }
}
