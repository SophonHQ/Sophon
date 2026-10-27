/*
 * Copyright (c) 2010-2025. Axon Framework
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

package io.github.sophonhq.sophon.springboot.autoconfig;

import io.github.sophonhq.sophon.messaging.annotation.HandlerEnhancerDefinition;
import io.github.sophonhq.sophon.spring.authorization.SecuredMessageHandlerDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

// Ordered after SophonTimeoutAutoConfiguration so the secured-handler enhancer is registered after the timeout
// enhancer, independent of auto-configuration class names.
@AutoConfiguration
@AutoConfigureAfter(SophonTimeoutAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.security.access.annotation.Secured")
public class SecurityAutoConfiguration {

    @Bean
    public HandlerEnhancerDefinition securedMessageHandlerDefinition() {
        return new SecuredMessageHandlerDefinition();
    }
}