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

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.eventhandling.EventMessage;
import org.sophonhq.sophon.eventhandling.GenericEventMessage;
import org.sophonhq.sophon.messaging.MetaData;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class validating the {@link GenericDeadLetter}.
 *
 * @author Steven van Beelen
 */
class GenericDeadLetterTest {

    private static final String SEQUENCE_IDENTIFIER = "sesequenceIdentifier";
    private static final EventMessage<String> MESSAGE = GenericEventMessage.asEventMessage("payload");

    @Test
    void constructorForIdentifierAndMessageSetsGivenIdentifierAndMessage() {
        Instant expectedTime = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedTime, ZoneId.systemDefault());

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        assertThat(testSubject.message()).isEqualTo(MESSAGE);
        assertThat(testSubject.cause()).isNotPresent();
        assertThat(testSubject.enqueuedAt()).isEqualTo(expectedTime);
        assertThat(testSubject.lastTouched()).isEqualTo(expectedTime);
        assertThat(testSubject.diagnostics()).isEmpty();
    }

    @Test
    void constructorForIdentifierMessageAndThrowableSetsGivenIdentifierMessageAndIdentifier() {
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);
        Instant expectedTime = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedTime, ZoneId.systemDefault());

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE, testThrowable);

        assertThat(testSubject.message()).isEqualTo(MESSAGE);
        Optional<Cause> resultCause = testSubject.cause();
        assertThat(resultCause).isPresent();
        assertThat(resultCause.get()).isEqualTo(expectedCause);
        assertThat(testSubject.enqueuedAt()).isEqualTo(expectedTime);
        assertThat(testSubject.lastTouched()).isEqualTo(expectedTime);
        assertThat(testSubject.diagnostics()).isEmpty();
    }

    @Test
    void constructorCompletelyManualSetsGivenFields() {
        ThrowableCause expectedCause = new ThrowableCause(new RuntimeException("just because"));
        Instant expectedEnqueuedAt = Instant.now();
        Instant expectedLastTouched = Instant.now();
        MetaData expectedDiagnostics = MetaData.with("key", "value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(
                SEQUENCE_IDENTIFIER, MESSAGE, expectedCause, expectedEnqueuedAt, expectedLastTouched,
                expectedDiagnostics
        );

        assertThat(testSubject.message()).isEqualTo(MESSAGE);
        Optional<Cause> resultCause = testSubject.cause();
        assertThat(resultCause).isPresent();
        assertThat(resultCause.get()).isEqualTo(expectedCause);
        assertThat(testSubject.enqueuedAt()).isEqualTo(expectedEnqueuedAt);
        assertThat(testSubject.lastTouched()).isEqualTo(expectedLastTouched);
        assertThat(testSubject.diagnostics()).isEqualTo(expectedDiagnostics);
    }

    @Test
    void invokingMarkTouchedAdjustsLastTouched() {
        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        Instant expectedLastTouched = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedLastTouched, ZoneId.systemDefault());
        DeadLetter<EventMessage<String>> result = testSubject.markTouched();

        assertThat(result.message()).isEqualTo(testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertThat(resultCause).isNotPresent();
        assertThat(result.enqueuedAt()).isEqualTo(testSubject.enqueuedAt());
        assertThat(result.lastTouched()).isEqualTo(expectedLastTouched);
        assertThat(result.diagnostics()).isEqualTo(testSubject.diagnostics());
    }

    @Test
    void invokingWithCauseAndWithoutOriginalCauseSetsGivenCause() {
        // Fix the clock to keep time consistent after withCause invocation.
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(testThrowable);

        assertThat(result.message()).isEqualTo(testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertThat(resultCause).isPresent();
        assertThat(resultCause.get()).isEqualTo(expectedCause);
        assertThat(result.enqueuedAt()).isEqualTo(testSubject.enqueuedAt());
        assertThat(result.lastTouched()).isEqualTo(testSubject.lastTouched());
        assertThat(result.diagnostics()).isEqualTo(testSubject.diagnostics());
    }

    @Test
    void invokingWithCauseAndOriginalCauseReplacesTheOriginalCause() {
        // Fix the clock to keep time consistent after withCause invocation.
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        Throwable originalThrowable = new RuntimeException("some other issue");
        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE, originalThrowable);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(testThrowable);

        assertThat(result.message()).isEqualTo(testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertThat(resultCause).isPresent();
        assertThat(resultCause.get()).isEqualTo(expectedCause);
        assertThat(result.enqueuedAt()).isEqualTo(testSubject.enqueuedAt());
        assertThat(result.lastTouched()).isEqualTo(testSubject.lastTouched());
        assertThat(result.diagnostics()).isEqualTo(testSubject.diagnostics());
    }

    @Test
    void invokingWithNullCauseAndNoOriginalCauseLeavesTheCauseEmpty() {
        // Fix the clock to keep time consistent after withCause invocation.
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(null);

        assertThat(result.message()).isEqualTo(testSubject.message());
        assertThat(result.cause()).isNotPresent();
        assertThat(result.enqueuedAt()).isEqualTo(testSubject.enqueuedAt());
        assertThat(result.lastTouched()).isEqualTo(testSubject.lastTouched());
        assertThat(result.diagnostics()).isEqualTo(testSubject.diagnostics());
    }

    @Test
    void invokingWithNullCauseAndOriginalCauseKeepsTheOriginalCause() {
        // Fix the clock to keep time consistent after withCause invocation.
        GenericDeadLetter.clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

        Throwable testThrowable = new RuntimeException("just because");
        ThrowableCause expectedCause = new ThrowableCause(testThrowable);

        DeadLetter<EventMessage<String>> testSubject =
                new GenericDeadLetter<>(SEQUENCE_IDENTIFIER, MESSAGE, testThrowable);

        DeadLetter<EventMessage<String>> result = testSubject.withCause(null);

        assertThat(result.message()).isEqualTo(testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertThat(resultCause).isPresent();
        assertThat(resultCause.get()).isEqualTo(expectedCause);
        assertThat(result.enqueuedAt()).isEqualTo(testSubject.enqueuedAt());
        assertThat(result.lastTouched()).isEqualTo(testSubject.lastTouched());
        assertThat(result.diagnostics()).isEqualTo(testSubject.diagnostics());
    }

    @Test
    void invokingWithDiagnosticsReplacesTheOriginalDiagnostics() {
        // Fix the clock to keep time consistent after withDiagnostics invocation.
        Instant expectedTime = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedTime, ZoneId.systemDefault());

        MetaData originalDiagnostics = MetaData.with("old-key", "old-value");
        MetaData expectedDiagnostics = MetaData.with("new-key", "new-value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(
                SEQUENCE_IDENTIFIER, MESSAGE, null, expectedTime, expectedTime, originalDiagnostics
        );

        DeadLetter<EventMessage<String>> result = testSubject.withDiagnostics(expectedDiagnostics);

        assertThat(result.message()).isEqualTo(testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertThat(resultCause).isNotPresent();
        assertThat(result.enqueuedAt()).isEqualTo(testSubject.enqueuedAt());
        assertThat(result.lastTouched()).isEqualTo(testSubject.lastTouched());
        assertThat(result.diagnostics()).isEqualTo(expectedDiagnostics);
    }

    @Test
    void invokingWithDiagnosticsBuilderAppendsTheOriginalDiagnostics() {
        // Fix the clock to keep time consistent after withDiagnostics invocation.
        Instant expectedTime = Instant.now();
        GenericDeadLetter.clock = Clock.fixed(expectedTime, ZoneId.systemDefault());

        MetaData originalDiagnostics = MetaData.with("old-key", "old-value");
        MetaData expectedDiagnostics = MetaData.with("old-key", "old-value").and("new-key", "new-value");

        DeadLetter<EventMessage<String>> testSubject = new GenericDeadLetter<>(
                SEQUENCE_IDENTIFIER, MESSAGE, null, expectedTime, expectedTime, originalDiagnostics
        );

        DeadLetter<EventMessage<String>> result =
                testSubject.withDiagnostics(original -> original.and("new-key", "new-value"));

        assertThat(result.message()).isEqualTo(testSubject.message());
        Optional<Cause> resultCause = result.cause();
        assertThat(resultCause).isNotPresent();
        assertThat(result.enqueuedAt()).isEqualTo(testSubject.enqueuedAt());
        assertThat(result.lastTouched()).isEqualTo(testSubject.lastTouched());
        assertThat(result.diagnostics()).isEqualTo(expectedDiagnostics);
    }
}