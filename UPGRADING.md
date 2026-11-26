# Upgrading Sophon

This guide describes what changes between major versions of Sophon and what you do to upgrade. To move an
application from Axon Framework 4 to Sophon, read [MIGRATING.md](MIGRATING.md) first.

## Upgrading from 1.x to 2.0

### Before you start

* Move your application to Java 21 or newer.
* Move your application to Spring Boot 4. If you use `sophon-spring` without Spring Boot, move to Spring Framework 7.
* If your application uses Hibernate ORM, move it to Hibernate ORM 7 while it runs on Sophon 1.2.x. Sophon 2.0
  runs its tests only on Hibernate ORM 7.
    * Without `org.hibernate.orm:hibernate-scan-jandex` on the classpath, Hibernate ORM 7 does not scan for entity
      classes. Spring Boot applications are not affected, because Spring Boot scans Sophon's entity packages itself.
    * If your persistence unit relies on Hibernate's scan to find Sophon's entity classes, add
      `hibernate-scan-jandex`. As an alternative, list Sophon's entity classes in `persistence.xml`:
        * Token store: `org.sophonhq.sophon.eventhandling.tokenstore.jpa.TokenEntry`.
        * Event store: `org.sophonhq.sophon.eventsourcing.eventstore.jpa.DomainEventEntry` and
          `org.sophonhq.sophon.eventsourcing.eventstore.jpa.SnapshotEventEntry`.
        * Saga store: `org.sophonhq.sophon.modelling.saga.repository.jpa.SagaEntry` and
          `org.sophonhq.sophon.modelling.saga.repository.jpa.AssociationValueEntry`.
        * Dead-letter queue: `org.sophonhq.sophon.eventhandling.deadletter.jpa.DeadLetterEntry`.
* Upgrade to the latest 1.2.x release first.
* Compile your application with deprecation warnings enabled (`-Xlint:deprecation`), and replace every deprecated
  Sophon API that it uses. Sophon 2.0 removes every API that 1.x marks as deprecated.
* If your application needs a removed API, stay on 1.x.

In the tables below, class names are relative to the `org.sophonhq.sophon` package. For example,
`spring.config.AnnotationDriven` is `org.sophonhq.sophon.spring.config.AnnotationDriven`.

### Removed dependencies

Sophon 2.0 no longer works with these libraries. Move your application to the replacement before you upgrade.

| Removed library                            | Use instead                                                                                    |
|--------------------------------------------|------------------------------------------------------------------------------------------------|
| Java Persistence API (`javax.persistence`) | Jakarta Persistence (`jakarta.persistence`)                                                    |
| Bean Validation (`javax.validation`)       | Jakarta Bean Validation (`jakarta.validation`)                                                 |
| Ehcache 2 (`net.sf.ehcache`)               | Ehcache 3 with `common.caching.EhCache3Adapter`, or JCache with `common.caching.JCacheAdapter` |
| JUnit 4                                    | JUnit 5                                                                                        |
| Spring Boot 2 and 3                        | Spring Boot 4                                                                                  |
| Spring Framework 5 and 6                   | Spring Framework 7                                                                             |
| Spring Security 5 and 6                    | Spring Security 7                                                                              |

### Removed API

Sophon 2.0 removes the `legacyjpa` and `legacyvalidation` packages. Each class in them has a Jakarta replacement with
the same simple name. To move to the replacement, change your `javax.persistence` and `javax.validation` imports to
`jakarta.persistence` and `jakarta.validation`. Then import the replacement class that the tables below name.

Sophon 2.0 also removes the `spanFactory(SpanFactory)` method from the builders of the buses, event processors,
deadline managers, repositories, saga managers and snapshotters. Use the `spanFactory` method that takes the span
factory type of the component, for example `SimpleCommandBus.Builder.spanFactory(CommandBusSpanFactory)`. To create
such a span factory from a `SpanFactory`, use the builder of its default implementation, for example
`DefaultCommandBusSpanFactory.builder().spanFactory(spanFactory).build()`.

#### `sophon-messaging`

| Removed                                                                                                                             | Use instead                                                                                                                                                                                                   |
|-------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `common.legacyjpa.EntityManagerProvider`                                                                                            | `common.jpa.EntityManagerProvider`                                                                                                                                                                            |
| `common.legacyjpa.SimpleEntityManagerProvider`                                                                                      | `common.jpa.SimpleEntityManagerProvider`                                                                                                                                                                      |
| `common.legacyjpa.PagingJpaQueryIterable`                                                                                           | `common.jpa.PagingJpaQueryIterable`                                                                                                                                                                           |
| `eventhandling.deadletter.legacyjpa.DeadLetterJpaConverter`                                                                         | `eventhandling.deadletter.jpa.DeadLetterJpaConverter`                                                                                                                                                         |
| `eventhandling.deadletter.legacyjpa.EventMessageDeadLetterJpaConverter`                                                             | `eventhandling.deadletter.jpa.EventMessageDeadLetterJpaConverter`                                                                                                                                             |
| `eventhandling.deadletter.legacyjpa.JpaDeadLetter`                                                                                  | `eventhandling.deadletter.jpa.JpaDeadLetter`                                                                                                                                                                  |
| `eventhandling.deadletter.legacyjpa.JpaSequencedDeadLetterQueue`                                                                    | `eventhandling.deadletter.jpa.JpaSequencedDeadLetterQueue`                                                                                                                                                    |
| `eventhandling.tokenstore.legacyjpa.JpaTokenStore`                                                                                  | `eventhandling.tokenstore.jpa.JpaTokenStore`                                                                                                                                                                  |
| `messaging.interceptors.legacyvalidation.BeanValidationInterceptor`                                                                 | `messaging.interceptors.BeanValidationInterceptor`                                                                                                                                                            |
| `messaging.interceptors.legacyvalidation.JSR303ViolationException`                                                                  | `messaging.interceptors.JSR303ViolationException`                                                                                                                                                             |
| `common.caching.EhCacheAdapter`                                                                                                     | `common.caching.EhCache3Adapter`                                                                                                                                                                              |
| The `eventhandling.ReplayToken` constructors                                                                                        | `ReplayToken.createReplayToken(...)` with the same arguments                                                                                                                                                  |
| `eventhandling.EventUtils.asDomainEventMessage(EventMessage)`                                                                       | None, the method was for internal use                                                                                                                                                                         |
| `eventhandling.TrackingEventProcessorConfiguration.andWorkerTerminationTimeout(long)`                                               | `andWorkerTerminationTimeout(long, TimeUnit)`                                                                                                                                                                 |
| `eventhandling.pooled.PooledStreamingEventProcessor.Builder.workerExecutorService(ScheduledExecutorService)`                        | `workerExecutor(ScheduledExecutorService)`                                                                                                                                                                    |
| `eventhandling.tokenstore.jdbc.TokenSchema.ownerColum()` and `TokenSchema.Builder.setOwnerColum(String)`                            | `ownerColumn()` and `setOwnerColumn(String)`                                                                                                                                                                  |
| `eventhandling.scheduling.quartz.QuartzEventScheduler.DirectEventJobDataBinder()`                                                   | `DirectEventJobDataBinder(Serializer)`                                                                                                                                                                        |
| `messaging.annotation.AnnotatedHandlerInspector.getHandlers()`                                                                      | `getAllHandlers()` or `getHandlers(Class)`                                                                                                                                                                    |
| `messaging.annotation.InterceptorChainParameterResolverFactory.initialize(InterceptorChain)`                                        | `callWithInterceptorChain(InterceptorChain, Callable)`                                                                                                                                                        |
| `messaging.responsetypes.AbstractResponseType.unwrapIfTypeFuture(Type)`                                                             | `common.ReflectionUtils.unwrapIfType(Type, Class...)`                                                                                                                                                         |
| `commandhandling.distributed.AbstractRoutingStrategy(UnresolvedRoutingKeyPolicy)`                                                   | `AbstractRoutingStrategy(RoutingStrategy)`                                                                                                                                                                    |
| The constructors of `commandhandling.distributed.AnnotationRoutingStrategy` and `MetaDataRoutingStrategy`                           | Their `builder()`                                                                                                                                                                                             |
| `commandhandling.distributed.CommandCallbackRepository.cancelCallbacks(Object)`                                                     | `cancelCallbacksForChannel(Object)`                                                                                                                                                                           |
| `commandhandling.callbacks.FailureLoggingCallback(CommandCallback)` and `FailureLoggingCallback(Logger, CommandCallback)`           | `new FailureLoggingCallback<>(logger).wrap(callback)`                                                                                                                                                         |
| `queryhandling.SubscriptionQueryResult.handle(Consumer, Consumer)`                                                                  | `handle(Consumer, Consumer, Consumer)`, with a consumer for errors                                                                                                                                            |
| `serialization.xml.XStreamSerializer.defaultSerializer()`                                                                           | `XStreamSerializer.builder()` with an `XStream` instance that allows your types                                                                                                                               |
| `deadline.GenericDeadlineMessage.asDeadlineMessage(String, Object)`                                                                 | `asDeadlineMessage(String, Object, Instant)`                                                                                                                                                                  |
| `serialization.JavaSerializer`                                                                                                      | `serialization.xml.XStreamSerializer`, `serialization.json.JacksonSerializer` or `serialization.jackson3.Jackson3Serializer`. For data that `JavaSerializer` wrote, see [Stored data](#stored-data)           |
| `queryhandling.SubscriptionQueryBackpressure`, and the methods of `QueryBus`, `QueryGateway` and `QueryUpdateEmitter` that take one | The same methods without the `SubscriptionQueryBackpressure` parameter. To control backpressure, use an `onBackpressure...` operator on the updates, for example `result.updates().onBackpressureBuffer(100)` |
| `tracing.NestingSpanFactory`                                                                                                        | The `distributedInSameTrace` setting of `DefaultCommandBusSpanFactory`, `DefaultQueryBusSpanFactory` and `DefaultEventProcessorSpanFactory`                                                                   |
| `deadline.quartz.DeadlineJob.DeadlineJobDataBinder.SERIALIZED_DEADLINE_MESSAGE` and `SERIALIZED_DEADLINE_MESSAGE_CLASS_NAME`        | None. No Sophon version writes deadline jobs with these keys                                                                                                                                                  |
| `eventhandling.scheduling.quartz.QuartzEventScheduler.DirectEventJobDataBinder.EVENT_KEY`                                           | None. No Sophon version writes event jobs with this key                                                                                                                                                       |
| `deadline.jobrunr.JobRunrDeadlineManager.execute(String)`                                                                           | `execute(String, String)`. No Sophon version schedules JobRunr jobs that call the one-argument method                                                                                                         |

#### `sophon-modelling`

| Removed                                                                                         | Use instead                                                                                                                      |
|-------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `modelling.command.legacyjpa.GenericJpaRepository`                                              | `modelling.command.GenericJpaRepository`                                                                                         |
| `modelling.saga.repository.legacyjpa.JpaSagaStore`                                              | `modelling.saga.repository.jpa.JpaSagaStore`                                                                                     |
| `modelling.saga.repository.jpa.AbstractSagaEntry`                                               | `modelling.saga.repository.jpa.SagaEntry`, which does not extend a base class                                                    |
| `modelling.command.AggregateIdentifier.routingKey`                                              | None, Sophon did not use it. Commands find their aggregate through `@TargetAggregateIdentifier`                                  |
| `modelling.command.VersionedAggregateIdentifier(String, Long)`                                  | `VersionedAggregateIdentifier(Object, Long)`                                                                                     |
| `modelling.command.AnnotationCommandTargetResolver()`                                           | `AnnotationCommandTargetResolver.builder()`                                                                                      |
| `modelling.command.inspection.EntityModel.commandHandlers()` and `commandHandlerInterceptors()` | `allCommandHandlers()` or `commandHandlers(Class)`, and `allCommandHandlerInterceptors()` or `commandHandlerInterceptors(Class)` |
| `modelling.command.inspection.AbstractChildEntityDefinition.isFieldTypeSupported(Field)`        | `isMemberTypeSupported(Member)`                                                                                                  |
| `modelling.command.inspection.CommandHandlerInterceptorHandlingMember`                          | `messaging.annotation.MessageInterceptingMember`                                                                                 |
| `modelling.saga.repository.jdbc.SagaSchema(String, String)`                                     | `SagaSchema.builder()`                                                                                                           |

#### `sophon-eventsourcing`

| Removed                                                                                                                 | Use instead                                                                             |
|-------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| `eventsourcing.eventstore.legacyjpa.EmbeddedEventStore`                                                                 | `eventsourcing.eventstore.EmbeddedEventStore`                                           |
| `eventsourcing.eventstore.legacyjpa.JpaEventStorageEngine`                                                              | `eventsourcing.eventstore.jpa.JpaEventStorageEngine`                                    |
| `eventsourcing.eventstore.legacyjpa.SQLErrorCodesResolver`                                                              | `eventsourcing.eventstore.jpa.SQLErrorCodesResolver`                                    |
| `eventsourcing.MultiStreamableMessageSource`                                                                            | `eventhandling.MultiStreamableMessageSource` in `sophon-messaging`                      |
| `snapshotFilter(Predicate)` on the builders of `eventsourcing.eventstore.AbstractEventStorageEngine` and its subclasses | `snapshotFilter(SnapshotFilter)`                                                        |
| `eventsourcing.eventstore.jdbc.JdbcEventStorageEngine.domainEventFields()` and `trackedEventFields()`                   | `EventSchema.domainEventFields()` and `EventSchema.trackedEventFields()`                |
| `JdbcEventStorageEngine.setGapTimeout(int)` and `setGapCleaningThreshold(int)`                                          | `gapTimeout(int)` and `gapCleaningThreshold(int)` on `JdbcEventStorageEngine.builder()` |
| `eventsourcing.snapshotting.RevisionSnapshotFilter(String)`                                                             | `RevisionSnapshotFilter.builder()`                                                      |

#### `sophon-configuration`

| Removed                                                                                             | Use instead                                                                                                                        |
|-----------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `config.ModuleConfiguration.phase()`, `start()` and `shutdown()`                                    | None. Register lifecycle handlers in `initialize(Configuration)` with `Configuration.onStart` and `Configuration.onShutdown`       |
| `config.Configurer.registerCommandHandler(int, Function)` and `registerQueryHandler(int, Function)` | `registerCommandHandler(Function)` and `registerQueryHandler(Function)`. Set the phase with `@StartHandler` and `@ShutdownHandler` |

#### `sophon-spring`

Sophon 2.0 removes the classic Spring configuration. Use the Spring Boot auto-configuration, or register the beans
that replace it, as the table shows.

The replacement beans treat some beans differently:

* `MessageHandlerLookup` registers every singleton bean that has message handler methods as a handler. Make sure
  that each aggregate class, including each subtype of a polymorphic aggregate, is a prototype bean. The `@Aggregate`
  stereotype does this for you.
* `SpringConfigurer` does not make Sophon components such as the `EventBus` or the `CommandGateway` available as
  Spring beans. Get them from the Sophon `Configuration` bean, or define them as beans yourself.
* `SpringConfigurer` does not create an event store from an `EventStorageEngine` bean. Define an event store bean,
  for example an `EmbeddedEventStore`.

| Removed                                                                                                           | Use instead                                                                                                                                                                       |
|-------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `spring.config.AnnotationDriven`                                                                                  | Spring Boot auto-configuration, or beans of `spring.config.MessageHandlerLookup`, `SpringAggregateLookup`, `SpringSagaLookup`, `SpringConfigurer` and `SpringSophonConfiguration` |
| `spring.config.AnnotationDrivenRegistrar`                                                                         | The same beans as for `AnnotationDriven`                                                                                                                                          |
| `spring.config.SpringSophonAutoConfigurer`                                                                        | `spring.config.SpringConfigurer` and `spring.config.SpringSophonConfiguration`                                                                                                    |
| `spring.config.SophonConfiguration`                                                                               | `spring.config.SpringConfigurer` and `spring.config.SpringSophonConfiguration`                                                                                                    |
| `spring.config.ConfigurerFactoryBean`                                                                             | `spring.config.SpringConfigurer`                                                                                                                                                  |
| `spring.config.RepositoryFactoryBean`                                                                             | `spring.config.SpringAggregateLookup` and `spring.config.SpringAggregateConfigurer`                                                                                               |
| `spring.modelling.SpringRepositoryFactoryBean`                                                                    | Get the `Repository` from the Sophon `Configuration`, or use the factory methods of `spring.config.BeanHelper`                                                                    |
| `spring.config.AbstractAnnotationHandlerBeanPostProcessor`                                                        | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                                                 |
| `spring.config.CommandHandlerSubscriber`                                                                          | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                                                 |
| `spring.config.EventHandlerRegistrar`                                                                             | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                                                 |
| `spring.config.QueryHandlerSubscriber`                                                                            | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                                                 |
| `spring.config.annotation.AnnotationCommandHandlerBeanPostProcessor`                                              | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                                                 |
| `spring.config.annotation.AnnotationQueryHandlerBeanPostProcessor`                                                | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                                                 |
| `spring.config.ApplicationContextLookupParameterResolverFactory`                                                  | `spring.config.annotation.SpringParameterResolverFactoryBean`                                                                                                                     |
| `spring.config.annotation.SpringContextParameterResolverFactoryBuilder`                                           | `spring.config.annotation.SpringParameterResolverFactoryBean`                                                                                                                     |
| `spring.config.annotation.SpringContextHandlerDefinitionBuilder`                                                  | `spring.config.annotation.HandlerDefinitionFactoryBean`                                                                                                                           |
| `spring.config.annotation.SpringHandlerDefinitionBean`                                                            | `spring.config.annotation.HandlerDefinitionFactoryBean`                                                                                                                           |
| `spring.config.annotation.SpringHandlerEnhancerDefinitionBean`                                                    | `spring.config.annotation.HandlerDefinitionFactoryBean`                                                                                                                           |
| `spring.config.NoBeanOfType`                                                                                      | Spring Boot's `@ConditionalOnMissingBean`                                                                                                                                         |
| `spring.config.TransactionManagerFactoryBean`                                                                     | A bean of `spring.messaging.unitofwork.SpringTransactionManager`                                                                                                                  |
| `spring.eventsourcing.SpringPrototypeAggregateFactory(String)` and `SpringPrototypeAggregateFactory(String, Map)` | `SpringPrototypeAggregateFactory(Class, String, Map)`                                                                                                                             |

#### `sophon-spring-boot-autoconfigure`

The JPA auto-configuration needs Jakarta Persistence on the classpath.

| Removed                                                               | Use instead                                                 |
|-----------------------------------------------------------------------|-------------------------------------------------------------|
| `springboot.autoconfig.legacyjpa.JpaJavaxAutoConfiguration`           | `springboot.autoconfig.JpaAutoConfiguration`                |
| `springboot.autoconfig.legacyjpa.JpaJavaxEventStoreAutoConfiguration` | `springboot.autoconfig.JpaEventStoreAutoConfiguration`      |
| `springboot.util.legacyjpa.ContainerManagedEntityManagerProvider`     | `springboot.util.jpa.ContainerManagedEntityManagerProvider` |

#### `sophon-metrics` and `sophon-micrometer`

| Removed                                                                                                      | Use instead                                                                                                                     |
|--------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| The constructors of `metrics.MessageTimerMonitor`                                                            | `MessageTimerMonitor.builder()`                                                                                                 |
| The `buildMonitor` methods of `micrometer.MessageTimerMonitor` and `micrometer.EventProcessorLatencyMonitor` | Their `builder()`                                                                                                               |
| `micrometer.GlobalMetricRegistry.registerEventProcessor(String, Function)`                                   | `registerEventProcessor(String, Function, Function)`, with separate tags for the latency metric                                 |
| `micrometer.PayloadTypeMessageMonitorWrapper`                                                                | Tags on the monitors. `micrometer.GlobalMetricRegistry.registerWithConfigurerWithDefaultTags(Configurer)` gives the same result |

#### `sophon-test`

| Removed                                                                                                                                              | Use instead                                                                           |
|------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| `test.aggregate.StubAggregateLifecycleRule`                                                                                                          | `test.aggregate.StubAggregateLifecycleExtension`, with JUnit 5                        |
| `test.matchers.Matchers.equalTo(Object)` and `equalTo(Object, FieldFilter)`                                                                          | `Matchers.deepEquals(Object)` and `deepEquals(Object, FieldFilter)`                   |
| `test.utils.AutowiredResourceInjector`                                                                                                               | `modelling.saga.SimpleResourceInjector`                                               |
| `test.aggregate.Reporter.reportWrongEvent(Collection, StringDescription, Throwable)`                                                                 | `reportWrongEvent(Collection, Description, Description, Throwable)`                   |
| `expectDeadlinesMet(Object...)` and `expectDeadlinesMetMatching(Matcher)` on `test.aggregate.ResultValidator` and `test.saga.FixtureExecutionResult` | `expectTriggeredDeadlines(Object...)` and `expectTriggeredDeadlinesMatching(Matcher)` |
| `test.deadline.DeadlineManagerValidator.assertDeadlinesMet(Object...)` and `assertDeadlinesMetMatching(Matcher)`                                     | `assertTriggeredDeadlines(Object...)` and `assertTriggeredDeadlinesMatching(Matcher)` |
| `test.deadline.StubDeadlineManager.getDeadlinesMet()`                                                                                                | `getTriggeredDeadlines()`                                                             |
| `test.aggregate.TestExecutor.andThenTimeElapses(Duration)` and `whenThenTimeElapses(Duration)`                                                       | `whenTimeElapses(Duration)`                                                           |
| `TestExecutor.andThenTimeAdvancesTo(Instant)` and `whenThenTimeAdvancesTo(Instant)`                                                                  | `whenTimeAdvancesTo(Instant)`                                                         |

### Removed configuration properties and values

| Removed                                                                                                      | Use instead                                                                                                               |
|--------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| The `java` value of `sophon.serializer.general`, `sophon.serializer.messages` and `sophon.serializer.events` | `xstream`, `jackson`, `jackson3`, `cbor` or `avro`. For data that `JavaSerializer` wrote, see [Stored data](#stored-data) |
| `sophon.tracing.nested-handlers`                                                                             | `sophon.tracing.event-processor.distributed-in-same-trace`                                                                |
| `sophon.tracing.nested-time-limit`                                                                           | `sophon.tracing.event-processor.distributed-in-same-trace-time-limit`                                                     |

If a serializer property is still set to `java`, the application fails at startup with an error that names the
property. Spring Boot ignores unknown properties, so the two `nested-*` properties have no effect anymore and cause no
error. Replace them with the new properties to keep the same behavior.

### Stored data

Sophon 2.0 has no `JavaSerializer`. Your event store, token store, saga store, dead-letter queue or scheduled
deadlines can contain data that `JavaSerializer` wrote. If they do, Sophon 2.0 cannot read that data out of the box.
Follow these steps to keep reading it:

1. Copy the `JavaSerializer` class of Sophon 1.2.x into your application, in your own package.
2. Configure your copy as the serializer for the components that read the old data. In Spring Boot, define your copy
   as a `Serializer` bean. To replace only the event or the message serializer, give the bean the qualifier
   `eventSerializer` or `messageSerializer`.
3. To move away from Java serialization, convert the stored data to another format with a one-time migration.

Sophon 2.0 also stops reading the oldest formats of scheduled Quartz jobs, and JobRunr jobs that call
`JobRunrDeadlineManager.execute(String)`. No Sophon version writes these formats. Only jobs that Axon Framework
scheduled can contain them, and [MIGRATING.md](MIGRATING.md) already says let those complete or schedule them
again.

### Behavior changes

* Sophon no longer finds `javax.persistence` annotations by name:
    * An aggregate whose identifier carries only `javax.persistence.Id` has no identifier. Use `jakarta.persistence.Id`
      or `@AggregateIdentifier`.
    * In a Spring application, an aggregate class with `javax.persistence.Entity` no longer gets a JPA repository. Use
      `jakarta.persistence.Entity`.
* Sophon's JPA entity classes, such as `eventhandling.tokenstore.jpa.TokenEntry`,
  `eventsourcing.eventstore.jpa.DomainEventEntry` and `modelling.saga.repository.jpa.SagaEntry`, carry only Jakarta
  Persistence annotations. A `javax.persistence` provider no longer maps them. The table and column definitions do
  not change.
* These builders no longer fall back to an XStream serializer that allows every type. Set a serializer, or `build()`
  fails with a `SophonConfigurationException`:
    * `deadline.quartz.QuartzDeadlineManager`.
    * `eventhandling.scheduling.quartz.QuartzEventScheduler`, unless you set an `EventJobDataBinder`.
    * `modelling.saga.repository.jdbc.JdbcSagaStore` and `modelling.saga.repository.jpa.JpaSagaStore`.
    * `eventsourcing.eventstore.jdbc.JdbcEventStorageEngine` and `eventsourcing.eventstore.jpa.JpaEventStorageEngine`,
      for both the event and the snapshot serializer.

  The `Configurer` and the Spring Boot auto-configuration set these serializers for you.
* `test.aggregate.TestExecutor.whenTimeElapses(Duration)` and `whenTimeAdvancesTo(Instant)` are abstract. A custom
  `TestExecutor` implementation must implement them.
* `eventhandling.tokenstore.AbstractTokenEntry` is no longer a JPA mapped superclass. The JPA token store uses
  `eventhandling.tokenstore.jpa.TokenEntry`, which does not extend it.
