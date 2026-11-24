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

package org.sophonhq.sophon.springboot.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.json.JacksonSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class
})
@TestPropertySource("classpath:application.serializertest.properties")
class ObjectMapperAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void eventSerializerIsOfTypeJacksonSerializerAndUsesDefaultSophonObjectMapperBean() {
        final Serializer serializer = applicationContext.getBean(Serializer.class);
        final CBORMapper cborMapper = applicationContext.getBean("defaultSophonCborMapper", CBORMapper.class);

        final Serializer eventSerializer = applicationContext.getBean("eventSerializer", Serializer.class);
        final ObjectMapper objectMapper = applicationContext.getBean("defaultSophonObjectMapper", ObjectMapper.class);

        assertThat(serializer).isInstanceOf(JacksonSerializer.class);
        assertThat(((JacksonSerializer) serializer).getObjectMapper()).isEqualTo(cborMapper);
        assertThat(eventSerializer).isInstanceOf(JacksonSerializer.class);
        assertThat(((JacksonSerializer) eventSerializer).getObjectMapper()).isEqualTo(objectMapper);
    }
}