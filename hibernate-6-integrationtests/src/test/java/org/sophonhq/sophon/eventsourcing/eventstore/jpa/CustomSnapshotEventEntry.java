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

package org.sophonhq.sophon.eventsourcing.eventstore.jpa;

import jakarta.persistence.Entity;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventsourcing.eventstore.AbstractSnapshotEventEntry;
import org.sophonhq.sophon.serialization.Serializer;

/**
 * @author Allard Buijze
 */
@Entity
public class CustomSnapshotEventEntry extends AbstractSnapshotEventEntry<String> {

    public CustomSnapshotEventEntry(DomainEventMessage event, Serializer serializer) {
        super(event, serializer, String.class);
    }

    /**
     * Default constructor required by JPA
     */
    protected CustomSnapshotEventEntry() {
    }
}
