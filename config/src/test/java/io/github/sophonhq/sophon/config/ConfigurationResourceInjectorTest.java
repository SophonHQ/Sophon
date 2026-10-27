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

package io.github.sophonhq.sophon.config;

import io.github.sophonhq.sophon.commandhandling.CommandBus;
import io.github.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import io.github.sophonhq.sophon.eventsourcing.eventstore.EventStore;
import io.github.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.junit.jupiter.api.*;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationResourceInjectorTest {

    private Configuration configuration;
    private ConfigurationResourceInjector testSubject;

    @BeforeEach
    void setUp() {
        configuration = DefaultConfigurer.defaultConfiguration()
                .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                .buildConfiguration();
        testSubject = new ConfigurationResourceInjector(configuration);
    }

    @Test
    void injectorHasResource() {
        Saga saga = new Saga();
        testSubject.injectResources(saga);

        assertSame(configuration.commandBus(), saga.commandBus);
        assertSame(configuration.commandGateway(), saga.commandGateway);
        assertSame(configuration.eventStore(), saga.eventStore);
        assertNull(saga.inexistent);
    }

    public static class Saga {

        @Inject
        private CommandBus commandBus;

        @Inject
        private String inexistent;

        private CommandGateway commandGateway;
        private EventStore eventStore;

        @Inject
        public void setCommandGateway(CommandGateway commandGateway) {
            this.commandGateway = commandGateway;
        }

        @Inject
        public void setEventStore(EventStore eventStore) {
            this.eventStore = eventStore;
        }
    }
}
