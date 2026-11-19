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

package org.sophonhq.sophon.commandhandling.gateway;

import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.commandhandling.CommandExecutionException;
import org.sophonhq.sophon.commandhandling.CommandMessage;
import org.sophonhq.sophon.common.SophonNonTransientException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class AbstractRetrySchedulerTest {

    public static class RetrySchedulerStub extends AbstractRetryScheduler {

        protected RetrySchedulerStub(Builder builder) {
            super(builder);
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected long computeRetryInterval(CommandMessage commandMessage, RuntimeException lastFailure, List<Class<? extends Throwable>[]> failures) {
            return 100;
        }

        static Builder builder() {
            return new Builder();
        }

        public static class Builder extends AbstractRetryScheduler.Builder<Builder> {
            RetrySchedulerStub build() {
                return new RetrySchedulerStub(this);
            }
        }
    }

    @Test
    void isExplicitlyNonTransient_defaults() {
        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub.builder().retryExecutor(mock(ScheduledExecutorService.class)).build();
        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new SophonNonTransientException("message") {}))
                .as("SophonNonTransientException should be treated as non-transient by default")
                .isTrue();
    }

    @Test
    void isExplicitlyNonTransient_defaults_and_addNonTransientFailures() {
        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
                .builder()
                .retryExecutor(mock(ScheduledExecutorService.class))
                .addNonTransientFailurePredicate(new NonTransientExceptionClassesPredicate(CommandExecutionException.class, IllegalArgumentException.class))
                .build();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new SophonNonTransientException("message") {}))
                .as("SophonNonTransientException should be treated as non-transient by default")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException("message", null)))
                .as("Per configuration, CommandExecutionException should be treated as non-transient")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("message", null)))
                .as("Per configuration, IllegalArgumentException should be treated as non-transient")
                .isTrue();
    }

    @Test
    void isExplicitlyNonTransient_nonTransientFailurePredicate_throwable() {
        Predicate<Throwable> nonTransientFailurePredicate = (Throwable failure) -> "I'm non-transient failure".equals(failure.getMessage());

        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
                .builder()
                .retryExecutor(mock(ScheduledExecutorService.class))
                .nonTransientFailurePredicate(nonTransientFailurePredicate)
                .build();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new Exception()))
                .as("Per configuration, failures without appropriate message are not considered as non-transient")
                .isFalse();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new Exception("a message")))
                .as("Per configuration, failures without appropriate message are not considered as non-transient")
                .isFalse();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new Exception("I'm non-transient failure")))
                .as("Per configuration, only failures with appropriate message should be treated as non-transient")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new RuntimeException("a message", new NullPointerException("I'm non-transient failure"))))
                .as("Per configuration, only failures with appropriate message should be treated as non-transient")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new Exception("a message", new IllegalArgumentException("a message"))))
                .as("Per configuration, failures without appropriate message are not considered as non-transient")
                .isFalse();
    }

    @Test
    void isExplicitlyNonTransient_nonTransientFailurePredicate_generics() {
        Predicate<IllegalArgumentException> nonTransientFailurePredicate = (failure) -> "I'm non-transient IllegalArgumentException failure".equals(failure.getMessage());

        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
                .builder()
                .retryExecutor(mock(ScheduledExecutorService.class))
                .nonTransientFailurePredicate(IllegalArgumentException.class, nonTransientFailurePredicate)
                .build();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException()))
                .as("Per configuration, IllegalArgumentException is transient only if their message says so")
                .isFalse();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("something")))
                .as("Per configuration, IllegalArgumentException is transient only if their message says so")
                .isFalse();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("I'm non-transient IllegalArgumentException failure")))
                .as("Per configuration, IllegalArgumentException with \"I'm non-transient IllegalArgumentException failure\" message should be treated as non-transient")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("message", new IllegalArgumentException("I'm non-transient IllegalArgumentException failure"))))
                .as("Per configuration, IllegalArgumentException with \"I'm non-transient IllegalArgumentException failure\" message should be treated as non-transient")
                .isTrue();

        // Here we are checking if non-castable failure types in predicate can cause problems - start
        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new RuntimeException()))
                .as("Per configuration, only IllegalArgumentException with appropriate message is treated as non-transient")
                .isFalse();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("a message", new RuntimeException())))
                .as("Per configuration, only IllegalArgumentException with appropriate message is treated as non-transient")
                .isFalse();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException(null, null)))
                .as("Per configuration, only IllegalArgumentException with appropriate message is treated as non-transient")
                .isFalse();
        // Here we are checking if non-castable failure types in predicate can cause problems - end
    }

    @Test
    void isExplicitlyNonTransient_nonTransientFailurePredicate_and_addNonTransientFailures() {
        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
                .builder()
                .retryExecutor(mock(ScheduledExecutorService.class))
                .nonTransientFailurePredicate(
                        // Not really needed (as this is a default), but added for clarity and explicitness.
                        new SophonNonTransientExceptionClassesPredicate()
                )
                .addNonTransientFailurePredicate(
                        new NonTransientExceptionClassesPredicate(CommandExecutionException.class, IllegalArgumentException.class)
                )
                .build();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new SophonNonTransientException("message") {}))
                .as("Per configuration, SophonNonTransientException should be treated as non-transient")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException("message", null)))
                .as("Per configuration, CommandExecutionException should be treated as non-transient")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("message", null)))
                .as("Per configuration, IllegalArgumentException should be treated as non-transient")
                .isTrue();
    }

    @Test
    void isExplicitlyNonTransient_typicalUsageDemo() {
        Predicate<CommandExecutionException> nonTransientCommandExecutionExceptionPredicate =
                (CommandExecutionException failure) -> Objects.equals(failure.getDetails().orElse("transient"), "I'm non-transient");

        RetrySchedulerStub retrySchedulerStub = RetrySchedulerStub
                .builder()
                .retryExecutor(mock(ScheduledExecutorService.class))
                .nonTransientFailurePredicate(
                        new NonTransientExceptionClassesPredicate(
                                SophonNonTransientException.class, NullPointerException.class, IllegalArgumentException.class, IllegalStateException.class
                        )
                )
                .addNonTransientFailurePredicate(CommandExecutionException.class, nonTransientCommandExecutionExceptionPredicate)
                .build();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException("failure", null, "I'm non-transient")))
                .as("Per configuration, CommandExecutionException with appropriate details should be treated as non-transient")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new CommandExecutionException("failure", null, "a details")))
                .as("Per configuration, CommandExecutionException with unexpected details should be treated as transient")
                .isFalse();

        Exception extendedFromCommandExecutionException = new CommandExecutionException("failure", null, "I'm non-transient") {};
        assertThat(retrySchedulerStub.isExplicitlyNonTransient(extendedFromCommandExecutionException))
                .as("Per configuration, CommandExecutionException descendants, with appropriate message, should be treated as non-transient")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new NullPointerException("a null")))
                .as("Per configuration, NullPointerException should be treated as non-transient")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new IllegalArgumentException("illegal")))
                .as("Per configuration, IllegalArgumentException should be treated as non-transient")
                .isTrue();

        assertThat(retrySchedulerStub.isExplicitlyNonTransient(new RuntimeException("runtime problem")))
                .as("Per configuration, RuntimeException should be treated as a transient failure")
                .isFalse();
    }
}
