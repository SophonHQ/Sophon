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

package org.sophonhq.sophon.eventhandling.replay;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.eventhandling.AllowReplay;
import org.sophonhq.sophon.eventhandling.AnnotationEventHandlerAdapter;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.eventhandling.GenericTrackedEventMessage;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.ReplayToken;
import org.sophonhq.sophon.eventhandling.TrackingToken;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;

class ReplayAwareMessageHandlerWrapperTest {

    private SomeHandler handler;
    private AnnotationEventHandlerAdapter testSubject;
    private ReplayToken replayToken;
    private GlobalSequenceTrackingToken regularToken;
    private ReplayPreventingHandler disallowingHandler;
    private AnnotationEventHandlerAdapter testDisallowingSubject;
    private SomeMethodHandler methodHandler;
    private AnnotationEventHandlerAdapter testMethodSubject;

    @BeforeEach
    void setUp() {
        handler = new SomeHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler);

        methodHandler = new SomeMethodHandler();
        testMethodSubject = new AnnotationEventHandlerAdapter(methodHandler);

        disallowingHandler = new ReplayPreventingHandler();
        testDisallowingSubject = new AnnotationEventHandlerAdapter(disallowingHandler);

        regularToken = new GlobalSequenceTrackingToken(1L);
        replayToken = new ReplayToken(regularToken);
    }

    @Test
    void invokeWithReplayTokens() throws Exception {
        GenericTrackedEventMessage<Object> stringEvent = new GenericTrackedEventMessage<>(replayToken,
                                                                                          asEventMessage("1"));
        GenericTrackedEventMessage<Object> longEvent = new GenericTrackedEventMessage<>(replayToken,
                                                                                        asEventMessage(1L));
        assertThat(testSubject.canHandle(stringEvent)).isTrue();
        assertThat(testMethodSubject.canHandle(stringEvent)).isTrue();
        assertThat(testSubject.canHandle(longEvent)).isTrue();
        assertThat(testMethodSubject.canHandle(longEvent)).isTrue();
        testSubject.handle(stringEvent);
        testMethodSubject.handle(stringEvent);
        testSubject.handle(longEvent);
        testMethodSubject.handle(longEvent);

        assertThat(handler.receivedLongs).isEmpty();
        assertThat(methodHandler.receivedLongs).isEmpty();
        assertThat(handler.receivedStrings).isNotEmpty();
        assertThat(methodHandler.receivedStrings).isNotEmpty();

        assertThat(testSubject.supportsReset()).isTrue();
        assertThat(testMethodSubject.supportsReset()).isTrue();
        assertThat(testDisallowingSubject.supportsReset()).isFalse();
    }

    @AllowReplay(false)
    private static class SomeHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();

        @AllowReplay
        @EventHandler
        public void handle(String event, TrackingToken token) {
            assertThat(token instanceof ReplayToken).isFalse();
            receivedStrings.add(event);
        }

        @EventHandler()
        public void handle(Long event, TrackingToken token) {
            assertThat(token instanceof ReplayToken).isFalse();
            receivedLongs.add(event);
        }
    }

    private static class SomeMethodHandler {

        private final List<String> receivedStrings = new ArrayList<>();
        private final List<Long> receivedLongs = new ArrayList<>();

        @AllowReplay
        @EventHandler
        public void handle(String event, TrackingToken token) {
            assertThat(token instanceof ReplayToken).isFalse();
            receivedStrings.add(event);
        }

        @EventHandler
        @AllowReplay(false)
        public void handle(Long event, TrackingToken token) {
            assertThat(token instanceof ReplayToken).isFalse();
            receivedLongs.add(event);
        }
    }

    @AllowReplay(false)
    private static class ReplayPreventingHandler {

        @EventHandler
        public void handle(String event, TrackingToken token) {
            assertThat(token instanceof ReplayToken).isFalse();
        }

        @EventHandler
        public void handle(Long event, TrackingToken token) {
            assertThat(token instanceof ReplayToken).isFalse();
        }
    }
}
