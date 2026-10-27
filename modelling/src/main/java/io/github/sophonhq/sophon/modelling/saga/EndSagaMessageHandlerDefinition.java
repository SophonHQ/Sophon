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

package io.github.sophonhq.sophon.modelling.saga;

import io.github.sophonhq.sophon.messaging.Message;
import io.github.sophonhq.sophon.messaging.annotation.HandlerEnhancerDefinition;
import io.github.sophonhq.sophon.messaging.annotation.MessageHandlingMember;
import io.github.sophonhq.sophon.messaging.annotation.WrappedMessageHandlingMember;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A {@link HandlerEnhancerDefinition} inspecting the existence of the {@link EndSaga} annotation on {@link
 * MessageHandlingMember}s. If present, the given {@code MessageHandlingMember} will be wrapped in a {@link
 * EndSageMessageHandlingMember}.
 *
 * @author Steven van Beelen
 * @since axon-4.5
 */
public class EndSagaMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public @NonNull
    <T> MessageHandlingMember<T> wrapHandler(@NonNull MessageHandlingMember<T> original) {
        return original.hasAnnotation(EndSaga.class) ? new EndSageMessageHandlingMember<>(original) : original;
    }

    /**
     * A {@link WrappedMessageHandlingMember} implementation dedicated towards {@link MessageHandlingMember}s annotated
     * with {@link EndSaga}. After invocation of the {@link #handle(Message, Object)} method, the saga's is ended
     * through the {@link SagaLifecycle#end()} method.
     *
     * @param <T> the entity type wrapped by this {@link MessageHandlingMember}
     */
    public static class EndSageMessageHandlingMember<T> extends WrappedMessageHandlingMember<T> {

        /**
         * Initializes the member using the given {@code delegate}.
         *
         * @param delegate the actual message handling member to delegate to
         */
        protected EndSageMessageHandlingMember(MessageHandlingMember<T> delegate) {
            super(delegate);
        }

        @Override
        public Object handle(@NonNull Message<?> message, @Nullable T target) throws Exception {
            try {
                return super.handle(message, target);
            } finally {
                SagaLifecycle.end();
            }
        }
    }
}
