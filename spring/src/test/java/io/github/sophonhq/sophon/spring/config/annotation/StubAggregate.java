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

package io.github.sophonhq.sophon.spring.config.annotation;

import io.github.sophonhq.sophon.modelling.command.AggregateIdentifier;
import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.EventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import io.github.sophonhq.sophon.eventsourcing.EventSourcingHandler;
import io.github.sophonhq.sophon.messaging.MetaData;
import io.github.sophonhq.sophon.spring.utils.StubDomainEvent;

import java.util.UUID;

import static io.github.sophonhq.sophon.modelling.command.AggregateLifecycle.apply;
import static io.github.sophonhq.sophon.modelling.command.AggregateLifecycle.markDeleted;

/**
 * @author Allard Buijze
 */
public class StubAggregate {

    private int invocationCount;

    @AggregateIdentifier
    private String identifier;

    public StubAggregate() {
        identifier = UUID.randomUUID().toString();
    }

    public StubAggregate(Object identifier) {
        this.identifier = identifier.toString();
    }

    public void doSomething() {
        apply(new StubDomainEvent());
    }

    @EventSourcingHandler
    protected void handle(EventMessage event) {
        identifier = ((DomainEventMessage) event).getAggregateIdentifier();
        invocationCount++;
    }

    public int getInvocationCount() {
        return invocationCount;
    }

    public DomainEventMessage createSnapshotEvent() {
        return new GenericDomainEventMessage<>("test", identifier, (long) 5,
                                               new StubDomainEvent(), MetaData.emptyInstance());
    }

    public void delete() {
        apply(new StubDomainEvent());
        markDeleted();
    }
}
