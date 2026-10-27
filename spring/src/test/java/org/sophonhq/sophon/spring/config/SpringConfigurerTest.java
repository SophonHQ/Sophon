/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.sophonhq.sophon.spring.config;

import org.junit.jupiter.api.*;
import org.sophonhq.sophon.commandhandling.CommandBus;
import org.sophonhq.sophon.commandhandling.SimpleCommandBus;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.config.Configuration;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpringConfigurerTest {

    private ConfigurableListableBeanFactory context;

    @BeforeEach
    void setUp() {
        context = mock(ConfigurableListableBeanFactory.class);
        when(context.getBeanNamesForType(any(Class.class))).thenReturn(new String[]{});
    }

    @Test
    void springComponentLoadedWhenNoComponentConfigured() {
        when(context.getBeanNamesForType(CommandBus.class)).thenReturn(new String[]{"commandBus"});

        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        when(context.getBean("commandBus", CommandBus.class)).thenReturn(commandBus);

        SpringConfigurer configurer = new SpringConfigurer(context);

        assertSame(commandBus, configurer.buildConfiguration().commandBus());
    }

    @Test
    void springPrimaryBeanUsedWhenMultipleCandidates() {
        when(context.getBeanNamesForType(CommandBus.class)).thenReturn(new String[]{"commandBus", "alternative"});
        when(context.getMergedBeanDefinition(any())).thenReturn(new GenericBeanDefinition());
        GenericBeanDefinition primary = new GenericBeanDefinition();
        primary.setPrimary(true);
        when(context.getMergedBeanDefinition("commandBus")).thenReturn(primary);
        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        when(context.getBean("commandBus", CommandBus.class)).thenReturn(commandBus);

        SpringConfigurer configurer = new SpringConfigurer(context);

        assertSame(commandBus, configurer.buildConfiguration().commandBus());

        //noinspection unchecked
        verify(context, never()).getBean(eq("alternative"), any(Class.class));
    }

    @Test
    void failsWhenMultiplePrimaryCandidates() {
        when(context.getBeanNamesForType(CommandBus.class)).thenReturn(new String[]{"commandBus", "alternative"});
        GenericBeanDefinition primary = new GenericBeanDefinition();
        primary.setPrimary(true);
        when(context.getMergedBeanDefinition(any())).thenReturn(primary);
        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        when(context.getBean("commandBus", CommandBus.class)).thenReturn(commandBus);

        SpringConfigurer configurer = new SpringConfigurer(context);

        Configuration configuration = configurer.buildConfiguration();
        assertThrows(SophonConfigurationException.class, configuration::commandBus);

        //noinspection unchecked
        verify(context, never()).getBean(anyString(), any(Class.class));
    }

    @Test
    void failsWhenMultipleNonPrimaryCandidates() {
        when(context.getBeanNamesForType(CommandBus.class)).thenReturn(new String[]{"commandBus", "alternative"});
        GenericBeanDefinition nonPrimary = new GenericBeanDefinition();
        when(context.getMergedBeanDefinition(any())).thenReturn(nonPrimary);
        SimpleCommandBus commandBus = SimpleCommandBus.builder().build();
        when(context.getBean("commandBus", CommandBus.class)).thenReturn(commandBus);

        SpringConfigurer configurer = new SpringConfigurer(context);

        Configuration configuration = configurer.buildConfiguration();
        assertThrows(SophonConfigurationException.class, configuration::commandBus);

        //noinspection unchecked
        verify(context, never()).getBean(anyString(), any(Class.class));
    }

    @Test
    void usesConfigurerDefaultsWhenNoBeansDefined() {
        SpringConfigurer configurer = new SpringConfigurer(context);

        assertNotNull(configurer.buildConfiguration().commandBus());

        //noinspection unchecked
        verify(context, never()).getBean(anyString(), any(Class.class));
    }
}