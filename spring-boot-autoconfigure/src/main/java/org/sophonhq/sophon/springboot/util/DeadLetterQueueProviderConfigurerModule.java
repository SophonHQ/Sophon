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

package org.sophonhq.sophon.springboot.util;

import org.jspecify.annotations.NonNull;
import org.sophonhq.sophon.config.Configuration;
import org.sophonhq.sophon.config.Configurer;
import org.sophonhq.sophon.config.ConfigurerModule;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.messaging.deadletter.SequencedDeadLetterQueue;
import org.sophonhq.sophon.springboot.EventProcessorProperties;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link ConfigurerModule} implementation dedicated towards registering a {@link SequencedDeadLetterQueue} provider
 * with the {@link org.sophonhq.sophon.config.EventProcessingConfigurer}.
 * <p>
 * Does so through invoking the
 * {@link org.sophonhq.sophon.config.EventProcessingConfigurer#registerDeadLetterQueueProvider(Function)} operation,
 * utilizing the given {@code deadLetterQueueProvider}. Only processing groups for which the dead-letter queue is
 * {@link EventProcessorProperties.Dlq#isEnabled() enabled} will receive the provided dead-letter queue.
 *
 * @author Gerard Klijs
 * @author Steven van Beelen
 * @since axon-4.8.0
 */
public class DeadLetterQueueProviderConfigurerModule implements ConfigurerModule {

    private final EventProcessorProperties eventProcessorProperties;
    private final Function<String, Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>>> deadLetterQueueProvider;

    /**
     * Construct a {@link DeadLetterQueueProviderConfigurerModule}, using the given {@code eventProcessorProperties} to
     * decide which processing groups receive the {@link SequencedDeadLetterQueue} from the given
     * {@code deadLetterQueueProvider}.
     *
     * @param eventProcessorProperties The properties dictating for which processing groups the dead-letter queue is
     *                                 {@link EventProcessorProperties.Dlq#isEnabled() enabled}.
     * @param deadLetterQueueProvider  The function providing the {@link SequencedDeadLetterQueue}.
     */
    public DeadLetterQueueProviderConfigurerModule(
            EventProcessorProperties eventProcessorProperties,
            Function<String, Function<Configuration, SequencedDeadLetterQueue<EventMessage<?>>>> deadLetterQueueProvider
    ) {
        this.eventProcessorProperties = eventProcessorProperties;
        this.deadLetterQueueProvider = deadLetterQueueProvider;
    }

    @Override
    public void configureModule(@NonNull Configurer configurer) {
        configurer.eventProcessing().registerDeadLetterQueueProvider(
                processingGroup -> dlqEnabled(processingGroup)
                        ? deadLetterQueueProvider.apply(processingGroup)
                        : null
        );

    }

    private boolean dlqEnabled( String processingGroup) {
        return dlqEnabled(eventProcessorProperties.getProcessors(), processingGroup);
    }

    private boolean dlqEnabled(Map<String, EventProcessorProperties.ProcessorSettings> processorSettings,
                               String processingGroup) {
        return Optional.ofNullable(processorSettings.get(processingGroup))
                       .map(EventProcessorProperties.ProcessorSettings::getDlq)
                       .map(EventProcessorProperties.Dlq::isEnabled)
                       .orElse(false);
    }
}
