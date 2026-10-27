/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.sophonhq.sophon.springboot.autoconfig;

import io.opentelemetry.api.OpenTelemetry;
import org.sophonhq.sophon.tracing.SpanFactory;
import org.sophonhq.sophon.tracing.opentelemetry.OpenTelemetrySpanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Automatically configured the {@link OpenTelemetrySpanFactory} as the method of providing tracing in Sophon.
 * For this to take effect, the {@code sophon-tracing-opentelemetry} dependency must be on the classpath.
 * <p>
 * When a Spring-managed {@link OpenTelemetry} bean is present (e.g. Spring Boot 3's
 * {@code OpenTelemetryAutoConfiguration} together with {@code micrometer-tracing-bridge-otel}), its
 * {@link io.opentelemetry.api.trace.Tracer Tracer} and
 * {@link io.opentelemetry.context.propagation.TextMapPropagator TextMapPropagator} are passed into the
 * {@link OpenTelemetrySpanFactory.Builder} explicitly. Without this, the builder falls back to
 * {@link io.opentelemetry.api.GlobalOpenTelemetry}, which Spring Boot 3 does not register — leaving the
 * factory with a no-op propagator that silently drops W3C trace context across asynchronous Sophon
 * boundaries (event processors, deadlines, distributed command/query buses).
 * <p>
 * If no {@link OpenTelemetry} bean is available the builder defaults are kept, preserving compatibility
 * with the OpenTelemetry Java agent and with manual SDK installations that call
 * {@link io.opentelemetry.sdk.OpenTelemetrySdkBuilder#buildAndRegisterGlobal()}.
 *
 * @author Mitchell Herrijgers
 * @since axon-4.6.0
 */
@AutoConfiguration
@AutoConfigureBefore({SophonTracingAutoConfiguration.class, SophonAutoConfiguration.class})
@ConditionalOnClass(name = "org.sophonhq.sophon.tracing.opentelemetry.OpenTelemetrySpanFactory")
public class OpenTelemetryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SpanFactory.class)
    public SpanFactory spanFactory(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
        OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
        if (openTelemetry == null) {
            return OpenTelemetrySpanFactory.builder().build();
        }
        return OpenTelemetrySpanFactory.builder()
                                       .tracer(openTelemetry.getTracer("Sophon-OpenTelemetry"))
                                       .contextPropagators(openTelemetry.getPropagators().getTextMapPropagator())
                                       .build();
    }
}
