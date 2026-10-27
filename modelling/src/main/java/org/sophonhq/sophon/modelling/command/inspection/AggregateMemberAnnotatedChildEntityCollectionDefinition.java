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

package org.sophonhq.sophon.modelling.command.inspection;

import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.common.ReflectionUtils;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.common.property.Property;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.modelling.command.AggregateMember;
import org.sophonhq.sophon.modelling.command.ForwardingMode;

import java.lang.reflect.Member;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static org.sophonhq.sophon.common.ReflectionUtils.resolveMemberGenericType;

/**
 * Implementation of a {@link AbstractChildEntityDefinition} that is used to detect Collections of entities (member type
 * assignable to {@link Iterable}) annotated with {@link AggregateMember}. If such a field or method is found a {@link
 * ChildEntity} is created that delegates to the entities in the annotated collection.
 *
 * @author Allard Buijze
 * @since axon-3.0
 */
public class AggregateMemberAnnotatedChildEntityCollectionDefinition extends AbstractChildEntityDefinition {

    @Override
    protected boolean isMemberTypeSupported(Member member) {
        try {
            return Iterable.class.isAssignableFrom(ReflectionUtils.getMemberValueType(member));
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    protected <T> EntityModel<Object> extractChildEntityModel(EntityModel<T> declaringEntity,
                                                              Map<String, Object> attributes,
                                                              Member member) {
        Class<?> entityType = (Class<?>) attributes.get("type");
        if (Void.class.equals(entityType)) {
            entityType = resolveMemberGenericType(member, 0).orElseThrow(
                    () -> new SophonConfigurationException(format(
                            "Unable to resolve entity type of member [%s]. Please provide type explicitly in @AggregateMember annotation.",
                            ReflectionUtils.getMemberGenericString(member)
                    )));
        }

        if (entityType.isInterface()) {
            throw new SophonConfigurationException(
                    "Aggregate Member type should be a concrete implementation instead of [" + entityType + "]."
            );
        }
        return declaringEntity.modelOf(entityType);
    }

    @Override
    protected <T> Object resolveCommandTarget(CommandMessage<?> msg,
                                              T parent,
                                              Member member,
                                              EntityModel<Object> childEntityModel) {
        Map<String, Property<Object>> commandHandlerRoutingKeys =
                extractCommandHandlerRoutingKeys(member, childEntityModel);

        Object routingValue = commandHandlerRoutingKeys.get(msg.getCommandName())
                                                       .getValue(msg.getPayload());
        Iterable<?> memberValue = ReflectionUtils.getMemberValue(member, parent);

        return StreamSupport.stream(memberValue.spliterator(), false)
                            .filter(i -> Objects.equals(routingValue, childEntityModel.getIdentifier(i)))
                            .findFirst()
                            .orElse(null);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> Stream<Object> resolveEventTargets(EventMessage message,
                                                     T parentEntity,
                                                     Member member,
                                                     ForwardingMode eventForwardingMode) {
        Iterable<Object> memberValue = ReflectionUtils.getMemberValue(member, parentEntity);
        return memberValue == null
                ? Stream.empty()
                : eventForwardingMode.filterCandidates(message, StreamSupport.stream(memberValue.spliterator(), false));
    }
}
