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

package org.sophonhq.sophon.test.aggregate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.modelling.command.AggregateIdentifier;
import org.sophonhq.sophon.modelling.command.TargetAggregateIdentifier;
import org.sophonhq.sophon.test.FixtureExecutionException;

import java.util.Collections;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.CoreMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;

/**
 * Test class intended to validate the {@link AggregateTestFixture}'s inner workings after a {@link
 * AggregateTestFixture#givenState(Supplier)}.
 *
 * @author Allard Buijze
 */
class FixtureTest_StateStorage {

    private static final String AGGREGATE_ID = "id";

    private FixtureConfiguration<StateStoredAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(StateStoredAggregate.class);
    }

    @AfterEach
    void tearDown() {
        if (CurrentUnitOfWork.isStarted()) {
            fail("A unit of work is still running");
        }
    }

    @Test
    void createStateStoredAggregate() {
        fixture.givenState(() -> new StateStoredAggregate(AGGREGATE_ID, "message"))
               .when(new SetMessageCommand(AGGREGATE_ID, "message2"))
               .expectEvents(new StubDomainEvent())
               .expectState(aggregate -> assertThat(aggregate.getMessage()).isEqualTo("message2"));
    }

    @Test
    void givenCommandsForStateStoredAggregate() {
        fixture.useStateStorage()
               .givenCommands(new InitializeCommand(AGGREGATE_ID, "message"))
               .when(new SetMessageCommand(AGGREGATE_ID, "message2"))
               .expectEvents(new StubDomainEvent())
               .expectState(aggregate -> assertThat(aggregate.getMessage()).isEqualTo("message2"));
    }


    @Test
    void createStateStoredAggregateWithCommand() {
        fixture.useStateStorage()
               .givenNoPriorActivity()
               .when(new InitializeCommand(AGGREGATE_ID, "message"))
               .expectEvents(new StubDomainEvent())
               .expectState(aggregate -> assertThat(aggregate.getMessage()).isEqualTo("message"));
    }

    @Test
    void emittedEventsFromExpectStateAreNotStored() {
        fixture.givenState(() -> new StateStoredAggregate(AGGREGATE_ID, "message"))
               .when(new SetMessageCommand(AGGREGATE_ID, "message2"))
               .expectEvents(new StubDomainEvent())
               .expectState(aggregate -> {
                   apply(new StubDomainEvent());
                   assertThat(aggregate.getMessage()).isEqualTo("message2");
               })
               .expectEvents(new StubDomainEvent())
               .expectState(aggregate -> assertThat(aggregate).isNotNull());
    }

    @Test
    void createStateStoredAggregate_ErrorInChanges() {
        ResultValidator<StateStoredAggregate> result =
                fixture.givenState(() -> new StateStoredAggregate(AGGREGATE_ID, "message"))
                       .when(new ErrorCommand(AGGREGATE_ID, "message2"))
                       .expectException(any(Exception.class))
                       .expectNoEvents();
        IllegalStateException e = assertThatExceptionOfType(IllegalStateException.class).isThrownBy(
                () -> result.expectState(aggregate -> assertThat(aggregate.getMessage()).isEqualTo("message2"))
        ).actual();
        assertThat(e.getMessage()).as("Wrong message: " + e.getMessage()).contains("Unit of Work");
        assertThat(e.getMessage()).as("Wrong message: " + e.getMessage()).contains("rolled back");
    }

    /**
     * Follow up on GitHub issue https://github.com/AxonFramework/AxonFramework/issues/1219
     */
    @Test
    void stateStoredAggregateCanAttachRegisteredResource() {
        String expectedMessage = "state stored resource injection works";
        HardToCreateResource testResource = spy(new HardToCreateResource());

        fixture.registerInjectableResource(testResource)
               .givenState(() -> new StateStoredAggregate(AGGREGATE_ID, "message"))
               .when(new HandleWithResourceCommand(AGGREGATE_ID, expectedMessage))
               .expectEvents(new StubDomainEvent());

        verify(testResource).difficultOperation(expectedMessage);
    }

    @Test
    void givenWithStateStorageException() {
        fixture.useStateStorage();

        assertThatThrownBy(() -> fixture.given(new StubDomainEvent()))
                .isInstanceOf(FixtureExecutionException.class);
    }

    @Test
    void givenWithEventListAndStateStorageExpectException() {
        fixture.useStateStorage();

        assertThatThrownBy(() -> fixture.given(Collections.singletonList(new StubDomainEvent())))
                .isInstanceOf(FixtureExecutionException.class);
    }

    private static class InitializeCommand {

        private final String id;
        private final String message;

        private InitializeCommand(String id, String message) {
            this.id = id;
            this.message = message;
        }

        public String getId() {
            return id;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class SetMessageCommand {

        @TargetAggregateIdentifier
        private final String id;
        private final String message;

        private SetMessageCommand(String id, String message) {
            this.id = id;
            this.message = message;
        }

        public String getId() {
            return id;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class ErrorCommand {

        @TargetAggregateIdentifier
        private final String id;
        private final String message;

        private ErrorCommand(String id, String message) {
            this.id = id;
            this.message = message;
        }

        public String getId() {
            return id;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class HandleWithResourceCommand {

        @TargetAggregateIdentifier
        private final String id;
        private final String messageToLog;

        private HandleWithResourceCommand(String id, String messageToLog) {
            this.id = id;
            this.messageToLog = messageToLog;
        }

        public String getId() {
            return id;
        }

        public String getMessageToLog() {
            return messageToLog;
        }
    }

    @SuppressWarnings("unused")
    public static class StateStoredAggregate {

        @AggregateIdentifier
        private String id;

        private String message;

        StateStoredAggregate(String id, String message) {
            this.id = id;
            this.message = message;
            // this event, published during the givenState operation, should not be included in the expectEvents phase
            apply(new StubDomainEvent());
        }

        @CommandHandler
        public StateStoredAggregate(InitializeCommand cmd) {
            this.id = cmd.getId();
            this.message = cmd.getMessage();
            apply(new StubDomainEvent());
        }

        @CommandHandler
        public void handle(SetMessageCommand cmd) {
            this.message = cmd.getMessage();
            apply(new StubDomainEvent());
        }

        @CommandHandler
        public void handle(ErrorCommand cmd) {
            this.message = cmd.getMessage();
            apply(new StubDomainEvent());
            throw new RuntimeException("Stub");
        }

        @CommandHandler
        public void handle(HandleWithResourceCommand command, HardToCreateResource resource) {
            resource.difficultOperation(command.getMessageToLog());
            apply(new StubDomainEvent());
        }

        public String getMessage() {
            return message;
        }
    }

    private static class StubDomainEvent {

        StubDomainEvent() {
        }
    }
}
