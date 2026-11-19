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

package org.sophonhq.sophon.springboot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.*;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.commandhandling.gateway.CommandGateway;
import org.sophonhq.sophon.queryhandling.QueryGateway;
import org.sophonhq.sophon.queryhandling.QueryHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SophonHandlerConfigurationTest.Context.class)
@EnableAutoConfiguration(exclude = {
        HibernateJpaAutoConfiguration.class,
        DataSourceAutoConfiguration.class
})
public class SophonHandlerConfigurationTest {

    @Autowired
    private QueryGateway queryGateway;

    @Autowired
    private CommandGateway commandGateway;

    @Test
    void messageRoutedToCorrectMethod() throws Exception {
        assertThat(commandGateway.send("info").get()).isEqualTo("Command: info");
        assertThat(queryGateway.query("info", String.class).get()).isEqualTo("Query: info");
    }

    @SuppressWarnings("unused")
    public static class CommandAndQueryHandler {

        @CommandHandler
        public String handle(String command) {
            return "Command: " + command;
        }

        @QueryHandler
        public String query(String query) {
            return "Query: " + query;
        }

    }

    @Configuration
    public static class Context {

        @Bean
        public CommandAndQueryHandler handler() {
            return new CommandAndQueryHandler();
        }
    }
}
