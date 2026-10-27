/*
 * Copyright (c) 2010-2025. Axon Framework
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

package io.github.sophonhq.sophon.integrationtests.deadline.jobrunr;

import io.github.sophonhq.sophon.common.transaction.NoTransactionManager;
import io.github.sophonhq.sophon.config.Configuration;
import io.github.sophonhq.sophon.config.ConfigurationScopeAwareProvider;
import io.github.sophonhq.sophon.deadline.DeadlineManager;
import io.github.sophonhq.sophon.deadline.DeadlineManagerSpanFactory;
import io.github.sophonhq.sophon.deadline.jobrunr.JobRunrDeadlineManager;
import io.github.sophonhq.sophon.integrationtests.deadline.AbstractDeadlineManagerTestSuite;
import io.github.sophonhq.sophon.messaging.ScopeAwareProvider;
import io.github.sophonhq.sophon.messaging.ScopeDescriptor;
import io.github.sophonhq.sophon.modelling.command.AggregateScopeDescriptor;
import io.github.sophonhq.sophon.serialization.TestSerializer;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.time.Duration;
import java.util.Objects;

import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobrunrDeadlineManagerTest extends AbstractDeadlineManagerTestSuite {

    private BackgroundJobServer backgroundJobServer;

    @AfterEach
    void cleanUp() {
        if (!Objects.isNull(backgroundJobServer)) {
            backgroundJobServer.stop();
            backgroundJobServer = null;
        }
    }

    @Override
    public DeadlineManager buildDeadlineManager(Configuration configuration) {
        StorageProvider storageProvider = new InMemoryStorageProvider();
        JobScheduler scheduler = new JobScheduler(storageProvider);
        JobRunrDeadlineManager manager = JobRunrDeadlineManager
                .builder()
                .jobScheduler(scheduler)
                .scopeAwareProvider(new ConfigurationScopeAwareProvider(configuration))
                .serializer(TestSerializer.JACKSON.getSerializer())
                .transactionManager(NoTransactionManager.INSTANCE)
                .spanFactory(configuration.getComponent(DeadlineManagerSpanFactory.class))
                .build();
        JobRunr.configure()
               .useJobActivator(new SimpleActivator(spy(manager)))
               .useStorageProvider(storageProvider)
               .useBackgroundJobServer(
                       usingStandardBackgroundJobServerConfiguration().andPollInterval(Duration.ofMillis(200))
               )
               .initialize();
        backgroundJobServer = JobRunr.getBackgroundJobServer();
        return manager;
    }

    @Test
    void shutdownInvokesSchedulerShutdown(@Mock ScopeAwareProvider scopeAwareProvider) {
        JobScheduler scheduler = spy(new JobScheduler(new InMemoryStorageProvider()));
        JobRunrDeadlineManager testSubject = JobRunrDeadlineManager.builder()
                                                                   .jobScheduler(scheduler)
                                                                   .scopeAwareProvider(scopeAwareProvider)
                                                                   .serializer(TestSerializer.JACKSON.getSerializer())
                                                                   .transactionManager(NoTransactionManager.INSTANCE)
                                                                   .build();

        testSubject.shutdown();

        verify(scheduler).shutdown();
    }

    @Override
    @Test
    @Disabled("Cancel all within scope is not implemented for the non pro version.")
    public void deadlineCancellationWithinScopeOnAggregate() {
    }

    @Override
    @Test
    @Disabled("Cancel all is not implemented for the non pro version.")
    public void deadlineCancelAllOnAggregateIsTracedCorrectly() {
    }

    @Override
    @Test
    @Disabled("Cancel all within scope is not implemented for the non pro version.")
    public void deadlineCancellationWithinScopeOnSaga() {
    }

    @Override
    @Test
    @Disabled("Cancel all is not implemented for the non pro version.")
    public void deadlineCancelAllOnSagaIsCorrectlyTraced() {
    }

    @Test
    void doNotThrowIllegalJobStateChangeExceptionForAnAlreadyDeletedJob() {
        DeadlineManager testSubject = configuration.deadlineManager();

        String deadlineName = "doubleDeleteDoesNotThrowException";
        String scheduleId = testSubject.schedule(Duration.ofMinutes(15), deadlineName, null,
                                                 new AggregateScopeDescriptor("aggregateType", "aggregateId"));
        testSubject.cancelSchedule(deadlineName, scheduleId);
        assertDoesNotThrow(() -> testSubject.cancelSchedule(deadlineName, scheduleId));
    }
}
