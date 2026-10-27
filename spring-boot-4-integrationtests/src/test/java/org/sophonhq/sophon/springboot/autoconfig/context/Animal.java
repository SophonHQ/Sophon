/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.sophonhq.sophon.springboot.autoconfig.context;

import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.eventsourcing.EventSourcingHandler;
import org.sophonhq.sophon.modelling.command.AggregateIdentifier;
import org.sophonhq.sophon.spring.stereotype.Aggregate;

import static org.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;

@Aggregate(snapshotTriggerDefinition = "animalSnapshotTriggerDefinition")
public abstract class Animal {

    @AggregateIdentifier
    protected String aggregateId;
    protected String name;

    @CommandHandler
    public void handle(RenameAnimalCommand command) {
        apply(new AnimalRenamedEvent(command.getAggregateId(), command.getRename()));
    }

    @EventSourcingHandler
    public void on(AnimalRenamedEvent event) {
        this.name = event.getRename();
    }

    public Animal() {
    }
}
