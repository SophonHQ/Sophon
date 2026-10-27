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

package io.github.sophonhq.sophon.modelling.command;

import io.github.sophonhq.sophon.common.Assert;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Structure that holds an Aggregate Identifier and an expected version of an aggregate.
 *
 * @author Allard Buijze
 * @since axon-1.2
 */
public class VersionedAggregateIdentifier {

    private final Object identifier;
    private final Long version;

    /**
     * Initializes a VersionedAggregateIdentifier with the given {@code identifier} and {@code version}.
     *
     * @param identifier The non-null string identifier of the targeted aggregate.
     * @param version    The expected version of the targeted aggregate, or {@code null} if the version is irrelevant.
     * @deprecated In favor of {@link VersionedAggregateIdentifier#VersionedAggregateIdentifier(Object, Long)}, since
     * the {@code identifier} can be a non-{@link String}.
     */
    @Deprecated
    public VersionedAggregateIdentifier(String identifier, Long version) {
        Assert.notNull(identifier, () -> "Identifier must not be null");
        this.identifier = identifier;
        this.version = version;
    }

    /**
     * Initializes a VersionedAggregateIdentifier with the given {@code identifier} and {@code version}.
     *
     * @param identifier The non-null identifier of the targeted aggregate.
     * @param version    The expected version of the targeted aggregate, or {@code null} if the version is irrelevant.
     */
    public VersionedAggregateIdentifier(@NonNull Object identifier, Long version) {
        Assert.notNull(identifier, () -> "Identifier must not be null");
        this.identifier = identifier;
        this.version = version;
    }

    /**
     * Returns the string representation of the identifier of the targeted Aggregate. May never return {@code null}.
     *
     * @return the string representation of identifier of the targeted Aggregate
     */
    @NonNull
    public String getIdentifier() {
        return identifier.toString();
    }

    /**
     * Returns the object representation of the identifier of the targeted Aggregate. May never return {@code null}.
     *
     * @return the object representation of the identifier of the targeted Aggregate
     */
    @NonNull
    public Object getIdentifierValue() {
        return identifier;
    }

    /**
     * Returns the version of the targeted Aggregate, or {@code null} if the version is irrelevant.
     *
     * @return the version of the targeted Aggregate
     */
    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VersionedAggregateIdentifier that = (VersionedAggregateIdentifier) o;
        return Objects.equals(identifier, that.identifier) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, version);
    }
}
