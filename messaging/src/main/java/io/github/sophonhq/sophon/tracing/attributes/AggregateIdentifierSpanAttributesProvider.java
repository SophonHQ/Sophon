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

package io.github.sophonhq.sophon.tracing.attributes;

import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.messaging.Message;
import io.github.sophonhq.sophon.tracing.SpanAttributesProvider;
import org.jspecify.annotations.NonNull;

import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

/**
 * Adds the aggregate identifier to the Span if the current message being handled is a {@link DomainEventMessage}.
 *
 * @author Mitchell Herrijgers
 * @since axon-4.6.0
 */
public class AggregateIdentifierSpanAttributesProvider implements SpanAttributesProvider {

    @Override
    public @NonNull Map<String, String> provideForMessage(@NonNull Message<?> message) {
        if (message instanceof DomainEventMessage) {
            DomainEventMessage<?> domainEventMessage = (DomainEventMessage<?>) message;
            return singletonMap("sophon_aggregate_identifier", domainEventMessage.getAggregateIdentifier());
        }
        return emptyMap();
    }
}
