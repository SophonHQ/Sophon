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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MockClock;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;

class CapacityMonitorTest {

    @Test
    void capacityWithoutTags() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CapacityMonitor testSubject = CapacityMonitor.buildMonitor("1",
                                                                   meterRegistry,
                                                                   1,
                                                                   TimeUnit.SECONDS,
                                                                   testClock);

        EventMessage<Object> foo = asEventMessage(1);
        EventMessage<Object> bar = asEventMessage("bar");
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar));

        testClock.addSeconds(1);

        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);

        Gauge capacityGauge = meterRegistry.get("1.capacity").gauge();
        assertThat(capacityGauge.value()).isEqualTo(2.0);
    }

    @Test
    void capacityWithPayloadTypeAsCustomTag() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CapacityMonitor testSubject = CapacityMonitor.buildMonitor("1",
                                                                   meterRegistry,
                                                                   1,
                                                                   TimeUnit.SECONDS,
                                                                   testClock,
                                                                   message -> Tags
                                                                           .of(TagsUtil.PAYLOAD_TYPE_TAG,
                                                                               message.getPayloadType()
                                                                                      .getSimpleName()));

        EventMessage<Object> foo = asEventMessage(1);
        EventMessage<Object> bar = asEventMessage("bar");
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar));

        testClock.addSeconds(1);

        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);

        Collection<Gauge> capacityGauges = meterRegistry.find("1.capacity").gauges();
        assertThat(capacityGauges).hasSize(2);
        assertThat(capacityGauges.stream()
                .anyMatch(gauge -> Objects.equals(gauge.getId().getTag("payloadType"), "Integer"))).isTrue();
        assertThat(capacityGauges.stream()
                .anyMatch(gauge -> Objects.equals(gauge.getId().getTag("payloadType"), "String"))).isTrue();
    }

    @Test
    void capacityWithMetadataAsCustomTag() {
        MockClock testClock = new MockClock();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CapacityMonitor testSubject = CapacityMonitor.buildMonitor("1",
                                                                   meterRegistry,
                                                                   1,
                                                                   TimeUnit.SECONDS,
                                                                   testClock,
                                                                   message -> Tags.of("myPayloadType",
                                                                                      message.getPayloadType()
                                                                                             .getSimpleName(),
                                                                                      "myMetaData",
                                                                                      message.getMetaData()
                                                                                             .get("myMetadataKey")
                                                                                             .toString()));

        EventMessage<Object> foo = asEventMessage(1).withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                           "myMetadataValue1"));
        EventMessage<Object> bar = asEventMessage("bar").withMetaData(Collections.singletonMap("myMetadataKey",
                                                                                               "myMetadataValue2"));
        Map<? super Message<?>, MessageMonitor.MonitorCallback> callbacks = testSubject
                .onMessagesIngested(Arrays.asList(foo, bar));

        testClock.addSeconds(1);

        callbacks.get(foo).reportSuccess();
        callbacks.get(bar).reportFailure(null);

        Collection<Gauge> capacityGauges = meterRegistry.find("1.capacity").gauges();
        assertThat(capacityGauges).hasSize(2);
        assertThat(capacityGauges.stream()
                .anyMatch(gauge -> Objects.equals(gauge.getId().getTag("myPayloadType"), "Integer"))).isTrue();
        assertThat(capacityGauges.stream()
                .anyMatch(gauge -> Objects.equals(gauge.getId().getTag("myPayloadType"), "String"))).isTrue();
        assertThat(capacityGauges.stream()
                .anyMatch(gauge -> Objects
                        .equals(gauge.getId().getTag("myMetaData"), "myMetadataValue1"))).isTrue();
        assertThat(capacityGauges.stream()
                .anyMatch(gauge -> Objects
                        .equals(gauge.getId().getTag("myMetaData"), "myMetadataValue2"))).isTrue();
    }
}
