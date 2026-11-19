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

package org.sophonhq.sophon.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.monitoring.MessageMonitor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;


class MessageCountingMonitorTest {

    private static final String PROCESSOR_NAME = "processorName";

    @Test
    void messagesWithoutTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageCountingMonitor testSubject = MessageCountingMonitor.buildMonitor(PROCESSOR_NAME,
                                                                                 meterRegistry);
        EventMessage<Object> foo = asEventMessage(1);
        EventMessage<Object> bar = asEventMessage("bar");
        EventMessage<Object> baz = asEventMessage("baz");
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar, baz));
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Counter ingestedCounter = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".ingestedCounter").counter());
        Counter processedCounter = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".processedCounter").counter());
        Counter successCounter = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".successCounter").counter());
        Counter failureCounter = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".failureCounter").counter());
        Counter ignoredCounter = requireNonNull(meterRegistry.find(PROCESSOR_NAME + ".ignoredCounter").counter());

        assertThat(ingestedCounter.count()).isEqualTo(3.0);
        assertThat(processedCounter.count()).isEqualTo(2.0);
        assertThat(successCounter.count()).isEqualTo(1.0);
        assertThat(failureCounter.count()).isEqualTo(1.0);
        assertThat(ignoredCounter.count()).isEqualTo(1.0);
    }

    @Test
    void messagesWithPayloadTypeAsCustomTag() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageCountingMonitor testSubject = MessageCountingMonitor.buildMonitor(PROCESSOR_NAME,
                                                                                 meterRegistry,
                                                                                 message -> Tags
                                                                                         .of(TagsUtil.PAYLOAD_TYPE_TAG,
                                                                                             message.getPayloadType()
                                                                                                    .getSimpleName()));
        EventMessage<Object> foo = asEventMessage(1);
        EventMessage<Object> bar = asEventMessage("bar");
        EventMessage<Object> baz = asEventMessage("baz");
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar, baz));
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Collection<Counter> ingestedCounters = meterRegistry.find(PROCESSOR_NAME + ".ingestedCounter").counters();
        Collection<Counter> processedCounters = meterRegistry.find(PROCESSOR_NAME + ".processedCounter").counters();
        Collection<Counter> successCounters = meterRegistry.find(PROCESSOR_NAME + ".successCounter").counters();
        Collection<Counter> failureCounters = meterRegistry.find(PROCESSOR_NAME + ".failureCounter").counters();
        Collection<Counter> ignoredCounters = meterRegistry.find(PROCESSOR_NAME + ".ignoredCounter").counters();

        assertThat(ingestedCounters).hasSize(2);
        assertThat(processedCounters).hasSize(2);
        assertThat(successCounters).hasSize(2);
        assertThat(failureCounters).hasSize(2);
        assertThat(ignoredCounters).hasSize(2);


        assertThat(ingestedCounters.stream()
                                   .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "Integer"))
                                   .allMatch(counter -> counter.count() == 1)).isTrue();
        assertThat(ingestedCounters.stream()
                                   .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "String"))
                                   .allMatch(counter -> counter.count() == 2)).isTrue();


        assertThat(processedCounters.stream()
                                    .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "Integer"))
                                    .allMatch(counter -> counter.count() == 1)).isTrue();
        assertThat(processedCounters.stream()
                                    .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "String"))
                                    .allMatch(counter -> counter.count() == 1)).isTrue();


        assertThat(successCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "Integer"))
                                  .allMatch(counter -> counter.count() == 1)).isTrue();
        assertThat(successCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "String"))
                                  .allMatch(counter -> counter.count() == 0)).isTrue();


        assertThat(failureCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "Integer"))
                                  .allMatch(counter -> counter.count() == 0)).isTrue();
        assertThat(failureCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "String"))
                                  .allMatch(counter -> counter.count() == 1)).isTrue();


        assertThat(ignoredCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "Integer"))
                                  .allMatch(counter -> counter.count() == 0)).isTrue();
        assertThat(ignoredCounters.stream()
                                  .filter(counter -> Objects.equals(counter.getId().getTag("payloadType"), "String"))
                                  .allMatch(counter -> counter.count() == 1)).isTrue();
    }

    @Test
    void messagesWithMetadataAsCustomTag() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MessageCountingMonitor testSubject = MessageCountingMonitor.buildMonitor(PROCESSOR_NAME,
                                                                                 meterRegistry,
                                                                                 message -> Tags
                                                                                         .of("myMetaData",
                                                                                             message.getMetaData()
                                                                                                    .get("myMetadataKey")
                                                                                                    .toString()));
        EventMessage<Object> foo = asEventMessage(1).withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                           "myMetadataValue1"));
        EventMessage<Object> bar = asEventMessage("bar").withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                               "myMetadataValue2"));
        ;
        EventMessage<Object> baz = asEventMessage("baz").withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                               "myMetadataValue2"));
        ;
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar, baz));
        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);
        callbacks.get(baz).reportIgnored();

        Collection<Counter> ingestedCounters = meterRegistry.find(PROCESSOR_NAME + ".ingestedCounter").counters();
        Collection<Counter> processedCounters = meterRegistry.find(PROCESSOR_NAME + ".processedCounter").counters();
        Collection<Counter> successCounters = meterRegistry.find(PROCESSOR_NAME + ".successCounter").counters();
        Collection<Counter> failureCounters = meterRegistry.find(PROCESSOR_NAME + ".failureCounter").counters();
        Collection<Counter> ignoredCounters = meterRegistry.find(PROCESSOR_NAME + ".ignoredCounter").counters();

        assertThat(ingestedCounters).hasSize(2);
        assertThat(processedCounters).hasSize(2);
        assertThat(successCounters).hasSize(2);
        assertThat(failureCounters).hasSize(2);
        assertThat(ignoredCounters).hasSize(2);


        assertThat(ingestedCounters.stream()
                                   .filter(counter -> Objects
                                           .equals(counter.getId().getTag("myMetaData"), "myMetadataValue1"))
                                   .allMatch(counter -> counter.count() == 1)).isTrue();
        assertThat(ingestedCounters.stream()
                                   .filter(counter -> Objects
                                           .equals(counter.getId().getTag("myMetaData"), "myMetadataValue2"))
                                   .allMatch(counter -> counter.count() == 2)).isTrue();


        assertThat(processedCounters.stream()
                                    .filter(counter -> Objects
                                            .equals(counter.getId().getTag("myMetaData"), "myMetadataValue1"))
                                    .allMatch(counter -> counter.count() == 1)).isTrue();
        assertThat(processedCounters.stream()
                                    .filter(counter -> Objects
                                            .equals(counter.getId().getTag("payloadType"), "myMetadataValue2"))
                                    .allMatch(counter -> counter.count() == 1)).isTrue();


        assertThat(successCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue1"))
                                  .allMatch(counter -> counter.count() == 1)).isTrue();
        assertThat(successCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue2"))
                                  .allMatch(counter -> counter.count() == 0)).isTrue();


        assertThat(failureCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue1"))
                                  .allMatch(counter -> counter.count() == 0)).isTrue();
        assertThat(failureCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue2"))
                                  .allMatch(counter -> counter.count() == 1)).isTrue();


        assertThat(ignoredCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue1"))
                                  .allMatch(counter -> counter.count() == 0)).isTrue();
        assertThat(ignoredCounters.stream()
                                  .filter(counter -> Objects
                                          .equals(counter.getId().getTag("payloadType"), "myMetadataValue2"))
                                  .allMatch(counter -> counter.count() == 1)).isTrue();
    }
}
