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

package org.sophonhq.sophon.serialization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
class LazyDeserializingObjectTest {

    private Serializer mockSerializer;

    private SerializedType mockType;
    private SerializedObject mockObject;

    private String mockDeserializedObject = "I'm a mock";

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mockSerializer = mock(Serializer.class);
        mockType = mock(SerializedType.class);
        mockObject = new SimpleSerializedObject(mockDeserializedObject, String.class, mockType);

        when(mockSerializer.classForType(mockType)).thenReturn(String.class);
        when(mockSerializer.deserialize(mockObject)).thenReturn(mockDeserializedObject);
    }

    @SuppressWarnings("unchecked")
    @Test
    void lazilyDeserialized() {
        LazyDeserializingObject<Object> testSubject = new LazyDeserializingObject<>(mockObject, mockSerializer);
        verify(mockSerializer, never()).deserialize(any(SerializedObject.class));
        assertThat(testSubject.getType()).isEqualTo(String.class);
        assertThat(testSubject.isDeserialized()).isFalse();
        verify(mockSerializer, never()).deserialize(any(SerializedObject.class));
        assertThat(testSubject.getObject()).isSameAs(mockDeserializedObject);
        assertThat(testSubject.isDeserialized()).isTrue();
    }

    @Test
    void lazilyDeserialized_NullObject() {
        assertThatThrownBy(() -> new LazyDeserializingObject<>(null, mockSerializer))
                .isInstanceOf(Exception.class);
    }

    @Test
    void lazilyDeserialized_NullSerializer() {
        assertThatThrownBy(() -> new LazyDeserializingObject<>(mockObject, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withProvidedDeserializedInstance() {
        LazyDeserializingObject<Object> testSubject = new LazyDeserializingObject<>(mockDeserializedObject);
        assertThat(testSubject.getType()).isEqualTo(mockDeserializedObject.getClass());
        assertThat(testSubject.getObject()).isSameAs(mockDeserializedObject);
        assertThat(testSubject.isDeserialized()).isTrue();
    }

    @Test
    void withProvidedDeserializedNullInstance() {
        assertThatThrownBy(() -> new LazyDeserializingObject<>(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
