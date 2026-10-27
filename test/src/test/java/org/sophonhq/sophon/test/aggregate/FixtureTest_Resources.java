/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.sophonhq.sophon.test.aggregate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandBus;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.modelling.command.AggregateIdentifier;
import org.sophonhq.sophon.eventsourcing.EventSourcingHandler;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;

/**
 * @author Allard Buijze
 */
class FixtureTest_Resources {

    private FixtureConfiguration<AggregateWithResources> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(AggregateWithResources.class);
    }

    @Test
    void resourcesAreScopedToSingleTest_ConstructorPartOne() {
        // executing the same test should pass, as resources are scoped to a single test only
        final Executor resource = mock(Executor.class);
        fixture.registerInjectableResource(resource)
               .given()
               .when(new CreateAggregateCommand("id"));

        verify(resource).execute(isA(Runnable.class));
        verifyNoMoreInteractions(resource);
    }

    @Test
    void resourcesAreScopedToSingleTest_ConstructorPartTwo() {
        resourcesAreScopedToSingleTest_ConstructorPartOne();
    }

    @Test
    void resourcesAreScopedToSingleTest_MethodPartOne() {
        // executing the same test should pass, as resources are scoped to a single test only
        final Executor resource = mock(Executor.class);
        fixture.registerInjectableResource(resource)
               .given(new MyEvent("id", 1))
               .when(new TestCommand("id"))
               .expectResultMessagePayload(fixture.getCommandBus());

        verify(resource).execute(isA(Runnable.class));
        verifyNoMoreInteractions(resource);
    }

    @Test
    void resourcesAreScopedToSingleTest_MethodPartTwo() {
        resourcesAreScopedToSingleTest_MethodPartOne();
    }

    public static class AggregateWithResources {

        @AggregateIdentifier
        private String id;

        @CommandHandler
        public AggregateWithResources(CreateAggregateCommand cmd, Executor resource, CommandBus commandBus) {
            apply(new MyEvent(cmd.getAggregateIdentifier(), 1));
            resource.execute(() -> fail("Should not really be executed"));
        }

        @CommandHandler
        public CommandBus handleCommand(TestCommand cmd, Executor resource, CommandBus commandBus) {
            resource.execute(() -> fail("Should not really be executed"));
            return commandBus;
        }

        @EventSourcingHandler
        void handle(MyEvent event, Executor resource) {
            assertNotNull(resource);
            this.id = event.getAggregateIdentifier().toString();
        }

        public AggregateWithResources() {
        }
    }
}
