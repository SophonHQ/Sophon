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

package org.sophonhq.sophon.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.*;
import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.common.ReflectionUtils;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.monitoring.MessageMonitor;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.commandhandling.GenericCommandMessage.asCommandMessage;

@ExtendWith(MockitoExtension.class)
class PayloadTypeMessageMonitorWrapperTest<T extends MessageMonitor<Message<?>> & MetricSet> {

    private static final CommandMessage<Object> STRING_MESSAGE = asCommandMessage("stringCommand");
    private static final CommandMessage<Object> INTEGER_MESSAGE = asCommandMessage(1);

    private PayloadTypeMessageMonitorWrapper<CapacityMonitor> testSubject;

    private Class<CapacityMonitor> expectedMonitorClass;

    @BeforeEach
    void setUp() {
        expectedMonitorClass = CapacityMonitor.class;
        testSubject = new PayloadTypeMessageMonitorWrapper<>(CapacityMonitor::new);
    }

    @Test
    void instantiateMessageMonitorOfTypeMonitorOnMessageIngested() throws Exception {
        Field payloadTypeMonitorsField = testSubject.getClass().getDeclaredField("payloadTypeMonitors");
        payloadTypeMonitorsField.setAccessible(true);

        String expectedMonitorName = STRING_MESSAGE.getPayloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE);

        Map<String, T> payloadTypeMonitors = ReflectionUtils.getFieldValue(payloadTypeMonitorsField, testSubject);
        assertThat(payloadTypeMonitors).hasSize(1);
        MessageMonitor<Message<?>> messageMessageMonitor = payloadTypeMonitors.get(expectedMonitorName);
        assertThat(messageMessageMonitor).isNotNull();
        assertThat(messageMessageMonitor).isInstanceOf(expectedMonitorClass);

        Map<String, Metric> resultMetrics = testSubject.getMetrics();
        assertThat(resultMetrics).hasSize(1);
        assertThat(resultMetrics.get(expectedMonitorName)).isNotNull();
    }

    @Test
    void instantiatesOneMessageMonitorPerIngestedPayloadType() throws Exception {
        Field payloadTypeMonitorsField = testSubject.getClass().getDeclaredField("payloadTypeMonitors");
        payloadTypeMonitorsField.setAccessible(true);

        String expectedStringMonitorName = STRING_MESSAGE.getPayloadType().getName();
        String expectedIntegerMonitorName = INTEGER_MESSAGE.getPayloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE); // First unique payload type
        testSubject.onMessageIngested(STRING_MESSAGE);
        testSubject.onMessageIngested(INTEGER_MESSAGE); // Second unique payload type

        Map<String, T> payloadTypeMonitors = ReflectionUtils.getFieldValue(payloadTypeMonitorsField, testSubject);
        assertThat(payloadTypeMonitors).hasSize(2);

        MessageMonitor<Message<?>> messageMessageMonitor = payloadTypeMonitors.get(expectedStringMonitorName);
        assertThat(messageMessageMonitor).isNotNull();
        assertThat(messageMessageMonitor).isInstanceOf(expectedMonitorClass);

        messageMessageMonitor = payloadTypeMonitors.get(expectedIntegerMonitorName);
        assertThat(messageMessageMonitor).isNotNull();
        assertThat(messageMessageMonitor).isInstanceOf(expectedMonitorClass);

        Map<String, Metric> resultMetrics = testSubject.getMetrics();
        assertThat(resultMetrics).hasSize(2);
        assertThat(resultMetrics.get(expectedStringMonitorName)).isNotNull();
        assertThat(resultMetrics.get(expectedStringMonitorName)).isNotNull();
    }

    @Test
    void monitorNameFollowsGivenMonitorNameBuilderSpecifics() {
        String testPrefix = "additional-monitor-name.";
        PayloadTypeMessageMonitorWrapper<CapacityMonitor> testSubject = new PayloadTypeMessageMonitorWrapper<>(
                CapacityMonitor::new, payloadType -> testPrefix + payloadType.getName());

        String expectedMonitorName = testPrefix + STRING_MESSAGE.getPayloadType().getName();

        testSubject.onMessageIngested(STRING_MESSAGE);

        Map<String, Metric> resultMetrics = testSubject.getMetrics();
        assertThat(resultMetrics).hasSize(1);
        assertThat(resultMetrics.get(expectedMonitorName)).isNotNull();
    }
}
