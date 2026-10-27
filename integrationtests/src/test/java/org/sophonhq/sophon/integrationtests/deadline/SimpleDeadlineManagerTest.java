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

package org.sophonhq.sophon.integrationtests.deadline;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.sophonhq.sophon.config.Configuration;
import org.sophonhq.sophon.config.ConfigurationScopeAwareProvider;
import org.sophonhq.sophon.deadline.DeadlineManager;
import org.sophonhq.sophon.deadline.DeadlineManagerSpanFactory;
import org.sophonhq.sophon.deadline.DefaultDeadlineManagerSpanFactory;
import org.sophonhq.sophon.deadline.SimpleDeadlineManager;
import org.sophonhq.sophon.messaging.ScopeAwareProvider;

import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimpleDeadlineManagerTest extends AbstractDeadlineManagerTestSuite {

    @Override
    public DeadlineManager buildDeadlineManager(Configuration configuration) {
        return SimpleDeadlineManager.builder()
                                    .scopeAwareProvider(new ConfigurationScopeAwareProvider(configuration))
                                    .spanFactory(configuration.getComponent(DeadlineManagerSpanFactory.class))
                                    .build();
    }

    @Test
    void shutdownInvokesExecutorServiceShutdown(@Mock ScopeAwareProvider scopeAwareProvider,
                                                @Mock ScheduledExecutorService scheduledExecutorService) {
        SimpleDeadlineManager testSubject = SimpleDeadlineManager.builder()
                                                                 .scopeAwareProvider(scopeAwareProvider)
                                                                 .scheduledExecutorService(scheduledExecutorService)
                                                                 .build();

        testSubject.shutdown();

        verify(scheduledExecutorService).shutdown();
    }
}
