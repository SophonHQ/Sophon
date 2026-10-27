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

package io.github.sophonhq.sophon.messaging;

import io.github.sophonhq.sophon.common.Assert;
import io.github.sophonhq.sophon.eventhandling.EventMessage;
import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.serialization.SerializedObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Default headers to include when publishing a message on a broker.
 *
 * @author Nakul Mishra
 * @since axon-3.0
 */
public abstract class Headers {

    /**
     * Key pointing to a message identifier.
     */
    public static final String MESSAGE_ID = "sophon-message-id";
    /**
     * Key pointing to the serialized payload of a message.
     */
    public static final String SERIALIZED_MESSAGE_PAYLOAD = "sophon-serialized-message-payload";
    /**
     * Key pointing to the payload type of a message.
     */
    public static final String MESSAGE_TYPE = "sophon-message-type";
    /**
     * Key pointing to the revision of a message.
     */
    public static final String MESSAGE_REVISION = "sophon-message-revision";
    /**
     * Key pointing to the timestamp of a message.
     */
    public static final String MESSAGE_TIMESTAMP = "sophon-message-timestamp";
    /**
     * Key pointing to the aggregate identifier of a message.
     */
    public static final String AGGREGATE_ID = "sophon-message-aggregate-id";
    /**
     * Key pointing to the aggregate sequence of a message.
     */
    public static final String AGGREGATE_SEQ = "sophon-message-aggregate-seq";
    /**
     * Key pointing to the aggregate type of a message.
     */
    public static final String AGGREGATE_TYPE = "sophon-message-aggregate-type";
    /**
     * Key pointing to the {@link MetaData} of a message.
     */
    public static final String MESSAGE_METADATA = "sophon-metadata";
    /**
     * Key pointing to the deadline name of a {@link io.github.sophonhq.sophon.deadline.DeadlineMessage}.
     */
    public static final String DEADLINE_NAME = "sophon-deadline-name";

    private Headers() {
    }

    /**
     * Generate defaults headers to recognise an event message.
     *
     * @param message          event message.
     * @param serializedObject payload.
     * @return headers
     */
    public static Map<String, Object> defaultHeaders(EventMessage<?> message,
                                                     SerializedObject<?> serializedObject) {
        Assert.notNull(message, () -> "Event message cannot be null");
        Assert.notNull(serializedObject, () -> "Serialized Object cannot be null");
        Assert.notNull(serializedObject.getType(), () -> "SerializedObject Type cannot be null");
        HashMap<String, Object> headers = new HashMap<>();
        headers.put(MESSAGE_ID, message.getIdentifier());
        headers.put(MESSAGE_TYPE, serializedObject.getType().getName());
        headers.put(MESSAGE_REVISION, serializedObject.getType().getRevision());
        headers.put(MESSAGE_TIMESTAMP, message.getTimestamp());

        if (message instanceof DomainEventMessage) {
            headers.put(AGGREGATE_ID, ((DomainEventMessage<?>) message).getAggregateIdentifier());
            headers.put(AGGREGATE_SEQ, ((DomainEventMessage<?>) message).getSequenceNumber());
            headers.put(AGGREGATE_TYPE, ((DomainEventMessage<?>) message).getType());
        }

        return Collections.unmodifiableMap(headers);
    }

    @Override
    public String toString() {
        return "[Headers]";
    }
}
