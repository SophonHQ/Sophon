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

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;

/**
 * @author Allard Buijze
 */
class JavaSerializerTest {

    private JavaSerializer testSubject;

    @BeforeEach
    void setUp() {
        testSubject = JavaSerializer.builder().build();
    }

    @Test
    void serializeAndDeserialize() {
        SerializedObject<byte[]> serializedObject = testSubject.serialize(new MySerializableObject("hello"),
                                                                          byte[].class);
        assertThat(serializedObject.getType().getName()).isEqualTo(MySerializableObject.class.getName());
        assertThat(serializedObject.getType().getRevision()).isEqualTo("2166108932776672373");

        Object actualResult = testSubject.deserialize(serializedObject);
        assertThat(actualResult).isInstanceOf(MySerializableObject.class);
        assertThat(((MySerializableObject) actualResult).getSomeProperty()).isEqualTo("hello");
    }

    @Test
    void classForType() {
        Class actual = testSubject.classForType(new SimpleSerializedType(MySerializableObject.class.getName(),
                                                                         "2166108932776672373"));
        assertThat(actual).isEqualTo(MySerializableObject.class);
    }

    @Test
    void classForType_CustomRevisionResolver() {
        testSubject = JavaSerializer.builder()
                                    .revisionResolver(new FixedValueRevisionResolver("fixed"))
                                    .build();
        Class actual = testSubject.classForType(new SimpleSerializedType(MySerializableObject.class.getName(),
                                                                         "fixed"));
        assertThat(actual).isEqualTo(MySerializableObject.class);
    }

    @Test
    void classForType_UnknownClass() {
        assertThat(testSubject.classForType(new SimpleSerializedType("unknown", "0")))
                .isEqualTo(UnknownSerializedType.class);
    }

    @Test
    void deserializeNullValue() {
        SerializedObject<byte[]> serializedNull = testSubject.serialize(null, byte[].class);
        SimpleSerializedObject<byte[]> serializedNullString = new SimpleSerializedObject<>(
                serializedNull.getData(), byte[].class, testSubject.typeForClass(String.class)
        );
        assertThatObject(testSubject.deserialize(serializedNull)).isNull();
        assertThatObject(testSubject.deserialize(serializedNullString)).isNull();
    }

    @Test
    void deserializeEmptyBytes() {
        assertThat(testSubject.classForType(SerializedType.emptyType())).isEqualTo(Void.class);
        assertThatObject(testSubject.deserialize(new SimpleSerializedObject<>(new byte[0],
                                                                              byte[].class,
                                                                              SerializedType.emptyType())))
                .isNull();
    }

    private static class MySerializableObject implements Serializable {

        private static final long serialVersionUID = 2166108932776672373L;
        private String someProperty;

        public MySerializableObject(String someProperty) {
            this.someProperty = someProperty;
        }

        public String getSomeProperty() {
            return someProperty;
        }
    }
}
