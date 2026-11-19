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

package org.sophonhq.sophon.modelling.saga.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.modelling.saga.AssociationValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 */
class AssociationValueMapTest {

    private AssociationValueMap testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new AssociationValueMap();
    }

    @Test
    void storeVarietyOfItems() {
        assertThat(testSubject.isEmpty()).isTrue();

        Object anObject = new Object();
        testSubject.add(av("1"), "T", "1");
        testSubject.add(av("1"), "T", "1");
        assertThat(testSubject.size()).as("Wrong count after adding an object twice").isOne();
        testSubject.add(av("2"), "T", "1");
        assertThat(testSubject.size()).as("Wrong count after adding two objects").isEqualTo(2);
        testSubject.add(av("a"), "T", "1");
        testSubject.add(av("a"), "T", "1");
        assertThat(testSubject.size()).as("Wrong count after adding two identical Strings").isEqualTo(3);
        testSubject.add(av("b"), "T", "1");
        assertThat(testSubject.size()).as("Wrong count after adding two identical Strings").isEqualTo(4);

        testSubject.add(av("a"), "T", "2");
        testSubject.add(av("a"), "Y", "2");
        assertThat(testSubject.size())
                .as("Wrong count after adding two identical Strings for different saga")
                .isEqualTo(6);
        assertThat(testSubject.findSagas("T", av("a"))).hasSize(2);
    }

    @Test
    void removeItems() {
        storeVarietyOfItems();
        assertThat(testSubject.size()).as("Wrong initial item count").isEqualTo(6);
        testSubject.remove(av("a"), "T", "1");
        assertThat(testSubject.size()).as("Wrong item count").isEqualTo(5);
        testSubject.remove(av("a"), "T", "2");
        assertThat(testSubject.size()).as("Wrong item count").isEqualTo(4);

        testSubject.clear();
        assertThat(testSubject.isEmpty()).isTrue();
        assertThat(testSubject.size()).as("Wrong item count").isZero();
    }

    private AssociationValue av(String value) {
        return new AssociationValue("key", value);
    }

    @Test
    void findAssociations() {
        List<AssociationValue> usedAssociations = new ArrayList<>(1000);
        for (int t = 0; t < 1000; t++) {
            String key = UUID.randomUUID().toString();
            for (int i = 0; i < 10; i++) {
                AssociationValue associationValue = new AssociationValue(key, UUID.randomUUID().toString());
                if (usedAssociations.size() < 1000) {
                    usedAssociations.add(associationValue);
                }
                testSubject.add(associationValue, "type", key);
            }
        }

        assertThat(testSubject.size()).isEqualTo(10000);
        for (AssociationValue item : usedAssociations) {
            Set<String> actualResult = testSubject.findSagas("type", item);
            assertThat(actualResult).as("Failure on item: " + usedAssociations.indexOf(item)).hasSize(1);
            assertThat(actualResult.iterator().next()).isEqualTo(item.getKey());
        }
    }
}
