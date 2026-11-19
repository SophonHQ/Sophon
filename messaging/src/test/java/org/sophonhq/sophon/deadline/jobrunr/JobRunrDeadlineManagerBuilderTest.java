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

package org.sophonhq.sophon.deadline.jobrunr;

import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.deadline.TestScopeDescriptor;
import org.sophonhq.sophon.messaging.ScopeAwareProvider;
import org.sophonhq.sophon.messaging.ScopeDescriptor;
import org.sophonhq.sophon.serialization.TestSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JobRunrDeadlineManagerBuilderTest {

    private static final String TEST_DEADLINE_NAME = "deadline-name";
    private JobRunrDeadlineManager.Builder builder;
    private final JobScheduler jobScheduler = mock(JobScheduler.class);
    private final TransactionManager transactionManager = mock(TransactionManager.class);
    private final ScopeAwareProvider scopeAwareProvider = mock(ScopeAwareProvider.class);

    @BeforeEach
    void newBuilder() {
        builder = JobRunrDeadlineManager.builder();
    }

    @Test
    void whenAllPropertiesAreSetCreatesManager() {
        JobRunrDeadlineManager manager = builder.scopeAwareProvider(scopeAwareProvider)
                                                .transactionManager(transactionManager)
                                                .jobScheduler(jobScheduler)
                                                .serializer(TestSerializer.JACKSON.getSerializer())
                                                .build();

        assertThat(manager).isNotNull();
    }

    @Test
    void validateNeedsAllPropertiesSet() {
        builder.jobScheduler(jobScheduler)
               .transactionManager(transactionManager);
        assertThatThrownBy(() -> builder.build()).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void whenSettingSchedulerWithNullThrowError() {
        assertThatThrownBy(() -> builder.jobScheduler(null)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void whenSettingTransactionManagerWithNullThrowError() {
        assertThatThrownBy(() -> builder.transactionManager(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void whenSettingScopeAwareProviderWithNullThrowError() {
        assertThatThrownBy(() -> builder.scopeAwareProvider(null))
                .isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void cancelAllNotImplemented() {
        JobRunrDeadlineManager manager = builder.scopeAwareProvider(scopeAwareProvider)
                                                .transactionManager(transactionManager)
                                                .jobScheduler(jobScheduler)
                                                .serializer(TestSerializer.JACKSON.getSerializer())
                                                .build();
        assertThatThrownBy(() -> manager.cancelAll(TEST_DEADLINE_NAME))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cancelAllWithinScopeNotImplemented() {
        JobRunrDeadlineManager manager = builder.scopeAwareProvider(scopeAwareProvider)
                                                .transactionManager(transactionManager)
                                                .jobScheduler(jobScheduler)
                                                .serializer(TestSerializer.JACKSON.getSerializer())
                                                .build();
        ScopeDescriptor descriptor = new TestScopeDescriptor("aggregate-type", "aggregate-identifier");
        assertThatThrownBy(() -> manager.cancelAllWithinScope(TEST_DEADLINE_NAME, descriptor))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
