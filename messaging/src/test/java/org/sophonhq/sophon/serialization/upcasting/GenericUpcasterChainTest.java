/*
 * Copyright (c) 2010-2020. Axon Framework
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

package org.sophonhq.sophon.serialization.upcasting;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link GenericUpcasterChain}.
 *
 * @author Rene de Waele
 */
class GenericUpcasterChainTest {

    @Test
    void chainWithSingleUpcasterCanUpcastMultipleEvents() {
        Object a = "a", b = "b", c = "c";
        Upcaster<Object> testSubject = new GenericUpcasterChain<>(new AToBUpcaster(a, b));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, a, c));
        assertThat(result).containsExactly(b, b, b, c);
    }

    @Test
    void upcastingResultOfOtherUpcaster() {
        Object a = "a", b = "b", c = "c";
        Upcaster<Object> testSubject = new GenericUpcasterChain<>(new AToBUpcaster(a, b), new AToBUpcaster(b, c));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, a, c));
        assertThat(result).containsExactly(c, c, c, c);
    }

    @Test
    void upcastingResultOfOtherUpcasterOnlyWorksIfUpcastersAreInCorrectOrder() {
        Object a = "a", b = "b", c = "c";
        Upcaster<Object> testSubject = new GenericUpcasterChain<>(new AToBUpcaster(b, c), new AToBUpcaster(a, b));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, a, c));
        assertThat(result).containsExactly(b, c, b, c);
    }

    @Test
    void remainderAddedAndUpcasted() {
        Object a = "a", b = "b", c = "c";
        Upcaster<Object> testSubject =
                new GenericUpcasterChain<>(new UpcasterWithRemainder(a, b), new AToBUpcaster(b, c));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, a, c));
        assertThat(result).containsExactly(a, c, a, c, a, c);
    }

    @Test
    void remainderReleasedAfterUpcasting() {
        Object a = "a", b = "b", c = "c", d = "d";
        Upcaster<Object> testSubject =
                new GenericUpcasterChain<>(new ConditionalUpcaster(a, b, c), new ConditionalUpcaster(c, d, a));
        Stream<Object> result = testSubject.upcast(Stream.of(a, d, a, b, d, a));
        assertThat(result).containsExactly(a, d, a, a);
    }

    @Test
    void upcastToMultipleTypes() {
        Object a = "a", b = "b", c = "c";
        Upcaster<Object> testSubject = new GenericUpcasterChain<>(new MultipleTypesUpcaster(), new AToBUpcaster(b, c));
        Stream<Object> result = testSubject.upcast(Stream.of(a, b, c));
        assertThat(result).containsExactly(a, a, c, c, c, c);
    }

    private static class AToBUpcaster extends SingleEntryUpcaster<Object> {

        private final Object a, b;

        private AToBUpcaster(Object a, Object b) {
            this.a = a;
            this.b = b;
        }

        @Override
        protected boolean canUpcast(Object intermediateRepresentation) {
            return intermediateRepresentation == a;
        }

        @Override
        protected Object doUpcast(Object intermediateRepresentation) {
            return b;
        }
    }

    private record UpcasterWithRemainder(List<Object> remainder) implements Upcaster<Object> {

        private UpcasterWithRemainder(Object... remainder) {
            this(List.of(remainder));
        }

        @Override
        public Stream<Object> upcast(Stream<Object> intermediateRepresentations) {
            return Stream.concat(intermediateRepresentations, remainder.stream());
        }
    }

    private record ConditionalUpcaster(Object first, Object second, Object replacement) implements Upcaster<Object> {

        @Override
        public Stream<Object> upcast(Stream<Object> intermediateRepresentations) {
            AtomicReference<Object> previous = new AtomicReference<>();
            return Stream.concat(intermediateRepresentations
                                         .filter(entry -> !(entry == first && previous.compareAndSet(null, entry)))
                                         .flatMap(entry -> {
                                             if (entry == second && previous.compareAndSet(first, null)) {
                                                 return Stream.of(replacement);
                                             }
                                             return Optional.ofNullable(previous.getAndSet(null))
                                                            .map(cached -> Stream.of(cached, entry)).orElse(Stream.of(
                                                             entry));
                                         }), Stream.generate(previous::get).limit(1).filter(Objects::nonNull));
        }
    }

    private static class MultipleTypesUpcaster implements Upcaster<Object> {

        @Override
        public Stream<Object> upcast(Stream<Object> intermediateRepresentations) {
            return intermediateRepresentations.flatMap(entry -> Stream.of(entry, entry));
        }
    }
}