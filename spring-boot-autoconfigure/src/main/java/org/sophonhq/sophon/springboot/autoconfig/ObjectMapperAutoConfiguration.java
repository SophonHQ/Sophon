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

package org.sophonhq.sophon.springboot.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sophonhq.sophon.springboot.SerializerProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

// Ordered before CBORMapperAutoConfiguration: a CBORMapper is an ObjectMapper and would otherwise satisfy the
// @ConditionalOnMissingBean of the default ObjectMapper below.
@AutoConfiguration
@AutoConfigureBefore({SophonAutoConfiguration.class, CBORMapperAutoConfiguration.class})
@AutoConfigureAfter(name = "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration")
@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
@EnableConfigurationProperties(value = SerializerProperties.class)
public class ObjectMapperAutoConfiguration {

    @Bean("defaultSophonObjectMapper")
    @ConditionalOnMissingBean
    @ConditionalOnExpression("'${sophon.serializer.general}' == 'jackson' || '${sophon.serializer.events}' == 'jackson' || '${sophon.serializer.messages}' == 'jackson'")
    public ObjectMapper defaultSophonObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
