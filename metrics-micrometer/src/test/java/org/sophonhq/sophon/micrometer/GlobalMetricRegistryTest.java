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

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.commandhandling.GenericCommandMessage;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.monitoring.MessageMonitor;
import org.sophonhq.sophon.monitoring.NoOpMessageMonitor;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;

class GlobalMetricRegistryTest {

    private GlobalMetricRegistry subject;

    private MetricRegistry dropWizardRegistry;

    @BeforeEach
    void setUp() {
        DropwizardConfig config = new DropwizardConfig() {
            @NonNull
            @Override
            public String prefix() {
                return "dropwizard";
            }

            @Override
            public String get(@NonNull String key) {
                return null;
            }
        };
        dropWizardRegistry = new MetricRegistry();
        subject = new GlobalMetricRegistry(new DropwizardMeterRegistry(config,
                                                                       dropWizardRegistry,
                                                                       HierarchicalNameMapper.DEFAULT,
                                                                       Clock.SYSTEM) {
            @NonNull
            @Override
            protected Double nullGaugeValue() {
                return 0.0;
            }
        });
    }

    @Test
    void createEventProcessorMonitor() {
        MessageMonitor<? super EventMessage<?>> monitor1 = subject.registerEventProcessor("test1");
        MessageMonitor<? super EventMessage<?>> monitor2 = subject.registerEventProcessor("test2");

        monitor1.onMessageIngested(asEventMessage("test")).reportSuccess();
        monitor2.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("test1"));
        assertTrue(output.contains("test2"));
    }

    @Test
    void createEventProcessorMonitorWithTags() {
        MessageMonitor<? super EventMessage<?>> monitor1 = subject.registerEventProcessor(
                "test1",
                message -> Tags.of(TagsUtil.PAYLOAD_TYPE_TAG, message.getPayloadType().getSimpleName()),
                message -> Tags.empty());
        MessageMonitor<? super EventMessage<?>> monitor2 = subject.registerEventProcessor(
                "test2",
                message -> Tags.of(TagsUtil.PAYLOAD_TYPE_TAG, message.getPayloadType().getSimpleName()),
                message -> Tags.empty());

        monitor1.onMessageIngested(asEventMessage("test")).reportSuccess();
        monitor2.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("test1"));
        assertTrue(output.contains("test2"));
    }

    @Test
    void createEventBusMonitor() {
        MessageMonitor<? super EventMessage<?>> monitor = subject.registerEventBus("eventBus");

        monitor.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("eventBus"));
    }

    @Test
    void createEventBusMonitorWithTags() {
        MessageMonitor<? super EventMessage<?>> monitor = subject.registerEventBus("eventBus", message -> Tags
                .of(TagsUtil.PAYLOAD_TYPE_TAG,
                    message.getPayloadType()
                           .getSimpleName()));

        monitor.onMessageIngested(asEventMessage("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("eventBus"));
    }

    @Test
    void createCommandBusMonitor() {
        MessageMonitor<? super CommandMessage<?>> monitor = subject.registerCommandBus("commandBus");

        monitor.onMessageIngested(new GenericCommandMessage<>("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("commandBus"));
    }

    @Test
    void createCommandBusMonitorWithTags() {
        MessageMonitor<? super CommandMessage<?>> monitor = subject.registerCommandBus("commandBus", message -> Tags
                .of(TagsUtil.PAYLOAD_TYPE_TAG,
                    message.getPayloadType()
                           .getSimpleName()));

        monitor.onMessageIngested(new GenericCommandMessage<>("test")).reportSuccess();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ConsoleReporter.forRegistry(dropWizardRegistry).outputTo(new PrintStream(out)).build().report();
        String output = new String(out.toByteArray());

        assertTrue(output.contains("commandBus"));
    }

    @Test
    void createMonitorForUnknownComponent() {
        MessageMonitor<? extends Message<?>> actual = subject.registerComponent(String.class, "test");

        assertSame(NoOpMessageMonitor.instance(), actual);
    }

    @Test
    void createMonitorForUnknownComponentWithTags() {
        MessageMonitor<? extends Message<?>> actual = subject.registerComponentWithDefaultTags(String.class, "test");

        assertSame(NoOpMessageMonitor.instance(), actual);
    }
}
