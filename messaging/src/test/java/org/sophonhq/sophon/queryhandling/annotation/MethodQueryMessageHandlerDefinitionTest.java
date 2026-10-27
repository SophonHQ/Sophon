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

package org.sophonhq.sophon.queryhandling.annotation;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.messaging.annotation.AnnotatedMessageHandlingMemberDefinition;
import org.sophonhq.sophon.messaging.annotation.ClasspathParameterResolverFactory;
import org.sophonhq.sophon.messaging.annotation.MessageHandlingMember;
import org.sophonhq.sophon.messaging.annotation.ParameterResolverFactory;
import org.sophonhq.sophon.messaging.annotation.UnsupportedHandlerException;
import org.sophonhq.sophon.messaging.responsetypes.ResponseTypes;
import org.sophonhq.sophon.queryhandling.GenericQueryMessage;
import org.sophonhq.sophon.queryhandling.QueryHandler;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test whether the {@link MethodQueryMessageHandlerDefinition} correctly deals with return types, as well as for
 * example {@link java.util.concurrent.Future} and {@link Optional} which contain a generic type.
 *
 * @author Allard Buijze
 */
class MethodQueryMessageHandlerDefinitionTest {

    private MethodQueryMessageHandlerDefinition testSubject;
    private AnnotatedMessageHandlingMemberDefinition handlerDefinition;
    private ParameterResolverFactory parameterResolver;

    @BeforeEach
    void setUp() {
        parameterResolver = ClasspathParameterResolverFactory.forClass(getClass());
        testSubject = new MethodQueryMessageHandlerDefinition();
        handlerDefinition = new AnnotatedMessageHandlingMemberDefinition();
    }

    @Test
    void voidNotAcceptedAsReturnType() {
        assertThrows(UnsupportedHandlerException.class, () -> messageHandler("illegalQueryResponseType"));
    }

    @Test
    void futureResponseTypeUnwrapped() {
        QueryHandlingMember<?> handler = messageHandler("futureReturnType");
        assertEquals(String.class, handler.getResultType());
    }

    @Test
    void optionalResponseTypeUnwrapped() throws Exception {
        QueryHandlingMember<MethodQueryMessageHandlerDefinitionTest> handler = messageHandler("optionalReturnType");
        assertEquals(String.class, handler.getResultType());

        GenericQueryMessage<String, String> message =
                new GenericQueryMessage<>("mock", ResponseTypes.instanceOf(String.class));

        assertTrue(handler.canHandle(message));

        Object invocationResult = handler.handle(message, this);
        assertNull(invocationResult);
    }


    @Test
    void unspecifiedOptionalResponseTypeUnwrapped() {
        QueryHandlingMember<?> handler = messageHandler("unspecifiedOptionalType");
        assertEquals(Object.class, handler.getResultType());
    }

    @Test
    void wildcardOptionalResponseTypeUnwrapped() {
        QueryHandlingMember<?> handler = messageHandler("wildcardOptionalType");
        assertEquals(Object.class, handler.getResultType());
    }

    @Test
    void upperBoundWildcardOptionalResponseTypeUnwrapped() {
        QueryHandlingMember<?> handler = messageHandler("upperBoundWildcardOptionalType");
        assertEquals(CharSequence.class, handler.getResultType());
    }

    private <R> QueryHandlingMember<R> messageHandler(String methodName) {
        try {
            MessageHandlingMember<MethodQueryMessageHandlerDefinitionTest> handler = handlerDefinition.createHandler(
                    MethodQueryMessageHandlerDefinitionTest.class,
                    MethodQueryMessageHandlerDefinitionTest.class.getDeclaredMethod(methodName, String.class),
                    parameterResolver).orElseThrow(IllegalArgumentException::new);
            //noinspection unchecked
            return testSubject.wrapHandler(handler)
                              .unwrap(QueryHandlingMember.class)
                              .orElseThrow(() -> new RuntimeException("Method not recognized as Query Definition"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Method used to build query handler does not exist", e);
        }
    }

    @SuppressWarnings("unused")
    @QueryHandler
    private void illegalQueryResponseType(String string) {
    }

    @SuppressWarnings("unused")
    @QueryHandler
    private CompletableFuture<String> futureReturnType(String string) {
        return null;
    }

    @SuppressWarnings("unused")
    @QueryHandler
    private Optional<String> optionalReturnType(String string) {
        return Optional.empty();
    }

    @SuppressWarnings({"unused", "rawtypes"})
    @QueryHandler
    private Optional unspecifiedOptionalType(String string) {
        return Optional.empty();
    }

    @SuppressWarnings("unused")
    @QueryHandler
    private Optional<?> wildcardOptionalType(String string) {
        return Optional.empty();
    }

    @SuppressWarnings("unused")
    @QueryHandler
    private Optional<? extends CharSequence> upperBoundWildcardOptionalType(String string) {
        return Optional.empty();
    }
}
