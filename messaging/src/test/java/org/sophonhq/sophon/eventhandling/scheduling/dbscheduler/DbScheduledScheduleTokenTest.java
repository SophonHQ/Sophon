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

package org.sophonhq.sophon.eventhandling.scheduling.dbscheduler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.sophonhq.sophon.eventhandling.scheduling.ScheduleToken;
import org.sophonhq.sophon.serialization.TestSerializer;

import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DbScheduledScheduleTokenTest {

    @Test
    void equalsIfSameUuidIsUsed() {
        String uuid = UUID.randomUUID().toString();
        ScheduleToken one = new DbSchedulerScheduleToken(uuid);
        ScheduleToken other = new DbSchedulerScheduleToken(uuid);

        assertThat(other).isEqualTo(one);
        assertThat(other)
                .hasToString(one.toString())
                .hasSameHashCodeAs(one);
    }

    @Test
    void notEqualsIfDifferentUuidIsUsed() {
        ScheduleToken one = new DbSchedulerScheduleToken(UUID.randomUUID().toString());
        ScheduleToken other = new DbSchedulerScheduleToken(UUID.randomUUID().toString());

        assertThat(other).isNotEqualTo(one);
        assertThat(other.toString()).isNotEqualTo(one.toString());
        assertThat(other.hashCode()).isNotEqualTo(one.hashCode());
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestSerializer serializer) {
        ScheduleToken tokenToTest = new DbSchedulerScheduleToken(UUID.randomUUID().toString());
        assertThat(serializer.serializeDeserialize(tokenToTest)).isEqualTo(tokenToTest);
    }

    public static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }
}
