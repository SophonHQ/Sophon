/*
 * Copyright (c) 2026. SophonHQ
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

package org.sophonhq.sophon.modelling.command;

import org.jspecify.annotations.NonNull;

import java.util.function.Function;

/**
 * Converts the {@link String} representation of an aggregate identifier into the identifier of a JPA-mapped aggregate,
 * as required by {@link GenericJpaRepository} to look up the aggregate through the {@code EntityManager}.
 * <p>
 * When a component of this type is registered with the Sophon {@code Configuration}, or defined as a bean in a Spring
 * application context, it is used by every {@link GenericJpaRepository} that Sophon configures for a JPA-mapped
 * aggregate. A {@code GenericJpaRepository} configured by the user is not affected.
 *
 * @since sophon-1.1.0
 */
@FunctionalInterface
public interface AggregateIdentifierConverter {

    /**
     * Returns a converter that returns the {@link String} representation of an aggregate identifier unchanged. This is
     * the converter used when none is configured, and suits aggregates with a {@code String} identifier.
     *
     * @return a converter returning the given aggregate identifier unchanged
     */
    static AggregateIdentifierConverter identity() {
        return (aggregateType, aggregateIdentifier) -> aggregateIdentifier;
    }

    /**
     * Converts the given {@code aggregateIdentifier} into the identifier of an aggregate of the given
     * {@code aggregateType}.
     *
     * @param aggregateType       the type of aggregate the identifier belongs to
     * @param aggregateIdentifier the {@link String} representation of the aggregate identifier
     * @return the identifier of the aggregate, of the type used by the aggregate's JPA mapping
     */
    @NonNull
    Object convert(@NonNull Class<?> aggregateType, @NonNull String aggregateIdentifier);

    /**
     * Returns a {@link Function} converting identifiers of aggregates of the given {@code aggregateType}, as accepted
     * by {@link GenericJpaRepository.Builder#identifierConverter(Function)}.
     *
     * @param aggregateType the type of aggregate to convert identifiers for
     * @return a {@link Function} converting identifiers of aggregates of the given {@code aggregateType}
     */
    default Function<String, ?> forAggregateType(@NonNull Class<?> aggregateType) {
        return aggregateIdentifier -> convert(aggregateType, aggregateIdentifier);
    }
}
