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

package org.sophonhq.sophon.modelling.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sophonhq.sophon.deadline.DeadlineMessage;
import org.sophonhq.sophon.deadline.GenericDeadlineMessage;
import org.sophonhq.sophon.messaging.Message;
import org.sophonhq.sophon.messaging.unitofwork.CurrentUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.UnitOfWork;
import org.sophonhq.sophon.modelling.command.inspection.AnnotatedAggregate;
import org.sophonhq.sophon.modelling.saga.SagaScopeDescriptor;

import java.time.Instant;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AbstractRepository}.
 *
 * @author Allard Buijze
 */
class AbstractRepositoryTest {

    private static final String AGGREGATE_ID = "some-identifier";

    private AbstractRepository<JpaAggregate, AnnotatedAggregate<JpaAggregate>> testSubject;

    private AnnotatedAggregate<JpaAggregate> spiedAggregate;
    private final Message<?> failureMessage = null;

    @BeforeEach
    void setUp() {
        testSubject = new AbstractRepository<JpaAggregate, AnnotatedAggregate<JpaAggregate>>(
                new AbstractRepository.Builder<JpaAggregate>(JpaAggregate.class) {}) {

            @Override
            protected AnnotatedAggregate<JpaAggregate> doCreateNew(Callable<JpaAggregate> factoryMethod)
                    throws Exception {
                return AnnotatedAggregate.initialize(factoryMethod, aggregateModel(), null);
            }

            @Override
            protected void doSave(AnnotatedAggregate<JpaAggregate> aggregate) {

            }

            @Override
            protected void doDelete(AnnotatedAggregate<JpaAggregate> aggregate) {

            }

            @Override
            protected AnnotatedAggregate<JpaAggregate> doLoad(String aggregateIdentifier, Long expectedVersion) {
                spiedAggregate = spy(AnnotatedAggregate.initialize(new JpaAggregate(), aggregateModel(), null));

                try {
                    //noinspection ConstantConditions
                    doThrow(new IllegalArgumentException()).when(spiedAggregate).handle(failureMessage);
                } catch (Exception e) {
                    // Fail silently for testings sake
                }

                if (!AGGREGATE_ID.equals(aggregateIdentifier)) {
                    throw new AggregateNotFoundException(aggregateIdentifier, "some-message");
                }

                return spiedAggregate;
            }
        };

        DefaultUnitOfWork.startAndGet(null);
    }

    @AfterEach
    void tearDown() {
        while (CurrentUnitOfWork.isStarted()) {
            CurrentUnitOfWork.get().rollback();
        }
    }

    @Test
    void aggregateTypeVerification_CorrectType() throws Exception {
        testSubject.newInstance(() -> new JpaAggregate("hi"));
    }

    @Test
    void aggregateTypeVerification_SubclassesAreAllowed() throws Exception {
        testSubject.newInstance(() -> new JpaAggregate("hi") {
            // anonymous subclass
        });
    }

    @Test
    void aggregateTypeVerification_WrongType() {
        //noinspection rawtypes
        AbstractRepository anonymousTestSubject =
                new AbstractRepository<JpaAggregate, AnnotatedAggregate<JpaAggregate>>(
                        new AbstractRepository.Builder<JpaAggregate>(JpaAggregate.class) {}) {

                    @Override
                    protected AnnotatedAggregate<JpaAggregate> doCreateNew(Callable<JpaAggregate> factoryMethod)
                            throws Exception {
                        return AnnotatedAggregate.initialize(factoryMethod, aggregateModel(), null);
                    }

                    @Override
                    protected void doSave(AnnotatedAggregate<JpaAggregate> aggregate) {

                    }

                    @Override
                    protected void doDelete(AnnotatedAggregate<JpaAggregate> aggregate) {

                    }

                    @Override
                    protected AnnotatedAggregate<JpaAggregate> doLoad(String aggregateIdentifier,
                                                                      Long expectedVersion) {
                        return null;
                    }
                };

        //noinspection unchecked
        assertThatThrownBy(() -> anonymousTestSubject.newInstance(() -> "Not allowed"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canResolveReturnsTrueForMatchingAggregateDescriptor() {
        assertThat(testSubject.canResolve(new AggregateScopeDescriptor(
                JpaAggregate.class.getSimpleName(), AGGREGATE_ID)
        )).isTrue();
    }

    @Test
    void canResolveReturnsTrueForExplicitAggregateType() {
        assertThat(testSubject.canResolve(new AggregateScopeDescriptor(
                JpaAggregate.JPA_AGGREGATE_CUSTOM_TYPE_NAME, AGGREGATE_ID)
        )).isTrue();
    }


    @Test
    void canResolveReturnsFalseNonAggregateScopeDescriptorImplementation() {
        assertThat(testSubject.canResolve(new SagaScopeDescriptor("some-saga-type", AGGREGATE_ID))).isFalse();
    }

    @Test
    void canResolveReturnsFalseForNonMatchingAggregateType() {
        assertThat(testSubject.canResolve(new AggregateScopeDescriptor("other-non-matching-type", AGGREGATE_ID)))
                .isFalse();
    }

    @Test
    void sendWorksAsExpected() throws Exception {
        DeadlineMessage<String> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", "payload", Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(JpaAggregate.class.getSimpleName(), AGGREGATE_ID);

        testSubject.send(testMsg, testDescriptor);

        verify(spiedAggregate).handle(testMsg);
    }

    @Test
    void sendThrowsIllegalArgumentExceptionIfHandleFails() throws Exception {
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(JpaAggregate.class.getSimpleName(), AGGREGATE_ID);

        //noinspection ConstantConditions
        assertThatThrownBy(() -> testSubject.send(failureMessage, testDescriptor))
                .isInstanceOf(IllegalArgumentException.class);

        //noinspection ConstantConditions
        verify(spiedAggregate).handle(failureMessage);
    }

    @Test
    void sendFailsSilentlyOnAggregateNotFoundException() throws Exception {
        DeadlineMessage<String> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", "payload", Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(JpaAggregate.class.getSimpleName(), "some-other-aggregate-id");

        testSubject.send(testMsg, testDescriptor);

        verifyNoInteractions(spiedAggregate);
    }

    @Test
    void checkedExceptionFromConstructorDoesNotAttemptToStoreAggregate() {
        // committing the unit of work does not throw an exception
        UnitOfWork<?> uow = CurrentUnitOfWork.get();
        uow.executeWithResult(() -> testSubject.newInstance(() -> {
            throw new Exception("Throwing checked exception");
        }), RuntimeException.class::isInstance);

        assertThat(uow.isActive()).isFalse();
        assertThat(uow.isRolledBack()).isFalse();
        assertThat(uow.getExecutionResult().isExceptionResult()).isTrue();
        assertThat(uow.getExecutionResult().getExceptionResult().getMessage()).isEqualTo("Throwing checked exception");
    }
}
