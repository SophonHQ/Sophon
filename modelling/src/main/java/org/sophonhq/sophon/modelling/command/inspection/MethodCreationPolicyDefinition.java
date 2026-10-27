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

package org.sophonhq.sophon.modelling.command.inspection;

import org.jspecify.annotations.NonNull;
import org.sophonhq.sophon.messaging.annotation.HandlerEnhancerDefinition;
import org.sophonhq.sophon.messaging.annotation.MessageHandlingMember;
import org.sophonhq.sophon.messaging.annotation.WrappedMessageHandlingMember;
import org.sophonhq.sophon.modelling.command.AggregateCreationPolicy;
import org.sophonhq.sophon.modelling.command.CreationPolicy;

import java.util.Map;

/**
 * Implementation of {@link HandlerEnhancerDefinition} used for {@link CreationPolicy} annotated methods.
 *
 * @author Marc Gathier
 * @since axon-4.3
 */
public class MethodCreationPolicyDefinition implements HandlerEnhancerDefinition {

    @Override
    public @NonNull
    <T> MessageHandlingMember<T> wrapHandler(@NonNull MessageHandlingMember<T> original) {
        return original.annotationAttributes(CreationPolicy.class)
                       .map(attr -> (MessageHandlingMember<T>) new MethodCreationPolicyHandlingMember<>(
                               original, attr))
                       .orElse(original);
    }

    private static class MethodCreationPolicyHandlingMember<T> extends WrappedMessageHandlingMember<T>
            implements CreationPolicyMember<T> {

        private final AggregateCreationPolicy creationPolicy;

        private MethodCreationPolicyHandlingMember(
                MessageHandlingMember<T> delegate, Map<String, Object> attr) {
            super(delegate);
            creationPolicy = (AggregateCreationPolicy) attr.get("creationPolicy");
        }

        @Override
        public AggregateCreationPolicy creationPolicy() {
            return creationPolicy;
        }
    }
}
