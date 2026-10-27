/*
 * Copyright (c) 2010-2023. Axon Framework
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

package io.github.sophonhq.sophon.springboot.autoconfig;

import io.github.sophonhq.sophon.common.transaction.TransactionManager;
import io.github.sophonhq.sophon.config.Configuration;
import io.github.sophonhq.sophon.config.ConfigurationScopeAwareProvider;
import io.github.sophonhq.sophon.deadline.DeadlineManager;
import io.github.sophonhq.sophon.deadline.DeadlineManagerSpanFactory;
import io.github.sophonhq.sophon.deadline.jobrunr.JobRunrDeadlineManager;
import io.github.sophonhq.sophon.eventhandling.EventBus;
import io.github.sophonhq.sophon.eventhandling.scheduling.EventScheduler;
import io.github.sophonhq.sophon.eventhandling.scheduling.jobrunr.JobRunrEventScheduler;
import io.github.sophonhq.sophon.messaging.ScopeAwareProvider;
import io.github.sophonhq.sophon.serialization.Serializer;
import io.github.sophonhq.sophon.tracing.SpanFactory;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto configuration class for the deadline manager and event scheduler using JobRunr.
 *
 * @author Gerard Klijs
 * @since axon-4.8.0
 */
@AutoConfiguration
@ConditionalOnBean(JobScheduler.class)
@AutoConfigureAfter(value = SophonTracingAutoConfiguration.class,
        name = {"org.jobrunr.spring.autoconfigure.JobRunrAutoConfiguration"})
public class SophonJobRunrAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventScheduler eventScheduler(
            JobScheduler jobScheduler,
            @Qualifier("eventSerializer") Serializer serializer,
            TransactionManager transactionManager,
            EventBus eventBus) {
        return JobRunrEventScheduler.builder()
                                    .jobScheduler(jobScheduler)
                                    .serializer(serializer)
                                    .transactionManager(transactionManager)
                                    .eventBus(eventBus)
                                    .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeadlineManager deadlineManager(
            JobScheduler jobScheduler,
            Configuration configuration,
            @Qualifier("eventSerializer") Serializer serializer,
            TransactionManager transactionManager,
            DeadlineManagerSpanFactory spanFactory) {
        ScopeAwareProvider scopeAwareProvider = new ConfigurationScopeAwareProvider(configuration);
        return JobRunrDeadlineManager.builder()
                                     .jobScheduler(jobScheduler)
                                     .scopeAwareProvider(scopeAwareProvider)
                                     .serializer(serializer)
                                     .transactionManager(transactionManager)
                                     .spanFactory(spanFactory)
                                     .build();
    }
}
