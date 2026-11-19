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

package org.sophonhq.sophon.spring.messaging;


import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.DomainEventMessage;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created on 04/02/17.
 *
 * @author Reda.Housni-Alaoui
 */
class DefaultEventMessageConverterTest {

	private EventMessageConverter eventMessageConverter = new DefaultEventMessageConverter();

	@Test
	void given_generic_event_message_when_converting_twice_then_resulting_event_should_be_the_same(){
		Instant instant = Instant.EPOCH;
		String id = UUID.randomUUID().toString();

		Map<String, Object> metaData = new HashMap<>();
		metaData.put("number", 100);
		metaData.put("string", "world");

		EventPayload payload = new EventPayload("hello");

		EventMessage<EventPayload> sophonMessage = new GenericEventMessage<>(id, payload, metaData, instant);

		EventMessage<EventPayload> convertedSophonMessage = eventMessageConverter.convertFromInboundMessage(eventMessageConverter.convertToOutboundMessage(sophonMessage));

		assertThat(convertedSophonMessage.getTimestamp()).isEqualTo(instant);
		assertThat(convertedSophonMessage.getMetaData()).containsEntry("number", 100);
		assertThat(convertedSophonMessage.getMetaData()).containsEntry("string", "world");
		assertThat(convertedSophonMessage.getPayload().name).isEqualTo("hello");
		assertThat(convertedSophonMessage.getIdentifier()).isEqualTo(id);
	}

	@Test
	void given_domain_event_message_when_converting_twice_then_resulting_event_should_be_the_same(){
		Instant instant = Instant.EPOCH;
		String id = UUID.randomUUID().toString();
		String aggId = UUID.randomUUID().toString();

		Map<String, Object> metaData = new HashMap<>();
		metaData.put("number", 100);
		metaData.put("string", "world");

		EventPayload payload = new EventPayload("hello");

		EventMessage<EventPayload> sophonMessage = new GenericDomainEventMessage<>("foo", aggId, 1, payload, metaData, id, instant);
		EventMessage<EventPayload> convertedSophonMessage = eventMessageConverter.convertFromInboundMessage(eventMessageConverter.convertToOutboundMessage(sophonMessage));

		assertThat(convertedSophonMessage).isInstanceOf(DomainEventMessage.class);

		DomainEventMessage<EventPayload> convertDomainMessage = (DomainEventMessage<EventPayload>) convertedSophonMessage;
		assertThat(convertDomainMessage.getTimestamp()).isEqualTo(instant);
		assertThat(convertDomainMessage.getMetaData()).containsEntry("number", 100);
		assertThat(convertDomainMessage.getMetaData()).containsEntry("string", "world");
		assertThat(convertDomainMessage.getPayload().name).isEqualTo("hello");
		assertThat(convertDomainMessage.getIdentifier()).isEqualTo(id);
		assertThat(convertDomainMessage.getType()).isEqualTo("foo");
		assertThat(convertDomainMessage.getAggregateIdentifier()).isEqualTo(aggId);
		assertThat(convertDomainMessage.getSequenceNumber()).isOne();
	}

	private class EventPayload{
		private final String name;

		EventPayload(String name){
			this.name = name;
		}
	}

}
