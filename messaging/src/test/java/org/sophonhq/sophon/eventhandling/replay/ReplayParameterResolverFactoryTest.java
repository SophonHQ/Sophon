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
import org.sophonhq.sophon.eventhandling.AnnotationEventHandlerAdapter;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.eventhandling.GenericTrackedEventMessage;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.ReplayStatus;
import org.sophonhq.sophon.eventhandling.ReplayToken;
import org.sophonhq.sophon.eventhandling.TrackingToken;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sophonhq.sophon.eventhandling.GenericEventMessage.asEventMessage;

class ReplayParameterResolverFactoryTest {

    private SomeHandler handler;
    private AnnotationEventHandlerAdapter testSubject;
    private ReplayToken replayToken;
    private GlobalSequenceTrackingToken regularToken;

    @BeforeEach
    void setUp() {
        handler = new SomeHandler();
        testSubject = new AnnotationEventHandlerAdapter(handler);
        regularToken = new GlobalSequenceTrackingToken(1L);
        replayToken = new ReplayToken(regularToken);
    }

    @Test
    void invokeWithReplayTokens() throws Exception {
        GenericTrackedEventMessage<Object> replayEvent = new GenericTrackedEventMessage<>(replayToken,
                                                                                          asEventMessage(1L));
        GenericTrackedEventMessage<Object> liveEvent = new GenericTrackedEventMessage<>(regularToken,
                                                                                        asEventMessage(2L));
        assertThat(testSubject.canHandle(replayEvent)).isTrue();
        assertThat(testSubject.canHandle(liveEvent)).isTrue();
        testSubject.handle(replayEvent);
        testSubject.handle(liveEvent);

        assertThat(handler.receivedLongs).containsExactlyElementsOf(asList(1L, 2L));
        assertThat(handler.receivedInReplay).containsExactlyElementsOf(singletonList(1L));
    }

    private static class SomeHandler {

        private final List<Long> receivedLongs = new ArrayList<>();
        private final List<Long> receivedInReplay = new ArrayList<>();

        @EventHandler
        public void handle(Long event, TrackingToken token, ReplayStatus replayStatus) {
            assertThat(token instanceof ReplayToken).isFalse();
            receivedLongs.add(event);
            if (replayStatus.isReplay()) {
                receivedInReplay.add(event);
            }
        }
    }
}
