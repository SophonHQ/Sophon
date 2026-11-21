/*
 * Copyright (c) 2010-2018. Axon Framework
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

package org.sophonhq.sophon.commandhandling.distributed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.GenericCommandMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.commandhandling.GenericCommandResultMessage.asCommandResultMessage;

class CommandCallbackRepositoryTest {
    private int successCounter;
    private int failCounter;

    @BeforeEach
    void reset() {
        successCounter = 0;
        failCounter = 0;
    }

    @Test
    void callback() {
        CommandCallbackRepository<Object> repository = new CommandCallbackRepository<>();
        CommandCallbackWrapper<Object, Object, Object> commandCallbackWrapper = createWrapper("A");
        repository.store("A", commandCallbackWrapper);

        assertThat(repository.callbacks()).hasSize(1);

        CommandCallbackWrapper<Object, Object, Object> fetchedCallback = repository.fetchAndRemove("A");
        assertThat(fetchedCallback).isEqualTo(commandCallbackWrapper);
        assertThat(repository.callbacks()).hasSize(0);

        fetchedCallback.reportResult(asCommandResultMessage(new Object()));
        assertThat(successCounter).isOne();
    }


    @Test
    void overwriteCallback() {
        CommandCallbackRepository<Object> repository = new CommandCallbackRepository<>();
        CommandCallbackWrapper<Object, Object, Object> commandCallbackWrapper = createWrapper("A");
        repository.store("A", commandCallbackWrapper);

        assertThat(repository.callbacks()).hasSize(1);

        CommandCallbackWrapper<Object, Object, Object> commandCallbackWrapper2 = createWrapper("A");
        repository.store("A", commandCallbackWrapper2);

        assertThat(failCounter).isOne();
        assertThat(repository.callbacks()).containsValue(commandCallbackWrapper2);
        assertThat(repository.callbacks()).doesNotContainValue(commandCallbackWrapper);
    }

    @Test
    void cancelCallbacks() {
        CommandCallbackRepository<Object> repository = new CommandCallbackRepository<>();
        repository.store("A", createWrapper("A"));
        repository.store("B", createWrapper("A"));
        repository.store("C", createWrapper("A"));
        repository.store("D", createWrapper("B"));

        assertThat(repository.callbacks()).hasSize(4);

        repository.cancelCallbacksForChannel("C");
        assertThat(repository.callbacks()).hasSize(4);

        repository.cancelCallbacksForChannel("A");
        assertThat(repository.callbacks()).hasSize(1);

        assertThat(failCounter).isEqualTo(3);
        assertThat(successCounter).isZero();
    }

    private CommandCallbackWrapper<Object, Object, Object> createWrapper(Object sessionId) {
        return new CommandCallbackWrapper<>(
                sessionId, new GenericCommandMessage<>(new Object()),
                (commandMessage, commandResultMessage) -> {
                    if (commandResultMessage.isExceptional()) {
                        failCounter++;
                    } else {
                        successCounter++;
                    }
                }
        );
    }
}
