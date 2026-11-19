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

package org.sophonhq.sophon.messaging.responsetypes;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.messaging.IllegalPayloadAccessException;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.queryhandling.GenericQueryResponseMessage;
import org.sophonhq.sophon.queryhandling.QueryResponseMessage;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConvertingResponseMessageTest {

    @Test
    void payloadIsConvertedToExpectedType() {
        QueryResponseMessage<?> msg = new GenericQueryResponseMessage<>(new String[]{"Some string result"})
                .withMetaData(MetaData.with("test", "value"));
        QueryResponseMessage<List<String>> wrapped = new ConvertingResponseMessage<>(
                ResponseTypes.multipleInstancesOf(String.class),
                msg);

        assertThat(wrapped.getPayloadType()).isEqualTo(List.class);
        assertThat(wrapped.getPayload()).containsExactlyElementsOf(singletonList("Some string result"));
        assertThat(wrapped.getMetaData()).containsEntry("test", "value");
    }

    @Test
    void illegalAccessPayloadWhenResultIsExceptional() {
        QueryResponseMessage<?> msg = GenericQueryResponseMessage.asResponseMessage(List.class, new RuntimeException());
        QueryResponseMessage<List<String>> wrapped = new ConvertingResponseMessage<>(
                ResponseTypes.multipleInstancesOf(String.class),
                msg);

        assertThat(wrapped.getPayloadType()).isEqualTo(List.class);
        assertThatThrownBy(wrapped::getPayload).isInstanceOf(IllegalPayloadAccessException.class);
    }
}