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

package org.sophonhq.sophon.springboot;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.ReflectionUtils;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.tracing.SpanFactory;
import org.sophonhq.sophon.tracing.opentelemetry.OpenTelemetrySpanFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SophonAutoConfigurationWithOpenTelemetryTest {

    @Test
    void spanFactoryIsOpenTelemetrySpanFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("spanFactory")).isTrue();
                    assertThat(context.getBean(SpanFactory.class)).isNotNull();
                    assertThat(context.getBean(SpanFactory.class).getClass()).isEqualTo(OpenTelemetrySpanFactory.class);
                });
    }

    @Test
    void spanFactoryUsesSpringManagedOpenTelemetryWhenAvailable() {
        TextMapPropagator expectedPropagator = W3CTraceContextPropagator.getInstance();
        SdkTracerProvider tracerProvider
                = SdkTracerProvider.builder()
                                   .addSpanProcessor(SimpleSpanProcessor.create(SpanExporter.composite()))
                                   .build();

        //noinspection resource
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
                                                      .setTracerProvider(tracerProvider)
                                                      .setPropagators(ContextPropagators.create(expectedPropagator))
                                                      .build();
        Tracer expectedTracer = openTelemetry.getTracer("Sophon-OpenTelemetry");

        new ApplicationContextRunner()
                .withUserConfiguration(ContextWithOpenTelemetry.class)
                .withBean(OpenTelemetry.class, () -> openTelemetry)
                .run(context -> {
                    SpanFactory spanFactory = context.getBean(SpanFactory.class);
                    assertThat(spanFactory.getClass()).isEqualTo(OpenTelemetrySpanFactory.class);

                    // The Spring-managed OpenTelemetry's tracer must be wired into the factory,
                    // not the no-op GlobalOpenTelemetry fallback.
                    Tracer wiredTracer = ReflectionUtils.getFieldValue(
                            OpenTelemetrySpanFactory.class.getDeclaredField("tracer"),
                            spanFactory
                    );
                    assertThat(wiredTracer)
                            .as("Factory must use the tracer from the Spring-managed OpenTelemetry bean")
                            .isSameAs(expectedTracer);

                    // The Spring-managed OpenTelemetry's propagator must be wired into the factory,
                    // not the no-op GlobalOpenTelemetry fallback.
                    TextMapPropagator wired = ReflectionUtils.getFieldValue(
                            OpenTelemetrySpanFactory.class.getDeclaredField("textMapPropagator"),
                            spanFactory
                    );

                    assertThat(wired)
                            .as("Factory must use the propagator from the Spring-managed OpenTelemetry bean")
                            .isSameAs(expectedPropagator);

                    // Behavioral confirmation: propagateContext injects the W3C "traceparent" header.
                    Tracer tracer = openTelemetry.getTracer("test");
                    Span span = tracer.spanBuilder("root").startSpan();
                    EventMessage<String> propagated;
                    try (Scope ignored = span.makeCurrent()) {
                        propagated = spanFactory.propagateContext(GenericEventMessage.asEventMessage("payload"));
                    } finally {
                        span.end();
                    }
                    assertThat(propagated.getMetaData().containsKey("traceparent"))
                            .as("W3C trace context must be injected into message metadata for async handlers to extract it")
                            .isTrue();
                });
    }

    @Test
    void spanFactoryFallsBackToGlobalOpenTelemetryWhenNoBeanAvailable() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    SpanFactory spanFactory = context.getBean(SpanFactory.class);
                    assertThat(spanFactory.getClass()).isEqualTo(OpenTelemetrySpanFactory.class);

                    // Without a Spring-managed OpenTelemetry bean, the builder defaults apply —
                    // tracer and propagator come from GlobalOpenTelemetry (Java agent / manual SDK
                    // installations that call buildAndRegisterGlobal()).
                    TextMapPropagator wired = ReflectionUtils.getFieldValue(
                            OpenTelemetrySpanFactory.class.getDeclaredField("textMapPropagator"),
                            spanFactory
                    );
                    assertThat(wired).as("Propagator must be set (default from GlobalOpenTelemetry)").isNotNull();
                });
    }

    @EnableAutoConfiguration(exclude = {
            HibernateJpaAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
    })
    @Configuration
    public static class Context {

    }

    @EnableAutoConfiguration(exclude = {
            HibernateJpaAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
    })
    @Configuration
    public static class ContextWithOpenTelemetry {

    }
}
