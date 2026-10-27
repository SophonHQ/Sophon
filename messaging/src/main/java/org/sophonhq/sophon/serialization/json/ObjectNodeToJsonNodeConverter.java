/*
 * Copyright (c) 2010-2021. Axon Framework
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

package org.sophonhq.sophon.serialization.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.sophonhq.sophon.serialization.ContentTypeConverter;

/**
 * A {@link ContentTypeConverter} implementation for Jackson 2 that converts an {@link ObjectNode} object
 * into a {@link JsonNode}. Intended to simplify JSON-typed event upcasters, which generally deal with an
 * {@code ObjectNode} as the event.
 * <p>
 * Will succeed converting at all times as an {@code ObjectNode} is a {@code JsonNode} by definition.
 *
 * @author Steven van Beelen
 * @since axon-4.6.0
 */
public class ObjectNodeToJsonNodeConverter implements ContentTypeConverter<ObjectNode, JsonNode> {

    @Override
    public Class<ObjectNode> expectedSourceType() {
        return ObjectNode.class;
    }

    @Override
    public Class<JsonNode> targetType() {
        return JsonNode.class;
    }

    @Override
    public JsonNode convert(ObjectNode original) {
        return original;
    }
}
