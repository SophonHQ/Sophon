/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.eventsourcing.eventstore.DomainEventStream;
import org.sophonhq.sophon.eventsourcing.eventstore.EventStoreException;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.modelling.command.CommandTargetResolver;
import org.sophonhq.sophon.modelling.command.VersionedAggregateIdentifier;
import org.sophonhq.sophon.test.FixtureExecutionException;
import org.sophonhq.sophon.test.SophonAssertionError;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.*;
import static org.sophonhq.sophon.test.matchers.Matchers.exactSequenceOf;
import static org.sophonhq.sophon.test.matchers.Matchers.matches;

/**
 * Test class validating an annotated aggregate instance.
 *
 * @author Allard Buijze
 */
class FixtureTest_Annotated {

    private static final String AGGREGATE_ID = "aggregateId";

    private FixtureConfiguration<AnnotatedAggregate> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(AnnotatedAggregate.class);
    }

    @AfterEach
    void tearDown() {
        if (CurrentUnitOfWork.isStarted()) {
            fail("A unit of work is still running");
        }
    }

    @Test
    void nullIdentifierIsRejected() {
        SophonAssertionError error = assertThatExceptionOfType(SophonAssertionError.class).isThrownBy(() ->
                fixture.given(new MyEvent(null, 0))
                       .when(new TestCommand("test"))
                       .expectEvents(new MyEvent("test", 1))
                       .expectSuccessfulHandlerExecution()
        ).actual();

        assertThat(error.getMessage())
                .as("Expected test to fail with IncompatibleAggregateException")
                .contains("IncompatibleAggregateException");
    }

    @Test
    void eventsCarryCorrectTimestamp() {
        fixture.givenCurrentTime(Instant.EPOCH)
               .andGiven(new MyEvent("AggregateId", 1), new MyEvent("AggregateId", 2))
               .andGivenCommands(new TestCommand("AggregateId"))
               .when(new TestCommand("AggregateId"))
               .expectEventsMatching(new TypeSafeMatcher<List<EventMessage<?>>>() {
                   @Override
                   protected boolean matchesSafely(List<EventMessage<?>> item) {
                       return item.stream().allMatch(i -> Instant.EPOCH.equals(i.getTimestamp()));
                   }

                   @Override
                   public void describeTo(Description description) {
                       description.appendText("list with all events with timestamp at epoch");
                   }
               });
        assertThat(fixture.getEventStore()
                          .readEvents("AggregateId")
                          .asStream()
                          .allMatch(i -> Instant.EPOCH.equals(i.getTimestamp()))).isTrue();
        assertThat(fixture.getEventStore().readEvents("AggregateId")
                          .asStream()
                          .map(EventMessage::getTimestamp)
                          .distinct()
                          .count()).isOne();
    }

    @Test
    void clockStandsStillDuringExecution() {
        fixture.given(new MyEvent("AggregateId", 1), new MyEvent("AggregateId", 2))
               .when(new TestCommand("AggregateId"));

        assertThat(fixture.getEventStore().readEvents("AggregateId")
                          .asStream()
                          .map(EventMessage::getTimestamp)
                          .distinct()
                          .count()).isOne();
    }

    @Test
    void aggregateCommandHandlersOverwrittenByCustomHandlers() {
        final AtomicBoolean invoked = new AtomicBoolean(false);
        fixture.registerCommandHandler(CreateAggregateCommand.class, commandMessage -> {
            invoked.set(true);
            return null;
        });

        fixture.given().when(new CreateAggregateCommand()).expectEvents();
        assertThat(invoked.get()).isTrue();
    }

    @Test
    void aggregateIdentifier_ServerGeneratedIdentifier() {
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.given()
               .when(new CreateAggregateCommand());
    }

    @Test
    void unavailableResourcesCausesFailure() {
        TestExecutor<AnnotatedAggregate> given = fixture.given();
        assertThatThrownBy(() -> given.when(new CreateAggregateCommand()))
                .isInstanceOf(FixtureExecutionException.class);
    }

    @Test
    void aggregateIdentifier_IdentifierAutomaticallyDeducted() {
        fixture.given(new MyEvent("AggregateId", 1), new MyEvent("AggregateId", 2))
               .when(new TestCommand("AggregateId"))
               .expectEvents(new MyEvent("AggregateId", 3))
               .expectState(aggregate -> assertThat(aggregate).isNotNull());

        DomainEventStream events = fixture.getEventStore().readEvents("AggregateId");
        for (int t = 0; t < 3; t++) {
            assertThat(events).hasNext();
            DomainEventMessage<?> next = events.next();
            assertThat(next.getAggregateIdentifier()).isEqualTo("AggregateId");
            assertThat(next.getSequenceNumber()).isEqualTo(t);
        }
    }

    @Test
    void fixtureGivenCommands_ResourcesNotAvailable() {
        assertThatThrownBy(() -> fixture.givenCommands(new CreateAggregateCommand(AGGREGATE_ID)))
                .isInstanceOf(FixtureExecutionException.class);
    }

    @Test
    void fixtureGivenCommands_ResourcesAvailable() {
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.givenCommands(new CreateAggregateCommand(AGGREGATE_ID),
                              new TestCommand(AGGREGATE_ID),
                              new TestCommand(AGGREGATE_ID),
                              new TestCommand(AGGREGATE_ID))
               .when(new TestCommand(AGGREGATE_ID))
               .expectEvents(new MyEvent(AGGREGATE_ID, 4));
    }

    @Test
    void aggregateIdentifier_CustomTargetResolver() {
        CommandTargetResolver mockCommandTargetResolver = mock(CommandTargetResolver.class);
        when(mockCommandTargetResolver.resolveTarget(any()))
                .thenReturn(new VersionedAggregateIdentifier(AGGREGATE_ID, 0L));

        fixture.registerCommandTargetResolver(mockCommandTargetResolver);
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.givenCommands(new CreateAggregateCommand(AGGREGATE_ID))
               .when(new TestCommand(AGGREGATE_ID))
               .expectEvents(new MyEvent(AGGREGATE_ID, 1));

        verify(mockCommandTargetResolver).resolveTarget(any());
    }

    @Test
    void aggregate_InjectCustomResourceAfterCreatingAnnotatedHandler() {
        // a 'when' will cause command handlers to be registered.
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.given()
               .when(new CreateAggregateCommand("AggregateId"));

        assertThatThrownBy(() -> fixture.registerInjectableResource("I am injectable"))
                .isInstanceOf(FixtureExecutionException.class);
    }

    @Test
    void fixtureGeneratesExceptionOnWrongEvents_DifferentAggregateIdentifiers() {
        assertThatThrownBy(() ->
                fixture.getEventStore().publish(
                        new GenericDomainEventMessage<>("test", UUID.randomUUID().toString(), 0, new StubDomainEvent()),
                        new GenericDomainEventMessage<>("test", UUID.randomUUID().toString(), 0, new StubDomainEvent()))
        ).isInstanceOf(EventStoreException.class);
    }

    @Test
    void fixtureGeneratesExceptionOnWrongEvents_WrongSequence() {
        String identifier = UUID.randomUUID().toString();
        assertThatThrownBy(() ->
                fixture.getEventStore().publish(
                        new GenericDomainEventMessage<>("test", identifier, 0, new StubDomainEvent()),
                        new GenericDomainEventMessage<>("test", identifier, 2, new StubDomainEvent()))
        ).isInstanceOf(EventStoreException.class);
    }

    @Test
    void fixture_AggregateDeleted() {
        fixture.given(new MyEvent(AGGREGATE_ID, 5))
               .when(new DeleteCommand(AGGREGATE_ID, false))
               .expectEvents(new MyAggregateDeletedEvent(false));
    }

    @Test
    void fixtureDetectsStateChangeOutsideOfHandler_AggregateDeleted() {
        TestExecutor<AnnotatedAggregate> exec = fixture.given(new MyEvent(AGGREGATE_ID, 5));
        AssertionError error = assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> exec.when(new DeleteCommand(AGGREGATE_ID, true)))
                .actual();
        assertThat(error.getMessage()).as("Wrong message: " + error.getMessage()).contains("considered deleted");
    }

    @Test
    void andGiven() {
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.givenCommands(new CreateAggregateCommand(AGGREGATE_ID))
               .andGiven(new MyEvent(AGGREGATE_ID, 1))
               .when(new TestCommand(AGGREGATE_ID))
               .expectEvents(new MyEvent(AGGREGATE_ID, 2));
    }

    @Test
    void andGivenCommands() {
        fixture.given(new MyEvent(AGGREGATE_ID, 1))
               .andGivenCommands(new TestCommand(AGGREGATE_ID))
               .when(new TestCommand(AGGREGATE_ID))
               .expectEvents(new MyEvent(AGGREGATE_ID, 3));
    }

    @Test
    void multipleAndGivenCommands() {
        fixture.given(new MyEvent(AGGREGATE_ID, 1))
               .andGivenCommands(new TestCommand(AGGREGATE_ID))
               .andGivenCommands(new TestCommand(AGGREGATE_ID))
               .when(new TestCommand(AGGREGATE_ID))
               .expectEvents(new MyEvent(AGGREGATE_ID, 4));
    }

    @Test
    void givenNoPriorActivityAndPublishingNoEvents() {
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.givenNoPriorActivity()
               .when(new CreateAggregateCommand(AGGREGATE_ID, CreateAggregateCommand.SHOULD_NOT_PUBLISH))
               .expectNoEvents()
               .expectSuccessfulHandlerExecution();
    }

    @Test
    void givenFollowedUpByAndGivenCurrentTimeWorksAsExcepted() {
        String testAggregateId = "1337";

        fixture.given(new MyEvent(testAggregateId, 1), new MyEvent(testAggregateId, 2))
               .andGivenCurrentTime(Instant.EPOCH)
               .when(new TestCommand(testAggregateId))
               .expectEventsMatching(exactSequenceOf(matches(
                       eventMessage -> eventMessage.getTimestamp() == Instant.EPOCH
                               && MyEvent.class.equals(eventMessage.getPayloadType())
               )));

        assertThat(fixture.getEventStore()
                          .readEvents(testAggregateId)
                          .asStream().count()).isEqualTo(3);
    }

    @Test
    void fixtureThrowsEventStoreExceptionWhenHandlingAggregateConstructingCommandWhileThereAreGivenEvents() {
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.given(new MyEvent(AGGREGATE_ID, 0))
               .when(new CreateAggregateCommand(AGGREGATE_ID))
               .expectException(EventStoreException.class)
               .expectNoEvents();
    }

    @Test
    void fixtureThrowsEventStoreExceptionWhenHandlingAggregateConstructingCommandWhileThereAreGivenCommand() {
        fixture.registerInjectableResource(new HardToCreateResource());
        fixture.givenCommands(new CreateAggregateCommand(AGGREGATE_ID))
               .when(new CreateAggregateCommand(AGGREGATE_ID))
               .expectException(EventStoreException.class)
               .expectNoEvents();
    }

    private static class StubDomainEvent {

        public StubDomainEvent() {
        }
    }
}
