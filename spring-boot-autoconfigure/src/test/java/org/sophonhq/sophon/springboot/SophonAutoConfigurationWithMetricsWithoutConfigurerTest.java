/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.sophonhq.sophon.springboot;

import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.*;
import org.sophonhq.sophon.config.Configurer;
import org.sophonhq.sophon.config.MessageMonitorFactory;
import org.sophonhq.sophon.eventhandling.EventBus;
import org.sophonhq.sophon.metrics.GlobalMetricRegistry;
import org.sophonhq.sophon.monitoring.NoOpMessageMonitor;
import org.sophonhq.sophon.springboot.autoconfig.MicrometerMetricsAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        MicrometerMetricsAutoConfiguration.class
})
@TestPropertySource("classpath:test.metrics.application.properties")
class SophonAutoConfigurationWithMetricsWithoutConfigurerTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MetricRegistry metricRegistry;
    @Autowired
    private GlobalMetricRegistry globalMetricRegistry;

    @Test
    void contextInitialization() {
        assertThat(applicationContext).isNotNull();

        assertThat(applicationContext.containsBean("metricRegistry")).isTrue();
        assertThat(applicationContext.getBean(MetricRegistry.class)).isNotNull();
        assertThat(metricRegistry.getClass()).isEqualTo(MetricRegistry.class);

        assertThat(applicationContext.containsBean("globalMetricRegistry")).isTrue();
        assertThat(applicationContext.getBean(GlobalMetricRegistry.class)).isNotNull();
        assertThat(globalMetricRegistry.getClass()).isEqualTo(GlobalMetricRegistry.class);

        assertThat(applicationContext.containsBean("metricsConfigurerModule")).isFalse();
    }

    @Configuration
    public static class Context {

        @Autowired
        public void configure(Configurer configurer) {
            configurer.configureMessageMonitor(EventBus.class, mockMessageMonitorFactory());
        }

        @Bean
        public MessageMonitorFactory mockMessageMonitorFactory() {
            MessageMonitorFactory mock = mock(MessageMonitorFactory.class);
            when(mock.create(any(), any(), any())).thenReturn(NoOpMessageMonitor.instance());
            return mock;
        }
    }
}
