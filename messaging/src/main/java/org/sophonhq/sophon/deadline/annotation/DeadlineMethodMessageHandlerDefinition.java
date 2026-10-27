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

package org.sophonhq.sophon.deadline.annotation;

import org.jspecify.annotations.NonNull;
import org.sophonhq.sophon.deadline.DeadlineMessage;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.annotation.HandlerEnhancerDefinition;
import org.sophonhq.sophon.messaging.annotation.MessageHandlingMember;
import org.sophonhq.sophon.messaging.annotation.WrappedMessageHandlingMember;

import java.util.Map;

/**
 * Implementation of a {@link HandlerEnhancerDefinition} that is used for {@link DeadlineHandler} annotated methods.
 *
 * @author Milan Savic
 * @author Steven van Beelen
 * @since axon-3.3
 */
public class DeadlineMethodMessageHandlerDefinition implements HandlerEnhancerDefinition {

    @Override
    public <T> MessageHandlingMember<T> wrapHandler(@NonNull MessageHandlingMember<T> original) {
        return original.annotationAttributes(DeadlineHandler.class)
                       .map(attr -> (MessageHandlingMember<T>) new DeadlineMethodMessageHandlingMember<>(original,
                                                                                                         attr))
                       .orElse(original);
    }

    private static class DeadlineMethodMessageHandlingMember<T> extends WrappedMessageHandlingMember<T>
            implements DeadlineHandlingMember<T> {

        private final String deadlineName;

        private DeadlineMethodMessageHandlingMember(MessageHandlingMember<T> delegate,
                                                    Map<String, Object> annotationAttributes) {
            super(delegate);
            deadlineName = (String) annotationAttributes.get("deadlineName");
        }

        @Override
        public boolean canHandle(@NonNull Message<?> message) {
            return message instanceof DeadlineMessage
                    && deadlineNameMatch((DeadlineMessage<?>) message)
                    && super.canHandle(message);
        }

        private boolean deadlineNameMatch(DeadlineMessage<?> message) {
            return deadlineNameMatchesAll() || deadlineName.equals(message.getDeadlineName());
        }

        private boolean deadlineNameMatchesAll() {
            return deadlineName.equals("");
        }

        @Override
        public int priority() {
            if (!deadlineNameMatchesAll()) {
                return 10000 + Math.min(Integer.MAX_VALUE - 10000, super.priority());
            } else {
                return 1000 + Math.min(Integer.MAX_VALUE - 1000, super.priority());
            }
        }
    }
}
