# Migration

Module containing [OpenRewrite](https://docs.openrewrite.org/) recipes that migrate an application from Axon
Framework 4 to Sophon. See [MIGRATING.md](../MIGRATING.md) for everything that changes, including the steps the recipe
does not cover.

## Migrate from Axon Framework 4 to Sophon

### Maven

```bash
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.github.sophonhq.sophon:sophon-migration:LATEST \
  -Drewrite.activeRecipes=io.github.sophonhq.sophon.migration.MigrateFromAxonFramework4
```

### Gradle

Requires a custom
[Gradle init script](https://docs.openrewrite.org/running-recipes/running-rewrite-on-a-gradle-project-without-modifying-the-build).

## Recipes

| Recipe                                                               | Migrates                                                                         |
|----------------------------------------------------------------------|----------------------------------------------------------------------------------|
| `io.github.sophonhq.sophon.migration.MigrateFromAxonFramework4`      | Everything below                                                                 |
| `io.github.sophonhq.sophon.migration.ChangeAxonJavaCode`             | Packages, renamed types and members, string literals and imports in Java sources |
| `io.github.sophonhq.sophon.migration.ChangeAxonMavenCoordinates`     | `org.axonframework:axon-*` dependencies                                          |
| `io.github.sophonhq.sophon.migration.ChangeAxonConfigurationProperties` | `axon.*` properties in `.properties` and YAML files                           |
| `io.github.sophonhq.sophon.migration.ChangeAxonResources`            | ServiceLoader files, Spring Boot registrations, JPA descriptors and logging configuration |
