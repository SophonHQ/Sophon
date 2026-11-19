/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.sophonhq.sophon.springboot;

import com.thoughtworks.xstream.XStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.json.JacksonSerializer;
import org.sophonhq.sophon.serialization.xml.XStreamSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ContextConfiguration
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@ExtendWith(SpringExtension.class)
class SophonAutoConfigurationWithOnlyEventSerializerTest {

    @Autowired
    private Serializer serializer;

    @Autowired
    @Qualifier("eventSerializer")
    private Serializer eventSerializer;

    @Autowired
    @Qualifier("messageSerializer")
    private Serializer messageSerializer;

    @Test
    void revertsToDefaultSerializer() {
        assertThat(serializer).isNotNull();
        assertThat(eventSerializer).isNotNull();
        assertThat(messageSerializer).isNotNull();
        assertThat(serializer.getClass()).isEqualTo(XStreamSerializer.class);
        assertThat(eventSerializer.getClass()).isEqualTo(JacksonSerializer.class);
        assertThat(messageSerializer.getClass()).isEqualTo(XStreamSerializer.class);
        assertThat(serializer).isNotSameAs(eventSerializer);
        assertThat(messageSerializer)
                .isNotSameAs(eventSerializer)
                .isNotSameAs(serializer);
    }

    @org.springframework.context.annotation.Configuration
    public static class Configuration {

        @Bean
        @Qualifier("eventSerializer")
        public Serializer myEventSerializer() {
            return JacksonSerializer.builder().build();
        }

        @Bean
        @Qualifier("messageSerializer")
        public Serializer myMessageSerializer(XStream xStream) {
            return XStreamSerializer.builder()
                                    .xStream(xStream)
                                    .build();
        }
    }
}
