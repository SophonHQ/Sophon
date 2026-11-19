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

import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.modelling.command.AggregateNotFoundException;
import org.sophonhq.sophon.test.SophonAssertionError;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.sophonhq.sophon.commandhandling.GenericCommandResultMessage.asCommandResultMessage;

/**
 * @author Allard Buijze
 * @since axon-0.7
 */
class FixtureTest_RegularParams {

    private FixtureConfiguration<StandardAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(StandardAggregate.class);
        fixture.registerAggregateFactory(new StandardAggregate.Factory());
    }

    @Test
    void fixture_NoEventsInStore() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .given()
                .when(new TestCommand(UUID.randomUUID()))
                .expectException(AggregateNotFoundException.class);
    }

    @Test
    void firstFixture() {
        ResultValidator<StandardAggregate> validator = fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                      fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 1))
                .when(new TestCommand("aggregateId"));
        validator.expectResultMessagePayload(null);
        validator.expectEvents(new MyEvent("aggregateId", 2));
    }

    @Test
    void firstFixtureMatchingCommandResultMessage() {
        ResultValidator<StandardAggregate> validator = fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                      fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 1))
                .when(new TestCommand("aggregateId"));
        validator.expectResultMessage(asCommandResultMessage(null));
        validator.expectEvents(new MyEvent("aggregateId", 2));
    }

    @Test
    void expectEventsIgnoresFilteredField() {
        ResultValidator<StandardAggregate> validator = fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                      fixture.getEventBus()))
                .registerFieldFilter(field -> !field.getName().equals("someBytes"))
                .given(new MyEvent("aggregateId", 1))
                .when(new TestCommand("aggregateId"));
        validator.expectResultMessagePayload(null);
        validator.expectEvents(new MyEvent("aggregateId", 2, "ignored".getBytes()));
    }

    @Test
    void fixture_SetterInjection() {
        MyCommandHandler commandHandler = new MyCommandHandler();
        commandHandler.setRepository(fixture.getRepository());
        fixture.registerAnnotatedCommandHandler(commandHandler)
                .given(new MyEvent("aggregateId", 1),
                       new MyEvent("aggregateId", 2))
                .when(new TestCommand("aggregateId"))
                .expectResultMessagePayloadMatching(IsNull.nullValue())
                .expectEvents(new MyEvent("aggregateId", 3));
    }

    @Test
    void fixture_GivenAList() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        fixture
                .registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                      fixture.getEventBus()))
                .given(givenEvents)
                .when(new TestCommand("aggregateId"))
                .expectEvents(new MyEvent("aggregateId", 4))
                .expectSuccessfulHandlerExecution();
    }

    @Test
    void fixtureDetectsStateChangeOutsideOfHandler_ExplicitValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));

        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class).isThrownBy(() ->
                fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                             fixture.getEventBus()))
                       .given(givenEvents)
                       .when(new IllegalStateChangeCommand("aggregateId", 5))).actual();
        assertThat(e.getMessage()).as("Wrong message: " + e.getMessage()).contains(".lastNumber\"");
        assertThat(e.getMessage()).as("Wrong message: " + e.getMessage()).contains("<5>");
        assertThat(e.getMessage()).as("Wrong message: " + e.getMessage()).contains("<4>");
    }

    @Test
    void fixtureIgnoredStateChangeInFilteredField() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        fixture.registerFieldFilter(field -> !field.getName().equals("lastNumber"));
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .given(givenEvents)
                .when(new IllegalStateChangeCommand("aggregateId", 5));
    }

    @Test
    void fixtureDetectsStateChangeOutsideOfHandler_NullValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class).isThrownBy(() ->
                fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                             fixture.getEventBus()))
                        .given(givenEvents)
                        .when(new IllegalStateChangeCommand("aggregateId", null))).actual();
        assertThat(e.getMessage()).as("Wrong message: " + e.getMessage()).contains(".lastNumber\"");
        assertThat(e.getMessage()).as("Wrong message: " + e.getMessage()).contains("<null>");
        assertThat(e.getMessage()).as("Wrong message: " + e.getMessage()).contains("<4>");
    }

    @Test
    void fixtureDetectsStateChangeOutsideOfHandler_Ignored() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        fixture.setReportIllegalStateChange(false);
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .given(givenEvents)
                .when(new IllegalStateChangeCommand("aggregateId", null));
    }

    @Test
    void fixtureDetectsStateChangeOutsideOfHandler_AggregateGeneratesIdentifier() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .given()
                .when(new CreateAggregateCommand(null));
    }

    @Test
    void fixtureDetectsStateChangeOutsideOfHandler_AggregateDeleted() {
        TestExecutor<StandardAggregate> exec = fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                                         fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 5));

        AssertionError error = assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> exec.when(new DeleteCommand("aggregateId", true)))
                .actual();
        assertThat(error.getMessage()).as("Wrong message: " + error.getMessage()).contains("considered deleted");
    }

    @Test
    void fixture_AggregateDeleted() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .given(new MyEvent("aggregateId", 5))
                .when(new DeleteCommand("aggregateId", false))
                .expectEvents(new MyAggregateDeletedEvent(false));
    }

    @Test
    void fixtureGivenCommands() {
        fixture.registerAnnotatedCommandHandler(new MyCommandHandler(fixture.getRepository(),
                                                                     fixture.getEventBus()))
                .givenCommands(new CreateAggregateCommand("aggregateId"),
                               new TestCommand("aggregateId"),
                               new TestCommand("aggregateId"),
                               new TestCommand("aggregateId"))
                .when(new TestCommand("aggregateId"))
                .expectEvents(new MyEvent("aggregateId", 4));
    }

    @Test
    void fixture_CommandHandlerDispatchesNonDomainEvents() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        // the domain events are part of the transaction, but the command handler directly dispatches an application
        // event to the event bus. This event dispatched anyway. The
        fixture
                .registerAnnotatedCommandHandler(commandHandler)
                .given(givenEvents)
                .when(new PublishEventCommand("aggregateId"))
                .expectEvents(new MyApplicationEvent());
    }

    @Test
    void fixture_ReportWrongNumberOfEvents() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class).isThrownBy(() ->
                fixture.registerAnnotatedCommandHandler(commandHandler)
                        .given(givenEvents)
                        .when(new TestCommand("aggregateId"))
                        .expectEvents(new MyEvent("aggregateId", 4),
                                      new MyEvent("aggregateId", 5))
        ).actual();
        assertThat(e.getMessage()).contains("org.sophonhq.sophon.test.aggregate.MyEvent <|> ");
    }

    @Test
    void fixture_ReportWrongEvents() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class).isThrownBy(() ->
                fixture
                        .registerAnnotatedCommandHandler(commandHandler)
                        .given(givenEvents)
                        .when(new TestCommand("aggregateId"))
                        .expectEvents(new MyOtherEvent())
        ).actual();
        assertThat(e.getMessage()).contains("org.sophonhq.sophon.test.aggregate.MyOtherEvent <|>"
                                                    + " org.sophonhq.sophon.test.aggregate.MyEvent");
    }

    @Test
    void fixture_UnexpectedException() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class).isThrownBy(() ->
                fixture
                        .registerAnnotatedCommandHandler(commandHandler)
                        .given(givenEvents)
                        .when(new StrangeCommand("aggregateId"))
                        .expectSuccessfulHandlerExecution()
        ).actual();
        String resultMessage = e.getMessage();
        assertThat(resultMessage)
                .as(resultMessage)
                .contains("but got <exception of type [StrangeCommandReceivedException]>");
    }

    @Test
    void fixture_UnexpectedReturnValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class).isThrownBy(() ->
                fixture
                        .registerAnnotatedCommandHandler(commandHandler)
                        .given(givenEvents)
                        .when(new TestCommand("aggregateId"))
                        .expectException(RuntimeException.class)
        ).actual();
        String resultMessage = e.getMessage();
        assertThat(resultMessage)
                .as(resultMessage)
                .contains("The command handler returned normally, but an exception was expected");
        assertThat(resultMessage)
                .as(resultMessage)
                .contains("<an instance of java.lang.RuntimeException>,\n but got <null>");
    }

    @Test
    void fixture_WrongReturnValue() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(), fixture.getEventBus());
        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class).isThrownBy(() ->
                fixture.registerAnnotatedCommandHandler(commandHandler)
                        .given(givenEvents)
                        .when(new TestCommand("aggregateId"))
                        .expectResultMessagePayload("some")
        ).actual();
        String resultMessage = e.getMessage();
        assertThat(resultMessage)
                .as(resultMessage)
                .contains("<Message with payload <\"some\">>,\n but got <Message with payload <null>>.");
    }

    @Test
    void fixture_WrongExceptionType() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class).isThrownBy(() ->
                fixture.registerAnnotatedCommandHandler(commandHandler)
                        .given(givenEvents)
                        .when(new StrangeCommand("aggregateId"))
                        .expectException(IOException.class)
        ).actual();
        String resultMessage = e.getMessage();
        assertThat(resultMessage)
                .as(resultMessage)
                .contains("<an instance of java.io.IOException>,\n "
                                  + "but got <exception of type [StrangeCommandReceivedException]>");
    }

    @Test
    void fixture_WrongEventContents() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());

        ResultValidator<StandardAggregate> resultValidator = fixture.registerAnnotatedCommandHandler(commandHandler)
                                                                    .given(givenEvents)
                                                                    .when(new TestCommand("aggregateId"));
        // Should be 4 instead of 5.
        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class)
                .isThrownBy(() -> resultValidator.expectEvents(new MyEvent("aggregateId", 5)))
                .actual();
        assertThat(e.getMessage()).contains("The message of type [MyEvent] was not as expected.");
        assertThat(e.getMessage()).contains("Expected <MyEvent{someValue=5");
        assertThat(e.getMessage()).contains("but got <MyEvent{someValue=4");
    }

    @Test
    void fixture_WrongEventContents_WithNullValues() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());

        ResultValidator<StandardAggregate> resultValidator = fixture.registerAnnotatedCommandHandler(commandHandler)
                                                                    .given(givenEvents)
                                                                    .when(new TestCommand("aggregateId"));
        // Should be 4 instead of null.
        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class)
                .isThrownBy(() -> resultValidator.expectEvents(new MyEvent("aggregateId", null)))
                .actual();
        assertThat(e.getMessage()).contains("The message of type [MyEvent] was not as expected.");
        assertThat(e.getMessage()).contains("Expected <MyEvent{someValue=null");
        assertThat(e.getMessage()).contains("but got <MyEvent{someValue=4");
    }

    @Test
    void fixture_ExpectedPublishedSameAsStored() {
        List<?> givenEvents = Arrays.asList(new MyEvent("aggregateId", 1),
                                            new MyEvent("aggregateId", 2),
                                            new MyEvent("aggregateId", 3));
        MyCommandHandler commandHandler = new MyCommandHandler(fixture.getRepository(),
                                                               fixture.getEventBus());
        SophonAssertionError e = assertThatExceptionOfType(SophonAssertionError.class).isThrownBy(() ->
                fixture
                        .registerAnnotatedCommandHandler(commandHandler)
                        .given(givenEvents)
                        .when(new StrangeCommand("aggregateId"))
                        .expectException(StrangeCommandReceivedException.class)
                        .expectEvents(new MyEvent("aggregateId", 4))
        ).actual();
        assertThat(e.getMessage()).contains("The published events do not match the expected events");
        assertThat(e.getMessage()).contains("org.sophonhq.sophon.test.aggregate.MyEvent <|> ");
        assertThat(e.getMessage()).contains("probable cause");
    }
}
