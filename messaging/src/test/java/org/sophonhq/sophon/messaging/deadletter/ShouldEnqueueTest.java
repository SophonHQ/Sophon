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

package org.sophonhq.sophon.messaging.deadletter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.MetaData;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating {@link ShouldEnqueue}.  Constructs a {@code ShouldEnqueue} through the constructor and
 * {@link Decisions} utility class for testing.
 *
 * @author Steven van Beelen
 */
class ShouldEnqueueTest {

    private DeadLetter<? extends Message<?>> testLetter;

    @BeforeEach
    void setUp() {
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        testLetter = new GenericDeadLetter<>("seqId", GenericEventMessage.asEventMessage("payload"));
    }

    @Test
    void constructorShouldEnqueueAllowsEnqueueing() {
        ShouldEnqueue<Message<?>> testSubject = new ShouldEnqueue<>();

        assertThat(testSubject.shouldEnqueue()).isTrue();
        assertThat(testSubject.enqueueCause()).isEmpty();

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertThat(result).isEqualTo(testLetter);
    }

    @Test
    void decisionsEnqueueAllowsEnqueueing() {
        ShouldEnqueue<Message<?>> testSubject = Decisions.enqueue();

        assertThat(testSubject.shouldEnqueue()).isTrue();
        assertThat(testSubject.enqueueCause()).isEmpty();

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertThat(result).isEqualTo(testLetter);
    }

    @Test
    void constructorShouldEnqueueWithCauseAllowsEnqueueingWithGivenCause() {
        Throwable testCause = new RuntimeException("just because");

        ShouldEnqueue<Message<?>> testSubject = new ShouldEnqueue<>(testCause);

        assertThat(testSubject.shouldEnqueue()).isTrue();
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertThat(resultCause).isPresent();
        assertThat(resultCause).hasValue(testCause);

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertThat(result).isEqualTo(testLetter);
    }

    @Test
    void decisionsEnqueueWithCauseAllowsEnqueueingWithGivenCause() {
        Throwable testCause = new RuntimeException("just because");

        ShouldEnqueue<Message<?>> testSubject = Decisions.enqueue(testCause);

        assertThat(testSubject.shouldEnqueue()).isTrue();
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertThat(resultCause).isPresent();
        assertThat(resultCause).hasValue(testCause);

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertThat(result).isEqualTo(testLetter);
    }

    @Test
    void decisionsRequeueWithCauseAllowsEnqueueingWithGivenCause() {
        Throwable testCause = new RuntimeException("just because");

        ShouldEnqueue<Message<?>> testSubject = Decisions.requeue(testCause);

        assertThat(testSubject.shouldEnqueue()).isTrue();
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertThat(resultCause).isPresent();
        assertThat(resultCause).hasValue(testCause);

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertThat(result).isEqualTo(testLetter);
    }

    @Test
    void constructorShouldEnqueueWithCauseAndDiagnosticsAllowsEnqueueingWithGivenCauseAndDiagnostics() {
        Throwable testCause = new RuntimeException("just because");
        MetaData testMetaData = MetaData.with("key", "value");

        ShouldEnqueue<Message<?>> testSubject = new ShouldEnqueue<>(testCause, letter -> testMetaData);

        assertThat(testSubject.shouldEnqueue()).isTrue();
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertThat(resultCause).isPresent();
        assertThat(resultCause).hasValue(testCause);

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertThat(result.message()).isEqualTo(testLetter.message());
        assertThat(result.cause()).isEqualTo(testLetter.cause());
        assertThat(result.enqueuedAt()).isEqualTo(testLetter.enqueuedAt());
        assertThat(result.lastTouched()).isEqualTo(testLetter.lastTouched());
        assertThat(result.diagnostics()).isEqualTo(testMetaData);
    }

    @Test
    void decisionsRequeueWithCauseAndDiagnosticsAllowsEnqueueingWithGivenCauseAndDiagnostics() {
        Throwable testCause = new RuntimeException("just because");
        MetaData testMetaData = MetaData.with("key", "value");

        ShouldEnqueue<Message<?>> testSubject = Decisions.requeue(testCause, letter -> testMetaData);

        assertThat(testSubject.shouldEnqueue()).isTrue();
        Optional<Throwable> resultCause = testSubject.enqueueCause();
        assertThat(resultCause).isPresent();
        assertThat(resultCause).hasValue(testCause);

        DeadLetter<? extends Message<?>> result = testSubject.withDiagnostics(testLetter);
        assertThat(result.message()).isEqualTo(testLetter.message());
        assertThat(result.cause()).isEqualTo(testLetter.cause());
        assertThat(result.enqueuedAt()).isEqualTo(testLetter.enqueuedAt());
        assertThat(result.lastTouched()).isEqualTo(testLetter.lastTouched());
        assertThat(result.diagnostics()).isEqualTo(testMetaData);
    }
}