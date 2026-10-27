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

import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.modelling.command.Aggregate;
import org.sophonhq.sophon.modelling.command.Repository;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;

/**
 * @author Allard Buijze
 */
class MyCommandHandler {

    private Repository<StandardAggregate> repository;
    private EventBus eventBus;

    MyCommandHandler(Repository<StandardAggregate> repository, EventBus eventBus) {
        this.repository = repository;
        this.eventBus = eventBus;
    }

    MyCommandHandler() {
    }

    @CommandHandler
    public void createAggregate(CreateAggregateCommand command) throws Exception {
        repository.newInstance(() -> new StandardAggregate(0, command.getAggregateIdentifier()));
    }

    @CommandHandler
    public void handleTestCommand(TestCommand testCommand) {
        repository.load(testCommand.getAggregateIdentifier().toString(), null)
                .execute(StandardAggregate::doSomething);
    }

    @CommandHandler
    public void handleStrangeCommand(StrangeCommand testCommand) {
        repository.load(testCommand.getAggregateIdentifier().toString(), null).execute(StandardAggregate::doSomething);
        eventBus.publish(new GenericEventMessage<>(new MyApplicationEvent()));
        throw new StrangeCommandReceivedException("Strange command received");
    }

    @CommandHandler
    public void handleEventPublishingCommand(PublishEventCommand testCommand) {
        eventBus.publish(new GenericEventMessage<>(new MyApplicationEvent()));
    }

    @CommandHandler
    public void handleIllegalStateChange(IllegalStateChangeCommand command) {
        Aggregate<StandardAggregate> aggregate = repository.load(command.getAggregateIdentifier().toString());
        aggregate.execute(r -> r.doSomethingIllegal(command.getNewIllegalValue()));
    }

    @CommandHandler
    public void handleDeleteAggregate(DeleteCommand command) {
        repository.load(command.getAggregateIdentifier().toString())
                .execute(r -> r.delete(command.isAsIllegalChange()));
    }

    public void setRepository(Repository<StandardAggregate> repository) {
        this.repository = repository;
    }
}
