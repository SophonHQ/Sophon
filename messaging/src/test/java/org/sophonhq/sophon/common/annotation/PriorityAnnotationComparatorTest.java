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

package org.sophonhq.sophon.common.annotation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.common.Priority;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityAnnotationComparatorTest {

    private PriorityAnnotationComparator<Object> testSubject;

    @BeforeEach
    void setUp() {
        testSubject = PriorityAnnotationComparator.getInstance();
    }

    @Test
    void compareDifferentPriorities() {
        assertThat(testSubject.compare(new LowPrio(), new HighPrio())).isGreaterThan(0);
        assertThat(testSubject.compare(new LowPrio(), new NeutralPrio())).isGreaterThan(0);
        assertThat(testSubject.compare(new NeutralPrio(), new HighPrio())).isGreaterThan(0);

        assertThat(testSubject.compare(new HighPrio(), new LowPrio())).isLessThan(0);
        assertThat(testSubject.compare(new NeutralPrio(), new LowPrio())).isLessThan(0);
        assertThat(testSubject.compare(new HighPrio(), new NeutralPrio())).isLessThan(0);
    }

    @Test
    void compareSamePriorities() {
        assertThat(testSubject.compare(new LowPrio(), new LowPrio())).isZero();
        assertThat(testSubject.compare(new NeutralPrio(), new NeutralPrio())).isZero();
        assertThat(testSubject.compare(new HighPrio(), new HighPrio())).isZero();
    }

    @Test
    void undefinedConsideredNeutralPriority() {
        assertThat(testSubject.compare(new UndefinedPrio(), new HighPrio())).isGreaterThan(0);
        assertThat(testSubject.compare(new UndefinedPrio(), new LowPrio())).isLessThan(0);
        assertThat(testSubject.compare(new UndefinedPrio(), new NeutralPrio())).isEqualTo(0);

        assertThat(testSubject.compare(new HighPrio(), new UndefinedPrio())).isLessThan(0);
        assertThat(testSubject.compare(new LowPrio(), new UndefinedPrio())).isGreaterThan(0);
        assertThat(testSubject.compare(new NeutralPrio(), new UndefinedPrio())).isEqualTo(0);
    }

    @Test
    void sortComparisonResultsInCorrectSortOrder() {
        HighPrio highPrio = new HighPrio();
        LowPrio lowPrio = new LowPrio();
        List<Object> result = Arrays.asList(new NeutralPrio(), new UndefinedPrio(), highPrio, lowPrio);

        for (int i = 0; i < 100; i++) {
            Collections.shuffle(result);
            result.sort(testSubject);

            assertThat(result.get(0)).isEqualTo(highPrio);
            assertThat(result.get(3)).isEqualTo(lowPrio);
        }
    }

    @Priority(Priority.LAST)
    private static class LowPrio {
    }

    @Priority(Priority.FIRST)
    private static class HighPrio {
    }

    @Priority(Priority.NEUTRAL)
    private static class NeutralPrio {
    }

    private static class UndefinedPrio {
    }
}
