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

package org.sophonhq.sophon.test.matchers;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Allard Buijze
 */
class MatchAllFieldFilterTest {

    @SuppressWarnings("unused")
    private String field;

    @Test
    void acceptWhenEmpty() throws Exception {
        assertThat(new MatchAllFieldFilter(Collections.emptyList())
                           .accept(MatchAllFieldFilterTest.class.getDeclaredField("field"))).isTrue();
    }

    @Test
    void acceptWhenAllAccept() throws Exception {
        assertThat(new MatchAllFieldFilter(Arrays.asList(AllFieldsFilter.instance(),
                                                         AllFieldsFilter.instance()))
                           .accept(MatchAllFieldFilterTest.class.getDeclaredField("field"))).isTrue();
    }

    @Test
    void rejectWhenOneRejects() throws Exception {
        assertThat(new MatchAllFieldFilter(Arrays.asList(AllFieldsFilter.instance(),
                                                         field -> false))
                           .accept(MatchAllFieldFilterTest.class.getDeclaredField("field"))).isFalse();
    }
}
