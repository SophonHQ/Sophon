/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.sophonhq.sophon.integrationtests.queryhandling;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.commandhandling.SimpleCommandBus;
import org.sophonhq.sophon.config.Configuration;
import org.sophonhq.sophon.config.Configurer;
import org.sophonhq.sophon.config.DefaultConfigurer;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.sophonhq.sophon.queryhandling.QueryHandler;
import org.sophonhq.sophon.queryhandling.SimpleQueryBus;

class ProjectorTest {

    // This ensures configurer modules on the classpath are not registered automatically
    private static final boolean DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES = false;

    @Test
    void queryHandlerAndEventHandlerCleanlyShutdown() {
        UserSummaryProjection userSummaryProjection = new UserSummaryProjection();

        Configurer configurer = DefaultConfigurer.defaultConfiguration(DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES);
        configurer.configureCommandBus(c -> SimpleCommandBus.builder().build())
                  .configureQueryBus(c -> SimpleQueryBus.builder().build())
                  .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                  .registerQueryHandler(c -> userSummaryProjection);

        configurer.eventProcessing()
                  .registerEventHandler(c -> userSummaryProjection);

        Configuration configuration = configurer.buildConfiguration();

        configuration.start();
        configuration.shutdown();
    }

    @SuppressWarnings("unused")
    private static class UserCreatedEvent {

        private final String userId;

        UserCreatedEvent(String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }
    }

    @SuppressWarnings("unused")
    private static class FindUserQuery {

        private final String userId;

        FindUserQuery(String userId) {
            this.userId = userId;
        }

        public String getUserId() {
            return userId;
        }
    }

    @SuppressWarnings("unused")
    private static class UserSummaryProjection {

        @EventHandler
        public void on(UserCreatedEvent event) {
        }

        @QueryHandler
        public UserSummaryProjection handle(FindUserQuery query) {
            return null;
        }
    }
}
