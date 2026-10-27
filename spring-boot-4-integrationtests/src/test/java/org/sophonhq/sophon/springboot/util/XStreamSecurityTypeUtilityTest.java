/*
 * Copyright (c) 2010-2026. Axon Framework
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

package org.sophonhq.sophon.springboot.util;

import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.sophonhq.sophon.springboot.util.XStreamSecurityTypeUtility.autoConfigBasePackages;

/**
 * Test class validating the {@link XStreamSecurityTypeUtility} through the {@link ApplicationContextRunner}. This
 * ensures an actual {@link org.springframework.context.ApplicationContext} is used for validating.
 *
 * @author Steven van Beelen
 */
class XStreamSecurityTypeUtilityTest {

    @Test
    void autoConfigBasePackagesTest() {
        // Sophon packages are added through the default ApplicationContextRunner that adds Sophon's DefaultEntityRegistrar.
        String[] expected = new String[]{
                "org.sophonhq.sophon.springboot.util.**",
                "org.sophonhq.sophon.eventhandling.tokenstore.**",
                "org.sophonhq.sophon.eventhandling.deadletter.jpa.**",
                "org.sophonhq.sophon.modelling.saga.repository.jpa.**",
                "org.sophonhq.sophon.eventsourcing.eventstore.jpa.**",
        };
        new ApplicationContextRunner()
                                      .withConfiguration(AutoConfigurations.of(TestContext.class))
                                      .run(context -> {
                                          String[] result = autoConfigBasePackages(context.getSourceApplicationContext());
                                          assertArrayEquals(expected, result);
                                      });
    }

    @EnableAutoConfiguration
    static class TestContext {

    }
}
