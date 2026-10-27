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

package io.github.sophonhq.sophon.modelling.command.inspection;

import io.github.sophonhq.sophon.commandhandling.CommandMessage;
import io.github.sophonhq.sophon.messaging.Message;
import io.github.sophonhq.sophon.messaging.annotation.HandlerEnhancerDefinition;
import io.github.sophonhq.sophon.messaging.annotation.MessageHandlingMember;
import io.github.sophonhq.sophon.messaging.annotation.WrappedMessageHandlingMember;
import io.github.sophonhq.sophon.modelling.command.CommandHandlerInterceptor;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Implementation of {@link HandlerEnhancerDefinition} used for {@link CommandHandlerInterceptor} annotated methods.
 *
 * @author Milan Savic
 * @since axon-3.3
 */
public class MethodCommandHandlerInterceptorDefinition implements HandlerEnhancerDefinition {

    @Override
    public @NonNull
    <T> MessageHandlingMember<T> wrapHandler(@NonNull MessageHandlingMember<T> original) {
        return original.annotationAttributes(CommandHandlerInterceptor.class)
                       .map(attr -> (MessageHandlingMember<T>) new MethodCommandHandlerInterceptorHandlingMember<>(
                               original, attr))
                       .orElse(original);
    }

    private static class MethodCommandHandlerInterceptorHandlingMember<T> extends WrappedMessageHandlingMember<T> {

        private final Pattern commandNamePattern;

        /**
         * Initializes the member using the given {@code delegate}.
         *
         * @param delegate the actual message handling member to delegate to
         */
        private MethodCommandHandlerInterceptorHandlingMember(MessageHandlingMember<T> delegate,
                                                              Map<String, Object> annotationAttributes) {
            super(delegate);
            commandNamePattern = Pattern.compile((String) annotationAttributes.get("commandNamePattern"));
        }

        @Override
        public boolean canHandle(@NonNull Message<?> message) {
            return super.canHandle(message) && commandNamePattern.matcher(((CommandMessage) message).getCommandName())
                                                                 .matches();
        }
    }
}
