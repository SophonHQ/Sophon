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

package org.sophonhq.sophon.serialization;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.messaging.MetaData;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Allard Buijze
 */
class SerializedMetaDataValueTest {

    @Test
    void serializeMetaData() {
        byte[] stubData = new byte[]{};
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(stubData, byte[].class);
        assertThat(serializedMetaData.getData()).isSameAs(stubData);
        assertThat(serializedMetaData.getContentType()).isEqualTo(byte[].class);
        assertThat(serializedMetaData.getType().getRevision()).isNull();
        assertThat(serializedMetaData.getType().getName()).isEqualTo(MetaData.class.getName());
    }

    @Test
    void isSerializedMetaData() {
        SerializedMetaData<byte[]> serializedMetaData = new SerializedMetaData<>(new byte[]{}, byte[].class);
        assertThat(SerializedMetaData.isSerializedMetaData(serializedMetaData)).isTrue();
        assertThat(SerializedMetaData.isSerializedMetaData(
                new SimpleSerializedObject<>("test", String.class, "type", "rev")
        )).isFalse();
    }
}
