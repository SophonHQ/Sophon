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

package io.github.sophonhq.sophon.test.saga;

import io.github.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import io.github.sophonhq.sophon.messaging.ScopeDescriptor;
import io.github.sophonhq.sophon.modelling.saga.SagaEventHandler;
import io.github.sophonhq.sophon.modelling.saga.SagaScopeDescriptor;
import io.github.sophonhq.sophon.modelling.saga.StartSaga;
import org.junit.jupiter.api.*;

import static io.github.sophonhq.sophon.test.matchers.Matchers.*;

/**
 * Test class validating a {@link io.github.sophonhq.sophon.messaging.ScopeDescriptor}, specifically an {@link
 * io.github.sophonhq.sophon.modelling.command.AggregateScopeDescriptor}, can be resolved on Aggregate's message handling
 * functions.
 *
 * @author Steven van Beelen
 */
class FixtureTest_ScopeDescriptor {

    private FixtureConfiguration fixture;

    @BeforeEach
    void setUp() {
        fixture = new SagaTestFixture<>(TestSaga.class);
    }

    @Test
    void resolvesScopeDescriptor() {
        fixture.givenNoPriorActivity()
               .whenPublishingA(new SagaStartEvent("some-identifier"))
               .expectDispatchedCommandsMatching(payloadsMatching(sequenceOf(matches(
                       command -> ScopeDescriptorCommand.class.isAssignableFrom(command.getClass()) &&
                               SagaScopeDescriptor.class.isAssignableFrom(
                                       ((ScopeDescriptorCommand) command).scopeDescriptor.getClass()
                               )
               ))));
    }

    private static class SagaStartEvent {

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final String identifier;

        private SagaStartEvent(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

    private static class ScopeDescriptorCommand {

        private final ScopeDescriptor scopeDescriptor;

        private ScopeDescriptorCommand(ScopeDescriptor scopeDescriptor) {
            this.scopeDescriptor = scopeDescriptor;
        }
    }

    @SuppressWarnings("unused")
    public static class TestSaga {

        @StartSaga
        @SagaEventHandler(associationProperty = "identifier")
        public void on(SagaStartEvent event, ScopeDescriptor scopeDescriptor, CommandGateway commandGateway) {
            commandGateway.send(new ScopeDescriptorCommand(scopeDescriptor));
        }
    }
}
