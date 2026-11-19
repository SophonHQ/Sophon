/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.sophonhq.sophon.tracing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpanTest {

    private TestSpan span = new TestSpan();

    @Test
    void runRunnableWorks() {
        span.run(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
        });
        assertThat(span.ended).isTrue();
    }

    @Test
    void runRunnableRegistersException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        assertThatThrownBy(() -> span.run(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            throw exception;
        })).isInstanceOf(RuntimeException.class);
        assertThat(span.ended).isTrue();
        assertThat(span.exception).isEqualTo(exception);
    }

    @Test
    void wrapRunnableWorks() {
        span.wrapRunnable(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
        }).run();
        assertThat(span.ended).isTrue();
    }

    @Test
    void wrapRunnableRegistersException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        Runnable wrappedRunnable = span.wrapRunnable(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            throw exception;
        });
        assertThatThrownBy(wrappedRunnable::run).isInstanceOf(RuntimeException.class);
        assertThat(span.ended).isTrue();
        assertThat(span.exception).isEqualTo(exception);
    }

    @Test
    void runSupplierWorks() {
        String result = span.runSupplier(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            return "SupplyString";
        });
        assertThat(span.ended).isTrue();
        assertThat(result).isEqualTo("SupplyString");
    }

    @Test
    void runSupplierRegistersException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        assertThatThrownBy(() -> span.runSupplier(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            throw exception;
        })).isInstanceOf(RuntimeException.class);
        assertThat(span.ended).isTrue();
        assertThat(span.exception).isEqualTo(exception);
    }

    @Test
    void wrapSupplierWorks() {
        String result = span.wrapSupplier(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            return "SupplyString";
        }).get();
        assertThat(span.ended).isTrue();
        assertThat(result).isEqualTo("SupplyString");
    }

    @Test
    void wrapSupplierRegistersException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        Supplier<Object> wrappedSupplier = span.wrapSupplier(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            throw exception;
        });
        assertThatThrownBy(wrappedSupplier::get).isInstanceOf(RuntimeException.class);
        assertThat(span.ended).isTrue();
        assertThat(span.exception).isEqualTo(exception);
    }

    @Test
    void runCallableWorks() throws Exception {
        String result = span.runCallable(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            return "CallString";
        });
        assertThat(span.ended).isTrue();
        assertThat(result).isEqualTo("CallString");
    }

    @Test
    void runCallableRegistersRuntimeException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        assertThatThrownBy(() -> span.runCallable(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            throw exception;
        })).isInstanceOf(RuntimeException.class);
        assertThat(span.ended).isTrue();
        assertThat(span.exception).isEqualTo(exception);
    }

    @Test
    void runCallableRegistersCheckedException() {
        IOException exception = new IOException("My custom exception");
        assertThatThrownBy(() -> span.runCallable(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            throw exception;
        })).isInstanceOf(IOException.class);
        assertThat(span.ended).isTrue();
        assertThat(span.exception).isEqualTo(exception);
    }

    @Test
    void wrapCallableWorks() throws Exception {
        String result = span.wrapCallable(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            return "CallString";
        }).call();
        assertThat(span.ended).isTrue();
        assertThat(result).isEqualTo("CallString");
    }

    @Test
    void wrapCallableRegistersRuntimeException() {
        RuntimeException exception = new RuntimeException("My custom exception");
        Callable<Object> wrappedCallable = span.wrapCallable(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            throw exception;
        });
        assertThatThrownBy(wrappedCallable::call).isInstanceOf(RuntimeException.class);
        assertThat(span.ended).isTrue();
        assertThat(span.exception).isEqualTo(exception);
    }

    @Test
    void wrapCallableRegistersCheckedException() {
        IOException exception = new IOException("My custom exception");
        Callable<Object> wrappedCallable = span.wrapCallable(() -> {
            assertThat(span.started).isTrue();
            assertThat(span.ended).isFalse();
            throw exception;
        });
        assertThatThrownBy(wrappedCallable::call).isInstanceOf(IOException.class);
        assertThat(span.ended).isTrue();
        assertThat(span.exception).isEqualTo(exception);
    }

    static class TestSpan implements Span {

        boolean started;
        boolean ended;
        Throwable exception;

        @Override
        public Span start() {
            this.started = true;
            return this;
        }

        @Override
        public SpanScope makeCurrent() {
            return () -> {};
        }

        @Override
        public void end() {
            this.ended = true;
        }

        @Override
        public Span recordException(Throwable t) {
            this.exception = t;
            return this;
        }

        @Override
        public Span addAttribute(String key, String value) {
            return this;
        }
    }
}
