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

package org.sophonhq.sophon.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.SlidingTimeWindowReservoir;
import com.codahale.metrics.Timer;
import org.junit.jupiter.api.*;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.monitoring.MessageMonitor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test class validating the {@link MessageTimerMonitor}.
 *
 * @author Marijn van Zelst
 */
class MessageTimerMonitorTest {

    private TestClock testClock;
    private MessageTimerMonitor testSubject;

    @BeforeEach
    void setUp() {
        testClock = new TestClock();
        testSubject = MessageTimerMonitor.builder()
                                         .clock(testClock)
                                         .build();
    }

    @Test
    void successMessage() {
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.reportSuccess();

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Timer all = (Timer) metricSet.get("allTimer");
        Timer successTimer = (Timer) metricSet.get("successTimer");
        Timer failureTimer = (Timer) metricSet.get("failureTimer");
        Timer ignoredTimer = (Timer) metricSet.get("ignoredTimer");

        assertThat(all.getSnapshot().getValues()).containsExactly(1000000);
        assertThat(successTimer.getSnapshot().getValues()).containsExactly(1000000);
        assertThat(failureTimer.getSnapshot().getValues()).isEmpty();
        assertThat(ignoredTimer.getSnapshot().getValues()).isEmpty();
    }

    @Test
    void failureMessage() {
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.reportFailure(null);

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Timer all = (Timer) metricSet.get("allTimer");
        Timer successTimer = (Timer) metricSet.get("successTimer");
        Timer failureTimer = (Timer) metricSet.get("failureTimer");
        Timer ignoredTimer = (Timer) metricSet.get("ignoredTimer");

        assertThat(all.getSnapshot().getValues()).containsExactly(1000000);
        assertThat(successTimer.getSnapshot().getValues()).isEmpty();
        assertThat(ignoredTimer.getSnapshot().getValues()).isEmpty();
        assertThat(failureTimer.getSnapshot().getValues()).containsExactly(1000000);
    }

    @Test
    void ignoredMessage() {
        MessageMonitor.MonitorCallback monitorCallback = testSubject.onMessageIngested(null);
        testClock.increase(1000);
        monitorCallback.reportIgnored();

        Map<String, Metric> metricSet = testSubject.getMetrics();

        Timer all = (Timer) metricSet.get("allTimer");
        Timer successTimer = (Timer) metricSet.get("successTimer");
        Timer failureTimer = (Timer) metricSet.get("failureTimer");
        Timer ignoredTimer = (Timer) metricSet.get("ignoredTimer");

        assertThat(all.getSnapshot().getValues()).containsExactly(1000000);
        assertThat(successTimer.getSnapshot().getValues()).isEmpty();
        assertThat(ignoredTimer.getSnapshot().getValues()).containsExactly(1000000);
        assertThat(failureTimer.getSnapshot().getValues()).isEmpty();
    }

    /**
     * A different {@link com.codahale.metrics.Reservoir} is used, which simply means the storage approach of the metric
     * histogram is adjusted. Within the test space, this still renders the same results, hence the similar assertion as
     * in {@link #testSuccessMessage()}.
     */
    @Test
    void customReservoir() {
        MessageTimerMonitor customReservoirTestSubject =
                MessageTimerMonitor.builder()
                                   .clock(testClock)
                                   .reservoirFactory(() -> new SlidingTimeWindowReservoir(2000, TimeUnit.MILLISECONDS))
                                   .build();

        MessageMonitor.MonitorCallback result = customReservoirTestSubject.onMessageIngested(null);
        testClock.increase(1000);
        result.reportSuccess();

        Map<String, Metric> metrics = customReservoirTestSubject.getMetrics();
        Timer all = (Timer) metrics.get("allTimer");
        Timer successTimer = (Timer) metrics.get("successTimer");
        Timer failureTimer = (Timer) metrics.get("failureTimer");
        Timer ignoredTimer = (Timer) metrics.get("ignoredTimer");

        assertThat(all.getSnapshot().getValues()).containsExactly(1000000);
        assertThat(successTimer.getSnapshot().getValues()).containsExactly(1000000);
        assertThat(failureTimer.getSnapshot().getValues()).isEmpty();
        assertThat(ignoredTimer.getSnapshot().getValues()).isEmpty();
    }

    @Test
    void buildWithNullClockThrowsSophonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder();
        assertThatThrownBy(() -> testSubject.clock(null)).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void buildWithNullReservoirFactoryThrowsSophonConfigurationException() {
        MessageTimerMonitor.Builder testSubject = MessageTimerMonitor.builder();
        assertThatThrownBy(() -> testSubject.reservoirFactory(null))
                .isInstanceOf(SophonConfigurationException.class);
    }
}
