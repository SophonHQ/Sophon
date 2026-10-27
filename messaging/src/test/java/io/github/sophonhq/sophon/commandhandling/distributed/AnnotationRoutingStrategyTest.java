/*
 * Copyright (c) 2010-2018. Axon Framework
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

package io.github.sophonhq.sophon.commandhandling.distributed;

import io.github.sophonhq.sophon.commandhandling.CommandMessage;
import io.github.sophonhq.sophon.commandhandling.GenericCommandMessage;
import io.github.sophonhq.sophon.commandhandling.RoutingKey;
import io.github.sophonhq.sophon.common.SophonConfigurationException;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link AnnotationRoutingStrategy}.
 *
 * @author Allard Buijze
 */
class AnnotationRoutingStrategyTest {

    private AnnotationRoutingStrategy testSubject;

    @BeforeEach
    void setUp() {
        testSubject = AnnotationRoutingStrategy.defaultStrategy();
    }

    @Test
    void getRoutingKeyFromField() {
        CommandMessage<SomeFieldAnnotatedCommand> testCommand = new GenericCommandMessage<>(new SomeFieldAnnotatedCommand());
        assertEquals("Target", testSubject.getRoutingKey(testCommand));

        CommandMessage<SomeOtherFieldAnnotatedCommand> otherTestCommand =
                new GenericCommandMessage<>(new SomeOtherFieldAnnotatedCommand());
        assertEquals("Target", testSubject.getRoutingKey(otherTestCommand));
    }

    @Test
    void getRoutingKeyFromMethod() {
        CommandMessage<SomeMethodAnnotatedCommand> testCommand =
                new GenericCommandMessage<>(new SomeMethodAnnotatedCommand());
        assertEquals("Target", testSubject.getRoutingKey(testCommand));

        CommandMessage<SomeOtherMethodAnnotatedCommand> otherTestCommand =
                new GenericCommandMessage<>(new SomeOtherMethodAnnotatedCommand());
        assertEquals("Target", testSubject.getRoutingKey(otherTestCommand));
    }

    @Test
    void nullRoutingKeyOnFieldThrowsCommandDispatchException() {
        AnnotationRoutingStrategy errorFallbackRoutingStrategy =
                AnnotationRoutingStrategy.builder()
                                         .fallbackRoutingStrategy(UnresolvedRoutingKeyPolicy.ERROR)
                                         .build();

        CommandMessage<SomeNullMethodAnnotatedCommand> testCommand =
                new GenericCommandMessage<>(new SomeNullMethodAnnotatedCommand());

        assertThrows(CommandDispatchException.class, () -> errorFallbackRoutingStrategy.getRoutingKey(testCommand));
    }

    @Test
    void nullRoutingKeyOnMethodThrowsCommandDispatchException() {
        AnnotationRoutingStrategy errorFallbackRoutingStrategy =
                AnnotationRoutingStrategy.builder()
                                         .fallbackRoutingStrategy(UnresolvedRoutingKeyPolicy.ERROR)
                                         .build();

        CommandMessage<SomeNullMethodAnnotatedCommand> testCommand =
                new GenericCommandMessage<>(new SomeNullMethodAnnotatedCommand());

        assertThrows(CommandDispatchException.class, () -> errorFallbackRoutingStrategy.getRoutingKey(testCommand));
    }

    @Test
    void resolvesRoutingKeyFromAnnotationDoesNotInvokeFallbackStrategy() {
        RoutingStrategy fallbackRoutingStrategy = mock(RoutingStrategy.class);

        AnnotationRoutingStrategy testSubjectWithMockedFallbackStrategy =
                AnnotationRoutingStrategy.builder()
                                         .fallbackRoutingStrategy(fallbackRoutingStrategy)
                                         .build();

        CommandMessage<SomeFieldAnnotatedCommand> testCommand =
                new GenericCommandMessage<>(new SomeFieldAnnotatedCommand());

        assertEquals("Target", testSubjectWithMockedFallbackStrategy.getRoutingKey(testCommand));
        verifyNoInteractions(fallbackRoutingStrategy);
    }

    @Test
    void resolvesRoutingKeyFromFallbackStrategy() {
        String expectedRoutingKey = "some-routing-key";
        RoutingStrategy fallbackRoutingStrategy = mock(RoutingStrategy.class);
        when(fallbackRoutingStrategy.getRoutingKey(any())).thenReturn(expectedRoutingKey);

        AnnotationRoutingStrategy testSubjectWithMockedFallbackStrategy =
                AnnotationRoutingStrategy.builder()
                                         .fallbackRoutingStrategy(fallbackRoutingStrategy)
                                         .build();

        CommandMessage<SomeCommandWithoutTheRoutingAnnotation> testCommand =
                new GenericCommandMessage<>(new SomeCommandWithoutTheRoutingAnnotation("target"));

        assertEquals(expectedRoutingKey, testSubjectWithMockedFallbackStrategy.getRoutingKey(testCommand));
        verify(fallbackRoutingStrategy).getRoutingKey(testCommand);
    }

    @Test
    void buildAnnotationRoutingStrategyFailsForNullFallbackRoutingStrategy() {
        AnnotationRoutingStrategy.Builder builderTestSubject = AnnotationRoutingStrategy.builder();
        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.fallbackRoutingStrategy(null));
    }

    @Test
    void buildAnnotationRoutingStrategyFailsForNullAnnotationType() {
        AnnotationRoutingStrategy.Builder builderTestSubject = AnnotationRoutingStrategy.builder();
        assertThrows(SophonConfigurationException.class, () -> builderTestSubject.annotationType(null));
    }

    public static class SomeFieldAnnotatedCommand {

        @SuppressWarnings("unused")
        @RoutingKey
        private final String target = "Target";
    }

    public static class SomeOtherFieldAnnotatedCommand {

        @SuppressWarnings("unused")
        @RoutingKey
        private final SomeObject target = new SomeObject("Target");
    }

    public static class SomeMethodAnnotatedCommand {

        @SuppressWarnings("FieldCanBeLocal")
        private final String target = "Target";

        @RoutingKey
        public String getTarget() {
            return target;
        }
    }

    public static class SomeOtherMethodAnnotatedCommand {

        private final SomeObject target = new SomeObject("Target");

        @RoutingKey
        public SomeObject getTarget() {
            return target;
        }
    }

    private static class SomeObject {

        private final String target;

        public SomeObject(String target) {
            this.target = target;
        }

        @Override
        public String toString() {
            return target;
        }
    }

    public static class SomeNullMethodAnnotatedCommand {

        private final String target = null;

        @RoutingKey
        public String getTarget() {
            //noinspection ConstantConditions
            return target;
        }
    }

    public static class SomeCommandWithoutTheRoutingAnnotation {

        private final String target;

        public SomeCommandWithoutTheRoutingAnnotation(String target) {
            this.target = target;
        }

        public String getTarget() {
            return target;
        }
    }
}
