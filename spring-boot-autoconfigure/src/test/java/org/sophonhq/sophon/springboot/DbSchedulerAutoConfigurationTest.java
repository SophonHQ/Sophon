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

package org.sophonhq.sophon.springboot;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import org.junit.jupiter.api.*;
import org.sophonhq.sophon.config.Configuration;
import org.sophonhq.sophon.config.ConfigurationScopeAwareProvider;
import org.sophonhq.sophon.deadline.DeadlineManager;
import org.sophonhq.sophon.deadline.dbscheduler.DbSchedulerDeadlineManager;
import org.sophonhq.sophon.deadline.dbscheduler.DbSchedulerHumanReadableDeadlineDetails;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.eventhandling.scheduling.EventScheduler;
import org.sophonhq.sophon.eventhandling.scheduling.dbscheduler.DbSchedulerEventScheduler;
import org.sophonhq.sophon.eventhandling.scheduling.dbscheduler.DbSchedulerHumanReadableEventData;
import org.sophonhq.sophon.messaging.ScopeAwareProvider;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.springboot.util.ConditionalOnMissingQualifiedBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

class DbSchedulerAutoConfigurationTest {

    @Test
    void eventSchedulerAndDeadlineManagerCreated() {
        new ApplicationContextRunner()
                .withUserConfiguration(DefaultContext.class)
                .run(context -> {
                    EventScheduler eventScheduler = context.getBean(EventScheduler.class);
                    assertNotNull(eventScheduler);
                    assertTrue(eventScheduler instanceof DbSchedulerEventScheduler);
                    DeadlineManager deadlineManager = context.getBean(DeadlineManager.class);
                    assertNotNull(deadlineManager);
                    assertTrue(deadlineManager instanceof DbSchedulerDeadlineManager);
                    assertEquals(2, context.getBeanNamesForType(Task.class).length);
                });
    }

    @Test
    void onlyTwoTaskBeansWhenOverwritten() {
        new ApplicationContextRunner()
                .withUserConfiguration(HumanReadableContext.class)
                .run(context -> {
                    EventScheduler eventScheduler = context.getBean(EventScheduler.class);
                    assertNotNull(eventScheduler);
                    assertTrue(eventScheduler instanceof DbSchedulerEventScheduler);
                    DeadlineManager deadlineManager = context.getBean(DeadlineManager.class);
                    assertNotNull(deadlineManager);
                    assertTrue(deadlineManager instanceof DbSchedulerDeadlineManager);
                    assertEquals(2, context.getBeanNamesForType(Task.class).length);
                    Class<?> dataclass = context.getBean("dbSchedulerEventDataTask", Task.class).getDataClass();
                    assertEquals(DbSchedulerHumanReadableEventData.class, dataclass);
                    dataclass = context.getBean("dbSchedulerDeadlineDetailsTask", Task.class).getDataClass();
                    assertEquals(DbSchedulerHumanReadableDeadlineDetails.class, dataclass);
                });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class DefaultContext {

        private final DataSource existingDataSource;
        private final List<Task<?>> configuredTasks;

        public DefaultContext(
                DataSource dataSource,
                List<Task<?>> configuredTasks) {
            this.existingDataSource = dataSource;
            this.configuredTasks = configuredTasks;
        }

        @Bean
        public Scheduler scheduler() {
            return Scheduler.create(existingDataSource, configuredTasks).build();
        }
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class HumanReadableContext {
        private final DataSource existingDataSource;
        private final List<Task<?>> configuredTasks;

        @Bean
        @Qualifier("eventDataTask")
        @ConditionalOnMissingQualifiedBean(beanClass = Task.class, qualifier = "eventDataTask")
        public Task<DbSchedulerHumanReadableEventData> dbSchedulerEventDataTask(
                ApplicationContext context
        ) {
            return DbSchedulerEventScheduler.humanReadableTask(() -> context.getBean(DbSchedulerEventScheduler.class));
        }

        @Bean
        @Qualifier("deadlineDetailsTask")
        @ConditionalOnMissingQualifiedBean(beanClass = Task.class, qualifier = "deadlineDetailsTask")
        public Task<DbSchedulerHumanReadableDeadlineDetails> dbSchedulerDeadlineDetailsTask(
                ApplicationContext context
        ) {
            return DbSchedulerDeadlineManager.humanReadableTask(() -> context.getBean(DbSchedulerDeadlineManager.class));
        }

        public HumanReadableContext(
                DataSource dataSource,
                ApplicationContext context
        ) {
            this.existingDataSource = dataSource;
            List<Task<?>> tasks = new ArrayList<>();
            tasks.add(dbSchedulerEventDataTask(context));
            tasks.add(dbSchedulerDeadlineDetailsTask(context));
            this.configuredTasks = tasks;
        }


        @Bean
        public Scheduler scheduler() {
            return Scheduler.create(existingDataSource, configuredTasks).build();
        }

        @Bean
        public EventScheduler eventScheduler(
                Scheduler scheduler,
                @Qualifier("eventSerializer") Serializer serializer,
                EventBus eventBus) {
            return DbSchedulerEventScheduler.builder()
                                            .scheduler(scheduler)
                                            .serializer(serializer)
                                            .useBinaryPojo(false)
                                            .eventBus(eventBus)
                                            .startScheduler(false)
                                            .build();
        }

        @Bean
        public DeadlineManager deadlineManager(
                Scheduler scheduler,
                Configuration configuration,
                @Qualifier("eventSerializer") Serializer serializer) {
            ScopeAwareProvider scopeAwareProvider = new ConfigurationScopeAwareProvider(configuration);
            return DbSchedulerDeadlineManager.builder()
                                             .scheduler(scheduler)
                                             .scopeAwareProvider(scopeAwareProvider)
                                             .serializer(serializer)
                                             .useBinaryPojo(false)
                                             .startScheduler(false)
                                             .build();
        }
    }
}
