/*
 * Copyright (c) 2010-2022. Axon Framework
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

package io.github.sophonhq.sophon.spring.config;

import io.github.sophonhq.sophon.config.Configuration;
import io.github.sophonhq.sophon.config.Configurer;
import io.github.sophonhq.sophon.spring.event.SophonStartedEvent;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class SpringSophonConfigurationTest {

    @Test
    void sophonStartedEventIsPublished() {
        Configurer configurer = mock(Configurer.class);
        ApplicationContext context = mock(ApplicationContext.class);
        Configuration configuration = mock(Configuration.class);
        when(configurer.buildConfiguration()).thenReturn(configuration);

        SpringSophonConfiguration springSophonConfiguration = new SpringSophonConfiguration(configurer);
        springSophonConfiguration.setApplicationContext(context);

        springSophonConfiguration.start();
        verify(context).publishEvent(isA(SophonStartedEvent.class));
    }

    @Test
    void lifecyclePhaseIsLowerThanWebServers() {
        int webServerGracefulShutdownLifecyclePhase = SmartLifecycle.DEFAULT_PHASE - 1024;
        int webServerDefaultLifecyclePhase = webServerGracefulShutdownLifecyclePhase - 1024;

        SpringSophonConfiguration springSophonConfiguration = new SpringSophonConfiguration(mock(Configurer.class));

        assertTrue(springSophonConfiguration.getPhase() < webServerDefaultLifecyclePhase);
    }
}
