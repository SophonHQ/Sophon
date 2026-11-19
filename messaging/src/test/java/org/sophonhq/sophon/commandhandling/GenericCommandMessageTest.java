/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.sophonhq.sophon.commandhandling;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.messaging.GenericMessage;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.MetaData;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.commandhandling.GenericCommandMessage.asCommandMessage;

/**
 * Test class validating the {@link GenericCommandMessage}.
 *
 * @author Allard Buijze
 */
class GenericCommandMessageTest {

    @Test
    void constructor() {
        Object payload = new Object();
        CommandMessage<Object> message1 = asCommandMessage(payload);
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message2 = new GenericCommandMessage<>(payload, metaData);
        GenericCommandMessage<Object> message3 = new GenericCommandMessage<>(payload, metaDataMap);

        assertThat(message1.getMetaData()).isSameAs(MetaData.emptyInstance());
        assertThat(message1.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(message1.getPayloadType()).isEqualTo(Object.class);

        assertThat(message2.getMetaData()).isSameAs(metaData);
        assertThat(message2.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(message2.getPayloadType()).isEqualTo(Object.class);

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
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message = new GenericCommandMessage<>(payload, metaData);
        GenericCommandMessage<Object> message1 = message.withMetaData(MetaData.emptyInstance());
        GenericCommandMessage<Object> message2 = message.withMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertThat(message1.getMetaData().size()).isZero();
        assertThat(message2.getMetaData().size()).isOne();
    }

    @Test
    void andMetaData() {
        Object payload = new Object();
        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        MetaData metaData = MetaData.from(metaDataMap);
        GenericCommandMessage<Object> message = new GenericCommandMessage<>(payload, metaData);
        GenericCommandMessage<Object> message1 = message.andMetaData(MetaData.emptyInstance());
        GenericCommandMessage<Object> message2 = message.andMetaData(
                MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertThat(message1.getMetaData().size()).isOne();
        assertThat(message1.getMetaData()).containsEntry("key", "value");
        assertThat(message2.getMetaData().size()).isOne();
        assertThat(message2.getMetaData()).containsEntry("key", "otherValue");
    }

    @Test
    void testToString() {
        String actual = GenericCommandMessage.asCommandMessage("MyPayload").andMetaData(MetaData.with("key", "value").and("key2", 13)).toString();
        assertThat(actual)
                .as("Wrong output: " + actual)
                .startsWith("GenericCommandMessage{payload={MyPayload}, metadata={");
        assertThat(actual).as("Wrong output: " + actual).contains("'key'->'value'");
        assertThat(actual).as("Wrong output: " + actual).contains("'key2'->'13'");
        assertThat(actual).as("Wrong output: " + actual).endsWith("', commandName='java.lang.String'}");
        assertThat(actual).as("Wrong output: " + actual).hasSize(173);
    }

    @Test
    void asCommandMessageWrapsPayload() {
        String expectedPayload = "some-payload";

        CommandMessage<Object> result = asCommandMessage(expectedPayload);

        assertThat(result.getPayload()).isEqualTo(expectedPayload);
        assertThat(result.getMetaData()).isEmpty();
        assertThat(result.getIdentifier()).isNotNull();
        assertThat(result.getPayloadType()).isEqualTo(String.class);
        assertThat(result.getCommandName()).isEqualTo(String.class.getName());
    }

    @Test
    void asCommandMessageReturnsCommandMessageAsIs() {
        CommandMessage<String> expected = new GenericCommandMessage<>("some-payload", MetaData.with("key", "value"));

        CommandMessage<Object> result = asCommandMessage(expected);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void asCommandMessageRetrievesPayloadAndMetaDataFromMessageImplementations() {
        String expectedPayload = "some-payload";
        MetaData expectedMetaData = MetaData.with("key", "value");

        String testIdentifier = "some-identifier";
        Message<String> testMessage =
                new GenericMessage<>(testIdentifier, expectedPayload, expectedMetaData);

        CommandMessage<Object> result = asCommandMessage(testMessage);

        assertThat(result.getPayload()).isEqualTo(expectedPayload);
        assertThat(result.getMetaData()).isEqualTo(expectedMetaData);
        assertThat(result.getIdentifier()).isNotEqualTo(testIdentifier);
        assertThat(result.getPayloadType()).isEqualTo(String.class);
        assertThat(result.getCommandName()).isEqualTo(String.class.getName());
    }
}
