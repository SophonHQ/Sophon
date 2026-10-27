/*
 * Copyright (c) 2010-2024. Axon Framework
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

package io.github.sophonhq.sophon.integrationtests.deadline.quartz;

import io.github.sophonhq.sophon.common.SophonConfigurationException;
import io.github.sophonhq.sophon.config.Configuration;
import io.github.sophonhq.sophon.config.ConfigurationScopeAwareProvider;
import io.github.sophonhq.sophon.deadline.DeadlineException;
import io.github.sophonhq.sophon.deadline.DeadlineManager;
import io.github.sophonhq.sophon.deadline.DeadlineManagerSpanFactory;
import io.github.sophonhq.sophon.deadline.quartz.QuartzDeadlineManager;
import io.github.sophonhq.sophon.integrationtests.deadline.AbstractDeadlineManagerTestSuite;
import io.github.sophonhq.sophon.messaging.ScopeAwareProvider;
import io.github.sophonhq.sophon.serialization.TestSerializer;
import io.github.sophonhq.sophon.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuartzDeadlineManagerTest extends AbstractDeadlineManagerTestSuite {

    @Override
    public DeadlineManager buildDeadlineManager(Configuration configuration) {
        try {
            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            QuartzDeadlineManager quartzDeadlineManager =
                    QuartzDeadlineManager.builder()
                                         .scheduler(scheduler)
                                         .scopeAwareProvider(new ConfigurationScopeAwareProvider(configuration))
                                         .serializer(TestSerializer.JACKSON.getSerializer())
                                         .spanFactory(configuration.getComponent(DeadlineManagerSpanFactory.class))
                                         .build();
            scheduler.start();
            return quartzDeadlineManager;
        } catch (SchedulerException e) {
            throw new SophonConfigurationException("Unable to configure quartz scheduler", e);
        }
    }

    @Test
    void shutdownInvokesSchedulerShutdown(@Mock ScopeAwareProvider scopeAwareProvider) throws SchedulerException {
        Scheduler scheduler = spy(new StdSchedulerFactory().getScheduler());
        QuartzDeadlineManager testSubject = QuartzDeadlineManager.builder()
                                                                 .scopeAwareProvider(scopeAwareProvider)
                                                                 .scheduler(scheduler)
                                                                 .serializer(TestSerializer.JACKSON.getSerializer())
                                                                 .build();

        testSubject.shutdown();

        verify(scheduler).shutdown(true);
    }

    @Test
    void shutdownFailureResultsInDeadlineException(@Mock ScopeAwareProvider scopeAwareProvider)
            throws SchedulerException {
        Scheduler scheduler = spy(new StdSchedulerFactory().getScheduler());
        doAnswer(invocation -> {
            throw new SchedulerException();
        }).when(scheduler).shutdown(true);
        QuartzDeadlineManager testSubject = QuartzDeadlineManager.builder()
                                                                 .scopeAwareProvider(scopeAwareProvider)
                                                                 .scheduler(scheduler)
                                                                 .serializer(TestSerializer.JACKSON.getSerializer())
                                                                 .build();

        assertThrows(DeadlineException.class, testSubject::shutdown);
    }

    @Test
    void buildWithoutSchedulerThrowsSophonConfigurationException() {
        ScopeAwareProvider scopeAwareProvider = mock(ScopeAwareProvider.class);
        QuartzDeadlineManager.Builder builderTestSubject =
                QuartzDeadlineManager.builder()
                                     .scopeAwareProvider(scopeAwareProvider)
                                     .serializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, builderTestSubject::build);
    }

    @Test
    void buildWithoutScopeAwareProviderThrowsSophonConfigurationException() {
        Scheduler scheduler = mock(Scheduler.class);
        QuartzDeadlineManager.Builder builderTestSubject =
                QuartzDeadlineManager.builder()
                                     .scheduler(scheduler)
                                     .serializer(TestSerializer.JACKSON.getSerializer());

        assertThrows(SophonConfigurationException.class, builderTestSubject::build);
    }
}
