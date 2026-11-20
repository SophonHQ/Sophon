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

package org.sophonhq.sophon.messaging.annotation;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sophonhq.sophon.common.annotation.AnnotationUtils;
import org.sophonhq.sophon.messaging.Message;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.util.Optional;

/**
 * Interface describing a handler for specific messages targeting entities of a specific type.
 *
 * @param <T> The type of entity to which the message handler will delegate the actual handling of the message
 * @author Allard Buijze
 * @since axon-3.0
 */
public interface MessageHandlingMember<T> {

    /**
     * Returns the payload type of messages that can be processed by this handler.
     *
     * @return The payload type of messages expected by this handler
     */
    Class<?> payloadType();

    /**
     * Returns a number representing the priority of this handler over other handlers capable of processing the same
     * message.
     * <p>
     * In general, a handler with a higher priority will receive the message before (or instead of) handlers with a
     * lower priority. However, the priority value may not be the only indicator that is used to determine the order of
     * invocation. For instance, a message processor may decide to ignore the priority value if one message handler is a
     * more specific handler of the message than another handler.
     *
     * @return Number indicating the priority of this handler over other handlers
     */
    default int priority() {
        return 0;
    }

    /**
     * Checks if this handler is capable of handling the given {@code message}.
     *
     * @param message The message that is to be handled
     * @return {@code true} if the handler is capable of handling the message, {@code false} otherwise
     */
    boolean canHandle(@NonNull Message<?> message);

    /**
     * Checks if this handler is capable of handling messages with the given {@code payloadType}.
     *
     * @param payloadType The payloadType of a message that is to be handled
     * @return {@code true} if the handler is capable of handling the message with given type, {@code false} otherwise
     */
    default boolean canHandleType(@NonNull Class<?> payloadType) {
        return true;
    }

    /**
     * Checks if this handlers is capable of handling {@link Message} implementations of the given {@code messageType}.
     * <p>
     * It is recommended to suppress the raw type use validation of the {@code messageType} parameter when implementing
     * this method, as usage of this method with a {@code Message} generic would required reflection or casting
     * otherwise.
     *
     * @param messageType the {@link Message}'s type to check if it can be handled by this handler
     * @return {@code true} if this handler can handle the given {@code messageType}, {@code false} otherwise
     */
    @SuppressWarnings("rawtypes")
    boolean canHandleMessageType(@NonNull Class<? extends Message> messageType);

    /**
     * Handles the given {@code message} by invoking the appropriate method on given {@code target}. This may result in
     * an exception if the given target is not capable of handling the message or if an exception is thrown during
     * invocation of the method.
     *
     * @param message The message to handle
     * @param target  The target to handle the message
     * @return The message handling result in case the invocation was successful
     * @throws Exception when there was a problem that prevented invocation of the method or if an exception was thrown
     *                   from the invoked method
     */
    Object handle(@NonNull Message<?> message, @Nullable T target) throws Exception;

    /**
     * Returns the wrapped handler object if its type is an instance of the given {@code handlerType}. For instance, if
     * this method is invoked with {@link java.lang.reflect.Executable} and the message is handled by a method of the
     * target entity, then this method will return the method handle as a {@link java.lang.reflect.Method}.
     *
     * @param handlerType The expected type of the wrapped handler
     * @param <HT>        The wrapped handler type
     * @return An Optional containing the wrapped handler object or an empty Optional if the handler is not an instance
     * of the given handlerType
     */
    <HT> Optional<HT> unwrap(Class<HT> handlerType);

    /**
     * Gets the declaring class of this Message Handling Member.
     *
     * @return the declaring class of this Message Handling Member
     */
    default Class<?> declaringClass() {
        return unwrap(Member.class).map(Member::getDeclaringClass)
                                   .orElseThrow(() -> new UnsupportedOperationException(
                                           "This implementation of MessageHandlingMember does not wrap a "
                                                   + "java.lang.reflect.Member. Please provide a different way of "
                                                   + "getting 'declaringClass' of this MessageHandlingMember."));
    }

    /**
     * Returns the signature of the member. This may be used in logging or exceptions to demarcate the actual class
     * member invoked. If this member does not have a signature, {@code "__unknown__"} is returned.
     *
     * @return the signature of the handling member
     */
    default String signature() {
        return unwrap(Executable.class).map(Executable::toGenericString)
                                       .orElse("__unknown__");
    }

    /**
     * Checks whether the handler carries an annotation of the given {@code annotationType}, either directly or as a
     * meta-annotation on any level.
     *
     * @param annotationType the type of annotation to check for on the handler
     * @return {@code true} if the annotation is present on the handler, {@code false} otherwise
     * @see #annotation(Class)
     */
    default boolean hasAnnotation(@NonNull Class<? extends Annotation> annotationType) {
        return annotation(annotationType).isPresent();
    }

    /**
     * Returns the annotation of the given {@code annotationType} on the handler, if it is present directly or as a
     * meta-annotation on any level.
     * <p>
     * Attribute overrides from meta-annotations are not applied: the annotation is returned as declared. Read the
     * attributes of Sophon handler annotations, and of other annotations meta-annotated with
     * {@link HasHandlerAttributes}, through {@link #attribute(String)} instead, which applies those overrides.
     *
     * @param annotationType the type of annotation to find on the handler
     * @param <A>            the type of annotation to find
     * @return an {@link Optional} containing the annotation, or an empty {@link Optional} if the handler does not carry
     * the annotation
     * @since sophon-2.0.0
     */
    default <A extends Annotation> Optional<A> annotation(@NonNull Class<A> annotationType) {
        return unwrap(Executable.class)
                .flatMap(executable -> AnnotationUtils.findAnnotation(executable, annotationType));
    }

    /**
     * Retrieve a single attributes for the given {@code attributeKey}. If this {@link MessageHandlingMember} does not
     * hold a value referencing the {@code attributeKey}, an {@link Optional#empty()} is returned. Otherwise a non-empty
     * {@link Optional} containing the attribute will be provided.
     * <p>
     * Use this method to read the attribute values of Sophon handler annotations, such as
     * {@link org.sophonhq.sophon.commandhandling.CommandHandler}, and of custom annotations meta-annotated with
     * {@link HasHandlerAttributes}. The key has the format {@code "<SimpleName>.<attribute>"}, where
     * {@code <SimpleName>} is the simple name of the annotation, for example {@code "CommandHandler.commandName"}.
     * Attribute overrides are applied: if a custom annotation uses a handler annotation as a meta-annotation and
     * declares an attribute with the same name, the value of the custom annotation is returned.
     * <p>
     * Annotations without {@link HasHandlerAttributes} add no keys, and neither do annotations without attributes. Use
     * {@link #annotation(Class)} to read those annotations, and {@link #hasAnnotation(Class)} to check for their
     * presence.
     * <p>
     * When using the method, consider checking the {@link org.sophonhq.sophon.messaging.HandlerAttributes} for a list
     * of common handler attributes.
     *
     * @param attributeKey the key to retrieve an attribute for
     * @return a non-empty {@link Optional} of the attribute referencing the given {@code attributeKey}. Otherwise an
     * {@link Optional#empty()} will be returned
     */
    default <R> Optional<R> attribute(String attributeKey) {
        return Optional.empty();
    }
}
