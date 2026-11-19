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

package org.sophonhq.sophon.common;

import org.junit.jupiter.api.Test;

import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class CollectionUtilsTest {

    @Test
    void createCollectionFromPotentialCollection() {
        Collection<String> collectionFromElement = CollectionUtils.asCollection("item");
        Collection<String> collectionFromNull = CollectionUtils.asCollection(null);
        Collection<String> collectionFromArray = CollectionUtils.asCollection(new String[]{"item1", "item2"});
        Collection<String> collectionFromCollection = CollectionUtils.asCollection(asList("item1", "item2"));
        Collection<String> collectionFromSpliterator = CollectionUtils.asCollection(Spliterators.spliterator(new String[] {"item1", "item2"}, Spliterator.ORDERED));
        Collection<String> collectionFromIterable = CollectionUtils.asCollection((Iterable) () -> asList("item1", "item2").iterator());

        assertThat(collectionFromElement).hasSize(1);
        assertThat(collectionFromNull).isEmpty();
        assertThat(collectionFromArray).hasSize(2);
        assertThat(collectionFromCollection).hasSize(2);
        assertThat(collectionFromSpliterator).hasSize(2);
        assertThat(collectionFromIterable).hasSize(2);
    }

    @Test
    void intersect() {
        TreeSet<Integer> result1 = CollectionUtils.intersect(asList(1, 2, 4, 5), asList(1, 3, 5, 7), TreeSet::new);
        TreeSet<Integer> result2 = CollectionUtils.intersect(emptyList(), asList(1, 3, 5, 7), TreeSet::new);
        List<Integer> result3 = CollectionUtils.intersect(singletonList(1), asList(1, 3, 5, 7), ArrayList::new);

        assertThat(result1).isEqualTo(new TreeSet<>(asList(1, 5)));
        assertThat(result2).isEqualTo(new TreeSet<>());
        assertThat(result3).containsExactlyElementsOf(singletonList(1));
    }
}
