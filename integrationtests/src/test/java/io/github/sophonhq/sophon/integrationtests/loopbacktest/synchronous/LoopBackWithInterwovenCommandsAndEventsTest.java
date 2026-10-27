/*
 * Copyright (c) 2010-2020. Axon Framework
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

package io.github.sophonhq.sophon.integrationtests.loopbacktest.synchronous;

import io.github.sophonhq.sophon.commandhandling.CommandHandler;
import io.github.sophonhq.sophon.commandhandling.CommandMessage;
import io.github.sophonhq.sophon.commandhandling.GenericCommandMessage;
import io.github.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import io.github.sophonhq.sophon.config.AggregateConfigurer;
import io.github.sophonhq.sophon.config.Configuration;
import io.github.sophonhq.sophon.config.DefaultConfigurer;
import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.eventsourcing.AggregateFactory;
import io.github.sophonhq.sophon.eventsourcing.EventSourcingHandler;
import io.github.sophonhq.sophon.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import io.github.sophonhq.sophon.messaging.Message;
import io.github.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import io.github.sophonhq.sophon.messaging.unitofwork.UnitOfWork;
import io.github.sophonhq.sophon.modelling.command.Aggregate;
import io.github.sophonhq.sophon.modelling.command.AggregateIdentifier;
import io.github.sophonhq.sophon.modelling.command.AggregateNotFoundException;
import io.github.sophonhq.sophon.modelling.command.AggregateRoot;
import io.github.sophonhq.sophon.modelling.command.Repository;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.github.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating that command dispatched within a UnitOfWork that publishes events withholds to the desired
 * event ordering.
 *
 * @author Gerard de Leeuw
 * @author Allard Buijze
 */
class LoopBackWithInterwovenCommandsAndEventsTest {

    // This ensures we do not wire Axon Server components
    private static final boolean DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES = false;

    private final String aggregateIdentifier = "Aggregate";
    private MyCommand command;
    private Configuration configuration;

    @BeforeEach
    void setUp() {
        AggregateConfigurer<MyAggregate> aggregateConfigurer =
                AggregateConfigurer.defaultConfiguration(MyAggregate.class)
                                   .configureAggregateFactory(c -> new AggregateFactory<MyAggregate>() {
                                       @Override
                                       public MyAggregate createAggregateRoot(String aggregateIdentifier,
                                                                              DomainEventMessage<?> firstEvent) {
                                           return new MyAggregate(aggregateIdentifier);
                                       }

                                       @Override
                                       public Class<MyAggregate> getAggregateType() {
                                           return MyAggregate.class;
                                       }
                                   });
        configuration = DefaultConfigurer.defaultConfiguration(DO_NOT_AUTO_LOCATE_CONFIGURER_MODULES)
                                         .configureEmbeddedEventStore(c -> new InMemoryEventStorageEngine())
                                         .configureAggregate(aggregateConfigurer)
                                         .registerCommandHandler(c -> new MyCommandHandler(
                                                 c.repository(MyAggregate.class), c.commandGateway()
                                         ))
                                         .buildConfiguration();
        configuration.start();

        command = new MyCommand("outer", aggregateIdentifier,
                                new MyCommand("middle", aggregateIdentifier,
                                              new MyCommand("inner", aggregateIdentifier)));
    }

    @Test
    void orderInCommandHandlerAggregate() {
        MyAggregate commandHandlerAggregate = configuration.commandGateway().sendAndWait(command);

        assertEquals(expectedDescriptions(command), commandHandlerAggregate.getHandledCommands());
    }

    @Test
    void orderInEventSourcedAggregate() {
        Repository<MyAggregate> repository = configuration.repository(MyAggregate.class);
        configuration.commandGateway().sendAndWait(command);

        UnitOfWork<CommandMessage<?>> unitOfWork =
                DefaultUnitOfWork.startAndGet(GenericCommandMessage.asCommandMessage("loading"));
        MyAggregate loadedAggregate = repository.load(aggregateIdentifier).invoke(Function.identity());
        unitOfWork.commit();

        assertEquals(expectedDescriptions(command), loadedAggregate.getHandledCommands());
    }

    @Test
    void orderInEventStore() {
        configuration.commandGateway().sendAndWait(command);
        assertEquals(expectedDescriptions(command), configuration.eventStore()
                                                                 .readEvents(aggregateIdentifier)
                                                                 .asStream()
                                                                 .map(Message::getPayload)
                                                                 .map(MyEvent.class::cast)
                                                                 .map(MyEvent::getDescription)
                                                                 .collect(Collectors.toList()));
    }

    private List<String> expectedDescriptions(MyCommand command) {
        List<String> descriptions = new ArrayList<>();
        descriptions.add(command.startDescription());
        if (command.getInnerCommand() != null) {
            descriptions.addAll(expectedDescriptions(command.getInnerCommand()));
        }
        descriptions.add(command.doneDescription());
        return descriptions;
    }

    /**
     * @author Gerard de Leeuw
     * @since axon-0.1.0 on 4-1-2017
     */
    @AggregateRoot
    public static class MyAggregate {

        private final List<String> handledCommands;
        @AggregateIdentifier
        private String aggregateIdentifier;

        public MyAggregate(String aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.handledCommands = new ArrayList<>();
        }

        public void handle(MyCommand command, CommandGateway commandGateway) {
            apply(new MyEvent(aggregateIdentifier, command.startDescription()));
            if (command.getInnerCommand() != null) {
                commandGateway.sendAndWait(command.getInnerCommand());
            }
            apply(new MyEvent(aggregateIdentifier, command.doneDescription()));
        }

        @EventSourcingHandler
        public void handle(MyEvent event) {
            this.aggregateIdentifier = event.getAggregateIdentifier();
            handledCommands.add(event.getDescription());
        }

        public List<String> getHandledCommands() {
            return handledCommands;
        }
    }

    /**
     * @author Gerard de Leeuw
     * @since axon-0.1.0 on 4-1-2017
     */
    public static class MyCommand {

        private final String name;
        private final String aggregateIdentifier;
        private final MyCommand innerCommand;

        public MyCommand(String name, String aggregateIdentifier) {
            this(name, aggregateIdentifier, null);
        }

        public MyCommand(String name, String aggregateIdentifier, MyCommand innerCommand) {
            this.name = name;
            this.aggregateIdentifier = aggregateIdentifier;
            this.innerCommand = innerCommand;
        }

        public String getName() {
            return name;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }

        public MyCommand getInnerCommand() {
            return innerCommand;
        }

        public String startDescription() {
            return "Start handling command " + name;
        }

        public String doneDescription() {
            return "Done handling command " + name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * @author Gerard de Leeuw
     * @since axon-0.1.0 on 4-1-2017
     */
    public static class MyCommandHandler {

        private final Repository<MyAggregate> repository;
        private final CommandGateway commandGateway;

        public MyCommandHandler(Repository<MyAggregate> repository, CommandGateway commandGateway) {
            this.repository = repository;
            this.commandGateway = commandGateway;
        }

        @CommandHandler
        public MyAggregate handle(MyCommand command) throws Exception {
            Aggregate<MyAggregate> aggregate;
            try {
                aggregate = repository.load(command.getAggregateIdentifier());
            } catch (AggregateNotFoundException e) {
                aggregate = repository.newInstance(() -> new MyAggregate(command.getAggregateIdentifier()));
            }
            aggregate.execute(a -> a.handle(command, commandGateway));
            return aggregate.invoke(Function.identity());
        }
    }

    /**
     * @author Gerard de Leeuw
     * @since axon-0.1.0 on 4-1-2017
     */
    public static class MyEvent {

        private final String aggregateIdentifier;
        private final String description;

        public MyEvent(String aggregateIdentifier, String description) {
            this.aggregateIdentifier = aggregateIdentifier;
            this.description = description;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
