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
import org.sophonhq.sophon.messaging.GenericMessage;
import org.sophonhq.sophon.messaging.MetaData;

import java.io.*;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Allard Buijze
 */
class GenericEventMessageTest {

    @Test
    void constructor() {
        Object payload = new Object();
        GenericEventMessage<Object> message1 = new GenericEventMessage<>(payload);
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericEventMessage<Object> message2 = new GenericEventMessage<>(payload, metaData);
        GenericEventMessage<Object> message3 = new GenericEventMessage<>(payload, metaDataMap);

        assertThat(message1.getMetaData()).isSameAs(MetaData.emptyInstance());
        assertThat(message1.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(message1.getPayloadType()).isEqualTo(Object.class);

        assertThat(message2.getMetaData()).isEqualTo(metaData);
        assertThat(message2.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(message2.getPayloadType()).isEqualTo(Object.class);

        assertThat(message3.getMetaData()).isNotSameAs(metaDataMap);
        assertThat(message3.getMetaData()).isEqualTo(metaDataMap);
        assertThat(message3.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(message3.getPayloadType()).isEqualTo(Object.class);

        assertThat(message1.getIdentifier()).isNotEqualTo(message2.getIdentifier());
        assertThat(message1.getIdentifier()).isNotEqualTo(message3.getIdentifier());
        assertThat(message2.getIdentifier()).isNotEqualTo(message3.getIdentifier());
    }

    @Test
    void withMetaData() {
        Object payload = new Object();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericEventMessage<Object> message = new GenericEventMessage<>(payload, metaData);
        GenericEventMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        GenericEventMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", "otherValue")));

        assertThat(message1.getMetaData().size()).isZero();
        assertThat(message2.getMetaData().size()).isOne();
    }

    @Test
    void andMetaData() {
        Object payload = new Object();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericEventMessage<Object> message = new GenericEventMessage<>(payload, metaData);
        GenericEventMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        GenericEventMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", "otherValue")));

        assertThat(message1.getMetaData().size()).isOne();
        assertThat(message1.getMetaData()).containsEntry("key", "value");
        assertThat(message2.getMetaData().size()).isOne();
        assertThat(message2.getMetaData()).containsEntry("key", "otherValue");
    }

    @Test
    void timestampInEventMessageIsAlwaysSerialized() throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        GenericEventMessage<String> testSubject =
                new GenericEventMessage<>(new GenericMessage<>("payload", Collections.singletonMap("key", "value")),
                                          Instant::now);
        oos.writeObject(testSubject);
        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        Object read = ois.readObject();

        assertThat(read.getClass()).isEqualTo(GenericEventMessage.class);
        assertThat(((GenericEventMessage<?>) read).getTimestamp()).isNotNull();
    }

    @Test
    void testToString() {
        String actual = GenericEventMessage.asEventMessage("MyPayload").andMetaData(MetaData.with("key", "value").and("key2", 13)).toString();
        assertThat(actual)
                .as("Wrong output: " + actual)
                .startsWith("GenericEventMessage{payload={MyPayload}, metadata={");
        assertThat(actual).as("Wrong output: " + actual).contains("'key'->'value'");
        assertThat(actual).as("Wrong output: " + actual).contains("'key2'->'13'");
        assertThat(actual).as("Wrong output: " + actual).contains("', timestamp='");
        assertThat(actual).as("Wrong output: " + actual).endsWith("}");
    }
}
