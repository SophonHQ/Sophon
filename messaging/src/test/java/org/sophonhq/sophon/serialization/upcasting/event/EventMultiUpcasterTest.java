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

package org.sophonhq.sophon.serialization.upcasting.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.EventData;
import org.sophonhq.sophon.eventhandling.GenericDomainEventEntry;
import org.sophonhq.sophon.eventhandling.GenericDomainEventMessage;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.TrackedDomainEventData;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.serialization.SerializedObject;
import org.sophonhq.sophon.serialization.SerializedType;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.SimpleSerializedType;
import org.sophonhq.sophon.serialization.json.JacksonSerializer;
import org.sophonhq.sophon.serialization.upcasting.Upcaster;
import org.sophonhq.sophon.utils.SecondStubEvent;
import org.sophonhq.sophon.utils.StubDomainEvent;
import org.sophonhq.sophon.utils.TestDomainEventEntry;
import org.sophonhq.sophon.utils.ThirdStubEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link EventMultiUpcaster}.
 *
 * @author Steven van Beelen
 */
class EventMultiUpcasterTest {

    private String expectedNewString;
    private Integer expectedNewInteger;
    private List<Boolean> expectedNewBooleans;

    private Serializer serializer;
    private Upcaster<IntermediateEventRepresentation> upcaster;

    @BeforeEach
    void setUp() {
        expectedNewString = "newNameValue";
        expectedNewInteger = 42;
        expectedNewBooleans = new ArrayList<>();
        expectedNewBooleans.add(true);
        expectedNewBooleans.add(false);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));
        serializer = JacksonSerializer.builder().objectMapper(objectMapper).build();
        upcaster = new StubEventMultiUpcaster(expectedNewString, expectedNewInteger, expectedNewBooleans);
    }

    @Test
    void upcasterIgnoresWrongEventType() {
        GenericDomainEventMessage<String> testEventMessage =
                new GenericDomainEventMessage<>("test", "aggregateId", 0, "someString");
        EventData<?> testEventData = new TestDomainEventEntry(testEventMessage, serializer);
        IntermediateEventRepresentation testRepresentation =
                spy(new InitialEventRepresentation(testEventData, serializer));

        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(testRepresentation))
                                                               .collect(toList());

        assertThat(result).hasSize(1);
        IntermediateEventRepresentation resultRepresentation = result.get(0);
        assertThat(resultRepresentation).isSameAs(testRepresentation);
        verify(testRepresentation, never()).getData();
    }

    @Test
    void upcasterIgnoresWrongEventRevision() {
        String expectedRevisionNumber = "1";

        GenericDomainEventMessage<StubDomainEvent> testEventMessage =
                new GenericDomainEventMessage<>("test", "aggregateId", 0, new StubDomainEvent("oldName"));
        EventData<?> testEventData = new TestDomainEventEntry(testEventMessage, serializer);
        IntermediateEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(testRepresentation))
                                                               .collect(toList());

        testRepresentation = spy(result.get(0));
        // Initial upcast was successful
        assertThat(testRepresentation.getType().getRevision()).isEqualTo(expectedRevisionNumber);

        result = upcaster.upcast(Stream.of(testRepresentation))
                         .collect(toList());

        assertThat(result).isNotEmpty();
        IntermediateEventRepresentation resultRepresentation = result.get(0);
        assertThat(resultRepresentation).isSameAs(testRepresentation);
        verify(testRepresentation, never()).getData();
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void upcastingDomainEventData() {
        String testAggregateType = "test";
        String testAggregateId = "aggregateId";
        GlobalSequenceTrackingToken testTrackingToken = new GlobalSequenceTrackingToken(10);
        long testSequenceNumber = 100;
        SerializedObject<String> testPayload = serializer.serialize(new StubDomainEvent("oldName"), String.class);
        EventData<?> testEventData = new TrackedDomainEventData<>(
                testTrackingToken, new GenericDomainEventEntry<>(
                testAggregateType, testAggregateId, testSequenceNumber, "eventId", Instant.now(),
                testPayload.getType().getName(), testPayload.getType().getRevision(), testPayload,
                serializer.serialize(MetaData.emptyInstance(), String.class)
        ));
        IntermediateEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(testRepresentation))
                                                               .collect(toList());

        assertThat(result).isNotEmpty();

        IntermediateEventRepresentation firstEventResult = result.get(0);
        assertThat(firstEventResult.getAggregateType()).hasValue(testAggregateType);
        assertThat(firstEventResult.getAggregateIdentifier()).hasValue(testAggregateId);
        assertThat(firstEventResult.getTrackingToken().get()).isEqualTo(testTrackingToken);
        assertThat(firstEventResult.getSequenceNumber()).hasValue(Long.valueOf(testSequenceNumber));

        IntermediateEventRepresentation secondEventResult = result.get(1);
        assertThat(secondEventResult.getAggregateType()).hasValue(testAggregateType);
        assertThat(secondEventResult.getAggregateIdentifier()).hasValue(testAggregateId);
        assertThat(secondEventResult.getTrackingToken().get()).isEqualTo(testTrackingToken);
        assertThat(secondEventResult.getSequenceNumber()).hasValue(Long.valueOf(testSequenceNumber));

        IntermediateEventRepresentation thirdEventResult = result.get(2);
        assertThat(thirdEventResult.getAggregateType()).hasValue(testAggregateType);
        assertThat(thirdEventResult.getAggregateIdentifier()).hasValue(testAggregateId);
        assertThat(thirdEventResult.getTrackingToken().get()).isEqualTo(testTrackingToken);
        assertThat(thirdEventResult.getSequenceNumber()).hasValue(Long.valueOf(testSequenceNumber));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void upcastsKnownType() {
        String expectedRevisionNumber = "1";
        String expectedSecondAndThirdRevisionNumber = null;

        MetaData testMetaData = MetaData.with("key", "value");
        GenericDomainEventMessage<StubDomainEvent> testEventMessage =
                new GenericDomainEventMessage<>("test", "aggregateId", 0, new StubDomainEvent("oldName"), testMetaData);
        EventData<?> testEventData = new TestDomainEventEntry(testEventMessage, serializer);
        InitialEventRepresentation testRepresentation = new InitialEventRepresentation(testEventData, serializer);

        List<IntermediateEventRepresentation> result = upcaster.upcast(Stream.of(testRepresentation))
                                                               .collect(toList());

        assertThat(result).isNotEmpty();

        IntermediateEventRepresentation firstResultRepresentation = result.get(0);
        assertThat(firstResultRepresentation.getType().getRevision()).isEqualTo(expectedRevisionNumber);
        assertThat(firstResultRepresentation.getMessageIdentifier()).isEqualTo(testEventData.getEventIdentifier());
        assertThat(firstResultRepresentation.getTimestamp()).isEqualTo(testEventData.getTimestamp());
        assertThat(firstResultRepresentation.getMetaData().getObject()).isEqualTo(testMetaData);
        StubDomainEvent firstUpcastedEvent = serializer.deserialize(firstResultRepresentation.getData());
        assertThat(firstUpcastedEvent.getName()).isEqualTo(expectedNewString);

        IntermediateEventRepresentation secondResultRepresentation = result.get(1);
        assertThat(secondResultRepresentation.getType().getRevision()).isEqualTo(expectedSecondAndThirdRevisionNumber);
        assertThat(secondResultRepresentation.getMessageIdentifier()).isEqualTo(testEventData.getEventIdentifier());
        assertThat(secondResultRepresentation.getTimestamp()).isEqualTo(testEventData.getTimestamp());
        assertThat(secondResultRepresentation.getMetaData().getObject()).isEqualTo(testMetaData);
        SecondStubEvent secondUpcastedEvent = serializer.deserialize(secondResultRepresentation.getData());
        assertThat(secondUpcastedEvent.getName()).isEqualTo(expectedNewString);
        assertThat(secondUpcastedEvent.getNumber()).isEqualTo(expectedNewInteger);

        IntermediateEventRepresentation thirdResultRepresentation = result.get(2);
        assertThat(thirdResultRepresentation.getType().getRevision()).isEqualTo(expectedSecondAndThirdRevisionNumber);
        assertThat(thirdResultRepresentation.getMessageIdentifier()).isEqualTo(testEventData.getEventIdentifier());
        assertThat(thirdResultRepresentation.getTimestamp()).isEqualTo(testEventData.getTimestamp());
        assertThat(thirdResultRepresentation.getMetaData().getObject()).isEqualTo(testMetaData);
        ThirdStubEvent thirdUpcastedEvent = serializer.deserialize(thirdResultRepresentation.getData());
        assertThat(thirdUpcastedEvent.getName()).isEqualTo(expectedNewString);
        assertThat(thirdUpcastedEvent.getNumber()).isEqualTo(expectedNewInteger);
        assertThat(thirdUpcastedEvent.getTruths()).containsExactlyElementsOf(expectedNewBooleans);
    }

    private static class StubEventMultiUpcaster extends EventMultiUpcaster {

        private final SerializedType targetType = new SimpleSerializedType(StubDomainEvent.class.getName(), null);

        private final String newStringValue;
        private final Integer newIntegerValue;
        private final List<Boolean> newBooleanValues;

        private StubEventMultiUpcaster(String newStringValue, Integer newIntegerValue, List<Boolean> newBooleanValues) {
            this.newStringValue = newStringValue;
            this.newIntegerValue = newIntegerValue;
            this.newBooleanValues = newBooleanValues;
        }

        @Override
        protected boolean canUpcast(IntermediateEventRepresentation intermediateRepresentation) {
            return intermediateRepresentation.getType().equals(targetType);
        }

        @Override
        protected Stream<IntermediateEventRepresentation> doUpcast(IntermediateEventRepresentation ir) {
            return Stream.of(
                    ir.upcastPayload(new SimpleSerializedType(targetType.getName(), "1"),
                                     JsonNode.class,
                                     this::doUpcast),
                    ir.upcastPayload(new SimpleSerializedType(SecondStubEvent.class.getName(), null),
                                     JsonNode.class,
                                     this::doUpcastTwo),
                    ir.upcastPayload(new SimpleSerializedType(ThirdStubEvent.class.getName(), null),
                                     JsonNode.class,
                                     this::doUpcastThree)
            );
        }

        private JsonNode doUpcast(JsonNode eventJsonNode) {
            if (!eventJsonNode.isObject()) {
                return eventJsonNode;
            }
            ObjectNode eventObjectNode = (ObjectNode) eventJsonNode;

            eventObjectNode.set("name", new TextNode(newStringValue));

            return eventObjectNode;
        }

        private JsonNode doUpcastTwo(JsonNode eventJsonNode) {
            if (!eventJsonNode.isObject()) {
                return eventJsonNode;
            }
            ObjectNode eventObjectNode = (ObjectNode) eventJsonNode;

            eventObjectNode.set("name", new TextNode(newStringValue));
            eventObjectNode.set("number", new IntNode(newIntegerValue));

            return eventJsonNode;
        }

        private JsonNode doUpcastThree(JsonNode eventJsonNode) {
            if (!eventJsonNode.isObject()) {
                return eventJsonNode;
            }
            ObjectNode eventObjectNode = (ObjectNode) eventJsonNode;

            eventObjectNode.set("name", new TextNode(newStringValue));
            eventObjectNode.set("number", new IntNode(newIntegerValue));
            ArrayNode truthsArrayNode = eventObjectNode.withArray("truths");
            newBooleanValues.forEach(truthsArrayNode::add);

            return eventJsonNode;
        }
    }
}
