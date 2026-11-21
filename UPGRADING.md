# Upgrading Sophon

This guide describes what changes between major versions of Sophon and what you do to upgrade. To move an
application from Axon Framework 4 to Sophon, read [MIGRATING.md](MIGRATING.md) first.

## Upgrading from 1.x to 2.0

### Before you start

* Upgrade to the latest 1.2.x release first.
* Compile your application with deprecation warnings enabled (`-Xlint:deprecation`), and replace every deprecated
  Sophon API that it uses. Sophon 2.0 removes every API that 1.x marks as deprecated.
* If your application needs a removed API, stay on 1.x.

In the tables below, class names are relative to the `org.sophonhq.sophon` package. For example,
`spring.config.AnnotationDriven` is `org.sophonhq.sophon.spring.config.AnnotationDriven`.

### Removed dependencies

### Removed API

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

| Removed                                                          | Use instead                                                                                                                                          |
|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `spring.config.AnnotationDriven`                                 | Spring Boot auto-configuration, or beans of `spring.config.MessageHandlerLookup`, `SpringAggregateLookup`, `SpringSagaLookup`, `SpringConfigurer` and `SpringSophonConfiguration` |
| `spring.config.AnnotationDrivenRegistrar`                        | The same beans as for `AnnotationDriven`                                                                                                             |
| `spring.config.SpringSophonAutoConfigurer`                       | `spring.config.SpringConfigurer` and `spring.config.SpringSophonConfiguration`                                                                       |
| `spring.config.SophonConfiguration`                              | `spring.config.SpringConfigurer` and `spring.config.SpringSophonConfiguration`                                                                       |
| `spring.config.ConfigurerFactoryBean`                            | `spring.config.SpringConfigurer`                                                                                                                     |
| `spring.config.RepositoryFactoryBean`                            | `spring.config.SpringAggregateLookup` and `spring.config.SpringAggregateConfigurer`                                                                  |
| `spring.modelling.SpringRepositoryFactoryBean`                   | Get the `Repository` from the Sophon `Configuration`, or use the factory methods of `spring.config.BeanHelper`                                       |
| `spring.config.AbstractAnnotationHandlerBeanPostProcessor`       | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                    |
| `spring.config.CommandHandlerSubscriber`                         | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                    |
| `spring.config.EventHandlerRegistrar`                            | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                    |
| `spring.config.QueryHandlerSubscriber`                           | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                    |
| `spring.config.annotation.AnnotationCommandHandlerBeanPostProcessor` | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                |
| `spring.config.annotation.AnnotationQueryHandlerBeanPostProcessor`   | `spring.config.MessageHandlerLookup` and `spring.config.MessageHandlerConfigurer`                                                                |
| `spring.config.ApplicationContextLookupParameterResolverFactory` | `spring.config.annotation.SpringParameterResolverFactoryBean`                                                                                        |
| `spring.config.annotation.SpringContextParameterResolverFactoryBuilder` | `spring.config.annotation.SpringParameterResolverFactoryBean`                                                                                 |
| `spring.config.annotation.SpringContextHandlerDefinitionBuilder` | `spring.config.annotation.HandlerDefinitionFactoryBean`                                                                                              |
| `spring.config.annotation.SpringHandlerDefinitionBean`           | `spring.config.annotation.HandlerDefinitionFactoryBean`                                                                                              |
| `spring.config.annotation.SpringHandlerEnhancerDefinitionBean`   | `spring.config.annotation.HandlerDefinitionFactoryBean`                                                                                              |
| `spring.config.NoBeanOfType`                                     | Spring Boot's `@ConditionalOnMissingBean`                                                                                                            |
| `spring.config.TransactionManagerFactoryBean`                    | A bean of `spring.messaging.unitofwork.SpringTransactionManager`                                                                                     |

### Removed configuration properties and values

### Stored data

### Behavior changes
