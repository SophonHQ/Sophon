/*
 * Copyright (c) 2010-2024. Axon Framework
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sophonhq.sophon.messaging.Message;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Creates spans that record whether they were started, ended or ended in an exception. Useful for tracing logic
 * assertions in unit tests and provides several out of the box.
 *
 * @author Mitchell Herrijgers
 * @since axon-4.6.0
 */
public class TestSpanFactory implements SpanFactory {

    private final Logger logger = LoggerFactory.getLogger(TestSpanFactory.class);

    private final Deque<TestSpan> activeSpan = new ArrayDeque<>();
    private final List<TestSpan> createdSpans = new CopyOnWriteArrayList<>();
    private final Map<Message<?>, TestSpan> propagatedContexts = new HashMap<>();


    /**
     * Resets the {@link TestSpanFactory} to pristine state.
     */
    public void reset() {
        this.activeSpan.clear();
        this.createdSpans.clear();
        this.propagatedContexts.clear();
        logger.debug("SpanFactory cleared");
    }

    /**
     * Verifies that a span was created, but not started.
     *
     * @param name Name of the span to verify.
     */
    public void verifyNotStarted(String name) {
        assertThat(findSpan(name, span -> span.started)).as(() -> createErrorMessageForSpan(name)).isNotPresent();
    }

    /**
     * Verifies that a span was created and started, but it not yet ended.
     *
     * @param name Name of the span to verify.
     */
    public void verifySpanActive(String name) {
        assertThat(findSpan(name, span -> span.started && !span.ended))
                .as(() -> createErrorMessageForSpan(name))
                .isPresent();
    }

    /**
     * Verifies that a span was created and started, but it not yet ended.
     *
     * @param name    Name of the span to verify.
     * @param message The exact message the span should have been created for.
     */
    public void verifySpanActive(String name, Message<?> message) {
        assertThat(findSpan(name, message, span -> span.started && !span.ended))
                .as(() -> createErrorMessageForSpan(name))
                .isPresent();
    }

    /**
     * Verifies that a span was created, started, and ended.
     *
     * @param name Name of the span to verify.
     */
    public void verifySpanCompleted(String name) {
        assertThat(findSpan(name, span -> span.started && span.ended))
                .as(() -> createErrorMessageForSpan(name))
                .isPresent();
    }

    /**
     * Verifies that a Span has a certain attribute set on it.
     *
     * @param name  Name of the span to verify.
     * @param key   The key of the attribute.
     * @param value The value of the attribute.
     */
    public void verifySpanHasAttributeValue(String name, String key, String value) {
        assertThat(findSpan(name, span -> span.attributes.containsKey(key) && span.attributes.get(key).equals(value)))
                .as(() -> createErrorMessageForSpan(name))
                .isPresent();
    }

    /**
     * Verifies that a span was created, started, and ended.
     *
     * @param name    Name of the span to verify.
     * @param message The exact message the span should have been created for.
     */
    public void verifySpanCompleted(String name, Message<?> message) {
        assertThat(findSpan(name, message, span -> span.started && span.ended))
                .as(() -> createErrorMessageForSpan(name))
                .isPresent();
    }


    /**
     * Verifies that a span had an exception registered to it.
     *
     * @param name           Name of the span to verify.
     * @param exceptionClass The type of exception
     */
    public void verifySpanHasException(String name, Class<?> exceptionClass) {
        assertThat(findSpan(name).map(s -> s.exception).orElse(null)).isInstanceOf(exceptionClass);
    }

    /**
     * Verify no span was started by this name.
     *
     * @param name Name of the span to verify.
     */
    public void verifyNoSpan(String name) {
        assertThat(findSpan(name)).isEmpty();
    }

    /**
     * Verifies the provided message had the context propagated to it.
     *
     * @param name    Name of the span to verify.
     * @param message The exact message the span should have been propagated to.
     */
    public void verifySpanPropagated(String name, Message<?> message) {
        assertThat(createdSpans.stream()
                               .anyMatch(s -> s.name.equals(name)
                                       && propagatedContexts.containsKey(message)
                                       && propagatedContexts.get(message) == s))
                .as(createErrorMessageForSpan(name))
                .isTrue();
    }

    /**
     * Verifies that a span was created and was of a certain type.
     *
     * @param name Name of the span to verify.
     * @see TestSpanType
     */
    public void verifySpanHasType(String name, TestSpanType type) {
        assertThat(findSpan(name).map(s -> s.type).orElse(null)).isEqualTo(type);
    }

    private Optional<TestSpan> findSpan(String name) {
        return findSpan(name, testSpan -> true);
    }

    private Optional<TestSpan> findSpan(String name, Predicate<TestSpan> filter) {
        return createdSpans.stream()
                           .filter(s -> s.name.equals(name))
                           .filter(filter)
                           .findFirst();
    }

    private String createErrorMessageForSpan(String name) {
        return String.format(
                "No span matching name %s, but got the following recorded spans: %s",
                name,
                createdSpans.stream().map(TestSpan::toString).collect(Collectors.joining("\n")));
    }

    private Optional<TestSpan> findSpan(String name, Message<?> message, Predicate<TestSpan> filter) {
        return findSpan(name, filter.and(
                s -> s.message != null
                        && s.message.getIdentifier().equals(message.getIdentifier())));
    }

    @Override
    public Span createRootTrace(Supplier<String> operationNameSupplier) {
        TestSpan span = new TestSpan(TestSpanType.ROOT, operationNameSupplier.get(), null);
        createdSpans.add(span);
        return span;
    }

    @Override
    public Span createHandlerSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                  boolean isChildTrace,
                                  Message<?>... linkedParents) {
        TestSpan span = new TestSpan(isChildTrace ? TestSpanType.HANDLER_CHILD : TestSpanType.HANDLER_LINK,
                                     operationNameSupplier.get(),
                                     parentMessage);
        createdSpans.add(span);
        return span;
    }

    @Override
    public Span createDispatchSpan(Supplier<String> operationNameSupplier, Message<?> parentMessage,
                                   Message<?>... linkedSiblings) {
        TestSpan span = new TestSpan(TestSpanType.DISPATCH, operationNameSupplier.get(), parentMessage);
        createdSpans.add(span);
        return span;
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier) {
        TestSpan span = new TestSpan(TestSpanType.INTERNAL, operationNameSupplier.get(), null);
        createdSpans.add(span);
        return span;
    }

    @Override
    public Span createInternalSpan(Supplier<String> operationNameSupplier, Message<?> message) {
        TestSpan span = new TestSpan(TestSpanType.INTERNAL, operationNameSupplier.get(), message);
        createdSpans.add(span);
        return span;
    }

    @Override
    public void registerSpanAttributeProvider(SpanAttributesProvider provider) {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that this method does not return a modified message. Lots of testcases rely on an equality check. Modifying
     * the metadata of the message will break this.
     */
    @Override
    public <M extends Message<?>> M propagateContext(M message) {
        synchronized (activeSpan) {
            if (activeSpan.isEmpty()) {
                return message;
            }
            propagatedContexts.put(message, activeSpan.getFirst());
        }
        return message;
    }

    /**
     * The type of the span, relates to the method of {@link SpanFactory}. Used for assertions.
     */
    public enum TestSpanType {
        ROOT,
        HANDLER_CHILD,
        HANDLER_LINK,
        DISPATCH,
        INTERNAL
    }

    /**
     * Implementation of a {@link Span} that registers what happens to it. Other than that, it does not do anything.
     * <p>
     * Starting a span will register it as the current to the {@link TestSpanFactory#activeSpan} deque. All actions are
     * logged to be able to easily debug testcases.
     */
    public class TestSpan implements Span {

        private final List<SpanScope> scopes = new CopyOnWriteArrayList<>();
        private final TestSpanType type;
        private final String name;
        private final Message<?> message;
        private boolean started;
        private boolean ended;
        private Throwable exception;
        private final AtomicInteger scopeCount = new AtomicInteger(-1);
        private final Map<String, String> attributes = new HashMap<>();

        public TestSpan(TestSpanType type, String name, Message<?> message) {
            this.type = type;
            this.name = name;
            this.message = message;
        }

        @Override
        public Span start() {
            started = true;
            synchronized (activeSpan) {
                activeSpan.addFirst(this);
            }
            logger.debug("+ {}", name);
            return this;
        }

        @Override
        public SpanScope makeCurrent() {
            return new TestSpanScope(scopeCount.incrementAndGet());
        }

        private class TestSpanScope implements SpanScope {

            private final int scopeNum;

            private TestSpanScope(int scopeNum) {
                this.scopeNum = scopeNum;
                logger.debug("++ {}:{}", name, scopeNum);
            }

            @Override
            public void close() {
                logger.debug("-- {}:{}", name, scopeNum);
                scopes.remove(this);
            }
        }

        @Override
        public void end() {
            if (ended) {
                throw new IllegalStateException("Span already ended!");
            }
            ended = true;
            if (!scopes.isEmpty()) {
                throw new IllegalStateException("All scopes should be closed! Still have " + scopes.size() + " open!");
            }
            synchronized (activeSpan) {
                activeSpan.remove(this);
            }
            logger.debug("- {}", name);
        }

        @Override
        public Span recordException(Throwable t) {
            logger.debug("Recorded exception for span with name {}", name, t);
            this.exception = t;
            return this;
        }

        @Override
        public Span addAttribute(String key, String value) {
            attributes.put(key, value);
            return this;
        }

        public TestSpanType getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public String getAttribute(String key) {
            return attributes.get(key);
        }

        public Message<?> getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return "TestSpan{" +
                    "type=" + type +
                    ", name='" + name + '\'' +
                    ", message=" + message +
                    ", started=" + started +
                    ", ended=" + ended +
                    ", exception=" + exception +
                    ", attributes=" + attributes +
                    '}';
        }
    }
}
