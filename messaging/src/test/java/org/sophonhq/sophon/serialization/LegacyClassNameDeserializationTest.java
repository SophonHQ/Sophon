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

package org.sophonhq.sophon.serialization;

import com.thoughtworks.xstream.XStream;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.sophonhq.sophon.eventhandling.GapAwareTrackingToken;
import org.sophonhq.sophon.eventhandling.GlobalSequenceTrackingToken;
import org.sophonhq.sophon.eventhandling.MultiSourceTrackingToken;
import org.sophonhq.sophon.eventhandling.ReplayToken;
import org.sophonhq.sophon.eventhandling.TrackingToken;
import org.sophonhq.sophon.eventhandling.tokenstore.ConfigToken;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.serialization.jackson3.Jackson3Serializer;
import org.sophonhq.sophon.serialization.json.JacksonSerializer;
import org.sophonhq.sophon.serialization.xml.XStreamSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;

/**
 * Test class validating that serializers configured to translate legacy class names read data written with Axon
 * Framework 4 class names. The legacy data is constructed by serializing Sophon objects and replacing the class names
 * with their Axon Framework 4 counterparts, which is exactly what Axon Framework 4 writes for these classes.
 */
class LegacyClassNameDeserializationTest {

    private static final String PACKAGE = "org.sophonhq.sophon";
    private static final String LEGACY_PACKAGE = "org.axonframework";

    static Stream<Arguments> serializedObjects() {
        Map<String, String> config = new HashMap<>();
        config.put("id", "storage-id");
        Map<String, TrackingToken> sources = new HashMap<>();
        sources.put("first", new GlobalSequenceTrackingToken(3));
        sources.put("second", GapAwareTrackingToken.newInstance(9, Arrays.asList(5L, 7L)));
        List<Object> objects = Arrays.asList(
                new GlobalSequenceTrackingToken(42),
                GapAwareTrackingToken.newInstance(12, Arrays.asList(8L, 10L)),
                ReplayToken.createReplayToken(new GlobalSequenceTrackingToken(10), new GlobalSequenceTrackingToken(4)),
                new MultiSourceTrackingToken(sources),
                new ConfigToken(config),
                MetaData.with("key", "value")
        );
        Map<String, Serializer> serializers = new LinkedHashMap<>();
        serializers.put("XStream", XStreamSerializer.builder()
                                                    .xStream(new XStream())
                                                    .translateLegacyClassNames()
                                                    .build());
        serializers.put("Jackson", JacksonSerializer.builder().translateLegacyClassNames().build());
        serializers.put("Jackson 3", Jackson3Serializer.builder().translateLegacyClassNames().build());
        return serializers.entrySet().stream().flatMap(
                serializer -> objects.stream().map(o -> Arguments.of(serializer.getKey(), serializer.getValue(), o))
        );
    }

    @MethodSource("serializedObjects")
    @ParameterizedTest(name = "[{index}] {0}: {2}")
    void deserializesDataWrittenWithAxonFrameworkClassNames(String serializerName, Serializer serializer, Object object) {
        SerializedObject<byte[]> legacy = withLegacyClassNames(serializer.serialize(object, byte[].class));

        assertThatObject(serializer.deserialize(legacy)).isEqualTo(object);
    }

    @MethodSource("serializedObjects")
    @ParameterizedTest(name = "[{index}] {0}: {2}")
    void resolvesAxonFrameworkClassNames(String serializerName, Serializer serializer, Object object) {
        SerializedType legacyType = new SimpleSerializedType(object.getClass().getName().replace(PACKAGE, LEGACY_PACKAGE),
                                                             null);

        assertThat(serializer.classForType(legacyType)).isEqualTo(object.getClass());
    }

    @MethodSource("serializedObjects")
    @ParameterizedTest(name = "[{index}] {0}: {2}")
    void leavesDataWithSophonClassNamesUnchanged(String serializerName, Serializer serializer, Object object) {
        assertThatObject(serializer.deserialize(serializer.serialize(object, byte[].class))).isEqualTo(object);
    }

    private static SerializedObject<byte[]> withLegacyClassNames(SerializedObject<byte[]> serialized) {
        SerializedType type = serialized.getType();
        SerializedType legacyType = new SimpleSerializedType(type.getName().replace(PACKAGE, LEGACY_PACKAGE),
                                                             type.getRevision());
        byte[] legacyData = new String(serialized.getData(), StandardCharsets.UTF_8).replace(PACKAGE, LEGACY_PACKAGE)
                                                                                .getBytes(StandardCharsets.UTF_8);
        return new SimpleSerializedObject<>(legacyData, byte[].class, legacyType);
    }
}
