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

package org.sophonhq.sophon.springboot.autoconfig;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the Spring Boot auto-configurations named in Sophon's ordering hints exist in the Spring Boot version of
 * the build. Spring Boot ignores a hint that names a missing class, so a class that a Spring Boot release moves would
 * otherwise change the order of Sophon's auto-configurations without an error.
 */
class AutoConfigurationOrderingHintsTest {

    private static final String METADATA = "META-INF/spring-autoconfigure-metadata.properties";
    private static final String SOPHON_AUTO_CONFIGURATIONS = "org.sophonhq.sophon.springboot.autoconfig.";
    private static final String SPRING_BOOT = "org.springframework.boot.";

    @Test
    void orderingHintsNameSpringBootClassesThatExist() throws IOException {
        Properties metadata = PropertiesLoaderUtils.loadAllProperties(METADATA);
        List<String> springBootClasses =
                metadata.stringPropertyNames()
                        .stream()
                        .filter(key -> key.startsWith(SOPHON_AUTO_CONFIGURATIONS))
                        .filter(key -> key.endsWith(".AutoConfigureAfter") || key.endsWith(".AutoConfigureBefore"))
                        .flatMap(key -> Arrays.stream(metadata.getProperty(key).split(",")))
                        .filter(name -> name.startsWith(SPRING_BOOT))
                        .distinct()
                        .toList();

        assertThat(springBootClasses).as("Spring Boot classes in Sophon's ordering hints")
                                     .isNotEmpty();
        assertThat(springBootClasses).as("Spring Boot classes in Sophon's ordering hints that do not exist")
                                     .filteredOn(name -> !ClassUtils.isPresent(name, getClass().getClassLoader()))
                                     .isEmpty();
    }
}
