/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.sophonhq.sophon.serialization.avro;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.message.SchemaStore;
import org.apache.avro.util.ClassSecurityValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import org.sophonhq.sophon.common.SophonConfigurationException;
import org.sophonhq.sophon.messaging.MetaData;
import org.sophonhq.sophon.serialization.ChainingConverter;
import org.sophonhq.sophon.serialization.Converter;
import org.sophonhq.sophon.serialization.RevisionResolver;
import org.sophonhq.sophon.serialization.SerializationException;
import org.sophonhq.sophon.serialization.SerializedObject;
import org.sophonhq.sophon.serialization.SerializedType;
import org.sophonhq.sophon.serialization.Serializer;
import org.sophonhq.sophon.serialization.SimpleSerializedObject;
import org.sophonhq.sophon.serialization.SimpleSerializedType;
import org.sophonhq.sophon.serialization.UnknownSerializedType;
import org.sophonhq.sophon.serialization.avro.test.ComplexObject;
import org.sophonhq.sophon.serialization.avro.test.ComplexObjectSchemas;
import org.sophonhq.sophon.serialization.json.JacksonSerializer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Test Avro Serializer with default Java-SpecificRecord-Strategy.
 *
 * @author Simon Zambrovski
 * @author Jan Galinski
 * @since axon-4.11.0
 */
public class AvroSerializerTest {

    private static final ComplexObject complexObject = ComplexObject
            .newBuilder()
            .setValue1("foo")
            .setValue2("bar")
            .setValue3(42)
            .build();


    private final RevisionResolver revisionResolver = payloadType -> null;
    private AvroSerializer testSubject;
    private Serializer serializer;
    private SchemaStore.Cache store;
    private static final GenericRecordToByteArrayConverter toByteArrayConverter = new GenericRecordToByteArrayConverter();

    @BeforeAll
    static void trustTestRecords() {
        ClassSecurityValidator.setGlobal(
                ClassSecurityValidator.composite(
                        ClassSecurityValidator.DEFAULT,
                        ClassSecurityValidator.builder().add(ComplexObject.class).build()
                )
        );
    }

    @AfterAll
    static void resetAvroTrust() {
        ClassSecurityValidator.setGlobal(ClassSecurityValidator.DEFAULT);
    }

    @BeforeEach
    void setUp() {
        serializer = spy(JacksonSerializer.defaultSerializer());
        store = new SchemaStore.Cache();
        testSubject = AvroSerializer
                .builder()
                .serializerDelegate(serializer)
                .revisionResolver(revisionResolver)
                .schemaStore(store)
                .build();
    }

    @Test
    void testBuilderMandatoryValues() {
        assertThatThrownBy(() -> AvroSerializer.builder().build())
                .isInstanceOf(SophonConfigurationException.class)
                .hasMessage("SchemaStore is mandatory");

        assertThatThrownBy(() -> AvroSerializer.builder()
                                               .schemaStore(new SchemaStore.Cache())
                                               .build())
                .isInstanceOf(SophonConfigurationException.class)
                .hasMessage("SerializerDelegate is mandatory");

        assertThatThrownBy(() -> AvroSerializer
                .builder()
                .serializerDelegate(serializer)
                .schemaStore(new SchemaStore.Cache())
                .includeDefaultAvroSerializationStrategies(false)
                .build())
                .isInstanceOf(SophonConfigurationException.class)
                .hasMessage("At least one AvroSerializerStrategy must be provided.");

        // that should work fine
        assertThat(AvroSerializer
                .builder()
                .revisionResolver(c -> "")
                .serializerDelegate(serializer)
                .schemaStore(new SchemaStore.Cache())
                .includeDefaultAvroSerializationStrategies(false)
                .addSerializerStrategy(new SpecificRecordBaseSerializerStrategy(
                        new SchemaStore.Cache(),
                        c -> "",
                        new DefaultSchemaIncompatibilityChecker()
                ))
                .build()).isNotNull();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testBuilderSetterContracts() {

        assertThatThrownBy(() -> AvroSerializer.builder().revisionResolver(null))
                .isInstanceOf(SophonConfigurationException.class)
                .hasMessage("RevisionResolver may not be null");


        assertThatThrownBy(() -> AvroSerializer.builder().schemaStore(null))
                .isInstanceOf(SophonConfigurationException.class)
                .hasMessage("SchemaStore may not be null");


        assertThatThrownBy(() -> AvroSerializer.builder().serializerDelegate(null))
                .isInstanceOf(SophonConfigurationException.class)
                .hasMessage("Serializer delegate may not be null");

        assertThatThrownBy(() -> AvroSerializer.builder().addSerializerStrategy(null))
                .isInstanceOf(SophonConfigurationException.class)
                .hasMessage("AvroSerializerStrategy may not be null");
    }

    @Test
    void deliverCorrectConverter() {
        Converter converter = testSubject.getConverter();
        assertThat(converter).isInstanceOf(ChainingConverter.class);
        // this is registered by AvroSerializer
        assertThat(converter.canConvert(byte[].class, GenericRecord.class)).isTrue();
        // this is registered by the JacksonSerializer
        assertThat(converter.canConvert(byte[].class, JsonNode.class)).isTrue();
    }

    @Test
    void deliverUnknownClassIfTypeIsNotOnClasspath() {
        Class<?> clazz = testSubject.classForType(new SimpleSerializedType("org.acme.Foo", null));
        assertThat(clazz).isEqualTo(UnknownSerializedType.class);
    }

    @Test
    void deliverEmptyType() {
        assertThat(testSubject.typeForClass(null)).isEqualTo(SimpleSerializedType.emptyType());
        assertThat(testSubject.typeForClass(Void.class)).isEqualTo(SimpleSerializedType.emptyType());
    }

    @Test
    void deliverNonEmptyType() {
        assertThat(testSubject.typeForClass(String.class)).isEqualTo(new SimpleSerializedType(
                String.class.getCanonicalName(),
                revisionResolver.revisionOf(String.class)
        ));
    }


    @Test
    void canSerialize() {
        assertThat(testSubject.canSerializeTo(byte[].class)).isTrue();
        assertThat(testSubject.canSerializeTo(GenericRecord.class)).isTrue();

        assertThat(testSubject.canSerializeTo(String.class)).isTrue();
        assertThat(testSubject.canSerializeTo(InputStream.class)).isTrue();
        assertThat(testSubject.canSerializeTo(Integer.class)).isFalse();
    }

    @Test
    void serializeMetaDataByDelegate() {

        MetaData original = MetaData.from(singletonMap("test", "test"));
        SerializedObject<byte[]> serialized = testSubject.serialize(original, byte[].class);
        MetaData actual = testSubject.deserialize(serialized);

        assertThat(actual).isNotNull();
        assertThat(actual).containsEntry("test", "test");
        assertThat(actual.size()).isOne();

        verify(serializer).serialize(original, byte[].class);
        verify(serializer).deserialize(serialized);
    }

    @Test
    void serializeNull() {
        SerializedObject<byte[]> serialized = testSubject.serialize(null, byte[].class);
        assertThat(serialized.getType()).isEqualTo(SerializedType.emptyType());
        assertThat(serialized.getData()).containsExactly("null".getBytes(StandardCharsets.UTF_8));
        verify(serializer).serialize(null, byte[].class);
    }

    @Test
    void deserializeEmptyBytes() {
        assertThat(testSubject.classForType(SerializedType.emptyType())).isEqualTo(Void.class);
        assertThatObject(testSubject.deserialize(
                new SimpleSerializedObject<>(new byte[0], byte[].class, SerializedType.emptyType())
        )).isNull();
    }

    @Test
    void serializeAndDeserializeComplexObject() {
        store.addSchema(ComplexObject.getClassSchema());

        SerializedObject<byte[]> serialized = testSubject.serialize(complexObject, byte[].class);
        assertThat(serialized.getType().getName()).isEqualTo(ComplexObject.class.getCanonicalName());


        ComplexObject deserialized = testSubject.deserialize(serialized);
        assertThat(deserialized).isEqualTo(complexObject);
    }

    @Test
    void serializeFromCompatibleObjectAndDeserialize() {
        store.addSchema(ComplexObject.getClassSchema());
        Schema schema2 = ComplexObjectSchemas.compatibleSchema;
        store.addSchema(schema2);
        GenericData.Record record = new GenericData.Record(schema2);
        record.put("value1", complexObject.getValue1());
        record.put("value2", complexObject.getValue2());
        record.put("value3", complexObject.getValue3());

        byte[] encodedBytes = toByteArrayConverter.convert(record);

        SerializedObject<byte[]> serialized = createSerializedObject(
                encodedBytes,
                ComplexObject.class.getCanonicalName()
        );
        assertThat(serialized.getType().getName()).isEqualTo(ComplexObject.class.getCanonicalName());

        ComplexObject deserialized = testSubject.deserialize(serialized);
        assertThat(deserialized).isEqualTo(complexObject);
    }

    @Test
    void deserializeFromGenericRecord() {
        store.addSchema(ComplexObject.getClassSchema());
        Schema schema2 = ComplexObjectSchemas.compatibleSchema;
        store.addSchema(schema2);
        GenericData.Record record = new GenericData.Record(schema2);
        record.put("value1", complexObject.getValue1());
        record.put("value2", complexObject.getValue2());
        record.put("value3", complexObject.getValue3());


        SerializedObject<GenericRecord> serialized = createSerializedObject(record);

        ComplexObject deserialized = testSubject.deserialize(serialized);
        assertThat(deserialized).isEqualTo(complexObject);
    }

    @Test
    void serializeFromCompatibleWithAdditionalIgnoredFieldObjectAndDeserialize() {
        store.addSchema(ComplexObject.getClassSchema());
        Schema schema2 = ComplexObjectSchemas.compatibleSchemaWithAdditionalField;
        store.addSchema(schema2);
        GenericData.Record record = new GenericData.Record(schema2);
        record.put("value1", complexObject.getValue1());
        record.put("value2", complexObject.getValue2());
        record.put("value3", complexObject.getValue3());
        record.put("value4", "ignored value");

        byte[] encodedBytes = toByteArrayConverter.convert(record);

        SerializedObject<byte[]> serialized = createSerializedObject(
                encodedBytes,
                ComplexObject.class.getCanonicalName()
        );
        assertThat(serialized.getType().getName()).isEqualTo(ComplexObject.class.getCanonicalName());

        ComplexObject deserialized = testSubject.deserialize(serialized);
        assertThat(deserialized).isEqualTo(complexObject);
    }

    @Test
    void serializeFromCompatibleSchemaAndDeserializeUsingDefault() {
        store.addSchema(ComplexObject.getClassSchema());
        Schema schema2 = ComplexObjectSchemas.compatibleSchemaWithoutValue2;
        store.addSchema(schema2);
        GenericData.Record record = new GenericData.Record(schema2);
        record.put("value1", complexObject.getValue1());
        record.put("value3", complexObject.getValue3());

        byte[] encodedBytes = toByteArrayConverter.convert(record);

        SerializedObject<byte[]> serialized = createSerializedObject(
                encodedBytes,
                ComplexObject.class.getCanonicalName()
        );
        assertThat(serialized.getType().getName()).isEqualTo(ComplexObject.class.getCanonicalName());

        ComplexObject deserialized = testSubject.deserialize(serialized);
        assertThat(deserialized.getValue2()).isEqualTo("default value");
    }

    @ParameterizedTest
    @MethodSource("serializerAndIncompatibleSerializedObject")
    void failToDeserializeFromIncompatibleSchema(
            SerializedObject<byte[]> serialized,
            AvroSerializer serializer,
            DefaultSchemaIncompatibilityChecker incompatibilityChecker,
            String expectedMessage) {
        assertThatThrownBy(() -> serializer.deserialize(serialized))
                .isInstanceOf(SerializationException.class)
                .hasMessage(expectedMessage);
        // check the incompatibility remains in the cache
        assertThat(incompatibilityChecker.getIncompatibilitiesCache()).hasSize(1);
    }

    static Stream<Arguments> serializerAndIncompatibleSerializedObject() {

        Schema writerSchema = ComplexObjectSchemas.incompatibleSchema;

        SchemaCompatibility.Incompatibility incompatibility =
                SchemaCompatibility.checkReaderWriterCompatibility(
                        ComplexObject.getClassSchema(), writerSchema
                ).getResult().getIncompatibilities().get(0);


        SchemaStore.Cache schemaStore = new SchemaStore.Cache();
        schemaStore.addSchema(ComplexObject.getClassSchema());
        schemaStore.addSchema(writerSchema);

        DefaultSchemaIncompatibilityChecker incompatibilityChecker = new DefaultSchemaIncompatibilityChecker();

        GenericData.Record record = new GenericData.Record(writerSchema);
        record.put("value2", complexObject.getValue1());
        record.put("value3", complexObject.getValue3());

        byte[] encodedBytes = toByteArrayConverter.convert(record);

        SerializedObject<byte[]> serialized = createSerializedObject(
                encodedBytes,
                ComplexObject.class.getCanonicalName()
        );
        assertThat(serialized.getType().getName()).isEqualTo(ComplexObject.class.getCanonicalName());


        return Stream.of(
                Arguments.of(
                        serialized,
                        AvroSerializer
                                .builder()
                                .serializerDelegate(JacksonSerializer.defaultSerializer())
                                .revisionResolver((c) -> null)
                                .performSchemaCompatibilityCheck(true)
                                .includeSchemasInStackTraces(false)
                                .schemaStore(schemaStore)
                                .schemaIncompatibilityChecker(incompatibilityChecker)
                                .build(),
                        incompatibilityChecker,
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                "[" + AvroUtil.incompatibilityPrinter(incompatibility) + "]",
                                false
                        ).getMessage()
                ),
                Arguments.of(
                        serialized,
                        AvroSerializer
                                .builder()
                                .serializerDelegate(JacksonSerializer.defaultSerializer())
                                .revisionResolver((c) -> null)
                                .schemaStore(schemaStore)
                                .performSchemaCompatibilityCheck(true)
                                .includeSchemasInStackTraces(true)
                                .build(),
                        incompatibilityChecker,
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                "[" + AvroUtil.incompatibilityPrinter(incompatibility) + "]",
                                true
                        ).getMessage()
                ),
                Arguments.of(
                        serialized,
                        AvroSerializer
                                .builder()
                                .serializerDelegate(JacksonSerializer.defaultSerializer())
                                .revisionResolver((c) -> null)
                                .schemaStore(schemaStore)
                                .performSchemaCompatibilityCheck(false)
                                .includeSchemasInStackTraces(false)
                                .build(),
                        incompatibilityChecker,
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                (Exception) null,
                                false
                        ).getMessage()
                ),
                Arguments.of(
                        serialized,
                        AvroSerializer
                                .builder()
                                .serializerDelegate(JacksonSerializer.defaultSerializer())
                                .revisionResolver((c) -> null)
                                .schemaStore(schemaStore)
                                .performSchemaCompatibilityCheck(false)
                                .includeSchemasInStackTraces(true)
                                .build(),
                        incompatibilityChecker,
                        AvroUtil.createExceptionFailedToDeserialize(
                                ComplexObject.class,
                                ComplexObject.getClassSchema(),
                                writerSchema,
                                (Exception) null,
                                true
                        ).getMessage()
                )
        );
    }

    @Test
    void failToDeserializeIfClassIsNotAvailable() {
        Schema writerSchema = ComplexObjectSchemas.incompatibleSchema;
        store.addSchema(writerSchema);
        GenericData.Record record = new GenericData.Record(writerSchema);
        record.put("value2", complexObject.getValue1());
        record.put("value3", complexObject.getValue3());

        SimpleSerializedObject<GenericRecord> object = new SimpleSerializedObject<>(
                record,
                GenericRecord.class,
                new SimpleSerializedType("org.acme.Foo", null));
        Object deserialized = testSubject.deserialize(object);
        assertThat(deserialized.getClass()).isEqualTo(UnknownSerializedType.class);
    }


    private static SerializedObject<byte[]> createSerializedObject(byte[] payload, String objectType) {
        return new SimpleSerializedObject<>(
                payload,
                byte[].class,
                new SimpleSerializedType(objectType, null));
    }

    private static SerializedObject<GenericRecord> createSerializedObject(GenericRecord record) {
        return new SimpleSerializedObject<>(
                record,
                GenericRecord.class,
                new SimpleSerializedType(record.getSchema().getFullName(), null));
    }
}
