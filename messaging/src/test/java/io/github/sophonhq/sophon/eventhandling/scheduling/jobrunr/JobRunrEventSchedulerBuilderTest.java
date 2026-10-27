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

package io.github.sophonhq.sophon.eventhandling.scheduling.jobrunr;

import io.github.sophonhq.sophon.common.SophonConfigurationException;
import io.github.sophonhq.sophon.common.transaction.TransactionManager;
import io.github.sophonhq.sophon.eventhandling.EventBus;
import io.github.sophonhq.sophon.serialization.TestSerializer;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobRunrEventSchedulerBuilderTest {

    private JobRunrEventScheduler.Builder builder;
    private final JobScheduler jobScheduler = mock(JobScheduler.class);
    private final TransactionManager transactionManager = mock(TransactionManager.class);
    private final EventBus eventBus = mock(EventBus.class);

    @BeforeEach
    void newBuilder() {
        builder = JobRunrEventScheduler.builder();
    }

    @Test
    void whenAllPropertiesAreSetCreatesManager() {
        JobRunrEventScheduler scheduler = builder.transactionManager(transactionManager)
                                                 .jobScheduler(jobScheduler)
                                                 .serializer(TestSerializer.JACKSON.getSerializer())
                                                 .eventBus(eventBus)
                                                 .build();

        assertNotNull(scheduler);
    }

    @Test
    void validateNeedsAllPropertiesSet() {
        builder.jobScheduler(jobScheduler)
               .transactionManager(transactionManager);
        assertThrows(SophonConfigurationException.class, () -> builder.build());
    }

    @Test
    void whenSettingSchedulerWithNullThrowError() {
        assertThrows(SophonConfigurationException.class, () -> builder.jobScheduler(null));
    }

    @Test
    void whenSettingNameWithNullThrowError() {
        assertThrows(SophonConfigurationException.class, () -> builder.jobName(null));
    }

    @Test
    void whenSettingTransactionManagerWithNullThrowError() {
        assertThrows(SophonConfigurationException.class, () -> builder.transactionManager(null));
    }

    @Test
    void whenSettingSerializerWithNullThrowError() {
        assertThrows(SophonConfigurationException.class, () -> builder.serializer(null));
    }

    @Test
    void whenSettingEventBusWithNullThrowError() {
        assertThrows(SophonConfigurationException.class, () -> builder.eventBus(null));
    }
}
