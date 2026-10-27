/*
 * Copyright (c) 2010-2025. Axon Framework
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

package org.sophonhq.sophon.deadline.jobrunr;

import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.states.IllegalJobStateChangeException;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobBuilder;
import org.jobrunr.scheduling.JobScheduler;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.common.transaction.NoTransactionManager;
import org.sophonhq.sophon.common.transaction.TransactionManager;
import org.sophonhq.sophon.deadline.AbstractDeadlineManager;
import org.sophonhq.sophon.deadline.DeadlineException;
import org.sophonhq.sophon.deadline.DeadlineManager;
import org.sophonhq.sophon.deadline.DeadlineManagerSpanFactory;
import org.sophonhq.sophon.deadline.DeadlineMessage;
import org.sophonhq.sophon.deadline.DefaultDeadlineManagerSpanFactory;
import org.sophonhq.sophon.deadline.GenericDeadlineMessage;
import org.sophonhq.sophon.deadline.quartz.QuartzDeadlineManager;
import org.sophonhq.sophon.lifecycle.Lifecycle;
import org.sophonhq.sophon.lifecycle.Phase;
import org.sophonhq.sophon.messaging.DefaultInterceptorChain;
import org.sophonhq.sophon.messaging.ExecutionException;
import org.sophonhq.sophon.messaging.InterceptorChain;
import org.sophonhq.sophon.messaging.ResultMessage;
import org.sophonhq.sophon.messaging.ScopeAwareProvider;
import org.sophonhq.sophon.messaging.ScopeDescriptor;
import org.sophonhq.sophon.messaging.unitofwork.DefaultUnitOfWork;
import org.sophonhq.sophon.messaging.unitofwork.UnitOfWork;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.SimpleSerializedObject;
import org.sophonhq.sophon.tracing.NoOpSpanFactory;
import org.sophonhq.sophon.tracing.Span;
import org.sophonhq.sophon.tracing.SpanFactory;
import org.sophonhq.sophon.tracing.SpanScope;

import java.time.Instant;
import java.util.UUID;

import static java.lang.String.format;
import static org.slf4j.LoggerFactory.getLogger;
import static org.sophonhq.sophon.common.BuilderUtils.assertNonNull;
import static org.sophonhq.sophon.deadline.GenericDeadlineMessage.asDeadlineMessage;
import static org.sophonhq.sophon.deadline.jobrunr.LabelUtils.getCombinedLabel;
import static org.sophonhq.sophon.deadline.jobrunr.LabelUtils.getLabel;

/**
 * Implementation of {@link DeadlineManager} that delegates scheduling and triggering to a Jobrunr
 * {@link JobScheduler}.
 *
 * @author Tom de Backer
 * @author Gerard Klijs
 * @since axon-4.7.0
 */
public class JobRunrDeadlineManager extends AbstractDeadlineManager implements Lifecycle {

    private static final Logger logger = getLogger(JobRunrDeadlineManager.class);
    protected static final String DELETE_REASON = "Deleted via Sophon DeadlineManager API";
    private static final String NOT_SUPPORTED_MSG =
            "The '%s' method is not supported without using JobRunrPro with the JobRunrProDeadlineManager.\n"
                    + "Move to the pro version and the extension or use 'cancelSchedule' method instead.\n"
                    + "Using 'cancelSchedule' requires keeping track of the returned 'scheduleId' from invoking 'schedule'.";

    private final ScopeAwareProvider scopeAwareProvider;
    private final JobScheduler jobScheduler;
    private final Serializer serializer;
    private final TransactionManager transactionManager;
    private final DeadlineManagerSpanFactory spanFactory;

    /**
     * Instantiate a Builder to be able to create a {@link JobRunrDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link SpanFactory} is defaulted to a {@link NoOpSpanFactory}.
     * <p>
     * The {@link JobScheduler}, {@link ScopeAwareProvider} and {@link Serializer} are <b>hard requirements</b> and as
     * such should be provided.
     *
     * @return a Builder to be able to create a {@link JobRunrDeadlineManager}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link JobRunrDeadlineManager} based on the fields contained in the
     * {@link JobRunrDeadlineManager.Builder}.
     * <p>
     * Will assert that the {@link ScopeAwareProvider}, {@link JobScheduler} and {@link Serializer} are not
     * {@code null}, and will throw an {@link SophonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link QuartzDeadlineManager.Builder} used to instantiate a {@link QuartzDeadlineManager}
     *                instance
     */
    protected JobRunrDeadlineManager(Builder builder) {
        builder.validate();
        this.scopeAwareProvider = builder.scopeAwareProvider;
        this.jobScheduler = builder.jobScheduler;
        this.serializer = builder.serializer;
        this.transactionManager = builder.transactionManager;
        this.spanFactory = builder.spanFactory;
    }

    /**
     * Provides the class to use in spans,
     *
     * @return the class name
     */
    @SuppressWarnings("squid:S3400")
    protected String getSpanClassName() {
        return "JobRunrDeadlineManager";
    }

    @Override
    public String schedule(@NonNull Instant triggerDateTime, @NonNull String deadlineName,
                           @Nullable Object messageOrPayload,
                           @NonNull ScopeDescriptor deadlineScope) {
        DeadlineMessage<Object> deadlineMessage = asDeadlineMessage(deadlineName, messageOrPayload, triggerDateTime);
        UUID deadlineId = UUID.randomUUID();
        Span span = spanFactory.createScheduleSpan(deadlineName, deadlineId.toString(), deadlineMessage);
        runOnPrepareCommitOrNow(span.wrapRunnable(() -> {
            DeadlineMessage<Object> interceptedDeadlineMessage = processDispatchInterceptors(deadlineMessage);
            String serializedDeadlineDetails = DeadlineDetails.serialized(
                    deadlineName,
                    deadlineScope,
                    interceptedDeadlineMessage,
                    serializer);
            String combinedLabel = getCombinedLabel(serializer, deadlineName, deadlineScope);
            JobBuilder job = JobBuilder.aJob()
                                       .withId(deadlineId)
                                       .withName(deadlineName)
                                       .withLabels(getLabel(deadlineName), combinedLabel)
                                       .withDetails(() -> this.execute(serializedDeadlineDetails,
                                                                       deadlineId.toString()))
                                       .scheduleAt(triggerDateTime);
            JobId id = jobScheduler.create(job);
            logger.debug("Job with id: [{}] was successfully created.", id);
        }));
        return deadlineId.toString();
    }

    @Override
    public void cancelSchedule(@NonNull String deadlineName, @NonNull String scheduleId) {
        Span span = spanFactory.createCancelScheduleSpan(deadlineName, scheduleId);
        runOnPrepareCommitOrNow(span.wrapRunnable(() -> {
            try {
                jobScheduler.delete(toUuid(scheduleId), DELETE_REASON);
            } catch (IllegalJobStateChangeException e) {
                if (!tryingToDeleteAlreadyDeletedJob(e.getFrom(), e.getTo())) {
                    throw e;
                }
            }
        }));
    }

    /**
     * Protective measure to ensure that if the scheduled job was already deleted, we ignore a second try to delete the
     * job.
     */
    private static boolean tryingToDeleteAlreadyDeletedJob(StateName from, StateName to) {
        return from == StateName.DELETED && to == StateName.DELETED;
    }

    @Override
    public void cancelAll(@NonNull String deadlineName) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_MSG, "cancelAll"));
    }

    private UUID toUuid(@NonNull String scheduleId) {
        try {
            return UUID.fromString(scheduleId);
        } catch (IllegalArgumentException e) {
            throw new DeadlineException("For JobRunr the scheduleId should be an UUID representation.", e);
        }
    }

    @Override
    public void cancelAllWithinScope(@NonNull String deadlineName, @NonNull ScopeDescriptor scope) {
        throw new UnsupportedOperationException(String.format(NOT_SUPPORTED_MSG, "cancelAllWithinScope"));
    }


    /**
     * This function should only be called via JobRunr when a deadline was triggered. It will try to execute the
     * scheduled deadline on the set scope. It will throw a {@link DeadlineException} in case of errors such that they
     * will be optionally retried by JobRunr.
     *
     * @param serializedDeadlineDetails {@code byte[]} containing the serialized {@link DeadlineDetails} object with all
     *                                  the needed details to execute.
     * @deprecated Kept for backwards compatibility, use {@link #execute(String, String)} instead.
     */
    @Deprecated
    public void execute(@NonNull String serializedDeadlineDetails) {
        execute(serializedDeadlineDetails, null);
    }

    /**
     * This function should only be called via JobRunr when a deadline was triggered. It will try to execute the
     * scheduled deadline on the set scope. It will throw a {@link DeadlineException} in case of errors such that they
     * will be optionally retried by JobRunr.
     *
     * @param serializedDeadlineDetails {@code byte[]} containing the serialized {@link DeadlineDetails} object with all
     *                                  the needed details to execute.
     * @param deadlineId                The {@link UUID} of the deadline.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void execute(@NonNull String serializedDeadlineDetails, String deadlineId) {
        SimpleSerializedObject<String> serializedDeadlineMetaData = new SimpleSerializedObject<>(
                serializedDeadlineDetails, String.class, DeadlineDetails.class.getName(), null
        );
        DeadlineDetails deadlineDetails = serializer.deserialize(serializedDeadlineMetaData);
        GenericDeadlineMessage deadlineMessage = deadlineDetails.asDeadLineMessage(serializer);
        Span span = spanFactory.createExecuteSpan(deadlineDetails.getDeadlineName(),
                                                  deadlineId,
                                                  deadlineMessage).start();
        try (SpanScope ignored = span.makeCurrent()) {
            UnitOfWork<DeadlineMessage<?>> unitOfWork = new DefaultUnitOfWork<>(deadlineMessage);
            unitOfWork.attachTransaction(transactionManager);
            unitOfWork.onRollback(uow -> span.recordException(uow.getExecutionResult().getExceptionResult()));
            InterceptorChain chain = new DefaultInterceptorChain<>(
                    unitOfWork,
                    handlerInterceptors(),
                    interceptedDeadlineMessage -> {
                        executeScheduledDeadline(interceptedDeadlineMessage,
                                                 deadlineDetails.getDeserializedScopeDescriptor(serializer));
                        return null;
                    });
            ResultMessage<?> resultMessage = unitOfWork.executeWithResult(chain::proceed);
            if (resultMessage.isExceptional()) {
                Throwable e = resultMessage.exceptionResult();
                span.recordException(e);
                logger.warn("An error occurred while triggering deadline with name [{}]. Original message: [{}]",
                            deadlineDetails.getDeadlineName(), e.getMessage());
                throw new DeadlineException("Failed to process", e);
            }
        } finally {
            span.end();
        }
    }

    @SuppressWarnings("Duplicates")
    private void executeScheduledDeadline(DeadlineMessage<?> deadlineMessage, ScopeDescriptor deadlineScope) {
        scopeAwareProvider.provideScopeAwareStream(deadlineScope)
                          .filter(scopeAwareComponent -> scopeAwareComponent.canResolve(deadlineScope))
                          .forEach(scopeAwareComponent -> {
                              try {
                                  scopeAwareComponent.send(deadlineMessage, deadlineScope);
                              } catch (Exception e) {
                                  String exceptionMessage = format(
                                          "Failed to send a DeadlineMessage for scope [%s]",
                                          deadlineScope.scopeDescription()
                                  );
                                  throw new ExecutionException(exceptionMessage, e);
                              }
                          });
    }

    @Override
    public void shutdown() {
        jobScheduler.shutdown();
    }

    @Override
    public void registerLifecycleHandlers(@NonNull LifecycleRegistry lifecycle) {
        lifecycle.onShutdown(Phase.INBOUND_EVENT_CONNECTORS, this::shutdown);
    }

    /**
     * Builder class to instantiate a {@link JobRunrDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager} and the
     * {@link DeadlineManagerSpanFactory} defaults to {@link DefaultDeadlineManagerSpanFactory} backed by a
     * {@link NoOpSpanFactory}.
     * <p>
     * The {@link JobScheduler}, {@link ScopeAwareProvider} and {@link Serializer} are <b>hard requirements</b> and as
     * such should be provided.
     */
    public static class Builder {

        private JobScheduler jobScheduler;
        private ScopeAwareProvider scopeAwareProvider;
        private Serializer serializer;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private DeadlineManagerSpanFactory spanFactory = DefaultDeadlineManagerSpanFactory.builder()
                                                                                          .spanFactory(NoOpSpanFactory.INSTANCE)
                                                                                          .build();

        /**
         * Sets the {@link JobScheduler} used for scheduling and triggering purposes of the deadlines.
         *
         * @param jobScheduler a {@link JobScheduler} used for scheduling and triggering purposes of the deadlines
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder jobScheduler(JobScheduler jobScheduler) {
            assertNonNull(jobScheduler, "JobScheduler may not be null");
            this.jobScheduler = jobScheduler;
            return this;
        }

        /**
         * Sets the {@link ScopeAwareProvider} which is capable of providing a stream of
         * {@link org.sophonhq.sophon.messaging.Scope} instances for a given {@link ScopeDescriptor}. Used to return the
         * right Scope to trigger a deadline in.
         *
         * @param scopeAwareProvider a {@link ScopeAwareProvider} used to find the right
         *                           {@link org.sophonhq.sophon.messaging.Scope} to trigger a deadline in
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder scopeAwareProvider(ScopeAwareProvider scopeAwareProvider) {
            assertNonNull(scopeAwareProvider, "ScopeAwareProvider may not be null");
            this.scopeAwareProvider = scopeAwareProvider;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize the {@code payload},
         * {@link org.sophonhq.sophon.messaging.MetaData} and the {@link ScopeDescriptor} into the {@link DeadlineDetails}
         * as well as the whole {@link DeadlineDetails} itself.
         *
         * @param serializer a {@link Serializer} used to de-/serialize the {@code payload},
         *                   {@link org.sophonhq.sophon.messaging.MetaData} and the {@link ScopeDescriptor} into the
         *                   {@link DeadlineDetails}, as well as the whole {@link DeadlineDetails} itself.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to build transactions and ties them to deadline. Defaults to a
         * {@link NoTransactionManager}.
         *
         * @param transactionManager a {@link TransactionManager} used to build transactions and ties them to deadline
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link SpanFactory} implementation to use for providing tracing capabilities. Defaults to a
         * {@link NoOpSpanFactory} by default, which provides no tracing capabilities.
         *
         * @param spanFactory The {@link SpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         * @deprecated Use {@link #spanFactory(DeadlineManagerSpanFactory)} instead as it provides more configuration
         * options.
         */
        @Deprecated
        public Builder spanFactory(@NonNull SpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = DefaultDeadlineManagerSpanFactory.builder().spanFactory(spanFactory).build();
            return this;
        }

        /**
         * Sets the {@link DeadlineManagerSpanFactory} implementation to use for providing tracing capabilities.
         * Defaults to a {@link DefaultDeadlineManagerSpanFactory} backed by a {@link NoOpSpanFactory} by default, which
         * provides no tracing capabilities.
         *
         * @param spanFactory The {@link DeadlineManagerSpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@NonNull DeadlineManagerSpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        /**
         * Initializes a {@link JobRunrDeadlineManager} as specified through this Builder.
         *
         * @return a {@link JobRunrDeadlineManager} as specified through this Builder
         */
        public JobRunrDeadlineManager build() {
            return new JobRunrDeadlineManager(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws SophonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws SophonConfigurationException {
            assertNonNull(scopeAwareProvider, "The ScopeAwareProvider is a hard requirement and should be provided.");
            assertNonNull(jobScheduler, "The JobScheduler is a hard requirement and should be provided.");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided.");
        }
    }
}
