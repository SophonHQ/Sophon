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

package org.sophonhq.sophon.commandhandling;

import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.LinkedList;

import static org.assertj.core.api.Assertions.assertThat;

class WrappedCommandCallbackTest {

    @Test
    void wrappedCallbackShouldCallBothInOrder() {
        LinkedList<String> calls = new LinkedList<>();

        CommandCallback<Object, Object> callbackOne = (message, result) -> calls.add("one");
        CommandCallback<Object, Object> callbackTwo = (message, result) -> calls.add("two");

        CommandCallback<Object, Object> wrappedCallback = callbackOne.wrap(callbackTwo);
        wrappedCallback.onResult(Mockito.mock(CommandMessage.class), Mockito.mock(CommandResultMessage.class));

        // Assert two was called first, since that is the delegate
        assertThat(calls.get(0)).isEqualTo("two");
        assertThat(calls.get(1)).isEqualTo("one");
    }

    @Test
    void wrapWithNullShouldRetainOriginal() {
        CommandCallback<Object, Object> original = (message, result) -> {};
        CommandCallback<Object, Object> wrappedWithNull = original.wrap(null);


        assertThat(wrappedWithNull).isSameAs(original);
    }
}
