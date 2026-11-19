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

package org.sophonhq.sophon.tracing.attributes;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.tracing.SpanAttributesProvider;

import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

class MetadataSpanAttributesProviderTest {

    private final SpanAttributesProvider provider = new MetadataSpanAttributesProvider();

    @Test
    void addsAllMetadata() {
        Message<?> message = new GenericEventMessage<>("MyEvent")
                .andMetaData(singletonMap("myKeyOne", "valueOne"))
                .andMetaData(singletonMap("myNumberKey", 2))
                .andMetaData(singletonMap("someOtherKey_2", "someValue"));
        Map<String, String> map = provider.provideForMessage(message);
        assertThat(map)
                .hasSize(3)
                .containsEntry("sophon_metadata_myKeyOne", "valueOne")
                .containsEntry("sophon_metadata_myNumberKey", "2")
                .containsEntry("sophon_metadata_someOtherKey_2", "someValue");
    }
}
