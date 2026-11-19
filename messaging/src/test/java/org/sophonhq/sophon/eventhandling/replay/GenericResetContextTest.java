/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.sophonhq.sophon.eventhandling.replay;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.messaging.GenericMessage;
import org.sophonhq.sophon.messaging.MetaData;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.eventhandling.replay.GenericResetContext.asResetContext;

/**
 * Test class validating the {@link GenericResetContext}.
 *
 * @author Steven van Beelen
 */
class GenericResetContextTest {

    private static final Object TEST_PAYLOAD = new Object();

    @Test
    void constructor() {
        ResetContext<Object> messageOne = asResetContext(TEST_PAYLOAD);
        ResetContext<Object> messageTwo = asResetContext(new GenericMessage<>(TEST_PAYLOAD));
        ResetContext<Object> messageThree = asResetContext(new GenericResetContext<>(TEST_PAYLOAD));

        Map<String, Object> metaDataMap = Collections.singletonMap("key", "value");
        ResetContext<Object> messageFour = new GenericResetContext<>(TEST_PAYLOAD, metaDataMap);
        MetaData metaData = MetaData.from(metaDataMap);
        ResetContext<Object> messageFive = new GenericResetContext<>(TEST_PAYLOAD, metaData);

        assertThat(messageOne.getMetaData()).isSameAs(MetaData.emptyInstance());
        assertThat(messageOne.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(messageOne.getPayloadType()).isEqualTo(Object.class);

        assertThat(messageTwo.getMetaData()).isSameAs(MetaData.emptyInstance());
        assertThat(messageTwo.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(messageTwo.getPayloadType()).isEqualTo(Object.class);

        assertThat(messageThree.getMetaData()).isSameAs(MetaData.emptyInstance());
        assertThat(messageThree.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(messageThree.getPayloadType()).isEqualTo(Object.class);

        assertThat(messageFour.getMetaData()).isNotSameAs(metaDataMap);
        assertThat(messageFour.getMetaData()).isEqualTo(metaDataMap);
        assertThat(messageFour.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(messageFour.getPayloadType()).isEqualTo(Object.class);

        assertThat(messageFive.getMetaData()).isSameAs(metaData);
        assertThat(messageFive.getPayload().getClass()).isEqualTo(Object.class);
        assertThat(messageFive.getPayloadType()).isEqualTo(Object.class);

        assertThat(messageFour.getIdentifier()).isNotEqualTo(messageOne.getIdentifier());
        assertThat(messageFive.getIdentifier()).isNotEqualTo(messageFour.getIdentifier());
        assertThat(messageOne.getIdentifier()).isNotEqualTo(messageFive.getIdentifier());
    }

    @Test
    void withMetaData() {
        MetaData metaData = MetaData.from(Collections.<String, Object>singletonMap("key", "value"));
        ResetContext<Object> startMessage = new GenericResetContext<>(TEST_PAYLOAD, metaData);

        ResetContext<Object> messageOne = startMessage.withMetaData(MetaData.emptyInstance());
        ResetContext<Object> messageTwo =
                startMessage.withMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertThat(messageOne.getMetaData().size()).isZero();
        assertThat(messageTwo.getMetaData().size()).isOne();
    }

    @Test
    void andMetaData() {
        MetaData metaData = MetaData.from(Collections.<String, Object>singletonMap("key", "value"));
        ResetContext<Object> startMessage = new GenericResetContext<>(TEST_PAYLOAD, metaData);

        ResetContext<Object> messageOne = startMessage.andMetaData(MetaData.emptyInstance());
        ResetContext<Object> messageTwo =
                startMessage.andMetaData(MetaData.from(Collections.singletonMap("key", (Object) "otherValue")));

        assertThat(messageOne.getMetaData().size()).isOne();
        assertThat(messageOne.getMetaData()).containsEntry("key", "value");
        assertThat(messageTwo.getMetaData().size()).isOne();
        assertThat(messageTwo.getMetaData()).containsEntry("key", "otherValue");
    }

    @Test
    void describeType() {
        assertThat(new GenericResetContext<>(TEST_PAYLOAD).describeType()).isEqualTo("GenericResetContext");
    }
}
