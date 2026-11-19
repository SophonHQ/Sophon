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

import com.github.kagkarlsson.scheduler.Scheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.serialization.TestSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DbSchedulerEventSchedulerBuilderTest {

    private DbSchedulerEventScheduler.Builder builder;
    private final Scheduler scheduler = mock(Scheduler.class);
    private final TransactionManager transactionManager = mock(TransactionManager.class);
    private final EventBus eventBus = mock(EventBus.class);

    @BeforeEach
    void newBuilder() {
        builder = DbSchedulerEventScheduler.builder();
    }

    @Test
    void whenAllPropertiesAreSetCreatesManager() {
        DbSchedulerEventScheduler eventScheduler = builder.transactionManager(transactionManager)
                                                          .scheduler(scheduler)
                                                          .serializer(TestSerializer.JACKSON.getSerializer())
                                                          .eventBus(eventBus)
                                                          .build();

        assertThat(eventScheduler).isNotNull();
    }

    @Test
    void validateNeedsAllPropertiesSet() {
        builder.scheduler(scheduler)
               .transactionManager(transactionManager);
        assertThatThrownBy(() -> builder.build()).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void whenSettingSchedulerWithNullThrowError() {
        assertThatThrownBy(() -> builder.scheduler(null)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void whenSettingTransactionManagerWithNullThrowError() {
        assertThatThrownBy(() -> builder.transactionManager(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void whenSettingSerializerWithNullThrowError() {
        assertThatThrownBy(() -> builder.serializer(null)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void whenSettingEventBusWithNullThrowError() {
        assertThatThrownBy(() -> builder.eventBus(null)).isInstanceOf(SophonConfigurationException.class);
    }
}
