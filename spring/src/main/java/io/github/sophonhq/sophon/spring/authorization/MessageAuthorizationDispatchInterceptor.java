/*
 * Copyright (c) 2010-2024. Axon Framework
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

package io.github.sophonhq.sophon.spring.authorization;

import io.github.sophonhq.sophon.messaging.Message;
import io.github.sophonhq.sophon.messaging.MessageDispatchInterceptor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * A {@link MessageDispatchInterceptor} that adds the {$code authorities} from the authorized principle.
 *
 * @author Roald Bankras
 * @since axon-4.11.0
 */
public class MessageAuthorizationDispatchInterceptor<T extends Message<?>> implements MessageDispatchInterceptor<T> {

    private static final Logger logger = LoggerFactory.getLogger(MessageAuthorizationDispatchInterceptor.class);

    @NonNull
    @Override
    public T handle(@NonNull T message) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            logger.debug("No authentication found.");
            return message;
        }

        logger.debug("Adding message metadata for authorities.");
        Map<String, String> authenticationDetails = new HashMap<>();
        String authorities = authentication.getAuthorities()
                                           .stream()
                                           .map(GrantedAuthority::getAuthority)
                                           .collect(Collectors.joining(","));
        authenticationDetails.put("authorities", authorities);
        //noinspection unchecked
        return (T) message.andMetaData(authenticationDetails);
    }

    @NonNull
    @Override
    public BiFunction<Integer, T, T> handle(@NonNull List<? extends T> list) {
        return (position, message) -> handle(message);
    }
}
