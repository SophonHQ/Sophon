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

package org.sophonhq.sophon.eventhandling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.messaging.annotation.ParameterResolver;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingTokenParameterResolverFactoryTest {

    private Method method;
    private TrackingTokenParameterResolverFactory testSubject;

    @BeforeEach
    void setUp() throws Exception {
        method = getClass().getDeclaredMethod("method1", Object.class, TrackingToken.class);
        testSubject = new TrackingTokenParameterResolverFactory();
    }

    @Test
    void createInstance() {
        assertThat(testSubject.createInstance(method, method.getParameters(), 0)).isNull();
        ParameterResolver<?> resolver = testSubject.createInstance(method, method.getParameters(), 1);

        assertThat(resolver).isNotNull();
        GenericEventMessage<String> message = new GenericEventMessage<>("test");
        assertThat(resolver.matches(message)).isFalse();
        GlobalSequenceTrackingToken trackingToken = new GlobalSequenceTrackingToken(1L);
        GenericTrackedEventMessage<String> trackedEventMessage = new GenericTrackedEventMessage<>(trackingToken,
                                                                                       message);
        assertThat(resolver.matches(trackedEventMessage)).isTrue();
        assertThat(resolver.resolveParameterValue(trackedEventMessage)).isSameAs(trackingToken);
    }

    @SuppressWarnings("unused")
    private void method1(Object param1, TrackingToken token) {

    }

}
