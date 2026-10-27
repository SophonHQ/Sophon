/*
 * Copyright (c) 2026. SophonHQ
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

package io.github.sophonhq.sophon.migration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

/**
 * Test class validating the {@code io.github.sophonhq.sophon.migration.MigrateFromAxonFramework4} recipe.
 */
class MigrateFromAxonFramework4Test implements RewriteTest {

    private static final String SOPHON_VERSION = sophonVersion();

    @TempDir
    static Path stubRepository;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("io.github.sophonhq.sophon.migration.MigrateFromAxonFramework4")
            .parser(JavaParser.fromJavaVersion().classpath("axon-messaging", "axon-configuration", "axon-spring"));
    }

    @Test
    void migratesPackagesTypesAndMembers() {
        rewriteRun(
                java(
                        """
                                import org.axonframework.common.AxonConfigurationException;
                                import org.axonframework.eventhandling.EventHandler;
                                import org.axonframework.serialization.xml.XStreamSerializer;
                                import org.axonframework.spring.config.SpringAxonAutoConfigurer;

                                class Projection {

                                    private final XStreamSerializer serializer =
                                            XStreamSerializer.builder().disableAxonTypeSecurity().build();
                                    private final String configurationBean = SpringAxonAutoConfigurer.AXON_CONFIGURATION_BEAN;

                                    @EventHandler
                                    void on(String event) {
                                        if (event.isEmpty()) {
                                            throw new AxonConfigurationException("empty");
                                        }
                                    }
                                }
                                """,
                        """
                                import io.github.sophonhq.sophon.common.SophonConfigurationException;
                                import io.github.sophonhq.sophon.eventhandling.EventHandler;
                                import io.github.sophonhq.sophon.serialization.xml.XStreamSerializer;
                                import io.github.sophonhq.sophon.spring.config.SpringSophonAutoConfigurer;

                                class Projection {

                                    private final XStreamSerializer serializer =
                                            XStreamSerializer.builder().disableSophonTypeSecurity().build();
                                    private final String configurationBean = SpringSophonAutoConfigurer.SOPHON_CONFIGURATION_BEAN;

                                    @EventHandler
                                    void on(String event) {
                                        if (event.isEmpty()) {
                                            throw new SophonConfigurationException("empty");
                                        }
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void migratesReferencesInStringLiterals() {
        rewriteRun(
                java(
                        """
                                class JpaConfiguration {

                                    String[] packages = {"org.axonframework.eventhandling.tokenstore.jpa", "com.example"};
                                    String excluded = "org.axonframework.springboot.autoconfig.AxonAutoConfiguration";
                                    String placeholder = "${axon.serializer.general}";
                                    String unrelated = "axon";
                                }
                                """,
                        """
                                class JpaConfiguration {

                                    String[] packages = {"io.github.sophonhq.sophon.eventhandling.tokenstore.jpa", "com.example"};
                                    String excluded = "io.github.sophonhq.sophon.springboot.autoconfig.SophonAutoConfiguration";
                                    String placeholder = "${sophon.serializer.general}";
                                    String unrelated = "axon";
                                }
                                """
                )
        );
    }

    @Test
    void migratesMavenCoordinates() throws IOException {
        // Sophon artifacts may not be published yet, so serve stubs of the ones the recipe resolves.
        stubArtifact("sophon-spring-boot-starter");
        rewriteRun(
                spec -> spec.executionContext(withStubRepository()),
                pomXml(
                        """
                                <project>
                                    <modelVersion>4.0.0</modelVersion>
                                    <groupId>com.example</groupId>
                                    <artifactId>app</artifactId>
                                    <version>1.0.0</version>
                                    <dependencies>
                                        <dependency>
                                            <groupId>org.axonframework</groupId>
                                            <artifactId>axon-spring-boot-starter</artifactId>
                                            <version>4.13.2</version>
                                        </dependency>
                                    </dependencies>
                                </project>
                                """,
                        """
                                <project>
                                    <modelVersion>4.0.0</modelVersion>
                                    <groupId>com.example</groupId>
                                    <artifactId>app</artifactId>
                                    <version>1.0.0</version>
                                    <dependencies>
                                        <dependency>
                                            <groupId>io.github.sophonhq.sophon</groupId>
                                            <artifactId>sophon-spring-boot-starter</artifactId>
                                            <version>%s</version>
                                        </dependency>
                                    </dependencies>
                                </project>
                                """.formatted(SOPHON_VERSION)
                )
        );
    }

    @Test
    void migratesPropertiesFiles() {
        rewriteRun(
                properties(
                        """
                                axon.serializer.general=jackson
                                axon.eventhandling.processors.payments.mode=pooled
                                logging.level.org.axonframework=DEBUG
                                spring.application.name=app
                                """,
                        """
                                sophon.serializer.general=jackson
                                sophon.eventhandling.processors.payments.mode=pooled
                                logging.level.io.github.sophonhq.sophon=DEBUG
                                spring.application.name=app
                                """,
                        spec -> spec.path("src/main/resources/application.properties")
                )
        );
    }

    @Test
    void migratesYamlFiles() {
        rewriteRun(
                yaml(
                        """
                                axon:
                                  serializer:
                                    general: jackson
                                logging:
                                  level:
                                    org.axonframework: DEBUG
                                """,
                        """
                                sophon:
                                  serializer:
                                    general: jackson
                                logging:
                                  level:
                                    io.github.sophonhq.sophon: DEBUG
                                """,
                        spec -> spec.path("src/main/resources/application.yml")
                )
        );
    }

    @Test
    void migratesServiceLoaderFiles() {
        rewriteRun(
                text(
                        """
                                com.example.CustomParameterResolverFactory
                                org.axonframework.messaging.annotation.DefaultParameterResolverFactory
                                """,
                        """
                                com.example.CustomParameterResolverFactory
                                io.github.sophonhq.sophon.messaging.annotation.DefaultParameterResolverFactory
                                """,
                        spec -> spec.path(
                                "src/main/resources/META-INF/services/org.axonframework.messaging.annotation.ParameterResolverFactory"
                        ).afterRecipe(file -> assertEquals(
                                Paths.get("src/main/resources/META-INF/services/"
                                                  + "io.github.sophonhq.sophon.messaging.annotation.ParameterResolverFactory"),
                                file.getSourcePath()
                        ))
                )
        );
    }

    @Test
    void migratesJpaDescriptors() {
        rewriteRun(
                xml(
                        """
                                <persistence-unit name="app">
                                    <class>org.axonframework.eventhandling.tokenstore.jpa.TokenEntry</class>
                                </persistence-unit>
                                """,
                        """
                                <persistence-unit name="app">
                                    <class>io.github.sophonhq.sophon.eventhandling.tokenstore.jpa.TokenEntry</class>
                                </persistence-unit>
                                """,
                        spec -> spec.path("src/main/resources/META-INF/persistence.xml")
                )
        );
    }

    private static void stubArtifact(String artifactId) throws IOException {
        Path artifactDirectory = stubRepository.resolve("io/github/sophonhq/sophon").resolve(artifactId);
        Files.createDirectories(artifactDirectory.resolve(SOPHON_VERSION));
        Files.writeString(artifactDirectory.resolve("maven-metadata.xml"), """
                <metadata>
                    <groupId>io.github.sophonhq.sophon</groupId>
                    <artifactId>%1$s</artifactId>
                    <versioning>
                        <latest>%2$s</latest>
                        <release>%2$s</release>
                        <versions>
                            <version>%2$s</version>
                        </versions>
                    </versioning>
                </metadata>
                """.formatted(artifactId, SOPHON_VERSION));
        Files.writeString(artifactDirectory.resolve(SOPHON_VERSION).resolve(artifactId + "-" + SOPHON_VERSION + ".pom"), """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>io.github.sophonhq.sophon</groupId>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                    <!-- A pom-packaged stub resolves without an accompanying jar -->
                    <packaging>pom</packaging>
                </project>
                """.formatted(artifactId, SOPHON_VERSION));
    }

    private static ExecutionContext withStubRepository() {
        ExecutionContext ctx = new InMemoryExecutionContext(t -> fail(t));
        MavenExecutionContextView.view(ctx).setRepositories(Arrays.asList(
                MavenRepository.builder()
                               .id("sophon-stubs")
                               .uri(stubRepository.toUri().toString())
                               .releases(true)
                               .snapshots(true)
                               .build(),
                MavenRepository.MAVEN_CENTRAL
        ));
        return ctx;
    }

    private static String sophonVersion() {
        try (InputStream in = MigrateFromAxonFramework4Test.class.getResourceAsStream("/migration-test.properties")) {
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("sophon.version");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
