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

import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.sophonhq.sophon.commandhandling.CommandBusSpanFactory;
import org.sophonhq.sophon.commandhandling.DefaultCommandBusSpanFactory;
import org.sophonhq.sophon.deadline.DeadlineManagerSpanFactory;
import org.sophonhq.sophon.deadline.DefaultDeadlineManagerSpanFactory;
import org.sophonhq.sophon.eventhandling.DefaultEventBusSpanFactory;
import org.sophonhq.sophon.eventhandling.DefaultEventProcessorSpanFactory;
import org.sophonhq.sophon.eventhandling.EventBusSpanFactory;
import org.sophonhq.sophon.eventhandling.EventProcessorSpanFactory;
import org.sophonhq.sophon.eventsourcing.DefaultSnapshotterSpanFactory;
import org.sophonhq.sophon.eventsourcing.SnapshotterSpanFactory;
import org.sophonhq.sophon.messaging.ScopeDescriptor;
import org.sophonhq.sophon.messaging.annotation.HandlerEnhancerDefinition;
import org.sophonhq.sophon.modelling.command.DefaultRepositorySpanFactory;
import org.sophonhq.sophon.modelling.command.RepositorySpanFactory;
import org.sophonhq.sophon.modelling.saga.DefaultSagaManagerSpanFactory;
import org.sophonhq.sophon.modelling.saga.SagaManagerSpanFactory;
import org.sophonhq.sophon.queryhandling.DefaultQueryBusSpanFactory;
import org.sophonhq.sophon.queryhandling.DefaultQueryUpdateEmitterSpanFactory;
import org.sophonhq.sophon.queryhandling.QueryBusSpanFactory;
import org.sophonhq.sophon.queryhandling.QueryUpdateEmitterSpanFactory;
import org.sophonhq.sophon.springboot.autoconfig.OpenTelemetryAutoConfiguration;
import org.sophonhq.sophon.springboot.autoconfig.SecurityAutoConfiguration;
import org.sophonhq.sophon.springboot.autoconfig.SophonTimeoutAutoConfiguration;
import org.sophonhq.sophon.tracing.NoOpSpanFactory;
import org.sophonhq.sophon.tracing.Span;
import org.sophonhq.sophon.tracing.SpanAttributesProvider;
import org.sophonhq.sophon.tracing.SpanFactory;
import org.sophonhq.sophon.tracing.TracingHandlerEnhancerDefinition;
import org.sophonhq.sophon.tracing.attributes.AggregateIdentifierSpanAttributesProvider;
import org.sophonhq.sophon.tracing.attributes.MessageIdSpanAttributesProvider;
import org.sophonhq.sophon.tracing.attributes.MessageNameSpanAttributesProvider;
import org.sophonhq.sophon.tracing.attributes.MessageTypeSpanAttributesProvider;
import org.sophonhq.sophon.tracing.attributes.MetadataSpanAttributesProvider;
import org.sophonhq.sophon.tracing.attributes.PayloadTypeSpanAttributesProvider;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;

class SophonAutoConfigurationWithTracingTest {

    @Test
    void spanFactoryDefaultsToNoop() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("spanFactory")).isTrue();
                    assertThat(context.getBean(SpanFactory.class)).isNotNull();
                    assertThat(context.getBean(SpanFactory.class).getClass()).isEqualTo(NoOpSpanFactory.class);
                });
    }


    @Test
    void handlerEnhancerDefinitionIsRegistered() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("tracingHandlerEnhancerDefinition")).isTrue();
                    assertThat(context.getBean(HandlerEnhancerDefinition.class)).isNotNull();
                    assertThat(context.getBean(HandlerEnhancerDefinition.class).getClass())
                            .isEqualTo(TracingHandlerEnhancerDefinition.class);
                });
    }


    @Test
    void registersAllAttrributeProvidersByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("aggregateIdentifierSpanAttributesProvider")).isTrue();
                    assertThat(context.getBean(AggregateIdentifierSpanAttributesProvider.class)).isNotNull();

                    assertThat(context.containsBean("messageIdSpanAttributesProvider")).isTrue();
                    assertThat(context.getBean(MessageIdSpanAttributesProvider.class)).isNotNull();

                    assertThat(context.containsBean("messageNameSpanAttributesProvider")).isTrue();
                    assertThat(context.getBean(MessageNameSpanAttributesProvider.class)).isNotNull();

                    assertThat(context.containsBean("messageTypeSpanAttributesProvider")).isTrue();
                    assertThat(context.getBean(MessageTypeSpanAttributesProvider.class)).isNotNull();

                    assertThat(context.containsBean("metadataSpanAttributesProvider")).isTrue();
                    assertThat(context.getBean(MetadataSpanAttributesProvider.class)).isNotNull();

                    assertThat(context.containsBean("payloadTypeSpanAttributesProvider")).isTrue();
                    assertThat(context.getBean(AggregateIdentifierSpanAttributesProvider.class)).isNotNull();
                });
    }

    @Test
    void registersAllAttrributeProvidersByDefaultAsList() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    Map<String, SpanAttributesProvider> beansOfType = context.getBeansOfType(SpanAttributesProvider.class);

                    assertThat(beansOfType).containsKey("aggregateIdentifierSpanAttributesProvider");
                    assertThat(beansOfType).containsKey("messageIdSpanAttributesProvider");
                    assertThat(beansOfType).containsKey("messageNameSpanAttributesProvider");
                    assertThat(beansOfType).containsKey("messageTypeSpanAttributesProvider");
                    assertThat(beansOfType).containsKey("metadataSpanAttributesProvider");
                    assertThat(beansOfType).containsKey("payloadTypeSpanAttributesProvider");
                });
    }

    @Test
    void aggregateIdAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "sophon.tracing.attribute-providers.aggregate-identifier=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("aggregateIdentifierSpanAttributesProvider")).isFalse();
                    assertThatThrownBy(() -> context.getBean(AggregateIdentifierSpanAttributesProvider.class))
                            .isInstanceOf(NoSuchBeanDefinitionException.class);
                });
    }


    @Test
    void messageIdAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "sophon.tracing.attribute-providers.message-id=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("messageIdSpanAttributesProvider")).isFalse();
                    assertThatThrownBy(() -> context.getBean(MessageIdSpanAttributesProvider.class))
                            .isInstanceOf(NoSuchBeanDefinitionException.class);
                });
    }

    @Test
    void messageNameAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "sophon.tracing.attribute-providers.message-name=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("messageNameSpanAttributesProvider")).isFalse();
                    assertThatThrownBy(() -> context.getBean(MessageNameSpanAttributesProvider.class))
                            .isInstanceOf(NoSuchBeanDefinitionException.class);
                });
    }

    @Test
    void messageTypeAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "sophon.tracing.attribute-providers.message-type=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("messageTypeSpanAttributesProvider")).isFalse();
                    assertThatThrownBy(() -> context.getBean(MessageTypeSpanAttributesProvider.class))
                            .isInstanceOf(NoSuchBeanDefinitionException.class);
                });
    }

    @Test
    void metadataAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "sophon.tracing.attribute-providers.metadata=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("metadataSpanAttributesProvider")).isFalse();
                    assertThatThrownBy(() -> context.getBean(MetadataSpanAttributesProvider.class))
                            .isInstanceOf(NoSuchBeanDefinitionException.class);
                });
    }

    @Test
    void payloadTypeAttributeProviderCanBeDisabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "sophon.tracing.attribute-providers.payload-type=false"
                )
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("payloadTypeSpanAttributesProvider")).isFalse();
                    assertThatThrownBy(() -> context.getBean(PayloadTypeSpanAttributesProvider.class))
                            .isInstanceOf(NoSuchBeanDefinitionException.class);
                });
    }

    @Test
    void setsSpanAttributeProviderOnTracer() {
        SpanFactory spanFactory = Mockito.mock(SpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(SpanFactory.class, () -> spanFactory)
                .run(context -> {
                    assertThat(context).isNotNull();
                    assertThat(context.getBean(SpanFactory.class)).isSameAs(spanFactory);

                    int numberOfProviders = context.getBeansOfType(SpanAttributesProvider.class).size();
                    Mockito.verify(spanFactory, Mockito.times(numberOfProviders)).registerSpanAttributeProvider(any());
                });
    }


    @Test
    void snapshotterSpanFactoryDefaultsToDefaultSnapshotterSpanFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("snapshotterSpanFactory")).isTrue();
                    assertThat(context.getBean(SnapshotterSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(SnapshotterSpanFactory.class).getClass())
                            .isEqualTo(DefaultSnapshotterSpanFactory.class);
                });
    }

    @Test
    void snapshotterSpanFactoryCanBeOverriddenByUser() {
        SnapshotterSpanFactory mock = Mockito.mock(SnapshotterSpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(SnapshotterSpanFactory.class, () -> mock)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("snapshotterSpanFactory")).isTrue();
                    assertThat(context.getBean(SnapshotterSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(SnapshotterSpanFactory.class)).isSameAs(mock);
                });
    }

    @Test
    void eventBusSpanFactoryDefaultsToDefaultEventBusSpanFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("eventBusSpanFactory")).isTrue();
                    assertThat(context.getBean(EventBusSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(EventBusSpanFactory.class).getClass())
                            .isEqualTo(DefaultEventBusSpanFactory.class);
                });
    }

    @Test
    void eventBusSpanFactoryCanBeOverriddenByUser() {
        EventBusSpanFactory mock = Mockito.mock(EventBusSpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(EventBusSpanFactory.class, () -> mock)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("eventBusSpanFactory")).isTrue();
                    assertThat(context.getBean(EventBusSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(EventBusSpanFactory.class)).isSameAs(mock);
                });
    }

    @Test
    void queryBusSpanFactoryDefaultsToDefaultQueryBusSpanFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("queryBusSpanFactory")).isTrue();
                    assertThat(context.getBean(QueryBusSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(QueryBusSpanFactory.class).getClass())
                            .isEqualTo(DefaultQueryBusSpanFactory.class);
                });
    }

    @Test
    void queryBusSpanFactoryCanBeOverriddenByUser() {
        QueryBusSpanFactory mock = Mockito.mock(QueryBusSpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(QueryBusSpanFactory.class, () -> mock)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("queryBusSpanFactory")).isTrue();
                    assertThat(context.getBean(QueryBusSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(QueryBusSpanFactory.class)).isSameAs(mock);
                });
    }

    @Test
    void queryUpdateEmitterSpanFactoryDefaultsToDefaultQueryUpdateEmitterSpanFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("queryUpdateEmitterSpanFactory")).isTrue();
                    assertThat(context.getBean(QueryUpdateEmitterSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(QueryUpdateEmitterSpanFactory.class).getClass())
                            .isEqualTo(DefaultQueryUpdateEmitterSpanFactory.class);
                });
    }
    @Test
    void queryUpdateEmitterSpanFactoryCanBeOverriddenByUser() {
        QueryUpdateEmitterSpanFactory mock = Mockito.mock(QueryUpdateEmitterSpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(QueryUpdateEmitterSpanFactory.class, () -> mock)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("queryUpdateEmitterSpanFactory")).isTrue();
                    assertThat(context.getBean(QueryUpdateEmitterSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(QueryUpdateEmitterSpanFactory.class)).isSameAs(mock);
                });
    }

    @Test
    void commandBusSpanFactoryDefaultsToDefaultCommandBusSpanFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("commandBusSpanFactory")).isTrue();
                    assertThat(context.getBean(CommandBusSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(CommandBusSpanFactory.class).getClass())
                            .isEqualTo(DefaultCommandBusSpanFactory.class);
                });
    }

    @Test
    void commandBusSpanFactoryCanBeOverriddenByUser() {
        CommandBusSpanFactory mock = Mockito.mock(CommandBusSpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(CommandBusSpanFactory.class, () -> mock)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("commandBusSpanFactory")).isTrue();
                    assertThat(context.getBean(CommandBusSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(CommandBusSpanFactory.class)).isSameAs(mock);
                });
    }

    @Test
    void deadlineManagerSpanFactoryDefaultsToDefaultDeadlineManagerSpanFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("deadlineManagerSpanFactory")).isTrue();
                    assertThat(context.getBean(DeadlineManagerSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(DeadlineManagerSpanFactory.class).getClass())
                            .isEqualTo(DefaultDeadlineManagerSpanFactory.class);
                });
    }

    @Test
    void deadlineManagerSpanFactoryCanBeOverriddenByUser() {
        DeadlineManagerSpanFactory mock = Mockito.mock(DeadlineManagerSpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(DeadlineManagerSpanFactory.class, () -> mock)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("deadlineManagerSpanFactory")).isTrue();
                    assertThat(context.getBean(DeadlineManagerSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(DeadlineManagerSpanFactory.class)).isSameAs(mock);
                });
    }

    @Test
    void deadlineManagerSpanFactoryUsesDefaultDeadlineScopeAttributeName() {
        Span span = Mockito.mock(Span.class);
        SpanFactory spanFactory = Mockito.mock(SpanFactory.class);
        Mockito.when(spanFactory.createInternalSpan(any())).thenReturn(span);
        ScopeDescriptor scope = Mockito.mock(ScopeDescriptor.class);
        Mockito.when(scope.scopeDescription()).thenReturn("scopeDescription");
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(SpanFactory.class, () -> spanFactory)
                .run(context -> {
                    context.getBean(DeadlineManagerSpanFactory.class)
                           .createCancelAllWithinScopeSpan("deadlineName", scope);

                    Mockito.verify(span).addAttribute("sophon.deadlineScope", "scopeDescription");
                });
    }

    @Test
    void sagaManagerSpanFactoryDefaultsToDefaultSagaManagerSpanFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("sagaManagerSpanFactory")).isTrue();
                    assertThat(context.getBean(SagaManagerSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(SagaManagerSpanFactory.class).getClass())
                            .isEqualTo(DefaultSagaManagerSpanFactory.class);
                });
    }

    @Test
    void sagaManagerSpanFactoryCanBeOverriddenByUser() {
        SagaManagerSpanFactory mock = Mockito.mock(SagaManagerSpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(SagaManagerSpanFactory.class, () -> mock)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("sagaManagerSpanFactory")).isTrue();
                    assertThat(context.getBean(SagaManagerSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(SagaManagerSpanFactory.class)).isSameAs(mock);
                });
    }

    @Test
    void repositorySpanFactoryDefaultsToDefaultRepositorySpanFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("repositorySpanFactory")).isTrue();
                    assertThat(context.getBean(RepositorySpanFactory.class)).isNotNull();
                    assertThat(context.getBean(RepositorySpanFactory.class).getClass())
                            .isEqualTo(DefaultRepositorySpanFactory.class);
                });
    }

    @Test
    void repositorySpanFactoryCanBeOverriddenByUser() {
        RepositorySpanFactory mock = Mockito.mock(RepositorySpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(RepositorySpanFactory.class, () -> mock)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("repositorySpanFactory")).isTrue();
                    assertThat(context.getBean(RepositorySpanFactory.class)).isNotNull();
                    assertThat(context.getBean(RepositorySpanFactory.class)).isSameAs(mock);
                });
    }

    @Test
    void repositorySpanFactoryUsesDefaultAggregateIdAttributeName() {
        Span span = Mockito.mock(Span.class);
        SpanFactory spanFactory = Mockito.mock(SpanFactory.class);
        Mockito.when(spanFactory.createInternalSpan(any())).thenReturn(span);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(SpanFactory.class, () -> spanFactory)
                .run(context -> {
                    context.getBean(RepositorySpanFactory.class).createLoadSpan("aggregateId");

                    Mockito.verify(span).addAttribute("sophon.aggregateId", "aggregateId");
                });
    }

    @Test
    void repositorySpanFactoryUsesConfiguredAggregateIdAttributeName() {
        Span span = Mockito.mock(Span.class);
        SpanFactory spanFactory = Mockito.mock(SpanFactory.class);
        Mockito.when(spanFactory.createInternalSpan(any())).thenReturn(span);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(SpanFactory.class, () -> spanFactory)
                .withPropertyValues("sophon.tracing.repository.aggregate-id-attribute-name=customAggregateId")
                .run(context -> {
                    context.getBean(RepositorySpanFactory.class).createLoadSpan("aggregateId");

                    Mockito.verify(span).addAttribute("customAggregateId", "aggregateId");
                });
    }

    @Test
    void eventProcessorSpanFactoryDefaultsToDefaultEventProcessorSpanFactory() {
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("eventProcessorSpanFactory")).isTrue();
                    assertThat(context.getBean(EventProcessorSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(EventProcessorSpanFactory.class).getClass())
                            .isEqualTo(DefaultEventProcessorSpanFactory.class);
                });
    }

    @Test
    void eventProcessorSpanFactoryCanBeOverriddenByUser() {
        EventProcessorSpanFactory mock = Mockito.mock(EventProcessorSpanFactory.class);
        new ApplicationContextRunner()
                .withUserConfiguration(Context.class)
                .withBean(EventProcessorSpanFactory.class, () -> mock)
                .run(context -> {
                    assertThat(context).isNotNull();

                    assertThat(context.containsBean("eventProcessorSpanFactory")).isTrue();
                    assertThat(context.getBean(EventProcessorSpanFactory.class)).isNotNull();
                    assertThat(context.getBean(EventProcessorSpanFactory.class)).isSameAs(mock);
                });
    }

    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            OpenTelemetryAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SophonTimeoutAutoConfiguration.class,
    })
    @Configuration
    public static class Context {

    }
}
