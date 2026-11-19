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

package org.sophonhq.sophon.test.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.commandhandling.GenericCommandMessage;
import org.sophonhq.sophon.messaging.MessageHandler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author Allard Buijze
 */
class RecordingCommandBusTest {

    private RecordingCommandBus testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new RecordingCommandBus();
    }

    @Test
    void publishCommand() {
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("First"));
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Second"),
                             (commandMessage, commandResultMessage) -> {
                                 if (commandResultMessage.isExceptional()) {
                                     fail("Didn't expect handling to fail");
                                 }
                                 assertThat(commandResultMessage.getPayload())
                                         .as("Expected default callback behavior to invoke onResult(null)")
                                         .isNull();
                             });
        List<CommandMessage<?>> actual = testSubject.getDispatchedCommands();
        assertThat(actual).hasSize(2);
        assertThat(actual.get(0).getPayload()).isEqualTo("First");
        assertThat(actual.get(1).getPayload()).isEqualTo("Second");
    }

    @Test
    void publishCommandWithCallbackBehavior() {
        testSubject.setCallbackBehavior((commandPayload, commandMetaData) -> "callbackResult");
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("First"));
        testSubject.dispatch(GenericCommandMessage.asCommandMessage("Second"),
                             (commandMessage, commandResultMessage) -> {
                                 if (commandResultMessage.isExceptional()) {
                                    fail("Didn't expect handling to fail");
                                 }
                                 assertThat(commandResultMessage.getPayload()).isEqualTo("callbackResult");
                             });
        List<CommandMessage<?>> actual = testSubject.getDispatchedCommands();
        assertThat(actual).hasSize(2);
        assertThat(actual.get(0).getPayload()).isEqualTo("First");
        assertThat(actual.get(1).getPayload()).isEqualTo("Second");
    }

    @Test
    void registerHandler() {
        MessageHandler<? super CommandMessage<?>> handler = command -> {
            fail("Did not expect handler to be invoked");
            return null;
        };
        testSubject.subscribe(String.class.getName(), handler);
        assertThat(testSubject.isSubscribed(handler)).isTrue();
        assertThat(testSubject.isSubscribed(String.class.getName(), handler)).isTrue();
    }
}
