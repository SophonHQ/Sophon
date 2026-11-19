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

package org.sophonhq.sophon.queryhandling;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandResultMessage;
import org.sophonhq.sophon.commandhandling.GenericCommandResultMessage;
import org.sophonhq.sophon.messaging.GenericMessage;
import org.sophonhq.sophon.messaging.GenericResultMessage;
import org.sophonhq.sophon.messaging.MetaData;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GenericSubscriptionQueryUpdateMessage}.
 *
 * @author Milan Savic
 */
class GenericSubscriptionQueryUpdateMessageTest {

    @Test
    void messageCreation() {
        String payload = "payload";

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(payload);

        assertThat(result.getPayload()).isEqualTo(payload);
    }

    @Test
    void messageCreationWithNullPayload() {
        String payload = null;

        GenericSubscriptionQueryUpdateMessage<String> result =
                new GenericSubscriptionQueryUpdateMessage<>(String.class, payload);

        assertThat(result.getPayload()).isNull();
    }

    @Test
    void andMetaData() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v2");
        SubscriptionQueryUpdateMessage<Object> original = new GenericSubscriptionQueryUpdateMessage<>(
                new GenericMessage<>("payload", metaData));


        Map<String, String> newMetaData = Collections.singletonMap("k2", "v3");
        SubscriptionQueryUpdateMessage<Object> result = original.andMetaData(newMetaData);

        assertThat(result.getPayload()).isEqualTo(original.getPayload());
        MetaData expectedMetaData = MetaData.from(metaData)
                                            .mergedWith(newMetaData);
        assertThat(result.getMetaData()).isEqualTo(expectedMetaData);
    }

    @Test
    void withMetaData() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v2");
        SubscriptionQueryUpdateMessage<Object> original = new GenericSubscriptionQueryUpdateMessage<>(
                new GenericMessage<>("payload", metaData));


        Map<String, String> newMetaData = Collections.singletonMap("k2", "v3");
        SubscriptionQueryUpdateMessage<Object> result = original.withMetaData(newMetaData);

        assertThat(result.getPayload()).isEqualTo(original.getPayload());
        assertThat(result.getMetaData()).isEqualTo(newMetaData);
    }

    @Test
    void messageCreationBasedOnExistingMessage() {
        GenericSubscriptionQueryUpdateMessage<String> original = new GenericSubscriptionQueryUpdateMessage<>("payload");

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(original);

        assertThat(result).isEqualTo(original);
    }

    @Test
    void messageCreationBasedOnResultMessage() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v1");
        CommandResultMessage<String> resultMessage = GenericCommandResultMessage.asCommandResultMessage(
                new GenericResultMessage<>("result", metaData));

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(
                resultMessage);

        assertThat(result.getPayload()).isEqualTo(resultMessage.getPayload());
        assertThat(result.getMetaData()).isEqualTo(resultMessage.getMetaData());
    }

    @Test
    void messageCreationBasedOnExceptionalResultMessage() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v1");
        RuntimeException exception = new RuntimeException();
        CommandResultMessage<String> resultMessage = GenericCommandResultMessage.asCommandResultMessage(
                new GenericResultMessage<>(exception, metaData));

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(
                resultMessage);

        assertThat(result.getMetaData()).isEqualTo(resultMessage.getMetaData());
        assertThat(result.isExceptional()).isTrue();
        assertThat(result.exceptionResult()).isEqualTo(exception);
    }

    @Test
    void messageCreationBasedOnAnyMessage() {
        Map<String, String> metaData = Collections.singletonMap("k1", "v1");
        GenericMessage<String> message = new GenericMessage<>("payload", metaData);

        SubscriptionQueryUpdateMessage<Object> result = GenericSubscriptionQueryUpdateMessage.asUpdateMessage(message);

        assertThat(result.getPayload()).isEqualTo(message.getPayload());
        assertThat(result.getMetaData()).isEqualTo(message.getMetaData());
    }
}
