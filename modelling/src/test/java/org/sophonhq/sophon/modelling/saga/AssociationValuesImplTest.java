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

package org.sophonhq.sophon.modelling.saga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Allard Buijze
 */
class AssociationValuesImplTest {

    private AssociationValuesImpl testSubject;
    private AssociationValue associationValue;

    @BeforeEach
    void setUp() {
        testSubject = new AssociationValuesImpl();
        associationValue = new AssociationValue("key", "value");
    }

    @Test
    void addAssociationValue() {
        testSubject.add(associationValue);

        assertThat(testSubject.addedAssociations()).hasSize(1);
        assertThat(testSubject.removedAssociations()).isEmpty();
    }

    @Test
    void addAssociationValue_AddedTwice() {
        testSubject.add(associationValue);
        testSubject.commit();
        testSubject.add(associationValue);
        assertThat(testSubject.addedAssociations()).isEmpty();
        assertThat(testSubject.removedAssociations()).isEmpty();
    }

    @Test
    void removeAssociationValue() {
        assertThat(testSubject.add(associationValue)).isTrue();
        testSubject.commit();
        assertThat(testSubject.remove(associationValue)).isTrue();
        assertThat(testSubject.addedAssociations()).isEmpty();
        assertThat(testSubject.removedAssociations()).hasSize(1);
    }

    @Test
    void removeAssociationValue_NotInContainer() {
        testSubject.remove(associationValue);
        assertThat(testSubject.addedAssociations()).isEmpty();
        assertThat(testSubject.removedAssociations()).isEmpty();
    }

    @Test
    void addAndRemoveEntry() {
        testSubject.add(associationValue);
        testSubject.remove(associationValue);

        assertThat(testSubject.addedAssociations()).isEmpty();
        assertThat(testSubject.removedAssociations()).isEmpty();
    }

    @Test
    void contains() {
        assertThat(testSubject.contains(associationValue)).isFalse();
        testSubject.add(associationValue);
        assertThat(testSubject.contains(associationValue)).isTrue();
        assertThat(testSubject.contains(new AssociationValue("key", "value"))).isTrue();
        testSubject.remove(associationValue);
        assertThat(testSubject.contains(associationValue)).isFalse();
    }

    @Test
    void asSet() {
        testSubject.add(associationValue);
        int t = 0;
        for (AssociationValue actual : testSubject.asSet()) {
            assertThat(actual).isSameAs(associationValue);
            t++;
        }
        assertThat(t).isOne();
    }

    @Test
    void iterator() {
        testSubject.add(associationValue);
        Iterator<AssociationValue> iterator = testSubject.iterator();
        assertThat(iterator.next()).isSameAs(associationValue);
        assertThat(iterator).isExhausted();
    }
}
