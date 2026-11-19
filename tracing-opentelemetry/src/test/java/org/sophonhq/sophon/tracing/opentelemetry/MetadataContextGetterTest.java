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

package org.sophonhq.sophon.tracing.opentelemetry;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataContextGetterTest {

    private final EventMessage<Object> message = GenericEventMessage
            .asEventMessage("MyEvent")
            .andMetaData(Collections.singletonMap("myKeyOne", "myValueTwo"))
            .andMetaData(Collections.singletonMap("MyKeyTwo", 2));

    @Test
    void shouldReceiveMetadataKeysFromMessage() {
        List<String> keys = StreamSupport.stream(MetadataContextGetter.INSTANCE.keys(message).spliterator(), false)
                                         .collect(Collectors.toList());
        assertThat(keys)
                .contains("myKeyOne")
                .contains("MyKeyTwo")
                .doesNotContain("MyKeyThree");
    }

    @Test
    void shouldGetItemFromMessage() {
        assertThat(MetadataContextGetter.INSTANCE.get(message, "myKeyOne")).isEqualTo("myValueTwo");
    }

    @Test
    void shouldGetNullFromNullMessage() {
        assertThat(MetadataContextGetter.INSTANCE.get(null, "myKeyOne")).isNull();
    }
}
