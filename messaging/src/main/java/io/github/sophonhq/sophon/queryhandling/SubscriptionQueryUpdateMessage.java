/*
 * Copyright (c) 2010-2022. Axon Framework
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

package io.github.sophonhq.sophon.queryhandling;

import io.github.sophonhq.sophon.messaging.ResultMessage;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Message which holds incremental update of an subscription query.
 *
 * @param <U> type of incremental update
 * @author Milan Savic
 * @since axon-3.3
 */
public interface SubscriptionQueryUpdateMessage<U> extends ResultMessage<U> {

    @Override
    SubscriptionQueryUpdateMessage<U> withMetaData(@NonNull Map<String, ?> metaData);

    @Override
    SubscriptionQueryUpdateMessage<U> andMetaData(@NonNull Map<String, ?> metaData);
}
