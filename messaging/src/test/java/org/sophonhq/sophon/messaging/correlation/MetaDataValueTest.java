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

package org.sophonhq.sophon.messaging.correlation;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.messaging.MetaData;

import java.io.*;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Allard Buijze
 */
class MetaDataValueTest {

    @Test
    void createMetaData() {
        Map<String, Object> metaDataValues = new HashMap<>();
        metaDataValues.put("first", "value");
        MetaData metaData = new MetaData(metaDataValues);
        metaDataValues.put("second", "value");

        assertThat(metaData)
                .containsEntry("first", "value")
                .doesNotContainKey("second");
    }

    @Test
    void mergedMetaData() {
        Map<String, Object> metaDataValues = new HashMap<>();
        metaDataValues.put("first", "value");
        MetaData metaData = new MetaData(metaDataValues);
        metaDataValues.put("second", "value");
        metaDataValues.put("first", "other");

        MetaData newMetaData = metaData.mergedWith(metaDataValues);
        assertThat(newMetaData)
                .containsEntry("first", "other")
                .containsEntry("second", "value");
    }

    @Test
    void removedMetaData() {
        Map<String, Object> metaDataValues = new HashMap<>();
        metaDataValues.put("first", "value");
        metaDataValues.put("second", "value");
        MetaData metaData = new MetaData(metaDataValues);

        MetaData newMetaData = metaData.withoutKeys(metaDataValues.keySet());
        assertThat(newMetaData).isEmpty();
    }

    @Test
    void equals() {
        Map<String, Object> metaDataValues = new HashMap<>();
        metaDataValues.put("first", "value");
        MetaData metaData1 = new MetaData(metaDataValues);
        metaDataValues.put("second", "value");
        MetaData metaData2 = new MetaData(metaDataValues);
        MetaData metaData3 = new MetaData(metaDataValues);

        assertThat(metaData1).isEqualTo(metaData1);
        assertThat(metaData2).isEqualTo(metaData3);
        assertThat(metaData1).isNotEqualTo(metaData2);
        assertThat(metaData1).isNotEqualTo(metaData3);
        assertThat(metaData3).isNotEqualTo(metaData1);
        assertThat(metaData1).isNotEqualTo(new Object());
        assertThat(metaData1).isNotEqualTo(null);

        // Map requires that Maps are equal, even if their implementation is different
        assertThat(metaData2).isEqualTo(metaDataValues);
        assertThat(metaDataValues).isEqualTo(metaData2);
    }

    @Test
    void serialization() throws IOException, ClassNotFoundException {
        MetaData metaData1 = MetaData.from(Collections.singletonMap("Key1", "Value"));
        MetaData metaData2 = MetaData.from(Collections.singletonMap("Key2", "Value"));
        MetaData emptyMetaData = MetaData.emptyInstance();

        assertThat(serialize(metaData1)).isEqualTo(metaData1);
        assertThat(serialize(metaData2)).isEqualTo(metaData2);
        assertThat(serialize(emptyMetaData)).isSameAs(emptyMetaData);
    }

    private MetaData serialize(MetaData metaData1) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(metaData1);
        oos.close();
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        return (MetaData) ois.readObject();
    }

    @Test
    void buildMetaDataThroughFrom() {
        Map<String, String> testMetaDataMap = new HashMap<>();
        testMetaDataMap.put("firstKey", "firstVal");
        testMetaDataMap.put("secondKey", "secondVal");

        MetaData result = MetaData.from(testMetaDataMap);

        assertThat(result)
                .containsEntry("firstKey", "firstVal")
                .containsEntry("secondKey", "secondVal");
    }

    @Test
    void buildMetaDataThroughWith() {
        MetaData result = MetaData.with("key", "val");

        assertThat(result).containsEntry("key", "val");
    }

    @Test
    void buildMetaDataThroughWithAnd() {
        MetaData result = MetaData.with("firstKey", "firstVal").and("secondKey", "secondVal");

        assertThat(result)
                .containsEntry("firstKey", "firstVal")
                .containsEntry("secondKey", "secondVal");
    }

    @Test
    void buildMetaDataThroughAndIfNotPresentAddsNewValue() {
        MetaData result = MetaData.with("firstKey", "firstVal").andIfNotPresent("secondKey", () -> "secondVal");

        assertThat(result)
                .containsEntry("firstKey", "firstVal")
                .containsEntry("secondKey", "secondVal");
    }

    @Test
    void buildMetaDataThroughAndIfNotPresentDoesntAddNewValue() {
        MetaData result = MetaData.with("firstKey", "firstVal").andIfNotPresent("firstKey", () -> "firstVal");

        assertThat(result).containsEntry("firstKey", "firstVal");
        assertThat(result.size()).isOne();
    }

    @Test
    void metaDataModification_Clear() {
        MetaData metaData = new MetaData(Collections.emptyMap());

        assertThatThrownBy(metaData::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metaDataModification_Put() {
        MetaData metaData = new MetaData(Collections.emptyMap());

        assertThatThrownBy(() -> metaData.put("", "")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metaDataModification_Remove() {
        MetaData metaData = new MetaData(Collections.emptyMap());

        assertThatThrownBy(() -> metaData.remove("")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metaDataModification_PutAll() {
        MetaData metaData = new MetaData(Collections.emptyMap());

        assertThatThrownBy(() -> metaData.putAll(Collections.emptyMap()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metaDataModification_KeySet_Remove() {
        Set<String> keySet = new MetaData(Collections.emptyMap()).keySet();

        assertThatThrownBy(() -> keySet.remove("Hello")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metaDataModification_Values_Remove() {
        Collection<Object> values = new MetaData(Collections.emptyMap()).values();

        assertThatThrownBy(() -> values.remove("Hello")).isInstanceOf(UnsupportedOperationException.class);
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
    @Test
    void metaDataModification_EntrySet_Remove() {
        Set<Map.Entry<String,Object>> entrySet = new MetaData(Collections.emptyMap()).entrySet();

        assertThatThrownBy(() -> entrySet.remove("Hello")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metaDataSubsetReturnsSubsetOfMetaDataInstance() {
        MetaData testMetaData = MetaData.with("firstKey", "firstValue")
                .and("secondKey", "secondValue")
                .and("thirdKey", "thirdValue")
                .and("fourthKey", "fourthValue");

        MetaData result = testMetaData.subset("secondKey", "fourthKey", "fifthKey");

        assertThat(result)
                .containsEntry("secondKey", "secondValue")
                .containsEntry("fourthKey", "fourthValue");
        assertThat(result.get("fifthKey")).isNull();
    }
}
