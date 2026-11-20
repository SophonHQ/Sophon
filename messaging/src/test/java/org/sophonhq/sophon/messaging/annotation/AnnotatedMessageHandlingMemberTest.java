/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.sophonhq.sophon.messaging.annotation;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.commandhandling.CommandHandler;
import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.eventhandling.EventHandler;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.messaging.HandlerAttributes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Test class validating the {@link AnnotatedMessageHandlingMember}.
 *
 * @author Steven van Beelen
 */
class AnnotatedMessageHandlingMemberTest {

    private AnnotatedMessageHandlingMember<AnnotatedHandler> testSubject;

    @BeforeEach
    void setUp() {
        try {
            testSubject = new AnnotatedMessageHandlingMember<>(
                    AnnotatedHandler.class.getMethod("handlingMethod", String.class),
                    EventMessage.class,
                    String.class,
                    ClasspathParameterResolverFactory.forClass(AnnotatedHandler.class)
            );
        } catch (NoSuchMethodException e) {
            fail(e.getMessage());
        }
    }

    @Test
    void canHandleMessageType() {
        assertThat(testSubject.canHandleMessageType(EventMessage.class)).isTrue();
        assertThat(testSubject.canHandleMessageType(CommandMessage.class)).isFalse();
    }

    @Test
    void hasAnnotation() {
        assertThat(testSubject.hasAnnotation(EventHandler.class)).isTrue();
        assertThat(testSubject.hasAnnotation(CommandHandler.class)).isFalse();
    }

    @Test
    void hasAnnotationDetectsMarkerAnnotation() {
        assertThat(testSubject.hasAnnotation(MarkerAnnotation.class)).isTrue();
    }

    @Test
    void annotationReturnsDirectlyPresentAnnotation() {
        assertThat(testSubject.annotation(EventHandler.class)).isPresent();
    }

    @Test
    void annotationReturnsMetaAnnotation() {
        Optional<MessageHandler> result = testSubject.annotation(MessageHandler.class);

        assertThat(result).hasValueSatisfying(
                annotation -> assertThat(annotation.messageType()).isEqualTo(EventMessage.class)
        );
    }

    @Test
    void annotationReturnsAnnotationWithoutHandlerAttributes() {
        Optional<ThirdPartyAnnotation> result = testSubject.annotation(ThirdPartyAnnotation.class);

        assertThat(result).hasValueSatisfying(annotation -> assertThat(annotation.value()).isEqualTo("third-party"));
        assertThat(testSubject.attribute("ThirdPartyAnnotation.thirdPartyAnnotation")).isEmpty();
    }

    @Test
    void annotationReturnsEmptyOptionalForAbsentAnnotation() {
        assertThat(testSubject.annotation(CommandHandler.class)).isEmpty();
    }

    @Test
    void attributeReturnsNonEmptyOptionalForMatchingAttributeKey() {
        Optional<Object> resultMessageType = testSubject.attribute(HandlerAttributes.MESSAGE_TYPE);
        Optional<Object> resultPayloadType = testSubject.attribute(HandlerAttributes.PAYLOAD_TYPE);

        assertThat(resultMessageType).isPresent();
        assertThat(resultMessageType).contains(EventMessage.class);

        assertThat(resultPayloadType).isPresent();
        assertThat(resultPayloadType).hasValue(Object.class);
    }

    @SuppressWarnings("unused")
    private static class AnnotatedHandler {

        @EventHandler
        @MarkerAnnotation
        @ThirdPartyAnnotation("third-party")
        public void handlingMethod(String event) {

        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface MarkerAnnotation {

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface ThirdPartyAnnotation {

        String value();
    }
}