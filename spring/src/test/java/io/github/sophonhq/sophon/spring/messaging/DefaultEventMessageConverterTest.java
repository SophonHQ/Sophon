/*
 * Copyright (c) 2010-2018. Axon Framework
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

package io.github.sophonhq.sophon.spring.messaging;


import io.github.sophonhq.sophon.eventhandling.DomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.EventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import io.github.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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

		assertEquals(instant, convertedSophonMessage.getTimestamp());
		assertEquals(100, convertedSophonMessage.getMetaData().get("number"));
		assertEquals("world", convertedSophonMessage.getMetaData().get("string"));
		assertEquals("hello", convertedSophonMessage.getPayload().name);
		assertEquals(id, convertedSophonMessage.getIdentifier());
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

		assertTrue(convertedSophonMessage instanceof DomainEventMessage);

		DomainEventMessage<EventPayload> convertDomainMessage = (DomainEventMessage<EventPayload>) convertedSophonMessage;
		assertEquals(instant, convertDomainMessage.getTimestamp());
		assertEquals(100, convertDomainMessage.getMetaData().get("number"));
		assertEquals("world", convertDomainMessage.getMetaData().get("string"));
		assertEquals("hello", convertDomainMessage.getPayload().name);
		assertEquals(id, convertDomainMessage.getIdentifier());
		assertEquals("foo", convertDomainMessage.getType());
		assertEquals(aggId, convertDomainMessage.getAggregateIdentifier());
		assertEquals(1, convertDomainMessage.getSequenceNumber());
	}

	private class EventPayload{
		private final String name;

		EventPayload(String name){
			this.name = name;
		}
	}

}
