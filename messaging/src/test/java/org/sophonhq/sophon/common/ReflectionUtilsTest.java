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

package org.sophonhq.sophon.common;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sophonhq.sophon.common.ReflectionUtils.*;

/**
 * Test class validating the {@link ReflectionUtils}.
 *
 * @author Allard Buijze
 */
class ReflectionUtilsTest {

    @Test
    void findFieldsInClass() {
        Iterable<Field> actualFields = ReflectionUtils.fieldsOf(SomeSubType.class);
        int t = 0;
        for (Field actual : actualFields) {
            if (actual.isSynthetic()) {
                // this test is probably running with ByteCode manipulation. We ignore synthetic fields.
                continue;
            }
            switch (t++) {
                case 0:
                    assertThat(actual.getName()).isEqualTo("field3");
                    break;
                case 1:
                case 2:
                    assertThat("field1".equals(actual.getName()) || "field2".equals(actual.getName()))
                            .as("Expected either field1 or field2, but got " + actual.getName()
                                        + " declared in " + actual.getDeclaringClass().getName())
                            .isTrue();
                    break;
            }
        }
        assertThat(t).isGreaterThanOrEqualTo(2);
    }

    @Test
    void nonRecursivelyFindFieldsInClass() {
        Iterable<Field> actualFields = ReflectionUtils.fieldsOf(SomeSubType.class, NOT_RECURSIVE);
        int t = 0;
        for (Field actual : actualFields) {
            if (actual.isSynthetic()) {
                // this test is probably running with ByteCode manipulation. We ignore synthetic fields.
                continue;
            }
            assertThat(actual.getName()).isEqualTo("field3");
            t++;
        }
        assertThat(t).isGreaterThanOrEqualTo(1);
    }

    @Test
    void findMethodsInClass() {
        Iterable<Method> actualMethods = ReflectionUtils.methodsOf(SomeSubType.class);
        int t = 0;
        for (Method actual : actualMethods) {
            if (actual.isSynthetic()) {
                //  this test is probably running with bytecode manipulation. We ignore synthetic methods.
                continue;
            }
            switch (t++) {
                case 0:
                    assertThat(actual.getName()).isEqualTo("getField3");
                    break;
                case 1:
                    assertThat(actual.getName()).isEqualTo("getField3");
                    assertThat(actual.getDeclaringClass().getSimpleName()).isEqualTo("SomeSubInterface");
                    break;
                case 2:
                case 3:
                    assertThat("getField1".equals(actual.getName()) || "getField2".equals(actual.getName()))
                            .as("Expected either getField1 or getField2, but got " + actual.getName()
                                        + " declared in " + actual.getDeclaringClass().getName())
                            .isTrue();
                    break;
                case 4:
                    assertThat(actual.getDeclaringClass().getSimpleName()).isEqualTo("SomeInterface");
                    break;
            }
        }
        assertThat(t).isGreaterThanOrEqualTo(4);
    }

    @Test
    void nonRecursivelyFindMethodsInClass() {
        Iterable<Method> actualMethods = ReflectionUtils.methodsOf(SomeSubType.class, NOT_RECURSIVE);
        int t = 0;
        for (Method actual : actualMethods) {
            if (actual.isSynthetic()) {
                //  this test is probably running with bytecode manipulation. We ignore synthetic methods.
                continue;
            }
            switch (t++) {
                case 0:
                    assertThat(actual.getName()).isEqualTo("getField3");
                    break;
                case 1:
                    assertThat(actual.getName()).isEqualTo("getField3");
                    assertThat(actual.getDeclaringClass().getSimpleName()).isEqualTo("SomeSubInterface");
                    break;
            }
        }
        assertThat(t).isGreaterThanOrEqualTo(2);
    }

    @Test
    void getFieldValue() throws NoSuchFieldException {
        Object value = ReflectionUtils.getFieldValue(SomeType.class.getDeclaredField("field1"), new SomeSubType());
        assertThat(value).isEqualTo("field1");
    }

    @Test
    void setFieldValue() throws Exception {
        int expectedFieldValue = 4;
        SomeSubType testObject = new SomeSubType();
        ReflectionUtils.setFieldValue(SomeSubType.class.getDeclaredField("field3"), testObject, expectedFieldValue);
        assertThat(testObject.getField3()).isEqualTo(expectedFieldValue);
    }

    @Test
    void isAccessible() throws NoSuchFieldException {
        Field field1 = SomeType.class.getDeclaredField("field1");
        Field field2 = SomeType.class.getDeclaredField("field2");
        Field field3 = SomeSubType.class.getDeclaredField("field3");
        assertThat(ReflectionUtils.isAccessible(field1)).isFalse();
        assertThat(ReflectionUtils.isAccessible(field2)).isFalse();
        assertThat(ReflectionUtils.isAccessible(field3)).isTrue();
    }

    @Test
    void explicitlyUnequal_NullValues() {
        assertThat(explicitlyUnequal(null, null)).isFalse();
        assertThat(explicitlyUnequal(null, "")).isTrue();
        assertThat(explicitlyUnequal("", null)).isTrue();
    }

    @Test
    void hasEqualsMethodTest() {
        assertThat(hasEqualsMethod(String.class)).isTrue();
        assertThat(hasEqualsMethod(ArrayList.class)).isTrue();
        assertThat(hasEqualsMethod(SomeType.class)).isFalse();
    }

    @SuppressWarnings("StringOperationCanBeSimplified")
    @Test
    void explicitlyUnequal_ComparableValues() {
        assertThat(explicitlyUnequal("value", new String("value"))).isFalse();
        assertThat(explicitlyUnequal("value1", "value2")).isTrue();
    }

    @Test
    void explicitlyUnequal_OverridesEqualsMethod() {
        assertThat(explicitlyUnequal(Collections.singletonList("value"), Collections.singletonList("value"))).isFalse();
        assertThat(explicitlyUnequal(Collections.singletonList("value1"), Collections.singletonList("value"))).isTrue();
    }

    @Test
    void explicitlyUnequal_NoEqualsOrComparable() {
        assertThat(explicitlyUnequal(new SomeType(), new SomeType())).isFalse();
    }

    @Test
    void resolvePrimitiveWrapperTypeForLong() {
        assertThat(resolvePrimitiveWrapperType(long.class)).isEqualTo(Long.class);
    }

    @Test
    void getMemberValueFromField() throws NoSuchFieldException {
        assertThatObject(ReflectionUtils.getMemberValue(SomeType.class.getDeclaredField("field1"), new SomeSubType()))
                .isEqualTo("field1");
    }

    @Test
    void getMemberValueFromMethod() throws NoSuchMethodException {
        assertThatObject(ReflectionUtils.getMemberValue(SomeType.class.getDeclaredMethod("getField1"),
                                                        new SomeSubType()))
                .isEqualTo("field1");
        assertThatObject(ReflectionUtils.getMemberValue(SomeTypeWithMethods.class.getDeclaredMethod("someMethod"),
                                                        new SomeTypeWithMethods()))
                .isEqualTo("someMethodResult");
    }

    @Test
    void getMemberValueFromVoidMethod() throws NoSuchMethodException {
        SomeTypeWithMethods testObject = new SomeTypeWithMethods();
        Object voidReturnValue = getMemberValue(SomeTypeWithMethods.class.getDeclaredMethod("someVoidMethod"),
                                                testObject);
        assertThat(voidReturnValue).isNull();
        assertThat(testObject.voidMethodInvocations.get()).isOne();
    }

    @Test
    void getMemberValueFromConstructor() throws NoSuchMethodException {
        Constructor<SomeType> testConstructor = SomeType.class.getDeclaredConstructor();
        SomeSubType testTarget = new SomeSubType();
        assertThatThrownBy(() -> ReflectionUtils.getMemberValue(testConstructor, testTarget))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getMemberValueTypeFromField() throws NoSuchFieldException {
        Class<?> fieldType = getMemberValueType(SomeType.class.getDeclaredField("field1"));
        assertThat(fieldType).isEqualTo(String.class);
    }

    @Test
    void getMemberValueTypeFromMethod() throws NoSuchMethodException {
        Class<?> methodValueType = getMemberValueType(SomeSubType.class.getDeclaredMethod("getField3"));
        assertThat(methodValueType).isEqualTo(int.class);
        Class<?> voidResultType = getMemberValueType(SomeTypeWithMethods.class.getDeclaredMethod("someVoidMethod"));
        assertThat(voidResultType).isEqualTo(void.class);
    }

    @Test
    void getMemberValueTypeFromConstructor() throws NoSuchMethodException {
        Constructor<SomeType> testConstructor = SomeType.class.getDeclaredConstructor();
        assertThatThrownBy(() -> getMemberValueType(testConstructor)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invokeAndGetMethodValueTest() throws NoSuchMethodException {
        assertThatObject(invokeAndGetMethodValue(SomeTypeWithMethods.class.getDeclaredMethod("getField1"),
                                                 new SomeTypeWithMethods()))
                .isEqualTo("field1");
        assertThatObject(invokeAndGetMethodValue(SomeTypeWithMethods.class.getDeclaredMethod("someMethod"),
                                                 new SomeTypeWithMethods()))
                .isEqualTo("someMethodResult");

        SomeTypeWithMethods testObject = new SomeTypeWithMethods();
        Object voidMethodResult = invokeAndGetMethodValue(SomeTypeWithMethods.class.getDeclaredMethod("someVoidMethod"),
                                                          testObject);
        assertThat(voidMethodResult).isNull();
        assertThat(testObject.voidMethodInvocations.get()).isOne();
    }

    @Test
    void memberGenericTypeFromField() throws NoSuchFieldException {
        Field field = SomeType.class.getDeclaredField("field1");
        Type memberGenericType = getMemberGenericType(field);
        assertThat(memberGenericType).isEqualTo(field.getGenericType());
    }

    @Test
    void memberGenericTypeFromMethod() throws NoSuchMethodException {
        Method method = SomeTypeWithMethods.class.getDeclaredMethod("someMethod");
        Type memberGenericType = getMemberGenericType(method);
        assertThat(memberGenericType).isEqualTo(method.getGenericReturnType());
    }

    @Test
    void memberGenericTypeFromConstructor() throws NoSuchMethodException {
        Constructor<SomeTypeWithMethods> constructor = SomeTypeWithMethods.class.getDeclaredConstructor();
        assertThatThrownBy(() -> getMemberGenericType(constructor)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void memberGenericStringFromField() throws NoSuchFieldException {
        Field field = SomeType.class.getDeclaredField("field1");
        String memberGenericString = getMemberGenericString(field);
        assertThat(memberGenericString).isEqualTo(field.toGenericString());
    }

    @Test
    void memberGenericStringFromMethod() throws NoSuchMethodException {
        Method method = SomeTypeWithMethods.class.getDeclaredMethod("someMethod");
        String memberGenericString = getMemberGenericString(method);
        assertThat(memberGenericString).isEqualTo(method.toGenericString());
    }

    @Test
    void memberGenericStringFromConstructor() throws NoSuchMethodException {
        Constructor<SomeTypeWithMethods> constructor = SomeTypeWithMethods.class.getDeclaredConstructor();
        String memberGenericString = getMemberGenericString(constructor);
        assertThat(memberGenericString).isEqualTo(constructor.toGenericString());
    }


    @Test
    void toDiscernableSignatureOfConstructor() throws NoSuchMethodException {
        Constructor<SomeTypeWithMethods> constructor = SomeTypeWithMethods.class.getDeclaredConstructor();
        String memberGenericString = toDiscernibleSignature(constructor);
        assertThat(memberGenericString)
                .isEqualTo("org.sophonhq.sophon.common.ReflectionUtilsTest$SomeTypeWithMethods()");
    }

    @Test
    void toDiscernableSignatureOfMethod() throws NoSuchMethodException {
        Method method = SomeTypeWithMethods.class.getMethod("someMethodWithParameters", String.class, Integer.class, Object.class);
        String memberGenericString = toDiscernibleSignature(method);
        assertThat(memberGenericString)
                .isEqualTo("someMethodWithParameters(java.lang.String,java.lang.Integer,java.lang.Object)");
    }

    @SuppressWarnings("FieldCanBeLocal")
    private static class SomeType implements SomeInterface {

        private final String field1 = "field1";
        private final String field2 = "field2";

        @Override
        public String getField1() {
            return field1;
        }

        public String getField2() {
            return field2;
        }
    }

    public interface SomeInterface {

        @SuppressWarnings("unused")
        String getField1();
    }

    public interface SomeSubInterface {

        int getField3();
    }

    public static class SomeSubType extends SomeType implements SomeSubInterface {

        public int field3 = 3;

        @Override
        public int getField3() {
            return field3;
        }
    }

    private static class SomeTypeWithMethods implements SomeInterface {

        public AtomicInteger voidMethodInvocations = new AtomicInteger();

        @Override
        public String getField1() {
            return "field1";
        }

        public String someMethod() {
            return "someMethodResult";
        }

        public void someVoidMethod() {
            voidMethodInvocations.incrementAndGet();
        }

        public String someMethodWithParameters(String parameterOne, Integer parameterTwo, Object parameter3) {
            return "someMethodWithParametersResult";
        }
    }
}
