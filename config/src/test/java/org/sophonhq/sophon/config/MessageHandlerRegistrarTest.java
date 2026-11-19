/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.sophonhq.sophon.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.sophonhq.sophon.common.Registration;
import org.sophonhq.sophon.common.SophonConfigurationException;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates the internals of the {@link MessageHandlerRegistrar}.
 *
 * @author Steven van Beelen
 */
@ExtendWith(MockitoExtension.class)
class MessageHandlerRegistrarTest {

    private static final Registration TEST_REGISTRATION_IMPLEMENTATION = () -> false;

    @Test
    void startThrowsSophonConfigurationExceptionForCreatingNullMessageHandler(@Mock Configuration config) {
        MessageHandlerRegistrar testSubject = new MessageHandlerRegistrar(
                () -> config, c -> null, (c, msgHandler) -> TEST_REGISTRATION_IMPLEMENTATION
        );

        assertThatThrownBy(testSubject::start).isInstanceOf(SophonConfigurationException.class);
    }

    @Test
    void startRegistersCreatedMessageHandler(@Mock Configuration config) {
        AtomicBoolean isCreated = new AtomicBoolean(false);
        AtomicBoolean isRegistered = new AtomicBoolean(false);

        MessageHandlerRegistrar testSubject = new MessageHandlerRegistrar(
                () -> config, c -> new SomeMessageHandler(isCreated),
                (c, msgHandler) -> {
                    isRegistered.set(true);
                    return TEST_REGISTRATION_IMPLEMENTATION;
                }
        );

        testSubject.start();

        assertThat(isCreated.get()).isTrue();
        assertThat(isRegistered.get()).isTrue();
    }

    @Test
    void shutdownCancelsMessageHandlerRegistration(@Mock Configuration config) {
        AtomicBoolean isCanceled = new AtomicBoolean(false);

        MessageHandlerRegistrar testSubject = new MessageHandlerRegistrar(
                () -> config, c -> new SomeMessageHandler(),
                (c, msgHandler) -> () -> {
                    isCanceled.set(true);
                    return false;
                }
        );
        testSubject.start();

        testSubject.shutdown();

        assertThat(isCanceled.get()).isTrue();
    }

    @Test
    void shutdownDoesNotThrowExceptionsIfWhenTheRegistrarHasNotStartedYet(@Mock Configuration config) {
        AtomicBoolean isCanceled = new AtomicBoolean(false);

        MessageHandlerRegistrar testSubject = new MessageHandlerRegistrar(
                () -> config,
                c -> new SomeMessageHandler(),
                (c, msgHandler) -> () -> {
                    isCanceled.set(true);
                    return false;
                }
        );

        assertThatNoException().isThrownBy(testSubject::shutdown);
        assertThat(isCanceled.get()).isFalse();
    }

    private static class SomeMessageHandler {

        private SomeMessageHandler() {
            // No-arg constructor
        }

        private SomeMessageHandler(AtomicBoolean isCreated) {
            isCreated.set(true);
        }
    }
}