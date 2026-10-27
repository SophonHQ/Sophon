# Migrating from Axon Framework 4 to Sophon

Sophon 1.0 is a fork of Axon Framework 4.13.2. Its programming model is unchanged, but packages, types, Maven
coordinates, configuration properties and several runtime identifiers carry the Sophon name, and support for
proprietary AxonIQ products was removed. This guide describes what changes for an application migrating from Axon
Framework 4 and how to migrate.

## Before you start

* **Upgrade to Axon Framework 4.13 first.** Sophon starts from 4.13.2; migrating from an older 4.x version combines
  two upgrades into one.
* **Axon Server is not supported.** The Axon Server connector was removed, so Spring Boot applications now use local
  command, query and event infrastructure by default (what `axon.axonserver.enabled=false` did before). Applications
  that use Axon Server as event store need to export their events to a JPA or JDBC event store before migrating, and
  replace Axon Server based routing with a different solution.
* **AxonIQ Console and the AxonIQ update checker were removed.** Nothing replaces them.
* **Axon Framework extensions do not work with Sophon.** Extensions such as Kafka, AMQP, Mongo, JGroups, Spring Cloud,
  multitenancy, Reactor and Kotlin are built against `org.axonframework` and cannot be combined with Sophon. This might
  change in the future when we start gradually porting those extensions to Sophon.
* **The `axon-legacy` module was removed.** Data written by Axon Framework 2 or 3 is no longer supported.

## 1. Run the migration recipe

The `sophon-migration` module contains an [OpenRewrite](https://docs.openrewrite.org/) recipe that performs most of
the migration:

```shell
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.sophonhq.sophon:sophon-migration:1.0.0 \
  -Drewrite.activeRecipes=io.github.sophonhq.sophon.migration.MigrateFromAxonFramework4
```

The recipe:

* moves all references from the `org.axonframework` package to `io.github.sophonhq.sophon`, renames the types and
  members listed under [Renamed types and members](#renamed-types-and-members), and orders the imports of changed
  files;
* replaces Axon Framework package and type names, and `${axon.*}` placeholders, in Java string literals, for example
  in `@EntityScan` or `@Value`;
* replaces `org.axonframework:axon-*` dependencies with `io.github.sophonhq.sophon:sophon-*` (see
  [Maven coordinates](#maven-coordinates));
* renames `axon.*` configuration properties to `sophon.*` in `.properties` and YAML files;
* renames `META-INF/services` files of Sophon extension points and replaces Axon Framework class and package names in
  ServiceLoader files, `spring.factories`, `AutoConfiguration.imports`, JPA descriptors, logging configuration and
  property files.

Review the result: the recipe cannot see everything, as described in the next section.

## 2. Complete the migration manually

* **`axon-bom`**: Sophon has no BOM yet. Remove the `axon-bom` import and give the Sophon dependencies an explicit
  version.
* **Removed modules**: remove dependencies on `axon-server-connector`, `axon-legacy` and Axon Framework extensions, and
  any code using them, including exclusions of removed auto-configurations such as `AxonServerAutoConfiguration`.
* **Property names in code**: property names that are not `${...}` placeholders, for example in
  `@ConditionalOnProperty("axon.metrics.enabled")`, are not migrated. At startup Sophon logs a warning listing any
  `axon.*` properties and `AXON_*` environment variables it finds.
* **Bean names**: beans referenced by name need the new names listed under
  [Spring bean names](#spring-bean-names).
* **Runtime identifiers**: dashboards, alerts or integrations relying on message headers, tracing attributes or
  scheduler names need the new names listed under [Runtime identifiers](#runtime-identifiers).

## 3. Existing data

Table and column names are unchanged, so existing databases can be used without schema changes. However, Axon
Framework stores the class names of some framework objects, like tracking tokens, saga state and dead letters. These
names refer to `org.axonframework` classes that no longer exist.

To read such data, let the serializers translate Axon Framework class names when deserializing:

```properties
sophon.serializer.translate-legacy-class-names=true
```

Without Spring Boot, enable `translateLegacyClassNames()` on the builder of the `XStreamSerializer`,
`JacksonSerializer` (including CBOR), `Jackson3Serializer` or `JavaSerializer`. For the `AvroSerializer`, enable it on
its serializer delegate. The translation covers the serialized type as well as class names inside the serialized data,
such as the token inside a `ReplayToken` or the type ids written by Jackson. With the `JavaSerializer`, only classes
declaring a `serialVersionUID`, which includes the tracking tokens and `MetaData`, can be read this way. Data written by
Sophon uses
the new names; keep the translation enabled while stored data may still contain the old ones.

The following is **not** covered and needs to be handled before switching:

* **Scheduled events and deadlines** in Quartz, JobRunr or db-scheduler refer to Axon Framework classes and task names.
  Let pending schedules complete, or cancel and re-schedule them after migrating.
* **Axon Server event stores**, see [Before you start](#before-you-start).
* **Mixed deployments**: Axon Framework and Sophon applications cannot share a distributed command bus, and message
  headers used by messaging integrations changed (see [Runtime identifiers](#runtime-identifiers)).

## Reference

### Maven coordinates

The group id changes from `org.axonframework` to `io.github.sophonhq.sophon`; artifact ids change from `axon-*` to
`sophon-*`:

| Axon Framework 4                 | Sophon                             |
|----------------------------------|------------------------------------|
| `axon-messaging`                 | `sophon-messaging`                 |
| `axon-modelling`                 | `sophon-modelling`                 |
| `axon-eventsourcing`             | `sophon-eventsourcing`             |
| `axon-configuration`             | `sophon-configuration`             |
| `axon-disruptor`                 | `sophon-disruptor`                 |
| `axon-spring`                    | `sophon-spring`                    |
| `axon-spring-boot-autoconfigure` | `sophon-spring-boot-autoconfigure` |
| `axon-spring-boot-starter`       | `sophon-spring-boot-starter`       |
| `axon-test`                      | `sophon-test`                      |
| `axon-metrics`                   | `sophon-metrics`                   |
| `axon-micrometer`                | `sophon-micrometer`                |
| `axon-tracing-opentelemetry`     | `sophon-tracing-opentelemetry`     |
| `axon-server-connector`          | removed                            |
| `axon-legacy`                    | removed                            |

### Packages

All classes moved from `org.axonframework` to `io.github.sophonhq.sophon`, keeping their sub-packages. For example,
`org.axonframework.eventhandling.EventHandler` is now `io.github.sophonhq.sophon.eventhandling.EventHandler`.

### Renamed types and members

| Axon Framework 4                                                    | Sophon                                                                |
|---------------------------------------------------------------------|-----------------------------------------------------------------------|
| `common.AxonException`                                              | `common.SophonException`                                              |
| `common.AxonTransientException`                                     | `common.SophonTransientException`                                     |
| `common.AxonNonTransientException`                                  | `common.SophonNonTransientException`                                  |
| `common.AxonConfigurationException`                                 | `common.SophonConfigurationException`                                 |
| `common.AxonThreadFactory`                                          | `common.SophonThreadFactory`                                          |
| `commandhandling.gateway.AxonNonTransientExceptionClassesPredicate` | `commandhandling.gateway.SophonNonTransientExceptionClassesPredicate` |
| `messaging.timeout.AxonTaskJanitor`                                 | `messaging.timeout.SophonTaskJanitor`                                 |
| `messaging.timeout.AxonTimeLimitedTask`                             | `messaging.timeout.SophonTimeLimitedTask`                             |
| `messaging.timeout.AxonTimeoutException`                            | `messaging.timeout.SophonTimeoutException`                            |
| `config.AxonTaskJanitorShutdownConfigurerModule`                    | `config.SophonTaskJanitorShutdownConfigurerModule`                    |
| `spring.config.AxonConfiguration`                                   | `spring.config.SophonConfiguration`                                   |
| `spring.config.SpringAxonConfiguration`                             | `spring.config.SpringSophonConfiguration`                             |
| `spring.config.SpringAxonAutoConfigurer`                            | `spring.config.SpringSophonAutoConfigurer`                            |
| `spring.event.AxonStartedEvent`                                     | `spring.event.SophonStartedEvent`                                     |
| `springboot.autoconfig.AxonAutoConfiguration`                       | `springboot.autoconfig.SophonAutoConfiguration`                       |
| `springboot.autoconfig.AxonDbSchedulerAutoConfiguration`            | `springboot.autoconfig.SophonDbSchedulerAutoConfiguration`            |
| `springboot.autoconfig.AxonJobRunrAutoConfiguration`                | `springboot.autoconfig.SophonJobRunrAutoConfiguration`                |
| `springboot.autoconfig.AxonTimeoutAutoConfiguration`                | `springboot.autoconfig.SophonTimeoutAutoConfiguration`                |
| `springboot.autoconfig.AxonTracingAutoConfiguration`                | `springboot.autoconfig.SophonTracingAutoConfiguration`                |
| `test.AxonAssertionError`                                           | `test.SophonAssertionError`                                           |
| `XStreamSerializer.Builder#disableAxonTypeSecurity()`               | `XStreamSerializer.Builder#disableSophonTypeSecurity()`               |
| `SpringAxonAutoConfigurer.AXON_CONFIGURATION_BEAN`                  | `SpringSophonAutoConfigurer.SOPHON_CONFIGURATION_BEAN`                |
| `SpringAxonAutoConfigurer.AXON_CONFIGURER_BEAN`                     | `SpringSophonAutoConfigurer.SOPHON_CONFIGURER_BEAN`                   |

### Configuration properties

All Spring Boot properties moved from the `axon` prefix to `sophon`, for example `axon.serializer.general` is now
`sophon.serializer.general`. Properties of removed features, like `axon.axonserver.*` and `axon.update-check.*`, have
no replacement. Sophon adds `sophon.serializer.translate-legacy-class-names`, described under
[Existing data](#3-existing-data).

The default tracing attribute names also changed, see [Runtime identifiers](#runtime-identifiers). In addition,
Spring Boot applications now record aggregate identifiers of repository spans as `sophon.aggregateId` and deadline
scopes as `sophon.deadlineScope`, like other applications already did. Axon Framework's Spring Boot configuration
recorded both as `axon.scope` and ignored `axon.tracing.repository.aggregate-id-attribute-name`.

### Spring bean names

| Axon Framework 4                                    | Sophon                                                        |
|-----------------------------------------------------|---------------------------------------------------------------|
| `defaultAxonObjectMapper`                           | `defaultSophonObjectMapper`                                   |
| `defaultAxonCborMapper`                             | `defaultSophonCborMapper`                                     |
| `defaultAxonXStream`                                | `defaultSophonXStream`                                        |
| `defaultAxonSchemaStore`                            | `defaultSophonSchemaStore`                                    |
| `springAxonConfiguration`                           | `springSophonConfiguration`                                   |
| `springAxonConfigurer`                              | `springSophonConfigurer`                                      |
| `axonTransactionManager`                            | `sophonTransactionManager`                                    |
| `axonTimeoutConfigurerModule`                       | `sophonTimeoutConfigurerModule`                               |
| `org.axonframework.spring.config.AxonConfiguration` | `io.github.sophonhq.sophon.spring.config.SophonConfiguration` |

### Runtime identifiers

| Purpose                                                 | Axon Framework 4                              | Sophon                                            |
|---------------------------------------------------------|-----------------------------------------------|---------------------------------------------------|
| Message headers (`Headers`)                             | `axon-message-id`, `axon-metadata`, ...       | `sophon-message-id`, `sophon-metadata`, ...       |
| Spring Messaging header prefix                          | `axon-message-`                               | `sophon-message-`                                 |
| Tracing span attributes                                 | `axon_message_id`, `axon_payload_type`, ...   | `sophon_message_id`, `sophon_payload_type`, ...   |
| Tracing attributes of deadlines, sagas and repositories | `axon.deadlineId`, `axon.aggregateId`, ...    | `sophon.deadlineId`, `sophon.aggregateId`, ...    |
| OpenTelemetry tracer name                               | `AxonFramework-OpenTelemetry`                 | `Sophon-OpenTelemetry`                            |
| Quartz event scheduler job group                        | `AxonFramework-Events`                        | `Sophon-Events`                                   |
| db-scheduler and JobRunr event task name                | `AxonScheduledEvent`                          | `SophonScheduledEvent`                            |
| db-scheduler deadline task name                         | `AxonDeadline`                                | `SophonDeadline`                                  |
| JGroups cluster name default                            | `Axon`                                        | `Sophon`                                          |
| Timeout janitor thread and logger                       | `axon-janitor`                                | `sophon-janitor`                                  |
| Jackson module names                                    | `Axon-Jackson Module`, `Axon-Jackson3 Module` | `Sophon-Jackson Module`, `Sophon-Jackson3 Module` |
