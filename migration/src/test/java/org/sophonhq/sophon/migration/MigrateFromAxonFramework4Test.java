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

package org.sophonhq.sophon.migration;

import org.assertj.core.api.Assertions;
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
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;
import static org.openrewrite.xml.Assertions.xml;
import static org.openrewrite.yaml.Assertions.yaml;

/**
 * Test class validating the {@code org.sophonhq.sophon.migration.MigrateFromAxonFramework4} recipe.
 */
class MigrateFromAxonFramework4Test implements RewriteTest {

    private static final String SOPHON_VERSION = sophonVersion();

    @TempDir
    static Path stubRepository;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.sophonhq.sophon.migration.MigrateFromAxonFramework4")
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
                        import org.sophonhq.sophon.common.SophonConfigurationException;
                        import org.sophonhq.sophon.eventhandling.EventHandler;
                        import org.sophonhq.sophon.serialization.xml.XStreamSerializer;
                        import org.sophonhq.sophon.spring.config.SpringSophonAutoConfigurer;
                        
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
                        
                            String[] packages = {"org.sophonhq.sophon.eventhandling.tokenstore.jpa", "com.example"};
                            String excluded = "org.sophonhq.sophon.springboot.autoconfig.SophonAutoConfiguration";
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
        stubArtifact("org.axonframework", "axon-spring-boot-starter", "4.13.2");
        stubArtifact("org.sophonhq.sophon", "sophon-spring-boot-starter", SOPHON_VERSION);
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
                                    <groupId>org.sophonhq.sophon</groupId>
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
                        logging.level.org.sophonhq.sophon=DEBUG
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
                            org.sophonhq.sophon: DEBUG
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
                        org.sophonhq.sophon.messaging.annotation.DefaultParameterResolverFactory
                        """,
                        spec -> spec.path(
                                "src/main/resources/META-INF/services/org.axonframework.messaging.annotation.ParameterResolverFactory"
                        ).afterRecipe(file -> assertThat(file.getSourcePath()).isEqualTo(
                                Paths.get("src/main/resources/META-INF/services/"
                                                  + "org.sophonhq.sophon.messaging.annotation.ParameterResolverFactory")
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
                            <class>org.sophonhq.sophon.eventhandling.tokenstore.jpa.TokenEntry</class>
                        </persistence-unit>
                        """,
                        spec -> spec.path("src/main/resources/META-INF/persistence.xml")
                )
        );
    }

    private static void stubArtifact(String groupId, String artifactId, String version) throws IOException {
        Path artifactDirectory = stubRepository.resolve(groupId.replace('.', '/')).resolve(artifactId);
        Files.createDirectories(artifactDirectory.resolve(version));
        Files.writeString(artifactDirectory.resolve("maven-metadata.xml"),
                          """
                          <metadata>
                              <groupId>%3$s</groupId>
                              <artifactId>%1$s</artifactId>
                              <versioning>
                                  <latest>%2$s</latest>
                                  <release>%2$s</release>
                                  <versions>
                                      <version>%2$s</version>
                                  </versions>
                              </versioning>
                          </metadata>
                          """.formatted(artifactId, version, groupId));
        Files.writeString(artifactDirectory.resolve(version).resolve(artifactId + "-" + version + ".pom"),
                          """
                          <project>
                              <modelVersion>4.0.0</modelVersion>
                              <groupId>%s</groupId>
                              <artifactId>%s</artifactId>
                              <version>%s</version>
                              <!-- A pom-packaged stub resolves without an accompanying jar -->
                              <packaging>pom</packaging>
                          </project>
                          """.formatted(groupId, artifactId, version));
    }

    private static ExecutionContext withStubRepository() {
        ExecutionContext ctx = new InMemoryExecutionContext(Assertions::fail);
        MavenExecutionContextView.view(ctx)
                                 .setAddCentralRepository(false)
                                 .setAddLocalRepository(false)
                                 .setRepositories(List.of(
                                         MavenRepository.builder()
                                                        .id("sophon-stubs")
                                                        .uri(stubRepository.toUri().toString())
                                                        .releases(true)
                                                        .snapshots(true)
                                                        .build()
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
