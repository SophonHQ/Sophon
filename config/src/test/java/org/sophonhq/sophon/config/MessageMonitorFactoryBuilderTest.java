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

package org.sophonhq.sophon.config;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.monitoring.MessageMonitor;

import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMonitorFactoryBuilderTest {

    private MessageMonitor<Message<?>> defaultMonitor = (message) -> null;

    private static class A {}
    private static class B {}
    private static class C {}
    private static class D {}

    @Test
    void validateRulesWithoutTypeHierarchy() {
        MessageMonitor<Message<?>> aMonitor = (message) -> null;
        MessageMonitor<Message<?>> bMonitor = (message) -> null;
        MessageMonitor<Message<?>> cMonitor = (message) -> null;

        BiFunction<Class<?>, String, MessageMonitor<Message<?>>> factory = new MessageMonitorFactoryBuilder()
                .add((conf, type, name) -> defaultMonitor)
                .add(A.class, (conf, type, name) -> aMonitor)
                .add(B.class, (conf, type, name) -> bMonitor)
                .add(C.class, "c", (conf, type, name) -> cMonitor)
                .build(null);

        // For an non-configured type, expect the default monitor
        assertThat(factory.apply(D.class, "any")).isEqualTo(defaultMonitor);

        // For a configured type, expect the configured monitor
        assertThat(factory.apply(A.class, "any")).isEqualTo(aMonitor);
        assertThat(factory.apply(B.class, "any")).isEqualTo(bMonitor);

        // For a configured name and type, expect the configured monitor only if both match
        // If no match is found, fall back to matching on type only
        assertThat(factory.apply(C.class, "c")).isEqualTo(cMonitor);
        assertThat(factory.apply(C.class, "any")).isEqualTo(defaultMonitor);
        assertThat(factory.apply(D.class, "c")).isEqualTo(defaultMonitor);
        assertThat(factory.apply(A.class, "c")).isEqualTo(aMonitor);
    }

    private interface I {}
    private static class K {}
    private static class L extends K {}
    private static class M extends L {}
    private static class N extends M implements I {}

    private MessageMonitor<Message<?>> kMonitor = (message) -> null;
    private MessageMonitor<Message<?>> mMonitor = (message) -> null;
    private MessageMonitor<Message<?>> iMonitor = (message) -> null;

    @Test
    void validateTypeHierarchy() {
        BiFunction<Class<?>, String, MessageMonitor<Message<?>>> factory = new MessageMonitorFactoryBuilder()
                .add((conf, type, name) -> defaultMonitor)
                .add(K.class, (conf, type, name) -> kMonitor)
                .add(M.class, (conf, type, name) -> mMonitor)
                .add(I.class, (conf, type, name) -> iMonitor)
                .build(null);

        validateTypeHierarchyResults(factory);

        // Repeat the same test, but with the 'add' statements reversed
        factory = new MessageMonitorFactoryBuilder()
                .add((conf, type, name) -> defaultMonitor)
                .add(I.class, (conf, type, name) -> iMonitor)
                .add(M.class, (conf, type, name) -> mMonitor)
                .add(K.class, (conf, type, name) -> kMonitor)
                .build(null);

        validateTypeHierarchyResults(factory);
    }

    private void validateTypeHierarchyResults(BiFunction<Class<?>, String, MessageMonitor<Message<?>>> factory) {
        // For a configured type, expect the configured monitor
        assertThat(factory.apply(K.class, "any")).isEqualTo(kMonitor);
        assertThat(factory.apply(M.class, "any")).isEqualTo(mMonitor);
        assertThat(factory.apply(I.class, "any")).isEqualTo(iMonitor);

        // For a non-configured type, expect the closest parent in the class hierarchy, or the normal default
        assertThat(factory.apply(Object.class, "any")).isEqualTo(defaultMonitor);
        assertThat(factory.apply(L.class, "any")).isEqualTo(kMonitor);

        // For a class that extends a base class and implements an interface, the choice is deterministic, but not
        // user controllable
        assertThat(factory.apply(N.class, "any")).isEqualTo(iMonitor);
    }

    @Test
    void validateMultipleClassesForSameName() {
        BiFunction<Class<?>, String, MessageMonitor<Message<?>>> factory = new MessageMonitorFactoryBuilder()
                .add((conf, type, name) -> defaultMonitor)
                .add(K.class, "name", (conf, type, name) -> kMonitor)
                .add(M.class, "name", (conf, type, name) -> mMonitor)
                .build(null);

        // For a configured type and name combination, expect the configured monitor
        assertThat(factory.apply(K.class, "name")).isEqualTo(kMonitor);
        assertThat(factory.apply(M.class, "name")).isEqualTo(mMonitor);

        // For a non-configured type, expect the closest parent in the class hierarchy, or the normal default
        assertThat(factory.apply(Object.class, "name")).isEqualTo(defaultMonitor);
        assertThat(factory.apply(L.class, "name")).isEqualTo(kMonitor);
        assertThat(factory.apply(N.class, "name")).isEqualTo(mMonitor);
    }
}
