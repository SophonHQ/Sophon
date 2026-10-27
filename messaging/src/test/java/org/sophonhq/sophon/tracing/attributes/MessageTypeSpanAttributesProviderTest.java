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

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.commandhandling.GenericCommandMessage;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.responsetypes.ResponseTypes;
import org.sophonhq.sophon.queryhandling.GenericQueryMessage;
import org.sophonhq.sophon.tracing.SpanAttributesProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageTypeSpanAttributesProviderTest {

    private final SpanAttributesProvider provider = new MessageTypeSpanAttributesProvider();

    @Test
    void correctTypeForQueryMessage() {
        Message<?> genericQueryMessage = new GenericQueryMessage<>("MyQuery",
                                                                 "myQueryName",
                                                                 ResponseTypes.instanceOf(String.class));
        Map<String, String> map = provider.provideForMessage(genericQueryMessage);
        assertEquals(1, map.size());
        assertEquals("GenericQueryMessage", map.get("sophon_message_type"));
    }

    @Test
    void correctTypeForCommandMessage() {
        Message<?> genericQueryMessage = new GenericCommandMessage<>("payload");
        Map<String, String> map = provider.provideForMessage(genericQueryMessage);
        assertEquals(1, map.size());
        assertEquals("GenericCommandMessage", map.get("sophon_message_type"));
    }
}
