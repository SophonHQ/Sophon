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

package org.sophonhq.sophon.springboot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Logs a warning when the {@link Environment} contains properties in the Axon Framework 4 {@code axon.*} namespace.
 * Sophon reads its configuration from {@code sophon.*}, so these properties would otherwise be silently ignored after
 * migrating from Axon Framework 4.
 *
 * @since 1.0.0
 */
public class LegacyPropertiesDetector implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(LegacyPropertiesDetector.class);

    private final Environment environment;

    /**
     * Constructs a {@link LegacyPropertiesDetector} checking the given {@code environment}.
     *
     * @param environment The {@link Environment} to check for Axon Framework 4 properties.
     */
    public LegacyPropertiesDetector(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        SortedSet<String> legacyPropertyNames = legacyPropertyNames();
        if (!legacyPropertyNames.isEmpty()) {
            logger.warn("Found Axon Framework configuration properties, which Sophon ignores: {}. "
                                + "Rename them from axon.* to sophon.* (or AXON_* to SOPHON_* for environment "
                                + "variables). See https://github.com/SophonHQ/Sophon/blob/main/MIGRATING.md.",
                        legacyPropertyNames);
        }
    }

    /**
     * Returns the names of all properties in the Axon Framework 4 namespace, being {@code axon.*} properties and
     * {@code AXON_*} environment variables.
     *
     * @return The sorted names of all Axon Framework 4 properties.
     */
    public SortedSet<String> legacyPropertyNames() {
        SortedSet<String> names = new TreeSet<>();
        if (!(environment instanceof ConfigurableEnvironment)) {
            return names;
        }
        for (PropertySource<?> propertySource : ((ConfigurableEnvironment) environment).getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource) {
                for (String name : ((EnumerablePropertySource<?>) propertySource).getPropertyNames()) {
                    if (name.startsWith("axon.") || name.startsWith("AXON_")) {
                        names.add(name);
                    }
                }
            }
        }
        return names;
    }
}
