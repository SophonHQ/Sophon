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

package org.sophonhq.sophon.eventhandling.scheduling.dbscheduler;

import org.sophonhq.sophon.common.SophonException;

/**
 * Exception indicating a problem in the Event Scheduling mechanism, more precisely the
 * {@link DbSchedulerEventScheduler} was not supplied properly.
 *
 * @author Gerard Klijs
 * @since axon-4.8.0
 */
public class EventSchedulerNotSuppliedException extends SophonException {

    /**
     * Initialize a EventSchedulerNotSuppliedException.
     */
    public EventSchedulerNotSuppliedException() {
        super("The DbSchedulerEventScheduler is not properly supplied to execute the task.");
    }
}
