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

package org.sophonhq.sophon.eventhandling;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.messaging.MetaData;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Allard Buijze
 */
class GenericDomainEventMessageTest {

    @Test
    void constructor() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        GenericDomainEventMessage<Object> message1 = new GenericDomainEventMessage<>("type", id, seqNo, payload);
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message2 = new GenericDomainEventMessage<>("type", id, seqNo, payload, metaData);
        GenericDomainEventMessage<Object> message3 = new GenericDomainEventMessage<>("type", id, seqNo, payload, metaDataMap);

        assertThat(message1.getAggregateIdentifier()).isSameAs(id);
        assertThat(message1.getSequenceNumber()).isEqualTo(seqNo);
        assertThat(message1.getMetaData()).isSameAs(MetaData.emptyInstance());
        assertThat(message1.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(message1.getPayloadType()).isEqualTo(Object.class);

        assertThat(message2.getAggregateIdentifier()).isSameAs(id);
        assertThat(message2.getSequenceNumber()).isEqualTo(seqNo);
        assertThat(message2.getMetaData()).isSameAs(metaData);
        assertThat(message2.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(message2.getPayloadType()).isEqualTo(Object.class);

        assertThat(message3.getAggregateIdentifier()).isSameAs(id);
        assertThat(message3.getSequenceNumber()).isEqualTo(seqNo);
        assertThat(message3.getMetaData()).isNotSameAs(metaDataMap);
        assertThat(message3.getMetaData()).isEqualTo(metaDataMap);
        assertThat(message3.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(message3.getPayloadType()).isEqualTo(Object.class);

        assertThat(message2.getIdentifier()).isNotEqualTo(message1.getIdentifier());
        assertThat(message3.getIdentifier()).isNotEqualTo(message1.getIdentifier());
        assertThat(message3.getIdentifier()).isNotEqualTo(message2.getIdentifier());
    }

    @Test
    void withMetaData() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message = new GenericDomainEventMessage<>("type", id, seqNo, payload, metaData);
        GenericDomainEventMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        GenericDomainEventMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertThat(message1.getMetaData().size()).isZero();
        assertThat(message2.getMetaData().size()).isOne();
    }

    @Test
    void andMetaData() {
        Object payload = new Object();
        long seqNo = 0;
        String id = UUID.randomUUID().toString();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericDomainEventMessage<Object> message = new GenericDomainEventMessage<>("type", id, seqNo, payload, metaData);
        GenericDomainEventMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        GenericDomainEventMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertThat(message1.getMetaData().size()).isOne();
        assertThat(message1.getMetaData()).containsEntry("key", "value");
        assertThat(message2.getMetaData().size()).isOne();
        assertThat(message2.getMetaData()).containsEntry("key", "otherValue");
    }

    @Test
    void testToString() {
        String actual = new GenericDomainEventMessage<>("AggregateType", "id1", 1, "MyPayload").andMetaData(MetaData.with("key", "value").and("key2", 13)).toString();
        assertThat(actual)
                .as("Wrong output: " + actual)
                .startsWith("GenericDomainEventMessage{payload={MyPayload}, metadata={");
        assertThat(actual).as("Wrong output: " + actual).contains("'key'->'value'");
        assertThat(actual).as("Wrong output: " + actual).contains("'key2'->'13'");
        assertThat(actual).as("Wrong output: " + actual).contains("', timestamp='");
        assertThat(actual).as("Wrong output: " + actual).contains("', aggregateIdentifier='id1'");
        assertThat(actual).as("Wrong output: " + actual).contains("', aggregateType='AggregateType'");
        assertThat(actual).as("Wrong output: " + actual).contains("', sequenceNumber=1");
        assertThat(actual).as("Wrong output: " + actual).endsWith("}");
    }
}
