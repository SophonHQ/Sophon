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

package org.sophonhq.sophon.deadline.dbscheduler;

import com.github.kagkarlsson.scheduler.task.TaskInstanceId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DbScheduledDeadlineTokenTest {

    @Test
    void equalsIfSameUuidIsUsed() {
        String uuid = UUID.randomUUID().toString();
        TaskInstanceId one = new DbSchedulerDeadlineToken(uuid);
        TaskInstanceId other = new DbSchedulerDeadlineToken(uuid);

        assertThat(other).isEqualTo(one);
        assertThat(other)
                .hasToString(one.toString())
                .hasSameHashCodeAs(one);
    }

    @Test
    void notEqualsIfDifferentUuidIsUsed() {
        TaskInstanceId one = new DbSchedulerDeadlineToken(UUID.randomUUID().toString());
        TaskInstanceId other = new DbSchedulerDeadlineToken(UUID.randomUUID().toString());

        assertThat(other).isNotEqualTo(one);
        assertThat(other.toString()).isNotEqualTo(one.toString());
        assertThat(other.hashCode()).isNotEqualTo(one.hashCode());
    }
}
