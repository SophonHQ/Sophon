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

package io.github.sophonhq.sophon.serialization.json;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import io.github.sophonhq.sophon.serialization.LegacyClassNames;

/**
 * {@link DeserializationProblemHandler} resolving polymorphic type ids that refer to Axon Framework 4 class names, as
 * written for {@code @JsonTypeInfo(use = Id.CLASS)} properties, to their Sophon equivalents.
 *
 * @since 1.0.0
 */
final class LegacyTypeIdHandler extends DeserializationProblemHandler {

    static final LegacyTypeIdHandler INSTANCE = new LegacyTypeIdHandler();

    private LegacyTypeIdHandler() {
    }

    @Override
    public JavaType handleUnknownTypeId(DeserializationContext ctxt,
                                        JavaType baseType,
                                        String subTypeId,
                                        TypeIdResolver idResolver,
                                        String failureMsg) {
        String translated = LegacyClassNames.translate(subTypeId);
        if (translated.equals(subTypeId)) {
            return null;
        }
        try {
            return ctxt.getTypeFactory()
                       .constructSpecializedType(baseType, ctxt.getTypeFactory().findClass(translated));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
