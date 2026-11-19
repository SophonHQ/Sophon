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

package org.sophonhq.sophon.commandhandling.distributed.commandfilter;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.commandhandling.GenericCommandMessage;
import org.sophonhq.sophon.messaging.GenericMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the filtering process of the {@link AcceptAll}, {@link DenyAll}, {@link CommandNameFilter} and
 * {@link DenyCommandNameFilter}.
 *
 * @author Koen Lavooij
 */
class CommandFilterTest {

    @Test
    void acceptAll() {
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(new Object());

        assertThat(AcceptAll.INSTANCE.matches(testCommand)).isTrue();
        assertThat(AcceptAll.INSTANCE.negate().matches(testCommand)).isFalse();
        assertThat(AcceptAll.INSTANCE.or(DenyAll.INSTANCE).matches(testCommand)).isTrue();
        assertThat(AcceptAll.INSTANCE.and(DenyAll.INSTANCE).matches(testCommand)).isFalse();
    }

    @Test
    void denyAll() {
        CommandMessage<Object> testCommand = new GenericCommandMessage<>(new Object());

        assertThat(DenyAll.INSTANCE.matches(testCommand)).isFalse();
        assertThat(DenyAll.INSTANCE.negate().matches(testCommand)).isTrue();
        assertThat(DenyAll.INSTANCE.or(AcceptAll.INSTANCE).matches(testCommand)).isTrue();
        assertThat(DenyAll.INSTANCE.and(AcceptAll.INSTANCE).matches(testCommand)).isFalse();
    }

    @Test
    void commandNameFilter() {
        CommandMessage<Object> testCommand =
                new GenericCommandMessage<>(new GenericMessage<>(new Object()), "acceptable");

        CommandNameFilter filterAcceptable = new CommandNameFilter("acceptable");
        CommandNameFilter filterOther = new CommandNameFilter("other");

        assertThat(filterAcceptable.matches(testCommand)).isTrue();
        assertThat(filterAcceptable.negate().matches(testCommand)).isFalse();

        assertThat(filterOther.matches(testCommand)).isFalse();
        assertThat(filterOther.negate().matches(testCommand)).isTrue();

        assertThat(filterOther.or(filterAcceptable).matches(testCommand)).isTrue();
        assertThat(filterAcceptable.or(filterOther).matches(testCommand)).isTrue();
        assertThat(filterOther.and(filterAcceptable).matches(testCommand)).isFalse();
        assertThat(filterAcceptable.and(filterOther).matches(testCommand)).isFalse();

        assertThat(filterOther.or(DenyAll.INSTANCE).matches(testCommand)).isFalse();
        assertThat(filterOther.or(AcceptAll.INSTANCE).matches(testCommand)).isTrue();
        assertThat(filterOther.and(DenyAll.INSTANCE).matches(testCommand)).isFalse();
        assertThat(filterOther.and(AcceptAll.INSTANCE).matches(testCommand)).isFalse();

        assertThat(filterAcceptable.or(DenyAll.INSTANCE).matches(testCommand)).isTrue();
        assertThat(filterAcceptable.or(AcceptAll.INSTANCE).matches(testCommand)).isTrue();
        assertThat(filterAcceptable.and(DenyAll.INSTANCE).matches(testCommand)).isFalse();
        assertThat(filterAcceptable.and(AcceptAll.INSTANCE).matches(testCommand)).isTrue();
    }

    @Test
    void denyCommandNameFilter() {
        CommandMessage<Object> testCommand =
                new GenericCommandMessage<>(new GenericMessage<>(new Object()), "acceptable");

        DenyCommandNameFilter filterAcceptable = new DenyCommandNameFilter("acceptable");
        DenyCommandNameFilter filterOther = new DenyCommandNameFilter("other");

        assertThat(filterAcceptable.matches(testCommand)).isFalse();
        assertThat(filterAcceptable.negate().matches(testCommand)).isTrue();

        assertThat(filterOther.matches(testCommand)).isTrue();
        assertThat(filterOther.negate().matches(testCommand)).isFalse();

        assertThat(filterOther.or(filterAcceptable).matches(testCommand)).isTrue();
        assertThat(filterAcceptable.or(filterOther).matches(testCommand)).isTrue();
        assertThat(filterOther.and(filterAcceptable).matches(testCommand)).isFalse();
        assertThat(filterAcceptable.and(filterOther).matches(testCommand)).isFalse();

        assertThat(filterOther.or(DenyAll.INSTANCE).matches(testCommand)).isTrue();
        assertThat(filterOther.or(AcceptAll.INSTANCE).matches(testCommand)).isTrue();
        assertThat(filterOther.and(DenyAll.INSTANCE).matches(testCommand)).isFalse();
        assertThat(filterOther.and(AcceptAll.INSTANCE).matches(testCommand)).isTrue();

        assertThat(filterAcceptable.or(DenyAll.INSTANCE).matches(testCommand)).isFalse();
        assertThat(filterAcceptable.or(AcceptAll.INSTANCE).matches(testCommand)).isTrue();
        assertThat(filterAcceptable.and(DenyAll.INSTANCE).matches(testCommand)).isFalse();
        assertThat(filterAcceptable.and(AcceptAll.INSTANCE).matches(testCommand)).isFalse();
    }
}
